package com.selfuse.catchphraseguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.selfuse.catchphraseguard.MonitorSettings
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState

@Composable
fun StatsAndSettingsTab(
    uiState: MonitorUiState,
    onSendTestPush: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            DailyStatsOverviewCard(uiState = uiState)
        }

        item {
            EngineSettingsCard(
                settings = uiState.settings,
                onSendTestPush = onSendTestPush
            )
        }

        item {
            OriginOsHealthCard(context = context)
        }
    }
}

@Composable
private fun DailyStatsOverviewCard(uiState: MonitorUiState) {
    val palette = LocalOriginPalette.current
    val context = LocalContext.current
    val todayTotal = uiState.keywords.sumOf { it.todayCount }
    val maxSingleWord = (uiState.keywords.maxOfOrNull { it.todayCount } ?: 0).coerceAtLeast(1)

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "今日数据看板",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            TextButton(
                onClick = {
                    MonitorStateStore.resetTodayCounts()
                    Toast.makeText(context, "今日统计与动态已清零", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("清零今日数据", fontSize = 12.sp, color = palette.accentBlue)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricTile(
                label = "今日自说口头禅",
                value = "$todayTotal",
                unit = "次",
                accent = palette.alertCoral,
                bg = palette.alertContainer,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "护盾拦截他人声音",
                value = "${uiState.todayFilteredOthersCount}",
                unit = "次",
                accent = palette.shieldEmerald,
                bg = palette.shieldContainer,
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            uiState.keywords.forEach { item ->
                val targetRatio = if (todayTotal == 0) {
                    0f
                } else {
                    (item.todayCount.toFloat() / maxSingleWord).coerceIn(0f, 1f)
                }
                val animRatio by animateFloatAsState(
                    targetValue = targetRatio,
                    animationSpec = tween(durationMillis = 400),
                    label = "wordBar_${item.word}"
                )

                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "「${item.word}」",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "今日 ${item.todayCount} 次 / 累计 ${item.totalCount} 次",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.cardSubtleSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accentBlue)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = accent.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun EngineSettingsCard(
    settings: MonitorSettings,
    onSendTestPush: () -> Unit
) {
    val palette = LocalOriginPalette.current

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "提醒与音频引擎设置",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingToggleItem(
            title = "手机横幅消息推送",
            subtitle = "本人说出盯防口头禅时立即弹出顶部系统消息",
            checked = settings.pushAlertEnabled,
            onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(pushAlertEnabled = v) } }
        )

        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

        SettingToggleItem(
            title = "同步三连脉冲短震",
            subtitle = "手机揣裤兜静音时，靠独特节奏立刻感知",
            checked = settings.vibrateEnabled,
            onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(vibrateEnabled = v) } }
        )

        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

        SettingToggleItem(
            title = "口袋/桌面远场收音增益",
            subtitle = "手机放桌上或口袋里时放大语音清晰度",
            checked = settings.micBoost,
            onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(micBoost = v) } }
        )

        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

        SettingToggleItem(
            title = "忽略远处微弱环境音",
            subtitle = "过滤低分贝底噪，进一步提升安静度",
            checked = settings.nearVoiceFilter,
            onCheckedChange = { v -> MonitorStateStore.updateSettings { it.copy(nearVoiceFilter = v) } }
        )

        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 10.dp))

        Text(
            text = "同一口头禅防刷屏冷却：${settings.cooldownSeconds} 秒",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary
        )
        Text(
            text = "连说一句「没有没有」时只合并推送一次提醒",
            fontSize = 12.sp,
            color = palette.textSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(1, 3, 5, 10).forEach { sec ->
                val active = settings.cooldownSeconds == sec
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) palette.accentBlue else palette.cardSubtleSurface)
                        .clickable {
                            MonitorStateStore.updateSettings { it.copy(cooldownSeconds = sec) }
                        }
                ) {
                    Text(
                        text = "${sec}秒",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else palette.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onSendTestPush,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text("发送一条测试推送消息", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalOriginPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OriginOsHealthCard(context: Context) {
    val palette = LocalOriginPalette.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryOk by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    var notifyOk by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "vivo OriginOS 锁屏保活体检",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            text = "若希望息屏放口袋长时间稳定运行，请确保以下两项已开启",
            fontSize = 12.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        HealthCheckRow(
            title = "系统横幅与锁屏通知权限",
            isReady = notifyOk,
            actionText = "去设置",
            onClick = {
                openNotificationSettings(context)
                notifyOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        )

        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

        HealthCheckRow(
            title = "后台高耗电免杀白名单",
            isReady = batteryOk,
            actionText = "一键申请",
            onClick = {
                requestIgnoreBattery(context)
                batteryOk = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        )
    }
}

@Composable
private fun HealthCheckRow(
    title: String,
    isReady: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    val palette = LocalOriginPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary
        )
        OriginPillBadge(
            backgroundColor = if (isReady) palette.shieldContainer else palette.warningContainer,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = if (isReady) "已就绪 · 点击查看" else actionText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReady) palette.shieldEmerald else palette.warningAmber
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppDetailsSettings(context)
    }
}

private fun requestIgnoreBattery(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } else {
            openAppDetailsSettings(context)
        }
    } catch (_: Exception) {
        openAppDetailsSettings(context)
    }
}

private fun openAppDetailsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
