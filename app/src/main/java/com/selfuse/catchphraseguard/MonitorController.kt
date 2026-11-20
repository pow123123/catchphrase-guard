package com.selfuse.catchphraseguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * v5 single entry point for starting/stopping/pausing monitoring.
 * Used by the UI, the Quick Settings tile, and notification actions.
 * The foreground service handles these actions.
 */
object MonitorController {
    const val ACTION_START = "com.selfuse.catchphraseguard.ACTION_START"
    const val ACTION_STOP = "com.selfuse.catchphraseguard.ACTION_STOP"
    const val ACTION_PAUSE = "com.selfuse.catchphraseguard.ACTION_PAUSE"
    const val ACTION_RESUME = "com.selfuse.catchphraseguard.ACTION_RESUME"

    /** Int minutes for ACTION_START. 0 or missing = until the user stops. */
    const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    /** Int minutes for ACTION_PAUSE. Defaults to 15. */
    const val EXTRA_PAUSE_MINUTES = "extra_pause_minutes"

    const val DEFAULT_PAUSE_MINUTES = 15

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Caller must have obtained RECORD_AUDIO first (and ideally POST_NOTIFICATIONS). */
    fun start(context: Context, durationMinutes: Int) {
        val intent = Intent(context, MonitorForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_DURATION_MINUTES, durationMinutes.coerceAtLeast(0))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) = send(context, ACTION_STOP)

    fun pause(context: Context, minutes: Int = DEFAULT_PAUSE_MINUTES) {
        val intent = Intent(context, MonitorForegroundService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_PAUSE_MINUTES, minutes)
        }
        runCatching { context.startService(intent) }
    }

    fun resume(context: Context) = send(context, ACTION_RESUME)

    private fun send(context: Context, action: String) {
        val intent = Intent(context, MonitorForegroundService::class.java).apply { this.action = action }
        runCatching { context.startService(intent) }
    }
}
