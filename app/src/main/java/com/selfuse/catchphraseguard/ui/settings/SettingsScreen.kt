@file:OptIn(ExperimentalLayoutApi::class)

package com.selfuse.catchphraseguard.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorController
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.NotificationHelper
import com.selfuse.catchphraseguard.VibrationStyle
import com.selfuse.catchphraseguard.ui.components.SectionHeader
import com.selfuse.catchphraseguard.ui.components.SubScreenScaffold
import com.selfuse.catchphraseguard.ui.components.SystemChecks
import com.selfuse.catchphraseguard.ui.components.rememberResumeCount
import com.selfuse.catchphraseguard.ui.tabular
import java.util.Locale

const val THRESHOLD_MIN = 0.20f
const val THRESHOLD_MAX = 0.68f
const val THRESHOLD_DEFAULT = 0.35f
private val COOLDOWN_OPTIONS = listOf(1, 3, 5, 10)

@Composable
fun SettingsScreen(
    state: MonitorUiState,
    onBack: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenRecognitionCheck: () -> Unit
) {
    val context = LocalContext.current
    val settings = state.settings
    val profile = state.voiceprintProfile
    var confirmClear by remember { mutableStateOf(false) }

    val sendTest: () -> Unit = {
        NotificationHelper.sendTestAlert(context)
        Toast.makeText(context, "已发送测试提醒", Toast.LENGTH_SHORT).show()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            sendTest()
        } else {
            NotificationHelper.sendTestAlert(context)
            Toast.makeText(context, "没有通知权限，已测试震动", Toast.LENGTH_LONG).show()
        }
    }

    // System status, refreshed every time we come back from system settings.
    val resumeCount = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val micOk = remember(resumeCount, refresh) { MonitorController.hasMicPermission(context) }
    val notifOk = remember(resumeCount, refresh) { MonitorController.hasNotificationPermission(context) }
    val batteryOk = remember(resumeCount, refresh) { SystemChecks.isIgnoringBatteryOptimizations(context) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (!ok) SystemChecks.openAppDetails(context)
    }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (!ok) SystemChecks.openAppDetails(context)
    }

    SubScreenScaffold(title = "设置", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            // ---------------- 盯防词 ----------------
            item { SectionHeader("盯防词") }
            item {
                val words = state.keywords.filter { it.enabled }.joinToString("、") { it.word }
                ListItem(
                    headlineContent = { Text("盯防词") },
                    supportingContent = { Text(words.ifEmpty { "还没有" }) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenKeywords)
                )
            }

            // ---------------- 我的声音 ----------------
            item { SectionHeader("我的声音") }
            item {
                ListItem(
                    headlineContent = { Text("我的声音") },
                    supportingContent = {
                        Text(
                            when {
                                !profile.isEnrolled -> "未录入 · 录入后只算你自己说的"
                                !settings.voiceprintEnabled -> "已录入 · 区分已关闭"
                                else -> "已录入 · 重新录制 · 测试"
                            }
                        )
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenVoice)
                )
            }

            // ---------------- 提醒方式 ----------------
            item { SectionHeader("提醒方式") }
            item {
                SwitchRow(
                    title = "通知提醒",
                    description = "说出口头禅时发一条通知，可直接点「不是我」",
                    checked = settings.pushAlertEnabled,
                    onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(pushAlertEnabled = v) } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("震动样式") }
                )
            }
            VibrationStyle.entries.forEach { style ->
                item(key = "vib_${style.name}") {
                    val selected = settings.vibrationStyle == style
                    ListItem(
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        headlineContent = { Text(style.label) },
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { MonitorStateStore.updateSettings { it.copy(vibrationStyle = style) } }
                        )
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("同一个词的提醒间隔") },
                        supportingContent = { Text("间隔内重复说同一个词，只计一次") }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        COOLDOWN_OPTIONS.forEach { sec ->
                            val selected = settings.cooldownSeconds == sec
                            FilterChip(
                                selected = selected,
                                onClick = { MonitorStateStore.updateSettings { it.copy(cooldownSeconds = sec) } },
                                label = { Text("$sec 秒") },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    headlineContent = { Text("发送一条测试提醒") },
                    supportingContent = { Text("看看通知和震动是否正常") },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !MonitorController.hasNotificationPermission(context)
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            sendTest()
                        }
                    }
                )
            }

            // ---------------- 识别 ----------------
            item { SectionHeader("识别") }
            item {
                SwitchRow(
                    title = "手机放远时增强收音",
                    description = "手机在桌上或口袋里时打开",
                    checked = settings.micBoost,
                    onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(micBoost = v) } }
                )
            }
            item {
                SwitchRow(
                    title = "只听近处的声音",
                    description = "忽略远处微弱的说话声，嘈杂环境下更准",
                    checked = settings.nearVoiceFilter,
                    onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(nearVoiceFilter = v) } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("检查识别效果") },
                    supportingContent = { Text("显示实时转写文字和麦克风音量") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenRecognitionCheck)
                )
            }
            item {
                ThresholdSetting(current = settings.voiceprintThreshold)
            }

            // ---------------- 系统 ----------------
            item { SectionHeader("系统") }
            item {
                StatusRow(
                    title = "麦克风权限",
                    ok = micOk,
                    actionLabel = "允许",
                    onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    StatusRow(
                        title = "通知权限",
                        ok = notifOk,
                        actionLabel = "允许",
                        onAction = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }
            item {
                StatusRow(
                    title = "后台不限制",
                    ok = batteryOk,
                    actionLabel = "去设置",
                    onAction = { SystemChecks.requestIgnoreBatteryOptimizations(context) }
                )
            }
            if (SystemChecks.isVivoFamily()) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        headlineContent = { Text("vivo / iQOO 手机") },
                        supportingContent = {
                            Text(
                                "还需要在 i管家 → 应用管理 → 自启动 里允许本应用，" +
                                    "并在 设置 → 电池 → 后台耗电管理 里选「允许后台高耗电」，否则锁屏后可能被停止。"
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("应用详情") },
                    supportingContent = { Text("在系统设置里管理权限和通知") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { SystemChecks.openAppDetails(context) }
                )
            }

            // ---------------- 数据 ----------------
            item { SectionHeader("数据") }
            item {
                ListItem(
                    headlineContent = { Text("清除全部记录", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("删除历史、趋势和逐条记录。盯防词、设置和你的声音会保留。") },
                    modifier = Modifier.clickable { confirmClear = true }
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部记录？") },
            text = { Text("所有监听历史和趋势都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    MonitorStateStore.clearAllHistory()
                    confirmClear = false
                    Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (description != null) {
            { Text(description) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

@Composable
private fun StatusRow(title: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (ok) "已开启" else "未开启") },
        trailingContent = {
            if (ok) {
                Icon(Icons.Filled.Check, contentDescription = "已开启", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    )
}

@Composable
private fun ThresholdSetting(current: Float) {
    var expanded by remember { mutableStateOf(false) }
    var value by remember(current) { mutableFloatStateOf(current.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            overlineContent = { Text("高级") },
            headlineContent = { Text("本人判定严格度") },
            supportingContent = {
                Text(if (expanded) "默认不用动。调高：别人更难被算成你；调低：你小声说也能认出" else "默认不用动")
            },
            trailingContent = {
                Text(
                    String.format(Locale.ROOT, "%.2f", value),
                    style = MaterialTheme.typography.bodyLarge.tabular()
                )
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )
        if (expanded) {
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = THRESHOLD_MIN..THRESHOLD_MAX,
                onValueChangeFinished = {
                    val v = value
                    MonitorStateStore.updateSettings { it.copy(voiceprintThreshold = v) }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "宽松",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
                TextButton(onClick = {
                    value = THRESHOLD_DEFAULT
                    MonitorStateStore.updateSettings { it.copy(voiceprintThreshold = THRESHOLD_DEFAULT) }
                }) {
                    Text("恢复默认")
                }
                Text(
                    "严格",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
