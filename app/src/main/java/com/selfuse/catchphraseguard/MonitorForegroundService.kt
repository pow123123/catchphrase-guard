package com.selfuse.catchphraseguard

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MonitorForegroundService : Service() {

    companion object {
        private const val TAG = "CatchphraseService"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 1600 // 100ms per chunk at 16kHz

        const val ACTION_START = "com.selfuse.catchphraseguard.ACTION_START"
        const val ACTION_STOP = "com.selfuse.catchphraseguard.ACTION_STOP"
        const val ACTION_PAUSE_FOR_ENROLL = "com.selfuse.catchphraseguard.ACTION_PAUSE_FOR_ENROLL"
        const val ACTION_RESUME_AFTER_ENROLL = "com.selfuse.catchphraseguard.ACTION_RESUME_AFTER_ENROLL"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var notificationSyncJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoringAndService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_FOR_ENROLL -> {
                serviceScope.launch {
                    monitorJob?.cancelAndJoin()
                    monitorJob = null
                    MonitorStateStore.setMicPausedForEnroll(true)
                }
                return START_STICKY
            }
            ACTION_RESUME_AFTER_ENROLL -> {
                MonitorStateStore.setMicPausedForEnroll(false)
                startRecognitionLoopIfNeeded()
                return START_STICKY
            }
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            MonitorStateStore.setRunningState(false, "缺少麦克风权限，请先授权")
            stopSelf()
            return START_NOT_STICKY
        }

        val state = MonitorStateStore.uiState.value
        val activeWords = state.keywords.filter { it.enabled }.map { it.word }
        val todayTotal = state.keywords.sumOf { it.todayCount }
        val vpProtected = state.settings.voiceprintEnabled && state.voiceprintProfile.isEnrolled
        val notification = NotificationHelper.buildOngoingNotification(
            context = this,
            activeKeywords = activeWords,
            todayTotalCount = todayTotal,
            voiceprintProtected = vpProtected
        )

        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NotificationHelper.ONGOING_NOTIFICATION_ID,
                notification,
                fgsType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            MonitorStateStore.setRunningState(false, "启动后台监听失败：${e.localizedMessage}")
            stopSelf()
            return START_NOT_STICKY
        }

        acquirePartialWakeLock()
        startRecognitionLoopIfNeeded()
        startNotificationSyncIfNeeded()

        return START_STICKY
    }

    private fun startNotificationSyncIfNeeded() {
        if (notificationSyncJob?.isActive == true) return
        notificationSyncJob = serviceScope.launch {
            MonitorStateStore.uiState.collect { state ->
                if (state.isRunning) {
                    val activeWords = state.keywords.filter { it.enabled }.map { it.word }
                    val todayTotal = state.keywords.sumOf { it.todayCount }
                    val vpProtected = state.settings.voiceprintEnabled && state.voiceprintProfile.isEnrolled
                    NotificationHelper.updateOngoingNotification(
                        context = this@MonitorForegroundService,
                        activeKeywords = activeWords,
                        todayTotalCount = todayTotal,
                        voiceprintProtected = vpProtected
                    )
                }
            }
        }
    }

    private fun startRecognitionLoopIfNeeded() {
        if (monitorJob?.isActive == true) return

        MonitorStateStore.setRunningState(true, "正在加载本地语音与声纹引擎…")

        monitorJob = serviceScope.launch(Dispatchers.IO) {
            var recognizer: OnlineRecognizer? = null
            var audioRecord: AudioRecord? = null
            val rollingVoiceBuffer = RollingVoiceBuffer()

            try {
                val transducer = OnlineTransducerModelConfig().apply {
                    encoder = "model/encoder.onnx"
                    decoder = "model/decoder.onnx"
                    joiner = "model/joiner.onnx"
                }
                val modelConfig = OnlineModelConfig().apply {
                    this.transducer = transducer
                    tokens = "model/tokens.txt"
                    numThreads = 2
                    provider = "cpu"
                    modelType = "zipformer"
                    debug = false
                }
                val recognizerConfig = OnlineRecognizerConfig().apply {
                    this.modelConfig = modelConfig
                    enableEndpoint = true
                }

                recognizer = OnlineRecognizer(applicationContext.assets, recognizerConfig)
                VoiceprintManager.warmUp(applicationContext)
                val stream = recognizer.createStream()

                try {
                    val minBufBytes = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val bufferSizeInBytes = maxOf(minBufBytes, CHUNK_SAMPLES * 2) * 2

                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSizeInBytes
                    )

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        MonitorStateStore.setRunningState(false, "麦克风初始化失败，可能被其他应用占用")
                        stopSelf()
                        return@launch
                    }

                    audioRecord.startRecording()
                    MonitorStateStore.setRunningState(true, "正在后台静默守护中")

                    val shortBuffer = ShortArray(CHUNK_SAMPLES)
                    val zeroSamples = FloatArray(CHUNK_SAMPLES)
                    val utteranceTriggeredCounts = mutableMapOf<String, Int>()
                    val lastAlertRealtimeMs = mutableMapOf<String, Long>()
                    var lastRecognizedText = ""
                    var utteranceStartMs = SystemClock.elapsedRealtime()

                    while (isActive) {
                        val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                        if (readCount <= 0) {
                            delay(40)
                            continue
                        }

                        val currentState = MonitorStateStore.uiState.value
                        val settings = currentState.settings
                        val gain = if (settings.micBoost) 2.2f else 1.0f

                        var rawSumSq = 0.0
                        var boostedSumSq = 0.0
                        val rawSamples = FloatArray(readCount)
                        val boostedSamples = FloatArray(readCount)
                        for (i in 0 until readCount) {
                            val raw = (shortBuffer[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
                            rawSamples[i] = raw
                            rawSumSq += raw * raw

                            val boosted = (raw * gain).coerceIn(-1.0f, 1.0f)
                            boostedSamples[i] = boosted
                            boostedSumSq += boosted * boosted
                        }
                        val rawRms = sqrt(rawSumSq / readCount).toFloat()
                        val uiRms = sqrt(boostedSumSq / readCount).toFloat()

                        rollingVoiceBuffer.append(rawSamples, rawRms)

                        val feedSamples = if (settings.nearVoiceFilter && uiRms < 0.015f) {
                            if (readCount == zeroSamples.size) zeroSamples else FloatArray(readCount)
                        } else {
                            boostedSamples
                        }

                        stream.acceptWaveform(feedSamples, SAMPLE_RATE)

                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }

                        val rawText = recognizer.getResult(stream).text.trim()
                        if (rawText.isNotEmpty()) {
                            if (rawText != lastRecognizedText) {
                                lastRecognizedText = rawText
                            }
                            MonitorStateStore.updateLivePreview(lastRecognizedText, uiRms)

                            checkAndTriggerKeywords(
                                currentText = rawText,
                                state = currentState,
                                audioRecord = audioRecord,
                                shortBuffer = shortBuffer,
                                recognizer = recognizer,
                                stream = stream,
                                rollingVoiceBuffer = rollingVoiceBuffer,
                                utteranceTriggeredCounts = utteranceTriggeredCounts,
                                lastAlertRealtimeMs = lastAlertRealtimeMs
                            )
                        } else {
                            MonitorStateStore.updateLivePreview(lastRecognizedText, uiRms)
                        }

                        val nowMs = SystemClock.elapsedRealtime()
                        val tooLongWithoutPause = (nowMs - utteranceStartMs) > 25_000L && rawText.length > 40
                        if (recognizer.isEndpoint(stream) || tooLongWithoutPause) {
                            recognizer.reset(stream)
                            utteranceTriggeredCounts.clear()
                            utteranceStartMs = nowMs
                        }
                    }
                } finally {
                    stream.release()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error inside recognition loop", e)
                MonitorStateStore.setRunningState(false, "监听异常中断：${e.localizedMessage ?: "未知错误"}")
                stopSelf()
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }
                try {
                    audioRecord?.release()
                } catch (_: Exception) {
                }
                try {
                    recognizer?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun checkAndTriggerKeywords(
        currentText: String,
        state: MonitorUiState,
        audioRecord: AudioRecord,
        shortBuffer: ShortArray,
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        rollingVoiceBuffer: RollingVoiceBuffer,
        utteranceTriggeredCounts: MutableMap<String, Int>,
        lastAlertRealtimeMs: MutableMap<String, Long>
    ) {
        val activeKeywords = state.keywords.filter { it.enabled }
        if (activeKeywords.isEmpty()) return

        val settings = state.settings
        val nowMs = SystemClock.elapsedRealtime()
        val cooldownMs = settings.cooldownSeconds * 1000L
        val useVoiceprint = settings.voiceprintEnabled && state.voiceprintProfile.isEnrolled

        for (item in activeKeywords) {
            val kw = item.word
            if (kw.isEmpty()) continue
            val occurrencesNow = countNonOverlappingOccurrences(currentText, kw)
            val prevOccurrences = utteranceTriggeredCounts[kw] ?: 0

            if (occurrencesNow > prevOccurrences) {
                utteranceTriggeredCounts[kw] = occurrencesNow
                val lastMs = lastAlertRealtimeMs[kw] ?: 0L
                if (lastMs != 0L && (nowMs - lastMs) < cooldownMs) {
                    continue
                }

                if (!useVoiceprint) {
                    lastAlertRealtimeMs[kw] = nowMs
                    val hitRecord = MonitorStateStore.recordSelfKeywordHit(kw, currentText, null)
                    NotificationHelper.triggerCatchphraseAlert(
                        context = applicationContext,
                        word = kw,
                        todayCount = hitRecord.todayCountAfterHit,
                        snippet = hitRecord.snippet,
                        pushEnabled = settings.pushAlertEnabled,
                        vibrateEnabled = settings.vibrateEnabled,
                        similarity = null
                    )
                } else {
                    // Micro-extension (up to 200ms) if active voice is very brief (<0.65s)
                    var extraReads = 0
                    while (rollingVoiceBuffer.activeVoiceDurationSec() < 0.65f && extraReads < 2) {
                        val extraCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                        if (extraCount <= 0) break
                        var sumSq = 0.0
                        val extraRaw = FloatArray(extraCount)
                        val gain = if (settings.micBoost) 2.2f else 1.0f
                        val extraBoosted = FloatArray(extraCount)
                        for (i in 0 until extraCount) {
                            val r = (shortBuffer[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
                            extraRaw[i] = r
                            sumSq += r * r
                            extraBoosted[i] = (r * gain).coerceIn(-1.0f, 1.0f)
                        }
                        val extraRms = sqrt(sumSq / extraCount).toFloat()
                        rollingVoiceBuffer.append(extraRaw, extraRms)
                        stream.acceptWaveform(extraBoosted, SAMPLE_RATE)
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                        extraReads++
                    }

                    val recentVoice = rollingVoiceBuffer.extractRecentActiveVoice(SAMPLE_RATE * 2)
                    val liveEmb = VoiceprintManager.extractEmbedding(
                        context = applicationContext,
                        samples = recentVoice,
                        sampleRate = SAMPLE_RATE
                    )
                    val updatedText = recognizer.getResult(stream).text.trim().ifEmpty { currentText }

                    if (liveEmb == null) {
                        // Too brief/quiet to form a reliable voiceprint -> treat cautiously as non-trigger
                        MonitorStateStore.recordFilteredOtherVoice(kw, updatedText, 0.15f)
                    } else {
                        val similarity = VoiceprintManager.computeBestSimilarity(
                            liveEmbedding = liveEmb,
                            enrolledVectors = state.voiceprintProfile.enrolledVectors
                        )
                        if (similarity >= settings.voiceprintThreshold) {
                            lastAlertRealtimeMs[kw] = SystemClock.elapsedRealtime()
                            val hitRecord = MonitorStateStore.recordSelfKeywordHit(kw, updatedText, similarity)
                            NotificationHelper.triggerCatchphraseAlert(
                                context = applicationContext,
                                word = kw,
                                todayCount = hitRecord.todayCountAfterHit,
                                snippet = hitRecord.snippet,
                                pushEnabled = settings.pushAlertEnabled,
                                vibrateEnabled = settings.vibrateEnabled,
                                similarity = similarity
                            )
                        } else {
                            // Filtered someone else's voice! Apply a brief 1.5s dedup cooldown
                            lastAlertRealtimeMs[kw] = SystemClock.elapsedRealtime() - (cooldownMs - 1500L).coerceAtLeast(0L)
                            MonitorStateStore.recordFilteredOtherVoice(kw, updatedText, similarity)
                        }
                    }
                }
            }
        }
    }

    private fun countNonOverlappingOccurrences(text: String, target: String): Int {
        if (text.isEmpty() || target.isEmpty()) return 0
        var count = 0
        var idx = text.indexOf(target)
        while (idx >= 0) {
            count++
            idx = text.indexOf(target, idx + target.length)
        }
        return count
    }

    private fun acquirePartialWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CatchphraseGuard::MicMonitorLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire partial wake lock", e)
        }
    }

    private fun releasePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun stopMonitoringAndService() {
        monitorJob?.cancel()
        monitorJob = null
        notificationSyncJob?.cancel()
        notificationSyncJob = null
        releasePartialWakeLock()
        MonitorStateStore.setRunningState(false, "待机中 · 点击下方开启守护")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        notificationSyncJob?.cancel()
        serviceScope.cancel()
        releasePartialWakeLock()
        MonitorStateStore.setRunningState(false, "待机中 · 点击下方开启守护")
        super.onDestroy()
    }
}
