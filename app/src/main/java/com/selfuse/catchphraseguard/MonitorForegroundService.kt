package com.selfuse.catchphraseguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * v5 foreground service. Runs one listening session at a time; controlled through
 * [MonitorController] actions (start / stop / pause N minutes / resume).
 *
 * All control state (jobs, foreground flag) is touched on the main thread only; the
 * recognition loop runs on Dispatchers.IO and hops back to Main to end the session.
 */
class MonitorForegroundService : Service() {

    companion object {
        private const val TAG = "CatchphraseService"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 1600 // 100ms per chunk at 16kHz
        private const val TIMER_TICK_MS = 15_000L

        /** Aliases kept for older callers; prefer [MonitorController]. */
        const val ACTION_START = MonitorController.ACTION_START
        const val ACTION_STOP = MonitorController.ACTION_STOP
        const val ACTION_PAUSE_FOR_ENROLL = "com.selfuse.catchphraseguard.ACTION_PAUSE_FOR_ENROLL"
        const val ACTION_RESUME_AFTER_ENROLL = "com.selfuse.catchphraseguard.ACTION_RESUME_AFTER_ENROLL"

        private const val STATUS_IDLE = "待机中"
    }

    private data class OngoingKey(
        val sessionId: Long?,
        val total: Int,
        val filtered: Int,
        val pausedUntilMs: Long?,
        val plannedEndAtMs: Long?,
        val micPausedForEnroll: Boolean,
        val voiceprintOn: Boolean,
        val words: List<String>
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Current (or last, possibly still finishing) recognition loop. */
    private var monitorJob: Job? = null
    private var notificationSyncJob: Job? = null
    private var autoEndJob: Job? = null
    private var autoResumeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MonitorStateStore.ensureInitialized(applicationContext)
        return when (intent?.action) {
            MonitorController.ACTION_START -> handleStart(
                intent.getIntExtra(MonitorController.EXTRA_DURATION_MINUTES, 0)
            )
            MonitorController.ACTION_STOP -> {
                finishSession(STATUS_IDLE)
                START_NOT_STICKY
            }
            MonitorController.ACTION_PAUSE -> handlePause(
                intent.getIntExtra(MonitorController.EXTRA_PAUSE_MINUTES, MonitorController.DEFAULT_PAUSE_MINUTES)
            )
            MonitorController.ACTION_RESUME -> handleResume()
            ACTION_PAUSE_FOR_ENROLL -> {
                val job = monitorJob
                job?.cancel()
                serviceScope.launch(Dispatchers.Main) {
                    job?.join()
                    MonitorStateStore.setMicPausedForEnroll(true)
                }
                stickyOrStop()
            }
            ACTION_RESUME_AFTER_ENROLL -> {
                MonitorStateStore.setMicPausedForEnroll(false)
                val session = MonitorStateStore.uiState.value.activeSession
                if (isForeground && session != null && !session.isPaused(System.currentTimeMillis())) {
                    startRecognitionLoopIfNeeded()
                }
                stickyOrStop()
            }
            // null intent = START_STICKY restart after the process was killed.
            else -> handleRestart()
        }
    }

    // ---------------------------------------------------------------------
    // Action handlers
    // ---------------------------------------------------------------------

    private fun handleStart(durationMinutes: Int): Int {
        if (!MonitorController.hasMicPermission(this)) {
            MonitorStateStore.setRunningState(false, "缺少麦克风权限，请先授权")
            stopIfIdle()
            return START_NOT_STICKY
        }

        // A session left over from a dead service instance is closed before starting anew.
        if (!isForeground && MonitorStateStore.uiState.value.activeSession != null) {
            MonitorStateStore.endSession()
        }

        val plannedMs = if (durationMinutes > 0) durationMinutes * 60_000L else null
        val createdNow = MonitorStateStore.uiState.value.activeSession == null
        val session = MonitorStateStore.startSession(plannedMs)

        if (!goForeground()) {
            if (createdNow) {
                MonitorStateStore.discardActiveSession()
            }
            MonitorTileService.requestUpdate(this)
            stopSelf()
            return START_NOT_STICKY
        }
        MonitorTileService.requestUpdate(this)
        continueSession(session)
        return START_STICKY
    }

    private fun handlePause(minutes: Int): Int {
        if (!ensureForegroundForSession()) return START_NOT_STICKY
        val pauseMs = minutes.coerceAtLeast(1) * 60_000L
        MonitorStateStore.pauseSession(pauseMs)
        monitorJob?.cancel()
        MonitorStateStore.updateLivePreview("", 0f)
        val session = MonitorStateStore.uiState.value.activeSession
        val resumeAt = session?.pausedUntilMs ?: (System.currentTimeMillis() + pauseMs)
        MonitorStateStore.setRunningState(true, "已暂停 · ${NotificationHelper.formatClock(resumeAt)} 恢复")
        scheduleAutoResume(resumeAt)
        refreshOngoingNotification()
        MonitorTileService.requestUpdate(this)
        return START_STICKY
    }

    private fun handleResume(): Int {
        if (!ensureForegroundForSession()) return START_NOT_STICKY
        resumeNow()
        return START_STICKY
    }

    private fun handleRestart(): Int {
        val session = MonitorStateStore.uiState.value.activeSession
        if (session == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (isForeground) {
            continueSession(session)
            return START_STICKY
        }
        if (!MonitorController.hasMicPermission(this) || !goForeground()) {
            finishSession("监听已中断")
            return START_NOT_STICKY
        }
        continueSession(session)
        return START_STICKY
    }

    /** For pause/resume: make sure the service is foreground with an active session. */
    private fun ensureForegroundForSession(): Boolean {
        if (MonitorStateStore.uiState.value.activeSession == null) {
            stopIfIdle()
            return false
        }
        if (isForeground) return true
        if (!MonitorController.hasMicPermission(this) || !goForeground()) {
            finishSession("监听已中断")
            return false
        }
        continueSession(MonitorStateStore.uiState.value.activeSession ?: return false)
        return true
    }

    /** Applies timers and starts listening (or waits out a pause) for [session]. */
    private fun continueSession(session: ActiveSession) {
        val nowMs = System.currentTimeMillis()
        val plannedEnd = session.plannedEndAtMs
        if (plannedEnd != null && nowMs >= plannedEnd) {
            finishSession("已到设定时长，监听自动结束")
            return
        }

        acquirePartialWakeLock()
        startNotificationSyncIfNeeded()
        scheduleAutoEnd(plannedEnd)

        val pausedUntil = session.pausedUntilMs
        if (session.pauseStartedAtMs != null && pausedUntil != null && nowMs < pausedUntil) {
            monitorJob?.cancel()
            MonitorStateStore.setRunningState(true, "已暂停 · ${NotificationHelper.formatClock(pausedUntil)} 恢复")
            scheduleAutoResume(pausedUntil)
            refreshOngoingNotification()
        } else {
            if (session.pauseStartedAtMs != null) MonitorStateStore.resumeSession()
            autoResumeJob?.cancel()
            if (!MonitorStateStore.uiState.value.isMicPausedForEnroll) {
                startRecognitionLoopIfNeeded()
            } else {
                MonitorStateStore.setRunningState(true, "录入声音中，监听暂停")
            }
            refreshOngoingNotification()
        }
    }

    private fun resumeNow() {
        autoResumeJob?.cancel()
        autoResumeJob = null
        MonitorStateStore.resumeSession()
        if (!MonitorStateStore.uiState.value.isMicPausedForEnroll) {
            startRecognitionLoopIfNeeded()
        }
        refreshOngoingNotification()
        MonitorTileService.requestUpdate(this)
    }

    /**
     * Ends the active session (if any), posts the summary, and stops the service.
     * Safe to call when nothing is running.
     */
    private fun finishSession(statusMessage: String) {
        autoEndJob?.cancel()
        autoEndJob = null
        autoResumeJob?.cancel()
        autoResumeJob = null
        notificationSyncJob?.cancel()
        notificationSyncJob = null
        monitorJob?.cancel()

        val record = MonitorStateStore.endSession()
        releasePartialWakeLock()
        MonitorStateStore.setRunningState(false, statusMessage)

        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        NotificationHelper.cancel(this, NotificationHelper.ONGOING_NOTIFICATION_ID)
        record?.takeIf { it.monitoredSec >= 30L || it.totalCount > 0 }?.let {
            NotificationHelper.postSessionSummary(applicationContext, it)
        }
        MonitorTileService.requestUpdate(this)
        stopSelf()
    }

    private fun stopIfIdle() {
        if (!isForeground) stopSelf()
    }

    private fun stickyOrStop(): Int {
        return if (isForeground) {
            START_STICKY
        } else {
            stopSelf()
            START_NOT_STICKY
        }
    }

    // ---------------------------------------------------------------------
    // Foreground + notification
    // ---------------------------------------------------------------------

    private fun goForeground(): Boolean {
        val wasForeground = isForeground
        val notification = NotificationHelper.buildOngoingNotification(this, MonitorStateStore.uiState.value)
        return try {
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
            isForeground = true
            if (!wasForeground) MonitorStateStore.setRunningState(true, "正在启动监听…")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            MonitorStateStore.setRunningState(false, "启动后台监听失败：${e.localizedMessage ?: "未知错误"}")
            false
        }
    }

    private fun refreshOngoingNotification() {
        if (!isForeground) return
        NotificationHelper.updateOngoingNotification(this, MonitorStateStore.uiState.value)
    }

    private fun startNotificationSyncIfNeeded() {
        if (notificationSyncJob?.isActive == true) return
        notificationSyncJob = serviceScope.launch {
            MonitorStateStore.uiState
                .distinctUntilChangedBy { state ->
                    val s = state.activeSession
                    OngoingKey(
                        sessionId = s?.id,
                        total = s?.totalCount ?: 0,
                        filtered = s?.filteredOthers ?: 0,
                        pausedUntilMs = s?.pausedUntilMs,
                        plannedEndAtMs = s?.plannedEndAtMs,
                        micPausedForEnroll = state.isMicPausedForEnroll,
                        voiceprintOn = state.settings.voiceprintEnabled && state.voiceprintProfile.isEnrolled,
                        words = state.keywords.filter { it.enabled }.map { it.word }
                    )
                }
                .collect { state ->
                    if (isForeground && state.activeSession != null) {
                        NotificationHelper.updateOngoingNotification(this@MonitorForegroundService, state)
                    }
                    MonitorTileService.requestUpdate(this@MonitorForegroundService)
                }
        }
    }

    // ---------------------------------------------------------------------
    // Timers (wall-clock based, robust to long delays)
    // ---------------------------------------------------------------------

    private fun scheduleAutoEnd(plannedEndAtMs: Long?) {
        autoEndJob?.cancel()
        autoEndJob = null
        if (plannedEndAtMs == null) return
        autoEndJob = serviceScope.launch(Dispatchers.Main) {
            waitUntil(plannedEndAtMs)
            autoEndJob = null
            finishSession("已到设定时长，监听自动结束")
        }
    }

    private fun scheduleAutoResume(resumeAtMs: Long) {
        autoResumeJob?.cancel()
        autoResumeJob = serviceScope.launch(Dispatchers.Main) {
            waitUntil(resumeAtMs)
            autoResumeJob = null
            if (MonitorStateStore.uiState.value.activeSession != null) resumeNow()
        }
    }

    private suspend fun waitUntil(targetMs: Long) {
        while (true) {
            val remaining = targetMs - System.currentTimeMillis()
            if (remaining <= 0L) return
            delay(minOf(remaining, TIMER_TICK_MS))
        }
    }

    // ---------------------------------------------------------------------
    // Recognition loop
    // ---------------------------------------------------------------------

    private fun startRecognitionLoopIfNeeded() {
        if (monitorJob?.isActive == true) return

        MonitorStateStore.setRunningState(true, "正在加载本地语音与声纹引擎…")

        val previousJob = monitorJob
        monitorJob = serviceScope.launch(Dispatchers.IO) {
            // Make sure a previous loop has released the microphone first.
            previousJob?.join()

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
                    decodingMethod = "modified_beam_search"
                    maxActivePaths = 4
                }

                recognizer = OnlineRecognizer(applicationContext.assets, recognizerConfig)
                VoiceprintManager.warmUp(applicationContext)
                if (!isActive) return@launch
                val stream = recognizer.createStream()

                try {
                    val minBufBytes = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val bufferSizeInBytes = maxOf(minBufBytes, CHUNK_SAMPLES * 2) * 2

                    audioRecord = VoiceprintManager.createCleanAudioRecord(bufferSizeInBytes)

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        endFromLoop("麦克风初始化失败，可能被其他应用占用")
                        return@launch
                    }

                    audioRecord.startRecording()
                    MonitorStateStore.setRunningState(true, "正在监听")

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

                        val feedSamples = if (settings.nearVoiceFilter && uiRms < 0.014f) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error inside recognition loop", e)
                endFromLoop("监听异常中断：${e.localizedMessage ?: "未知错误"}")
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

    /** Called from the loop (IO thread) on fatal errors: end the session on the main thread. */
    private fun endFromLoop(statusMessage: String) {
        serviceScope.launch(Dispatchers.Main) { finishSession(statusMessage) }
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
                    val hit = MonitorStateStore.recordSelfKeywordHit(kw, currentText, null, null)
                    alertForHit(hit)
                } else {
                    // Capture up to 300ms continuation if user is still speaking a short phrase (<0.9s)
                    var extraReads = 0
                    while (rollingVoiceBuffer.currentTurnDurationSec() < 0.90f &&
                        rollingVoiceBuffer.latestFrameIsVoiced() &&
                        extraReads < 3
                    ) {
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

                    val turnSpeech = rollingVoiceBuffer.extractCurrentTurnSpeech(
                        maxSamples = (SAMPLE_RATE * 1.6f).toInt()
                    )
                    val liveEmb = VoiceprintManager.extractEmbedding(
                        context = applicationContext,
                        rawSamples = turnSpeech,
                        sampleRate = SAMPLE_RATE
                    )
                    val updatedText = recognizer.getResult(stream).text.trim().ifEmpty { currentText }

                    if (liveEmb == null) {
                        MonitorStateStore.recordFilteredOtherVoice(kw, updatedText, 0.12f, null)
                    } else {
                        // Use the latest profile so fresh "不是我" samples apply immediately.
                        val latest = MonitorStateStore.uiState.value
                        val decision = VoiceprintManager.decide(
                            liveEmb,
                            latest.voiceprintProfile,
                            latest.settings.voiceprintThreshold
                        )
                        if (decision.isSelf) {
                            lastAlertRealtimeMs[kw] = SystemClock.elapsedRealtime()
                            val hit = MonitorStateStore.recordSelfKeywordHit(
                                kw,
                                updatedText,
                                decision.selfScore,
                                liveEmb
                            )
                            alertForHit(hit)
                        } else {
                            lastAlertRealtimeMs[kw] =
                                SystemClock.elapsedRealtime() - (cooldownMs - 1500L).coerceAtLeast(0L)
                            MonitorStateStore.recordFilteredOtherVoice(
                                kw,
                                updatedText,
                                decision.selfScore,
                                liveEmb
                            )
                        }
                    }
                }
            }
        }
    }

    private fun alertForHit(hit: HitRecord) {
        val state = MonitorStateStore.uiState.value
        val sessionCount = state.activeSession?.totalCount
            ?: state.keywords.firstOrNull { it.word == hit.word }?.todayCount
            ?: 1
        NotificationHelper.showHitAlert(
            context = applicationContext,
            hit = hit,
            sessionCount = sessionCount,
            settings = state.settings
        )
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

    // ---------------------------------------------------------------------
    // Wake lock
    // ---------------------------------------------------------------------

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

    override fun onDestroy() {
        monitorJob?.cancel()
        notificationSyncJob?.cancel()
        autoEndJob?.cancel()
        autoResumeJob?.cancel()
        serviceScope.cancel()
        releasePartialWakeLock()
        isForeground = false
        // The session itself is kept: a START_STICKY restart continues it.
        MonitorStateStore.setRunningState(false, STATUS_IDLE)
        MonitorTileService.requestUpdate(this)
        super.onDestroy()
    }
}
