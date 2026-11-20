package com.selfuse.catchphraseguard

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: one tap to start / end a listening session without opening the app.
 */
class MonitorTileService : TileService() {

    companion object {
        private const val TAG = "MonitorTileService"
        private const val RC_TILE_TRAMPOLINE = 900
        private const val RC_TILE_OPEN_MAIN = 901

        /** Asks the system to refresh the tile (calls onStartListening). Safe to call anytime. */
        fun requestUpdate(context: Context) {
            try {
                TileService.requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, MonitorTileService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "requestListeningState failed", e)
            }
        }
    }

    private var scope: CoroutineScope? = null
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        MonitorStateStore.ensureInitialized(applicationContext)
        refreshTile(MonitorStateStore.uiState.value)

        listenJob?.cancel()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        listenJob = s.launch {
            MonitorStateStore.uiState.collect { refreshTile(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        MonitorStateStore.ensureInitialized(applicationContext)
        val state = MonitorStateStore.uiState.value

        if (state.activeSession != null) {
            if (state.isRunning) {
                MonitorController.stop(applicationContext)
            } else {
                // Orphaned session (service died without restarting): close it out directly.
                MonitorStateStore.endSession()
                    ?.takeIf { it.monitoredSec >= 30L || it.totalCount > 0 }
                    ?.let { NotificationHelper.postSessionSummary(applicationContext, it) }
            }
            refreshTile(MonitorStateStore.uiState.value)
            return
        }

        if (isLocked) {
            unlockAndRun { startFromTile() }
        } else {
            startFromTile()
        }
    }

    private fun startFromTile() {
        if (!MonitorController.hasMicPermission(this)) {
            // Permission must be granted from the app UI.
            launchActivity(Intent(this, MainActivity::class.java), RC_TILE_OPEN_MAIN)
            return
        }
        // Starting a microphone foreground service needs a foreground context.
        launchActivity(Intent(this, TileTrampolineActivity::class.java), RC_TILE_TRAMPOLINE)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchActivity(intent: Intent, requestCode: Int) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch activity from tile", e)
        }
    }

    private fun refreshTile(state: MonitorUiState) {
        val tile = qsTile ?: return
        val session = state.activeSession
        val nowMs = System.currentTimeMillis()

        tile.label = "口头禅监听"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wave_notify)
        tile.state = if (session != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        val subtitle = when {
            session == null -> "未开启"
            session.isPaused(nowMs) -> "已暂停 · 本次 ${session.totalCount} 次"
            else -> "本次 ${session.totalCount} 次"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.contentDescription = "口头禅监听，$subtitle"

        try {
            tile.updateTile()
        } catch (e: Exception) {
            Log.w(TAG, "updateTile failed", e)
        }
    }
}
