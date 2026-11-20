package com.selfuse.catchphraseguard.ui.components

import android.Manifest
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorController
import com.selfuse.catchphraseguard.VoiceCaptureResult
import com.selfuse.catchphraseguard.VoiceprintManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Round "hold to talk" button. Press and hold to talk, release to finish; or tap once and
 * it stops by itself after a natural pause. Tap again while recording to stop early.
 * Wraps [VoiceprintManager.captureInteractiveUtterance] and asks for the mic permission if needed.
 */
@Composable
fun VoiceCaptureButton(
    multiScale: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    idleLabel: String = "按住说话",
    onRecordingChange: (Boolean) -> Unit = {},
    onResult: (VoiceCaptureResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnRecordingChange by rememberUpdatedState(onRecordingChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentMultiScale by rememberUpdatedState(multiScale)

    var isRecording by remember { mutableStateOf(false) }
    var stopSignal by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var voicedSec by remember { mutableFloatStateOf(0f) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopSignal?.complete(Unit) }
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isRecording -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        label = "captureButtonColor"
    )
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isRecording -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .size(132.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (currentEnabled) {
                                if (isRecording) {
                                    stopSignal?.complete(Unit)
                                } else if (!MonitorController.hasMicPermission(context)) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    val downAt = SystemClock.elapsedRealtime()
                                    val signal = CompletableDeferred<Unit>()
                                    stopSignal = signal
                                    isRecording = true
                                    voicedSec = 0f
                                    currentOnRecordingChange(true)
                                    scope.launch {
                                        val result = VoiceprintManager.captureInteractiveUtterance(
                                            context = context,
                                            stopSignal = signal,
                                            autoStopOnSilence = true,
                                            multiScale = currentMultiScale,
                                            onLiveFeedback = { _, rms, voiced ->
                                                level = (rms / 0.12f).coerceIn(0f, 1f)
                                                voicedSec = voiced
                                            }
                                        )
                                        isRecording = false
                                        level = 0f
                                        stopSignal = null
                                        currentOnRecordingChange(false)
                                        currentOnResult(result)
                                    }
                                    val released = tryAwaitRelease()
                                    // Held long enough = push-to-talk: release ends the recording.
                                    // A short tap = hands-free: stops by itself after a pause.
                                    if (released && SystemClock.elapsedRealtime() - downAt >= 400L) {
                                        signal.complete(Unit)
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when {
                        isRecording && voicedSec >= 0.8f -> "听到了\n松开或停顿结束"
                        isRecording -> "在听…"
                        else -> idleLabel
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (isRecording) level else 0f },
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isRecording) "再点一下可以提前结束" else "按住说，松开结束；或轻点一下，说完停顿会自动结束",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
