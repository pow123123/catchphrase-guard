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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

sealed class VoiceCaptureResult {
    data class Success(val embedding: FloatArray, val activeDurationSec: Float) : VoiceCaptureResult()
    data object TooQuiet : VoiceCaptureResult()
    data class Error(val message: String) : VoiceCaptureResult()
}

object VoiceprintManager {
    private const val TAG = "VoiceprintManager"
    const val SAMPLE_RATE = 16000

    private val lock = Any()
    private var extractor: SpeakerEmbeddingExtractor? = null

    private fun getOrCreateExtractor(context: Context): SpeakerEmbeddingExtractor {
        synchronized(lock) {
            extractor?.let { return it }
            val config = SpeakerEmbeddingExtractorConfig().apply {
                model = "model/speaker.onnx"
                numThreads = 2
                provider = "cpu"
                debug = false
            }
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

    fun extractEmbedding(context: Context, samples: FloatArray, sampleRate: Int = SAMPLE_RATE): FloatArray? {
        if (samples.size < sampleRate / 3) return null
        return try {
            synchronized(lock) {
                val ext = getOrCreateExtractor(context)
                val stream = ext.createStream()
                try {
                    stream.acceptWaveform(samples, sampleRate)
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

    fun computeBestSimilarity(liveEmbedding: FloatArray, enrolledVectors: List<FloatArray>): Float {
        if (enrolledVectors.isEmpty()) return 1.0f
        val normLive = l2Normalize(liveEmbedding)
        var best = -1.0f
        for (ref in enrolledVectors) {
            if (ref.size == normLive.size) {
                var dot = 0f
                for (i in normLive.indices) {
                    dot += normLive[i] * ref[i]
                }
                if (dot > best) best = dot
            }
        }
        return best.coerceIn(0f, 1f)
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

    suspend fun captureVoiceClipForEmbedding(
        context: Context,
        durationMs: Long,
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
            while (!MonitorStateStore.uiState.value.isMicPausedForEnroll && waited < 15) {
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
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext VoiceCaptureResult.Error("麦克风繁忙，请稍后重试")
            }

            audioRecord.startRecording()
            val shortBuf = ShortArray(chunkSamples)
            val activeChunks = ArrayList<FloatArray>()
            var activeSampleCount = 0
            val startMs = SystemClock.elapsedRealtime()

            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                if (elapsed >= durationMs) break

                val read = audioRecord.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) {
                    delay(30)
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

                if (rms >= 0.009f) {
                    activeChunks.add(chunk)
                    activeSampleCount += read
                }
            }
            onProgress(1f, 0f)

            val activeSec = activeSampleCount.toFloat() / SAMPLE_RATE
            if (activeSampleCount < (SAMPLE_RATE * 0.7f).toInt()) {
                return@withContext VoiceCaptureResult.TooQuiet
            }

            val merged = FloatArray(activeSampleCount)
            var offset = 0
            for (c in activeChunks) {
                System.arraycopy(c, 0, merged, offset, c.size)
                offset += c.size
            }

            val embedding = extractEmbedding(appContext, merged, SAMPLE_RATE)
                ?: return@withContext VoiceCaptureResult.Error("声纹特征提取失败，请重试")

            VoiceCaptureResult.Success(embedding = embedding, activeDurationSec = activeSec)
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

class RollingVoiceBuffer(
    private val maxChunks: Int = 26, // ~2.6 seconds of 100ms chunks
    private val voiceRmsFloor: Float = 0.0085f
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
    fun activeVoiceDurationSec(): Float {
        var totalSamples = 0
        for (f in frames) {
            if (f.rms >= voiceRmsFloor) {
                totalSamples += f.samples.size
            }
        }
        return totalSamples.toFloat() / VoiceprintManager.SAMPLE_RATE
    }

    @Synchronized
    fun extractRecentActiveVoice(maxSamples: Int = 32000): FloatArray {
        if (frames.isEmpty()) return FloatArray(0)
        val list = frames.toList()
        val keep = BooleanArray(list.size)
        for (i in list.indices) {
            if (list[i].rms >= voiceRmsFloor) {
                keep[i] = true
                if (i > 0) keep[i - 1] = true
                if (i + 1 < list.size) keep[i + 1] = true
            }
        }

        val selected = ArrayList<FloatArray>()
        var collected = 0
        for (i in list.indices.reversed()) {
            if (keep[i]) {
                selected.add(list[i].samples)
                collected += list[i].samples.size
                if (collected >= maxSamples) break
            }
        }
        if (collected == 0) return FloatArray(0)

        selected.reverse()
        val result = FloatArray(collected)
        var pos = 0
        for (arr in selected) {
            System.arraycopy(arr, 0, result, pos, arr.size)
            pos += arr.size
        }
        return result
    }
}
