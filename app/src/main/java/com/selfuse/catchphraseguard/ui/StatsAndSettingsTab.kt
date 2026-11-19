package com.selfuse.catchphraseguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.selfuse.catchphraseguard.MonitorSettings
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import kotlin.math.roundToInt

@Composable
fun StatsAndSettingsTab(
    uiState: MonitorUiState,
    onSendTestPush: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "鲸云调音与设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        item {
            WhaleReportBannerCard(uiState = uiState)
        }

        item {
            WhaleEqConsoleCard(settings = uiState.settings)
        }

        item {
            OriginOsPermissionGroupCard(
                context = context,
                onSendTestPush = onSendTestPush
            )
        }
    }
}

/**
 * Top NetEase Yearly Report style Dark-Gold & Crimson Dual Metric Banner matching Screen 3 mockup!
 */
@Composable
private fun WhaleReportBannerCard(uiState: MonitorUiState) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current
    val todaySelfTotal = uiState.keywords.sumOf { it.todayCount }
    val todayFiltered = uiState.todayFilteredOthersCount
    val denom = (todaySelfTotal + todayFiltered).coerceAtLeast(1)

    val selfBarRatio by animateFloatAsState(
        targetValue = if (todaySelfTotal == 0) 0.14f else (todaySelfTotal.toFloat() / denom).coerceIn(0.18f, 1f),
        animationSpec = tween(420),
        label = "selfMetricBar"
    )
    val shieldBarRatio by animateFloatAsState(
        targetValue = if (todayFiltered == 0) 0.14f else (todayFiltered.toFloat() / denom).coerceIn(0.18f, 1f),
        animationSpec = tween(420),
        label = "shieldMetricBar"
    )

    val bannerShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF2D2316),
                        Color(0xFF1E1618),
                        Color(0xFF33151B)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            palette.vinylGold.copy(alpha = 0.55f),
                            palette.neteaseRed.copy(alpha = 0.50f)
                        )
                    )
                ),
                bannerShape
            )
    ) {
        // Subtle decorative Whale-tail acoustic silhouette in right background
        Canvas(
            modifier = Modifier
                .matchParentSize()
        ) {
            val w = size.width
            val h = size.height
            val whaleArc = Path().apply {
                moveTo(w * 0.62f, h * 0.88f)
                cubicTo(w * 0.74f, h * 0.72f, w * 0.82f, h * 0.52f, w * 0.94f, h * 0.28f)
                cubicTo(w * 0.90f, h * 0.52f, w * 0.92f, h * 0.68f, w * 0.98f, h * 0.76f)
            }
            drawPath(
                path = whaleArc,
                color = palette.neteaseRedBright.copy(alpha = 0.14f),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left Metric: Today's Self Catchphrases (Champagne Gold)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日已捕获热词",
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$todaySelfTotal",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF7DFAC)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "次",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.vinylGold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x40000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(selfBarRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(palette.vinylGold, Color(0xFFFFF0CA))
                                    )
                                )
                        )
                    }
                }

                // Right Metric: Shield Blocked Others (Coral Red)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "护盾已拦截",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Text(
                            text = "清零",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.vinylGold.copy(alpha = 0.85f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    MonitorStateStore.resetTodayCounts()
                                    Toast.makeText(context, "今日战报已清零", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$todayFiltered",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.neteaseRedBright
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "次",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.neteaseRedBright,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x40000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shieldBarRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(palette.neteaseRed, Color(0xFFFF8580))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Middle Whale-Cloud EQ Console Card:
 * 2x2 Glowing Sound-Effect Plugin Matrix + Custom Glowing Coral Fader + Recessed Cooldown Pills!
 */
@Composable
private fun WhaleEqConsoleCard(settings: MonitorSettings) {
    val palette = LocalVinylPalette.current

    VinylSectionCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "鲸云声学调音台",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFF3D599), Color(0xFFCFA25B))
                        )
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Whale EQ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF231608)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 1 of 2x2 Sound-Effect Plugin Tiles
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            EqPluginTile(
                title = "强提醒推送",
                subtitle = if (settings.pushAlertEnabled) "横幅已激活" else "已静音",
                active = settings.pushAlertEnabled,
                activeAccent = palette.neteaseRedBright,
                activeBg = Color(0xFF2C171B),
                onClick = {
                    MonitorStateStore.updateSettings { it.copy(pushAlertEnabled = !it.pushAlertEnabled) }
                },
                modifier = Modifier.weight(1f)
            ) { tint ->
                BellOutlineIcon(tint = tint, size = 22.dp)
            }

            EqPluginTile(
                title = "三连震动",
                subtitle = if (settings.vibrateEnabled) "脉冲体感开启" else "不震动",
                active = settings.vibrateEnabled,
                activeAccent = palette.vinylGold,
                activeBg = Color(0xFF292117),
                onClick = {
                    MonitorStateStore.updateSettings { it.copy(vibrateEnabled = !it.vibrateEnabled) }
                },
                modifier = Modifier.weight(1f)
            ) { tint ->
                HapticPulseIcon(tint = tint, size = 22.dp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2 of 2x2 Sound-Effect Plugin Tiles
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            EqPluginTile(
                title = "远场增益",
                subtitle = if (settings.micBoost) "口袋/桌面增强" else "标准增益",
                active = settings.micBoost,
                activeAccent = palette.neteaseRedBright,
                activeBg = Color(0xFF2C171B),
                onClick = {
                    MonitorStateStore.updateSettings { it.copy(micBoost = !it.micBoost) }
                },
                modifier = Modifier.weight(1f)
            ) { tint ->
                MicCanvasIcon(tint = tint, size = 22.dp)
            }

            EqPluginTile(
                title = "智能降噪门",
                subtitle = if (settings.nearVoiceFilter) "滤除远处微弱音" else "全频拾音",
                active = settings.nearVoiceFilter,
                activeAccent = palette.vinylGold,
                activeBg = Color(0xFF292117),
                onClick = {
                    MonitorStateStore.updateSettings { it.copy(nearVoiceFilter = !it.nearVoiceFilter) }
                },
                modifier = Modifier.weight(1f)
            ) { tint ->
                NoiseGateIcon(tint = tint, size = 22.dp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Glowing Sensitivity Threshold Fader
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "声纹识别严格度阈值 · ${(settings.voiceprintThreshold * 100).roundToInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )
            Text(
                text = when {
                    settings.voiceprintThreshold < 0.29f -> "高敏防漏"
                    settings.voiceprintThreshold > 0.45f -> "严防误报"
                    else -> "黄金推荐"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.vinylGold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        WhaleGlowingSlider(
            value = settings.voiceprintThreshold,
            minValue = 0.20f,
            maxValue = 0.68f,
            onValueChange = { newVal ->
                MonitorStateStore.updateSettings { it.copy(voiceprintThreshold = newVal) }
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "同一热词防刷屏冷却间隔",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Recessed Dark Capsule Cooldown Selector (`1s | 3s | 5s | 10s`)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.cardRecessed)
                .border(1.dp, Color(0xFF252732), RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            listOf(1, 3, 5, 10).forEach { sec ->
                val selected = settings.cooldownSeconds == sec
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) palette.neteaseRed else Color.Transparent
                        )
                        .clickable {
                            MonitorStateStore.updateSettings { it.copy(cooldownSeconds = sec) }
                        }
                ) {
                    Text(
                        text = "${sec}s",
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (selected) Color.White else palette.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun EqPluginTile(
    title: String,
    subtitle: String,
    active: Boolean,
    activeAccent: Color,
    activeBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (Color) -> Unit
) {
    val palette = LocalVinylPalette.current
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val bg by animateColorAsState(
        targetValue = if (active) activeBg else palette.cardElevated,
        animationSpec = tween(220),
        label = "tileBg_$title"
    )
    val borderCol by animateColorAsState(
        targetValue = if (active) activeAccent.copy(alpha = 0.85f) else Color(0xFF2E303D),
        animationSpec = tween(220),
        label = "tileBorder_$title"
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) activeAccent else palette.textMuted,
        animationSpec = tween(220),
        label = "tileIcon_$title"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(68.dp)
            .shadow(
                elevation = if (active) 10.dp else 0.dp,
                shape = shape,
                ambientColor = activeAccent.copy(alpha = 0.4f),
                spotColor = activeAccent.copy(alpha = 0.4f)
            )
            .clip(shape)
            .background(bg)
            .border(if (active) 1.3.dp else 1.dp, borderCol, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 13.dp)
    ) {
        iconContent(iconTint)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) palette.textPrimary else palette.textSecondary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (active) activeAccent.copy(alpha = 0.9f) else palette.textMuted
            )
        }
    }
}

/**
 * Custom Glowing Coral-Red Whale-EQ Horizontal Fader (zero clunky native Slider chrome!).
 */
@Composable
private fun WhaleGlowingSlider(
    value: Float,
    minValue: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalVinylPalette.current
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        val ratio = (x / widthPx.toFloat()).coerceIn(0f, 1f)
        onValueChange(minValue + ratio * (maxValue - minValue))
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(minValue, maxValue) {
                detectTapGestures { offset ->
                    updateFromX(offset.x)
                }
            }
            .pointerInput(minValue, maxValue) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val pad = 10.dp.toPx()
            val trackW = (size.width - pad * 2f).coerceAtLeast(1f)
            val activeEnd = pad + trackW * fraction

            // Inactive recessed track
            drawRoundRect(
                color = Color(0xFF282A36),
                topLeft = Offset(pad, cy - 2.5.dp.toPx()),
                size = Size(trackW, 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Active glowing coral-red track
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(palette.neteaseRed, Color(0xFFFF857A))
                ),
                topLeft = Offset(pad, cy - 2.5.dp.toPx()),
                size = Size((activeEnd - pad).coerceAtLeast(2f), 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Soft red halo around thumb
            drawCircle(
                color = palette.neteaseRedBright.copy(alpha = 0.28f),
                radius = 12.dp.toPx(),
                center = Offset(activeEnd, cy)
            )

            // Luminous coral-cream thumb
            drawCircle(
                color = Color(0xFFFFC8BD),
                radius = 7.5.dp.toPx(),
                center = Offset(activeEnd, cy)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx(),
                center = Offset(activeEnd, cy)
            )
        }
    }
}

/**
 * Bottom Grouped Dark Settings Rows for OriginOS Permissions & Push Sound Test.
 */
@Composable
private fun OriginOsPermissionGroupCard(
    context: Context,
    onSendTestPush: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryOk by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    var notifyOk by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    VinylSectionCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        PermissionGroupRow(
            iconBg = palette.neteaseRedContainer,
            title = "OriginOS 通知与横幅权限",
            badgeText = if (notifyOk) "已开启" else "去开启",
            badgeBg = if (notifyOk) palette.shieldContainer else palette.neteaseRedContainer,
            badgeFg = if (notifyOk) palette.shieldEmerald else palette.neteaseRedBright,
            onClick = {
                openNotificationSettings(context)
                notifyOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        ) {
            BellOutlineIcon(tint = palette.neteaseRedBright, size = 17.dp)
        }

        HorizontalDivider(
            color = palette.cardBorder.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 11.dp)
        )

        PermissionGroupRow(
            iconBg = palette.vinylGoldContainer,
            title = "后台保活与电池无限制",
            badgeText = if (batteryOk) "已开启" else "一键申请",
            badgeBg = palette.vinylGoldContainer,
            badgeFg = palette.vinylGold,
            onClick = {
                requestIgnoreBattery(context)
                batteryOk = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        ) {
            ShieldCanvasIcon(tint = palette.vinylGold, size = 17.dp)
        }

        HorizontalDivider(
            color = palette.cardBorder.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 11.dp)
        )

        PermissionGroupRow(
            iconBg = Color(0xFF242634),
            title = "发送一条测试强提醒推送",
            badgeText = "立即试听 ›",
            badgeBg = Color(0xFF262836),
            badgeFg = palette.textSecondary,
            onClick = onSendTestPush
        ) {
            ChartBarsTabIcon(tint = palette.neteaseRedBright, size = 17.dp)
        }
    }
}

@Composable
private fun PermissionGroupRow(
    iconBg: Color,
    title: String,
    badgeText: String,
    badgeBg: Color,
    badgeFg: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val palette = LocalVinylPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconBg)
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
        VinylPillBadge(
            backgroundColor = badgeBg,
            borderColor = badgeFg.copy(alpha = 0.35f)
        ) {
            Text(
                text = badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeFg
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
