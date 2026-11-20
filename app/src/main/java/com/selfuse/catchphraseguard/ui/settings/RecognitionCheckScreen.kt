package com.selfuse.catchphraseguard.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorController
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.ui.components.SectionHeader
import com.selfuse.catchphraseguard.ui.components.SubScreenScaffold
import com.selfuse.catchphraseguard.ui.components.SystemChecks
import com.selfuse.catchphraseguard.ui.tabular
import java.util.Locale

@Composable
fun RecognitionCheckScreen(
    state: MonitorUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val session = state.activeSession
    val isPaused = session != null && session.isPaused(System.currentTimeMillis())
    val listening = state.isRunning && session != null && !isPaused

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            MonitorController.hasMicPermission(context)
        if (micOk) {
            MonitorController.start(context, MonitorStateStore.uiState.value.settings.defaultDurationMinutes)
        } else {
            Toast.makeText(context, "需要麦克风权限才能开始监听", Toast.LENGTH_LONG).show()
        }
    }

    val startMonitoring: () -> Unit = {
        if (MonitorController.hasMicPermission(context) && MonitorController.hasNotificationPermission(context)) {
            MonitorController.start(context, state.settings.defaultDurationMinutes)
        } else {
            permissionLauncher.launch(SystemChecks.startPermissions())
        }
    }

    val normalizedLevel = (state.micLevel / 0.12f).coerceIn(0f, 1f)
    val score = state.lastSimilarityScore
    val threshold = state.settings.voiceprintThreshold

    SubScreenScaffold(title = "检查识别效果", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 24.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        when {
                            listening -> "正在监听"
                            isPaused -> "监听已暂停"
                            else -> "尚未开始监听"
                        }
                    )
                },
                supportingContent = {
                    Text(
                        when {
                            listening -> state.statusMessage.ifBlank { "对着手机说几句话，下面会显示实时转写" }
                            isPaused -> "继续监听后才能看到实时识别结果"
                            else -> "开始监听后，这里会显示麦克风音量和实时转写文字"
                        }
                    )
                },
                trailingContent = {
                    when {
                        !state.isRunning || session == null -> FilledTonalButton(
                            onClick = startMonitoring,
                            enabled = state.keywords.any { it.enabled }
                        ) { Text("开始") }
                        isPaused -> FilledTonalButton(
                            onClick = { MonitorController.resume(context) }
                        ) { Text("继续") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )

            SectionHeader("实时转写")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 120.dp)
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    val preview = state.livePreviewText
                    Text(
                        text = preview.ifBlank {
                            if (listening) "正在听，说句话试试…" else "暂无内容"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (preview.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            SectionHeader("麦克风音量")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.settings.micBoost) "当前已开启放远增强" else "标准收音",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.ROOT, "RMS %.3f", state.micLevel),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (listening) normalizedLevel else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }

            SectionHeader("最近一次声纹判定")
            ListItem(
                headlineContent = {
                    Text(
                        text = if (score != null) {
                            "相似度 ${String.format(Locale.ROOT, "%.2f", score)}"
                        } else {
                            "暂无记录"
                        },
                        style = MaterialTheme.typography.titleMedium.tabular()
                    )
                },
                supportingContent = {
                    val text = when {
                        score != null && state.lastSimilarityWasSelf == true ->
                            "判定为本人（当前门槛 ${String.format(Locale.ROOT, "%.2f", threshold)}）"
                        score != null && state.lastSimilarityWasSelf == false ->
                            "判定为他人，已忽略（当前门槛 ${String.format(Locale.ROOT, "%.2f", threshold)}）"
                        !state.voiceprintProfile.isEnrolled ->
                            "尚未录入你的声音"
                        !state.settings.voiceprintEnabled ->
                            "声音区分已关闭"
                        else ->
                            "说到盯防词后会显示这次的声纹相似度"
                    }
                    Text(text)
                }
            )
        }
    }
}
