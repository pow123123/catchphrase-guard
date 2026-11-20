package com.selfuse.catchphraseguard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * v5 notifications: the ongoing session notification (foreground service), per-hit alerts with
 * [不是我] / [暂停 15 分钟] actions, and the end-of-session summary.
 */
object NotificationHelper {
    const val ONGOING_NOTIFICATION_ID = 1001
    const val SUMMARY_NOTIFICATION_ID = 1002
    private const val TEST_ALERT_NOTIFICATION_ID = 1003
    private const val ALERT_NOTIFICATION_BASE_ID = 2000
    private const val ALERT_SLOTS = 8

    const val STATUS_CHANNEL_ID = "monitor_ongoing_status"
    /** v5: no channel sound / vibration; vibration is done manually per settings.vibrationStyle. */
    const val ALERT_CHANNEL_ID = "catchphrase_alert_v5"
    const val SUMMARY_CHANNEL_ID = "session_summary_v5"
    private val LEGACY_CHANNEL_IDS = listOf("catchphrase_alert_push_v1", "catchphrase_alert_push_v2")

    private const val BRAND_ACCENT_COLOR = 0xFFFF6B4A.toInt() // Coral-orange matching the app icon

    // PendingIntent request codes (each must be unique per distinct intent)
    private const val RC_OPEN_FROM_ONGOING = 100
    private const val RC_ONGOING_PAUSE = 101
    private const val RC_ONGOING_RESUME = 102
    private const val RC_ONGOING_STOP = 103
    private const val RC_OPEN_FROM_SUMMARY = 110
    private const val RC_OPEN_FROM_ALERT = 200
    private const val RC_ALERT_NOT_ME_BASE = 300
    private const val RC_ALERT_PAUSE_BASE = 400
    private const val RC_OPEN_FROM_TEST = 500

    private val TRIPLE_SHORT_WAVEFORM = longArrayOf(0, 90, 70, 90, 70, 140)
    private const val SINGLE_LONG_MS = 400L

    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val alertSequence = AtomicInteger(0)

    @Volatile
    private var cachedBrandIconBitmap: Bitmap? = null

    private fun getBrandLargeIcon(context: Context): Bitmap? {
        cachedBrandIconBitmap?.let { return it }
        return try {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_art)
                ?: return null
            val targetPx = 192
            val scaled = Bitmap.createScaledBitmap(raw, targetPx, targetPx, true)
            val rounded = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rounded)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, targetPx, targetPx)
            val rectF = RectF(rect)
            val cornerRadius = targetPx * 0.24f

            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, rect, rect, paint)

            cachedBrandIconBitmap = rounded
            rounded
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // Channels
    // ---------------------------------------------------------------------

    @Volatile
    private var channelsReady = false

    fun ensureChannels(context: Context) {
        if (channelsReady) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "后台监听常驻状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "监听进行中时常驻显示，可在这里暂停或结束"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "口头禅提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到你说出口头禅时弹出提醒。震动方式在 App 设置里调整"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val summaryChannel = NotificationChannel(
            SUMMARY_CHANNEL_ID,
            "监听小结",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每次监听结束后显示本次次数和频率"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(summaryChannel)
        for (legacy in LEGACY_CHANNEL_IDS) {
            try {
                manager.deleteNotificationChannel(legacy)
            } catch (_: Exception) {
            }
        }
        channelsReady = true
    }

    // ---------------------------------------------------------------------
    // Ongoing (foreground service) notification
    // ---------------------------------------------------------------------

    fun buildOngoingNotification(
        context: Context,
        state: MonitorUiState,
        nowMs: Long = System.currentTimeMillis()
    ): Notification {
        ensureChannels(context)

        val session = state.activeSession
        val total = session?.totalCount ?: 0
        val filtered = session?.filteredOthers ?: 0
        val voiceprintOn = state.settings.voiceprintEnabled && state.voiceprintProfile.isEnrolled
        val paused = session?.isPaused(nowMs) == true
        val activeWords = state.keywords.filter { it.enabled }.map { it.word }
        val wordsText = if (activeWords.isEmpty()) "暂无启用的口头禅" else activeWords.joinToString("、")

        val title: String
        val text: String
        when {
            session == null -> {
                title = "正在准备监听…"
                text = "盯防：$wordsText"
            }
            paused -> {
                val resumeAt = session.pausedUntilMs?.let { formatClock(it) } ?: "--:--"
                title = "已暂停 · $resumeAt 恢复"
                text = "本次 $total 次 · 点「继续」可提前恢复"
            }
            state.isMicPausedForEnroll -> {
                title = "录入声音中 · 监听暂停"
                text = "本次 $total 次 · 录完会自动继续"
            }
            else -> {
                title = buildString {
                    append("监听中 · 本次 $total 次")
                    if (voiceprintOn || filtered > 0) append(" · 已忽略他人 $filtered 次")
                }
                text = buildString {
                    session.plannedEndAtMs?.let { append("${formatClock(it)} 自动结束 · ") }
                    append("盯防：$wordsText")
                }
            }
        }

        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wave_notify)
            .setColor(BRAND_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openMainPendingIntent(context, RC_OPEN_FROM_ONGOING))

        if (session != null && !paused) {
            builder.setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(nowMs - session.monitoredMs(nowMs))
        } else {
            builder.setUsesChronometer(false)
                .setShowWhen(false)
        }

        if (session != null) {
            if (paused) {
                builder.addAction(
                    0,
                    "继续",
                    receiverPendingIntent(context, NotificationActionReceiver.ACTION_RESUME, RC_ONGOING_RESUME)
                )
            } else {
                builder.addAction(
                    0,
                    "暂停 ${MonitorController.DEFAULT_PAUSE_MINUTES} 分钟",
                    receiverPendingIntent(context, NotificationActionReceiver.ACTION_PAUSE, RC_ONGOING_PAUSE)
                )
            }
        }
        builder.addAction(
            0,
            "结束",
            receiverPendingIntent(context, NotificationActionReceiver.ACTION_STOP, RC_ONGOING_STOP)
        )

        getBrandLargeIcon(context)?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    /** Re-posts the ongoing notification. Ignored when no session is active. */
    fun updateOngoingNotification(context: Context, state: MonitorUiState) {
        if (state.activeSession == null) return
        if (!canPostNotifications(context)) return
        val notification = buildOngoingNotification(context, state)
        try {
            NotificationManagerCompat.from(context).notify(ONGOING_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    // ---------------------------------------------------------------------
    // Per-hit alert
    // ---------------------------------------------------------------------

    /**
     * Vibrates per [MonitorSettings.vibrationStyle] and (if enabled) posts
     * "没有 · 本次第 3 次" with [不是我] and [暂停 15 分钟].
     */
    fun showHitAlert(
        context: Context,
        hit: HitRecord,
        sessionCount: Int,
        settings: MonitorSettings
    ) {
        ensureChannels(context)
        vibrate(context, settings.vibrationStyle)

        if (!settings.pushAlertEnabled || !canPostNotifications(context)) return

        val slot = Math.floorMod(alertSequence.getAndIncrement(), ALERT_SLOTS)
        val notificationId = ALERT_NOTIFICATION_BASE_ID + slot

        val notMeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOT_ME
            putExtra(NotificationActionReceiver.EXTRA_HIT_ID, hit.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val notMePendingIntent = PendingIntent.getBroadcast(
            context,
            RC_ALERT_NOT_ME_BASE + slot,
            notMeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAUSE
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            RC_ALERT_PAUSE_BASE + slot,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "${hit.word} · 本次第 $sessionCount 次"
        val body = hit.snippet.ifBlank { "刚刚说了「${hit.word}」" }

        val builder = baseAlertBuilder(context, title, body, RC_OPEN_FROM_ALERT)
            .addAction(0, "不是我", notMePendingIntent)
            .addAction(0, "暂停 ${MonitorController.DEFAULT_PAUSE_MINUTES} 分钟", pausePendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /** Settings → 提醒方式 → 测试. Uses the current vibration style. */
    fun sendTestAlert(context: Context) {
        MonitorStateStore.ensureInitialized(context)
        ensureChannels(context)
        val settings = MonitorStateStore.uiState.value.settings
        vibrate(context, settings.vibrationStyle)
        if (!canPostNotifications(context)) return

        val builder = baseAlertBuilder(
            context = context,
            title = "没有 · 本次第 1 次（测试）",
            body = "看到这条横幅，说明提醒通知正常。",
            openRequestCode = RC_OPEN_FROM_TEST
        )
        try {
            NotificationManagerCompat.from(context).notify(TEST_ALERT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {
        }
    }

    private fun baseAlertBuilder(
        context: Context,
        title: String,
        body: String,
        openRequestCode: Int
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wave_notify)
            .setColor(BRAND_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(0)
            .setAutoCancel(true)
            .setContentIntent(openMainPendingIntent(context, openRequestCode))
        getBrandLargeIcon(context)?.let { builder.setLargeIcon(it) }
        return builder
    }

    // ---------------------------------------------------------------------
    // Session summary
    // ---------------------------------------------------------------------

    /** "本次监听结束 · 42 分钟" / "5 次（每小时 7.1 次）· 已忽略他人 6 次". Tap opens MainActivity. */
    fun postSessionSummary(context: Context, record: SessionRecord) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val minutes = (record.monitoredSec + 30L) / 60L
        val durationText = if (minutes < 1L) "不到 1 分钟" else "$minutes 分钟"
        val title = "本次监听结束 · $durationText"
        val text = buildString {
            append("${record.totalCount} 次")
            record.ratePerHour?.let {
                append("（每小时 ${String.format(Locale.getDefault(), "%.1f", it)} 次）")
            }
            if (record.filteredOthers > 0) append(" · 已忽略他人 ${record.filteredOthers} 次")
        }

        val builder = NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wave_notify)
            .setColor(BRAND_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openMainPendingIntent(context, RC_OPEN_FROM_SUMMARY))
        getBrandLargeIcon(context)?.let { builder.setLargeIcon(it) }

        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    // ---------------------------------------------------------------------
    // Vibration
    // ---------------------------------------------------------------------

    fun vibrate(context: Context, style: VibrationStyle) {
        if (style == VibrationStyle.OFF) return
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            val effect = when (style) {
                VibrationStyle.TRIPLE_SHORT -> VibrationEffect.createWaveform(TRIPLE_SHORT_WAVEFORM, -1)
                VibrationStyle.SINGLE_LONG -> VibrationEffect.createOneShot(
                    SINGLE_LONG_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                VibrationStyle.OFF -> return
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    fun formatClock(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter)

    private fun openMainPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun receiverPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
