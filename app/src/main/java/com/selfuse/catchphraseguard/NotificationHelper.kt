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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

object NotificationHelper {
    const val ONGOING_NOTIFICATION_ID = 1001
    const val STATUS_CHANNEL_ID = "monitor_ongoing_status"
    const val ALERT_CHANNEL_ID = "catchphrase_alert_push_v2"
    private const val BRAND_ACCENT_COLOR = 0xFFFF6B4A.toInt() // Coral-orange matching Icon B

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
            val cornerRadius = targetPx * 0.24f // OriginOS squircle corner ratio

            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, rect, rect, paint)

            cachedBrandIconBitmap = rounded
            rounded
        } catch (_: Exception) {
            null
        }
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "后台监听常驻状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于保持麦克风在锁屏和后台稳定运行"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "口头禅命中推送提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到你本人说出设定的口头禅时立即弹出消息提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 90, 70, 90, 70, 140)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(alertChannel)
    }

    fun buildOngoingNotification(
        context: Context,
        activeKeywords: List<String>,
        todayTotalCount: Int,
        voiceprintProtected: Boolean = false
    ): Notification {
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wordsText = if (activeKeywords.isEmpty()) {
            "暂无启用的口头禅"
        } else {
            activeKeywords.joinToString("、")
        }
        val modeTag = if (voiceprintProtected) "声纹护盾开启" else "全员监听"

        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wave_notify)
            .setColor(BRAND_ACCENT_COLOR)
            .setContentTitle("口头禅守护中 · 今日 $todayTotalCount 次 ($modeTag)")
            .setContentText("盯防词：$wordsText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .addAction(0, "停止监听", stopPendingIntent)

        getBrandLargeIcon(context)?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    fun updateOngoingNotification(
        context: Context,
        activeKeywords: List<String>,
        todayTotalCount: Int,
        voiceprintProtected: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        val notification = buildOngoingNotification(
            context = context,
            activeKeywords = activeKeywords,
            todayTotalCount = todayTotalCount,
            voiceprintProtected = voiceprintProtected
        )
        try {
            NotificationManagerCompat.from(context).notify(ONGOING_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun triggerCatchphraseAlert(
        context: Context,
        word: String,
        todayCount: Int,
        snippet: String,
        pushEnabled: Boolean,
        vibrateEnabled: Boolean,
        similarity: Float? = null
    ) {
        ensureChannels(context)

        if (vibrateEnabled) {
            triggerTriplePulseVibration(context)
        }

        if (!pushEnabled || !canPostNotifications(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val simTag = if (similarity != null) {
            " · 本人声纹 ${(similarity * 100).roundToInt()}%"
        } else {
            ""
        }
        val bodyText = if (snippet.isNotBlank()) {
            "今日第 $todayCount 次说「$word」$simTag\n刚刚语境：$snippet"
        } else {
            "今日第 $todayCount 次抓到你说「$word」$simTag"
        }

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wave_notify)
            .setColor(BRAND_ACCENT_COLOR)
            .setContentTitle("抓到口头禅：【$word】")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        getBrandLargeIcon(context)?.let { builder.setLargeIcon(it) }

        val notificationId = 2000 + (alertSequence.getAndIncrement() % 8)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun triggerTriplePulseVibration(context: Context) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 90, 70, 90, 70, 140),
                    -1
                )
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
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
