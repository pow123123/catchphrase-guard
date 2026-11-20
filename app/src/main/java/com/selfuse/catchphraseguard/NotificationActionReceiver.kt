package com.selfuse.catchphraseguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification buttons: 不是我 / 暂停 15 分钟 / 继续 / 结束.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOT_ME = "com.selfuse.catchphraseguard.action.NOTIFICATION_NOT_ME"
        const val ACTION_PAUSE = "com.selfuse.catchphraseguard.action.NOTIFICATION_PAUSE"
        const val ACTION_RESUME = "com.selfuse.catchphraseguard.action.NOTIFICATION_RESUME"
        const val ACTION_STOP = "com.selfuse.catchphraseguard.action.NOTIFICATION_STOP"

        const val EXTRA_HIT_ID = "extra_hit_id"
        /** The alert notification to dismiss after handling the action. */
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        MonitorStateStore.ensureInitialized(appContext)

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_NOT_ME -> {
                val hitId = intent.getLongExtra(EXTRA_HIT_ID, -1L)
                if (hitId != -1L) {
                    MonitorStateStore.markNotMe(hitId)
                }
                if (notificationId != -1) NotificationHelper.cancel(appContext, notificationId)
                MonitorTileService.requestUpdate(appContext)
            }
            ACTION_PAUSE -> {
                val minutes = intent.getIntExtra(
                    MonitorController.EXTRA_PAUSE_MINUTES,
                    MonitorController.DEFAULT_PAUSE_MINUTES
                )
                MonitorController.pause(appContext, minutes)
                if (notificationId != -1) NotificationHelper.cancel(appContext, notificationId)
            }
            ACTION_RESUME -> MonitorController.resume(appContext)
            ACTION_STOP -> MonitorController.stop(appContext)
        }
    }
}
