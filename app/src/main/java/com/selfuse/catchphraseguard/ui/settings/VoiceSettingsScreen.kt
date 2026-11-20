package com.selfuse.catchphraseguard.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.VoiceCaptureResult
import com.selfuse.catchphraseguard.VoiceDecision
import com.selfuse.catchphraseguard.VoiceprintManager
import com.selfuse.catchphraseguard.ui.components.SectionHeader
import com.selfuse.catchphraseguard.ui.components.SubScreenScaffold
import com.selfuse.catchphraseguard.ui.components.VoiceCaptureButton
import com.selfuse.catchphraseguard.ui.onboarding.VoiceEnrollment
import com.selfuse.catchphraseguard.ui.tabular
import java.util.Locale

private enum class VoiceSubMode { OVERVIEW, ENROLL, TEST }

@Composable
fun VoiceSettingsScreen(
    state: MonitorUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(VoiceSubMode.OVERVIEW) }

    BackHandler(enabled = mode != VoiceSubMode.OVERVIEW) {
        mode = VoiceSubMode.OVERVIEW
    }

    when (mode) {
        VoiceSubMode.OVERVIEW -> VoiceOverview(
            state = state,
            onBack = onBack,
            onStartEnroll = { mode = VoiceSubMode.ENROLL },
            onStartTest = { mode = VoiceSubMode.TEST }
        )

        VoiceSubMode.ENROLL -> SubScreenScaffold(
            title = if (state.voiceprintProfile.isEnrolled) "重新录制声音" else "录制我的声音",
            onBack = { mode = VoiceSubMode.OVERVIEW }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "随口回答 3 个问题，不用念稿。重新录制会覆盖旧的声音特征。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                VoiceEnrollment(
                    onFinished = { embeddings, stepCount ->
                        MonitorStateStore.saveEnrolledVoiceprint(embeddings, stepCount)
                        Toast.makeText(context, "已更新你的声音", Toast.LENGTH_SHORT).show()
                        mode = VoiceSubMode.OVERVIEW
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        VoiceSubMode.TEST -> VoiceTestView(
            state = state,
            onBack = { mode = VoiceSubMode.OVERVIEW }
        )
    }
}

@Composable
private fun VoiceOverview(
    state: MonitorUiState,
    onBack: () -> Unit,
    onStartEnroll: () -> Unit,
    onStartTest: () -> Unit
) {
    val context = LocalContext.current
    val profile = state.voiceprintProfile
    val settings = state.settings
    var confirmClearVoice by remember { mutableStateOf(false) }
    var confirmClearNegatives by remember { mutableStateOf(false) }

    SubScreenScaffold(title = "我的声音", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    headlineContent = {
                        Text(if (profile.isEnrolled) "已录入我的声音" else "还没有录入声音")
                    },
                    supportingContent = {
                        Text(
                            if (profile.isEnrolled) {
                                val updated = profile.updatedAtText.ifBlank { "刚刚" }
                                "已采集 ${profile.sampleCount} 段 · 更新于 $updated"
                            } else {
                                "录入后，别人说盯防词会被忽略，只算你自己说的。"
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            item {
                if (profile.isEnrolled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onStartEnroll,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重新录制")
                        }
                        OutlinedButton(
                            onClick = onStartTest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("测试识别")
                        }
                    }
                } else {
                    Button(
                        onClick = onStartEnroll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text("录制我的声音")
                    }
                }
            }

            item { SectionHeader("声纹区分") }
            item {
                SwitchRow(
                    title = "只提醒我自己的声音",
                    description = if (profile.isEnrolled) {
                        "关闭后，任何人说盯防词都会计入"
                    } else {
                        "录入声音后自动开启"
                    },
                    checked = settings.voiceprintEnabled && profile.isEnrolled,
                    enabled = profile.isEnrolled,
                    onCheckedChange = { v ->
                        MonitorStateStore.updateSettings { it.copy(voiceprintEnabled = v) }
                    }
                )
            }

            item {
                val negCount = profile.negativeVectors.size
                ListItem(
                    headlineContent = { Text("「不是我」学习样本") },
                    supportingContent = {
                        Text(
                            if (negCount == 0) {
                                "还没有。误报时在通知或小结里点「不是我」，就会记下来。"
                            } else {
                                "已记住 $negCount 条他人的声音特征，下次遇到类似声音会自动过滤。"
                            }
                        )
                    },
                    trailingContent = if (negCount > 0) {
                        {
                            TextButton(onClick = { confirmClearNegatives = true }) { Text("清空") }
                        }
                    } else {
                        null
                    }
                )
            }

            if (profile.isEnrolled) {
                item { SectionHeader("重置") }
                item {
                    ListItem(
                        headlineContent = {
                            Text("清除我的声音", color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = {
                            Text("删除已录制的声纹和「不是我」样本，历史计数不受影响。")
                        },
                        modifier = Modifier.clickable { confirmClearVoice = true }
                    )
                }
            }
        }
    }

    if (confirmClearNegatives) {
        AlertDialog(
            onDismissRequest = { confirmClearNegatives = false },
            title = { Text("清空「不是我」样本？") },
            text = { Text("会忘掉之前标记过的他人声音特征，你自己的声音不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    MonitorStateStore.clearNegativeSamples()
                    confirmClearNegatives = false
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                }) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearNegatives = false }) { Text("取消") }
            }
        )
    }

    if (confirmClearVoice) {
        AlertDialog(
            onDismissRequest = { confirmClearVoice = false },
            title = { Text("清除我的声音？") },
            text = { Text("清除后，监听时任何人说盯防词都会提醒，直到重新录制。") },
            confirmButton = {
                TextButton(onClick = {
                    MonitorStateStore.clearVoiceprint()
                    confirmClearVoice = false
                    Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearVoice = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VoiceTestView(
    state: MonitorUiState,
    onBack: () -> Unit
) {
    var lastDecision by remember { mutableStateOf<VoiceDecision?>(null) }
    var tip by remember { mutableStateOf<String?>(null) }
    val threshold = state.settings.voiceprintThreshold

    SubScreenScaffold(title = "测试声音识别", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "随口说一句平常的话（比如带「没有」或「不是」），也可以让旁边的人说一句试试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            VoiceCaptureButton(
                multiScale = false,
                idleLabel = "按住测试",
                onRecordingChange = { recording ->
                    if (recording) tip = null
                },
                onResult = { result ->
                    when (result) {
                        is VoiceCaptureResult.Success -> {
                            lastDecision = VoiceprintManager.decide(
                                liveEmbedding = result.primaryEmbedding,
                                profile = state.voiceprintProfile,
                                threshold = threshold
                            )
                        }
                        is VoiceCaptureResult.TooQuiet -> {
                            tip = "刚才说得太短或太轻，多说几个字试试"
                        }
                        is VoiceCaptureResult.Error -> {
                            tip = result.message
                        }
                    }
                }
            )
            val tipText = tip
            if (tipText != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            val decision = lastDecision
            if (decision != null) {
                Spacer(Modifier.height(28.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (decision.isSelf) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (decision.isSelf) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null
                            )
                            Text(
                                text = if (decision.isSelf) "判定为：是你本人" else "判定为：不是你（会被忽略）",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "与你的声音相似度：${String.format(Locale.ROOT, "%.2f", decision.selfScore)}（当前门槛 ${String.format(Locale.ROOT, "%.2f", threshold)}）",
                            style = MaterialTheme.typography.bodyMedium.tabular()
                        )
                        if (decision.negativeScore != null) {
                            Text(
                                text = "与「不是我」样本最高相似度：${String.format(Locale.ROOT, "%.2f", decision.negativeScore)}",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (!decision.isSelf && decision.selfScore >= threshold) {
                            Text(
                                text = "虽然达到门槛，但与「不是我」样本接近，所以被忽略。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
