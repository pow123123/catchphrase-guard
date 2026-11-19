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
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        if (intent?.action == ACTION_STOP) {
            stopMonitoringAndService()
            return START_NOT_STICKY
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
        val notification = NotificationHelper.buildOngoingNotification(
            context = this,
            activeKeywords = activeWords,
            todayTotalCount = todayTotal
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
                    NotificationHelper.updateOngoingNotification(
                        context = this@MonitorForegroundService,
                        activeKeywords = activeWords,
                        todayTotalCount = todayTotal
                    )
                }
            }
        }
    }

    private fun startRecognitionLoopIfNeeded() {
        if (monitorJob?.isActive == true) return

        MonitorStateStore.setRunningState(true, "正在加载本地离线语音模型…")

        monitorJob = serviceScope.launch(Dispatchers.IO) {
            var recognizer: OnlineRecognizer? = null
            var audioRecord: AudioRecord? = null

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
                        MonitorStateStore.setRunningState(false, "麦克风初始化失败，可能被其他应用独占")
                        stopSelf()
                        return@launch
                    }

                    audioRecord.startRecording()
                    MonitorStateStore.setRunningState(true, "正在后台静默监听中（纯本地离线）")

                    val shortBuffer = ShortArray(CHUNK_SAMPLES)
                    val zeroSamples = FloatArray(CHUNK_SAMPLES)
                    val utteranceTriggeredCounts = mutableMapOf<String, Int>()
                    val lastAlertRealtimeMs = mutableMapOf<String, Long>()
                    var lastRecognizedText = ""
                    var utteranceStartMs = SystemClock.elapsedRealtime()

                    while (isActive) {
                        val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                        if (readCount <= 0) {
                            delay(50)
                            continue
                        }

                        val currentState = MonitorStateStore.uiState.value
                        val settings = currentState.settings
                        val gain = if (settings.micBoost) 2.2f else 1.0f

                        var sumSquares = 0.0
                        val floatSamples = FloatArray(readCount)
                        for (i in 0 until readCount) {
                            val normalized = (shortBuffer[i] / 32768.0f * gain).coerceIn(-1.0f, 1.0f)
                            floatSamples[i] = normalized
                            sumSquares += normalized * normalized
                        }
                        val rms = sqrt(sumSquares / readCount).toFloat()

                        val feedSamples = if (settings.nearVoiceFilter && rms < 0.015f) {
                            if (readCount == zeroSamples.size) zeroSamples else FloatArray(readCount)
                        } else {
                            floatSamples
                        }

                        stream.acceptWaveform(feedSamples, SAMPLE_RATE)

                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }

                        val rawText = recognizer.getResult(stream).text.trim()
                        if (rawText.isNotEmpty()) {
                            if (rawText != lastRecognizedText) {
                                lastRecognizedText = rawText
                                MonitorStateStore.updateLivePreview(rawText, rms)
                            } else {
                                MonitorStateStore.updateLivePreview(lastRecognizedText, rms)
                            }
                            checkAndTriggerKeywords(
                                currentText = rawText,
                                activeKeywords = currentState.keywords.filter { it.enabled },
                                settings = settings,
                                utteranceTriggeredCounts = utteranceTriggeredCounts,
                                lastAlertRealtimeMs = lastAlertRealtimeMs
                            )
                        } else {
                            MonitorStateStore.updateLivePreview(lastRecognizedText, rms)
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
        activeKeywords: List<KeywordItem>,
        settings: MonitorSettings,
        utteranceTriggeredCounts: MutableMap<String, Int>,
        lastAlertRealtimeMs: MutableMap<String, Long>
    ) {
        if (activeKeywords.isEmpty()) return
        val nowMs = SystemClock.elapsedRealtime()
        val cooldownMs = settings.cooldownSeconds * 1000L

        for (item in activeKeywords) {
            val kw = item.word
            if (kw.isEmpty()) continue
            val occurrencesNow = countNonOverlappingOccurrences(currentText, kw)
            val prevOccurrences = utteranceTriggeredCounts[kw] ?: 0

            if (occurrencesNow > prevOccurrences) {
                utteranceTriggeredCounts[kw] = occurrencesNow
                val lastMs = lastAlertRealtimeMs[kw] ?: 0L
                if (lastMs == 0L || (nowMs - lastMs) >= cooldownMs) {
                    lastAlertRealtimeMs[kw] = nowMs
                    val hitRecord = MonitorStateStore.recordKeywordHit(kw, currentText)
                    NotificationHelper.triggerCatchphraseAlert(
                        context = applicationContext,
                        word = kw,
                        todayCount = hitRecord.todayCountAfterHit,
                        snippet = hitRecord.snippet,
                        pushEnabled = settings.pushAlertEnabled,
                        vibrateEnabled = settings.vibrateEnabled
                    )
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
                acquire(12 * 60 * 60 * 1000L) // Up to 12 hours safety cap
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
        MonitorStateStore.setRunningState(false, "已停止监听")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        notificationSyncJob?.cancel()
        serviceScope.cancel()
        releasePartialWakeLock()
        MonitorStateStore.setRunningState(false, "已停止监听")
        super.onDestroy()
    }
}
