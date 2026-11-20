package com.selfuse.catchphraseguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Invisible activity launched by the Quick Settings tile. A microphone foreground service may
 * only be started while the app is in the foreground, so the tile bounces through here and
 * waits briefly until the service enters foreground before finishing.
 */
class TileTrampolineActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var startedAtMs = 0L
    private val finishWhenReady = object : Runnable {
        override fun run() {
            val running = MonitorStateStore.uiState.value.isRunning
            val timedOut = SystemClock.elapsedRealtime() - startedAtMs >= 600L
            if (running || timedOut || isFinishing) {
                MonitorTileService.requestUpdate(applicationContext)
                finish()
                overridePendingTransitionCompat()
            } else {
                handler.postDelayed(this, 30L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorStateStore.ensureInitialized(applicationContext)

        if (MonitorController.hasMicPermission(this)) {
            val settings = MonitorStateStore.uiState.value.settings
            try {
                startedAtMs = SystemClock.elapsedRealtime()
                MonitorController.start(this, settings.defaultDurationMinutes)
                handler.postDelayed(finishWhenReady, 30L)
                return
            } catch (e: Exception) {
                Log.e("TileTrampoline", "Unable to start monitoring", e)
                openMain()
            }
        } else {
            openMain()
        }
        MonitorTileService.requestUpdate(applicationContext)
        finish()
        overridePendingTransitionCompat()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishWhenReady)
        super.onDestroy()
    }

    private fun openMain() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun overridePendingTransitionCompat() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}
