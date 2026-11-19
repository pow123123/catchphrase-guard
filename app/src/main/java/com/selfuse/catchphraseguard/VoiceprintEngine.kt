package com.selfuse.catchphraseguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

sealed class VoiceCaptureResult {
    data class Success(
        val embeddings: List<FloatArray>,
        val primaryEmbedding: FloatArray,
        val activeDurationSec: Float
    ) : VoiceCaptureResult()

    data object TooQuiet : VoiceCaptureResult()
    data class Error(val message: String) : VoiceCaptureResult()
}

object VoiceprintManager {
    private const val TAG = "VoiceprintManager"
    const val SAMPLE_RATE = 16000
    private const val TARGET_RMS = 0.085f

    private val lock = Any()
    private var extractor: SpeakerEmbeddingExtractor? = null

    private fun getOrCreateExtractor(context: Context): SpeakerEmbeddingExtractor {
        synchronized(lock) {
            extractor?.let { return it }
            val config = SpeakerEmbeddingExtractorConfig(
                model = "model/speaker.onnx",
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val created = SpeakerEmbeddingExtractor(context.applicationContext.assets, config)
            extractor = created
            return created
        }
    }

    fun warmUp(context: Context) {
        try {
            getOrCreateExtractor(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to warm up SpeakerEmbeddingExtractor", e)
        }
    }

    fun createCleanAudioRecord(bufferSizeInBytes: Int): AudioRecord {
        val vrRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )
        if (vrRecord.state == AudioRecord.STATE_INITIALIZED) {
            return vrRecord
        }
        try {
            vrRecord.release()
        } catch (_: Exception) {
        }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )
    }

    /**
     * Removes DC offset and normalizes speech loudness to a consistent RMS target
     * without chopping internal unvoiced consonants or pauses.
     */
    fun normalizeSpeechWaveform(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var sum = 0.0
        for (s in samples) {
            sum += s
        }
        val mean = (sum / samples.size).toFloat()

        var sumSq = 0.0
        for (s in samples) {
            val centered = s - mean
            sumSq += centered * centered
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        if (rms < 1e-5f) return samples

        val gain = (TARGET_RMS / rms).coerceIn(0.6f, 6.5f)
        return FloatArray(samples.size) { i ->
            val v = (samples[i] - mean) * gain
            v.coerceIn(-0.96f, 0.96f)
        }
    }

    fun extractEmbedding(
        context: Context,
        rawSamples: FloatArray,
        sampleRate: Int = SAMPLE_RATE
    ): FloatArray? {
        if (rawSamples.size < (sampleRate * 0.35f).toInt()) return null
        val prepared = normalizeSpeechWaveform(rawSamples)
        return try {
            synchronized(lock) {
                val ext = getOrCreateExtractor(context)
                val stream = ext.createStream()
                try {
                    stream.acceptWaveform(prepared, sampleRate)
                    stream.inputFinished()
                    if (!ext.isReady(stream)) return null
                    val raw = ext.compute(stream)
                    if (raw.isEmpty()) null else l2Normalize(raw)
                } finally {
                    stream.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting speaker embedding", e)
            null
        }
    }

    /**
     * Extracts both full-utterance embedding and short (~1.1s) sub-window embeddings
     * so short catchphrases ("没有", "不是") match temporal pooling statistics accurately.
     */
    fun extractMultiScaleEmbeddings(context: Context, trimmedSpeech: FloatArray): List<FloatArray> {
        val results = ArrayList<FloatArray>(3)
        extractEmbedding(context, trimmedSpeech, SAMPLE_RATE)?.let { results.add(it) }

        val subWindowSamples = (SAMPLE_RATE * 1.15f).toInt() // ~1.15s
        if (trimmedSpeech.size >= (SAMPLE_RATE * 1.65f).toInt()) {
            val headSlice = trimmedSpeech.copyOfRange(0, subWindowSamples)
            extractEmbedding(context, headSlice, SAMPLE_RATE)?.let { results.add(it) }

            val tailStart = (trimmedSpeech.size - subWindowSamples).coerceAtLeast(0)
            val tailSlice = trimmedSpeech.copyOfRange(tailStart, trimmedSpeech.size)
            extractEmbedding(context, tailSlice, SAMPLE_RATE)?.let { results.add(it) }
        }
        return results
    }

    fun computeBestSimilarity(liveEmbedding: FloatArray, enrolledVectors: List<FloatArray>): Float {
        if (enrolledVectors.isEmpty()) return 1.0f
        val normLive = l2Normalize(liveEmbedding)
        val matches = ArrayList<Float>(enrolledVectors.size)
        for (ref in enrolledVectors) {
            if (ref.size == normLive.size) {
                var dot = 0f
                for (i in normLive.indices) {
                    dot += normLive[i] * ref[i]
                }
                matches.add(dot)
            }
        }
        if (matches.isEmpty()) return 0f
        matches.sortDescending()
        // Blend top-1 and top-2 match slightly when multiple sub-windows exist for stability
        val best = matches[0]
        val second = if (matches.size > 1) matches[1] else best
        val score = 0.75f * best + 0.25f * second
        return score.coerceIn(0f, 1f)
    }

    fun buildEnrolledReferenceSet(stepEmbeddings: List<FloatArray>): List<FloatArray> {
        if (stepEmbeddings.isEmpty()) return emptyList()
        val normalizedList = stepEmbeddings.map { l2Normalize(it) }
        if (normalizedList.size == 1) return normalizedList

        val dim = normalizedList.first().size
        val sum = FloatArray(dim)
        var count = 0
        for (vec in normalizedList) {
            if (vec.size == dim) {
                for (i in 0 until dim) {
                    sum[i] += vec[i]
                }
                count++
            }
        }
        return if (count > 1) {
            normalizedList + listOf(l2Normalize(sum))
        } else {
            normalizedList
        }
    }

    fun l2Normalize(vec: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vec) {
            sumSq += v * v
        }
        val norm = sqrt(sumSq).toFloat()
        if (norm < 1e-8f) return vec
        return FloatArray(vec.size) { vec[it] / norm }
    }

    suspend fun captureInteractiveUtterance(
        context: Context,
        stopSignal: CompletableDeferred<Unit>,
        autoStopOnSilence: Boolean = true,
        maxDurationMs: Long = 9000L,
        multiScale: Boolean = true,
        onLiveFeedback: (elapsedSec: Float, rmsLevel: Float, voicedSec: Float) -> Unit
    ): VoiceCaptureResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            return@withContext VoiceCaptureResult.Error("缺少麦克风权限，请先允许录音权限")
        }

        val wasServiceRunning = MonitorStateStore.uiState.value.isRunning
        if (wasServiceRunning) {
            val pauseIntent = Intent(appContext, MonitorForegroundService::class.java).apply {
                action = MonitorForegroundService.ACTION_PAUSE_FOR_ENROLL
            }
            appContext.startService(pauseIntent)
            var waited = 0
            while (!MonitorStateStore.uiState.value.isMicPausedForEnroll && waited < 18) {
                delay(50)
                waited++
            }
            delay(80)
        }

        var audioRecord: AudioRecord? = null
        try {
            val chunkSamples = 1280 // 80ms per frame at 16kHz
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuf, chunkSamples * 2) * 2
            audioRecord = createCleanAudioRecord(bufferBytes)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext VoiceCaptureResult.Error("麦克风繁忙，请稍后重试")
            }

            audioRecord.startRecording()
            val shortBuf = ShortArray(chunkSamples)
            val allChunks = ArrayList<FloatArray>()
            val chunkRmsList = ArrayList<Float>()
            var voicedChunkCount = 0
            var trailingSilenceChunks = 0
            val voiceGate = 0.0065f
            val startMs = SystemClock.elapsedRealtime()

            while (isActive) {
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                if (stopSignal.isCompleted || elapsedMs >= maxDurationMs) break

                val read = audioRecord.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) {
                    delay(20)
                    continue
                }

                var sumSq = 0.0
                val chunk = FloatArray(read)
                for (i in 0 until read) {
                    val f = (shortBuf[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
                    chunk[i] = f
                    sumSq += f * f
                }
                val rms = sqrt(sumSq / read).toFloat()
                allChunks.add(chunk)
                chunkRmsList.add(rms)

                if (rms >= voiceGate) {
                    voicedChunkCount++
                    trailingSilenceChunks = 0
                } else if (voicedChunkCount > 0) {
                    trailingSilenceChunks++
                }

                val voicedSecNow = (voicedChunkCount * chunkSamples).toFloat() / SAMPLE_RATE
                val elapsedSecNow = (SystemClock.elapsedRealtime() - startMs) / 1000f
                onLiveFeedback(elapsedSecNow, rms, voicedSecNow)

                // Hands-free natural pause completion: spoke >= 1.0s and paused >= 0.72s (9 chunks)
                if (autoStopOnSilence && voicedSecNow >= 1.0f && trailingSilenceChunks >= 9) {
                    break
                }
            }
            onLiveFeedback(0f, 0f, 0f)

            val activeSec = (voicedChunkCount * chunkSamples).toFloat() / SAMPLE_RATE
            if (activeSec < 0.55f || allChunks.isEmpty()) {
                return@withContext VoiceCaptureResult.TooQuiet
            }

            val firstVoiced = chunkRmsList.indexOfFirst { it >= voiceGate }.coerceAtLeast(0)
            val lastVoiced = chunkRmsList.indexOfLast { it >= voiceGate }.coerceAtLeast(firstVoiced)
            val startIdx = (firstVoiced - 1).coerceAtLeast(0)
            val endIdx = (lastVoiced + 1).coerceAtMost(allChunks.lastIndex)

            var totalSamples = 0
            for (i in startIdx..endIdx) {
                totalSamples += allChunks[i].size
            }
            val continuousSpeech = FloatArray(totalSamples)
            var offset = 0
            for (i in startIdx..endIdx) {
                val arr = allChunks[i]
                System.arraycopy(arr, 0, continuousSpeech, offset, arr.size)
                offset += arr.size
            }

            val embeddings = if (multiScale) {
                extractMultiScaleEmbeddings(appContext, continuousSpeech)
            } else {
                extractEmbedding(appContext, continuousSpeech, SAMPLE_RATE)?.let { listOf(it) } ?: emptyList()
            }

            if (embeddings.isEmpty()) {
                return@withContext VoiceCaptureResult.Error("声纹提取未成功，请再随口说一句")
            }

            VoiceCaptureResult.Success(
                embeddings = embeddings,
                primaryEmbedding = embeddings.first(),
                activeDurationSec = activeSec
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in captureInteractiveUtterance", e)
            VoiceCaptureResult.Error("录音异常：${e.localizedMessage ?: "未知错误"}")
        } finally {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            if (wasServiceRunning) {
                val resumeIntent = Intent(appContext, MonitorForegroundService::class.java).apply {
                    action = MonitorForegroundService.ACTION_RESUME_AFTER_ENROLL
                }
                appContext.startService(resumeIntent)
            }
        }
    }

    suspend fun captureVoiceClipForEmbedding(
        context: Context,
        durationMs: Long,
        multiScale: Boolean = false,
        onProgress: (progress: Float, rmsLevel: Float) -> Unit
    ): VoiceCaptureResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            return@withContext VoiceCaptureResult.Error("缺少麦克风权限，请先允许录音权限")
        }

        val wasServiceRunning = MonitorStateStore.uiState.value.isRunning
        if (wasServiceRunning) {
            val pauseIntent = Intent(appContext, MonitorForegroundService::class.java).apply {
                action = MonitorForegroundService.ACTION_PAUSE_FOR_ENROLL
            }
            appContext.startService(pauseIntent)
            var waited = 0
            while (!MonitorStateStore.uiState.value.isMicPausedForEnroll && waited < 18) {
                delay(50)
                waited++
            }
            delay(80)
        }

        var audioRecord: AudioRecord? = null
        try {
            val chunkSamples = 1280 // 80ms per frame at 16kHz
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuf, chunkSamples * 2) * 2
            audioRecord = createCleanAudioRecord(bufferBytes)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext VoiceCaptureResult.Error("麦克风繁忙，请稍后重试")
            }

            audioRecord.startRecording()
            val shortBuf = ShortArray(chunkSamples)
            val allChunks = ArrayList<FloatArray>()
            val chunkRmsList = ArrayList<Float>()
            var voicedChunkCount = 0
            val voiceGate = 0.0065f
            val startMs = SystemClock.elapsedRealtime()

            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                if (elapsed >= durationMs) break

                val read = audioRecord.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) {
                    delay(25)
                    continue
                }

                var sumSq = 0.0
                val chunk = FloatArray(read)
                for (i in 0 until read) {
                    val f = (shortBuf[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
                    chunk[i] = f
                    sumSq += f * f
                }
                val rms = sqrt(sumSq / read).toFloat()
                val progress = ((SystemClock.elapsedRealtime() - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
                onProgress(progress, rms)

                allChunks.add(chunk)
                chunkRmsList.add(rms)
                if (rms >= voiceGate) {
                    voicedChunkCount++
                }
            }
            onProgress(1f, 0f)

            val activeSec = (voicedChunkCount * chunkSamples).toFloat() / SAMPLE_RATE
            if (activeSec < 0.55f || allChunks.isEmpty()) {
                return@withContext VoiceCaptureResult.TooQuiet
            }

            // Trim ONLY leading and trailing silence, keeping natural continuous waveform intact!
            val firstVoiced = chunkRmsList.indexOfFirst { it >= voiceGate }.coerceAtLeast(0)
            val lastVoiced = chunkRmsList.indexOfLast { it >= voiceGate }.coerceAtLeast(firstVoiced)
            val startIdx = (firstVoiced - 1).coerceAtLeast(0)
            val endIdx = (lastVoiced + 1).coerceAtMost(allChunks.lastIndex)

            var totalSamples = 0
            for (i in startIdx..endIdx) {
                totalSamples += allChunks[i].size
            }
            val continuousSpeech = FloatArray(totalSamples)
            var offset = 0
            for (i in startIdx..endIdx) {
                val arr = allChunks[i]
                System.arraycopy(arr, 0, continuousSpeech, offset, arr.size)
                offset += arr.size
            }

            val embeddings = if (multiScale) {
                extractMultiScaleEmbeddings(appContext, continuousSpeech)
            } else {
                extractEmbedding(appContext, continuousSpeech, SAMPLE_RATE)?.let { listOf(it) } ?: emptyList()
            }

            if (embeddings.isEmpty()) {
                return@withContext VoiceCaptureResult.Error("声纹特征提取失败，请再试一次")
            }

            VoiceCaptureResult.Success(
                embeddings = embeddings,
                primaryEmbedding = embeddings.first(),
                activeDurationSec = activeSec
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing voice clip", e)
            VoiceCaptureResult.Error("录音异常：${e.localizedMessage ?: "未知错误"}")
        } finally {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            if (wasServiceRunning) {
                val resumeIntent = Intent(appContext, MonitorForegroundService::class.java).apply {
                    action = MonitorForegroundService.ACTION_RESUME_AFTER_ENROLL
                }
                appContext.startService(resumeIntent)
            }
        }
    }
}

/**
 * Stores recent continuous 100ms audio frames and isolates ONLY the current speaker turn
 * (stopping at any >=400ms silence gap so a previous speaker's voice never contaminates the embedding,
 * while keeping internal consonants and micro-pauses 100% continuous!).
 */
class RollingVoiceBuffer(
    private val maxChunks: Int = 30, // 3.0 seconds of 100ms chunks
    private val voiceRmsFloor: Float = 0.0065f
) {
    private data class AudioFrame(val samples: FloatArray, val rms: Float)

    private val frames = ArrayDeque<AudioFrame>(maxChunks + 2)

    @Synchronized
    fun append(samples: FloatArray, rms: Float) {
        if (frames.size >= maxChunks) {
            frames.removeFirst()
        }
        frames.addLast(AudioFrame(samples.copyOf(), rms))
    }

    @Synchronized
    fun clear() {
        frames.clear()
    }

    @Synchronized
    fun latestFrameIsVoiced(): Boolean {
        val last = frames.lastOrNull() ?: return false
        return last.rms >= voiceRmsFloor
    }

    @Synchronized
    fun currentTurnDurationSec(): Float {
        val span = findCurrentTurnSpan() ?: return 0f
        var samples = 0
        val list = frames.toList()
        for (i in span.first..span.second) {
            samples += list[i].samples.size
        }
        return samples.toFloat() / VoiceprintManager.SAMPLE_RATE
    }

    @Synchronized
    fun extractCurrentTurnSpeech(maxSamples: Int = 24000): FloatArray {
        val list = frames.toList()
        val span = findCurrentTurnSpan(list) ?: return FloatArray(0)

        val endIdx = (span.second + 1).coerceAtMost(list.lastIndex)
        var startIdx = (span.first - 1).coerceAtLeast(0)

        var total = 0
        for (i in endIdx downTo startIdx) {
            total += list[i].samples.size
            if (total >= maxSamples) {
                startIdx = i
                break
            }
        }

        val result = FloatArray(total)
        var pos = 0
        for (i in startIdx..endIdx) {
            val arr = list[i].samples
            System.arraycopy(arr, 0, result, pos, arr.size)
            pos += arr.size
        }
        return result
    }

    private fun findCurrentTurnSpan(list: List<AudioFrame> = frames.toList()): Pair<Int, Int>? {
        if (list.isEmpty()) return null
        val lastVoiced = list.indexOfLast { it.rms >= voiceRmsFloor }
        if (lastVoiced < 0) return null

        var turnStart = lastVoiced
        var consecutiveSilence = 0
        for (i in lastVoiced downTo 0) {
            if (list[i].rms >= voiceRmsFloor) {
                turnStart = i
                consecutiveSilence = 0
            } else {
                consecutiveSilence++
                // >= 4 consecutive 100ms silent chunks (400ms pause) = speaker turn boundary!
                if (consecutiveSilence >= 4) {
                    break
                }
            }
        }
        return turnStart to lastVoiced
    }
}
