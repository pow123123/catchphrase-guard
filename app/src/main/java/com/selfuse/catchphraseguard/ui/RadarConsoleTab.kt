package com.selfuse.catchphraseguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfuse.catchphraseguard.HitRecord
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.PulseEventType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RadarConsoleTab(
    uiState: MonitorUiState,
    onToggleMonitoring: () -> Unit,
    onJumpToVoiceprint: () -> Unit
) {
    val palette = LocalOriginPalette.current
    var selectedFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Self, 2=Filtered

    // Keep track of brief 3-second color flash after self-hit or filtered-other event
    var activeFlashType by remember { mutableStateOf(PulseEventType.NONE) }
    LaunchedEffect(uiState.lastEventTimestampMs, uiState.lastEventPulseType) {
        if (uiState.lastEventTimestampMs > 0L && uiState.lastEventPulseType != PulseEventType.NONE) {
            val age = System.currentTimeMillis() - uiState.lastEventTimestampMs
            if (age in 0..3000) {
                activeFlashType = uiState.lastEventPulseType
                delay(2600L)
                activeFlashType = PulseEventType.NONE
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            VoiceprintStatusBanner(
                uiState = uiState,
                onClick = onJumpToVoiceprint
            )
        }

        item {
            OriginGlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 22.dp, horizontal = 18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val todaySelfTotal = uiState.keywords.sumOf { it.todayCount }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "实时声呐监听",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = uiState.statusMessage,
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                        OriginPillBadge(
                            backgroundColor = palette.accentBlue.copy(alpha = if (palette.isDark) 0.22f else 0.12f)
                        ) {
                            Text(
                                text = "今日自说 $todaySelfTotal 次",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AcousticBreathingOrb(
                        isRunning = uiState.isRunning,
                        micLevel = uiState.micLevel,
                        flashType = activeFlashType,
                        onToggle = onToggleMonitoring
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val activeWordList = uiState.keywords.filter { it.enabled }.map { it.word }
                    Text(
                        text = if (activeWordList.isEmpty()) {
                            "当前未勾选盯防词 · 请前往「声纹与词库」开启"
                        } else {
                            "盯防词：${activeWordList.joinToString(" · ")}"
                        },
                        fontSize = 13.sp,
                        color = palette.textSecondary
                    )

                    if (uiState.isRunning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LiveStreamPreviewBox(uiState = uiState)
                    }
                }
            }
        }

        item {
            ActivityTimelineSection(
                recentHits = uiState.recentHits,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it }
            )
        }
    }
}

@Composable
private fun VoiceprintStatusBanner(
    uiState: MonitorUiState,
    onClick: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val enrolled = uiState.voiceprintProfile.isEnrolled
    val vpActive = enrolled && uiState.settings.voiceprintEnabled

    val bgColor = when {
        vpActive -> palette.shieldContainer
        !enrolled -> palette.warningContainer
        else -> palette.cardSubtleSurface
    }
    val accent = when {
        vpActive -> palette.shieldEmerald
        !enrolled -> palette.warningAmber
        else -> palette.textSecondary
    }
    val title = when {
        vpActive -> "本人声纹护盾已激活 · 仅提醒你自己的声音"
        !enrolled -> "尚未录入本人声纹 · 点此 10 秒开启专属识别"
        else -> "本人声纹护盾已暂停 · 当前任何人说都会提醒"
    }
    val subtitle = when {
        vpActive -> "今日已静默过滤他人语音 ${uiState.todayFilteredOthersCount} 次 · 阈值 ${(uiState.settings.voiceprintThreshold * 100).roundToInt()}%"
        !enrolled -> "录入三段简短原声后，朋友或视频里的口头禅将自动静默屏蔽"
        else -> "点击前往「声纹与词库」可重新打开声纹过滤开关"
    }

    OriginGlassCard(
        containerColor = bgColor,
        borderColor = accent.copy(alpha = 0.28f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ShieldCanvasIcon(tint = accent, size = 24.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
            Text(
                text = "›",
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                color = accent
            )
        }
    }
}

@Composable
private fun AcousticBreathingOrb(
    isRunning: Boolean,
    micLevel: Float,
    flashType: PulseEventType,
    onToggle: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "orbBreathing")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathPhase"
    )

    val smoothedMic by animateFloatAsState(
        targetValue = if (isRunning) (micLevel * 7.5f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "micAmplitude"
    )

    val targetCoreColor = when {
        !isRunning -> if (palette.isDark) Color(0xFF334155) else Color(0xFF94A3B8)
        flashType == PulseEventType.SELF_HIT -> palette.alertCoral
        flashType == PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
        else -> palette.accentBlue
    }
    val targetGlowColor = when {
        !isRunning -> if (palette.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
        flashType == PulseEventType.SELF_HIT -> palette.alertCoral
        flashType == PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
        else -> palette.accentCyan
    }

    val coreColor by animateColorAsState(
        targetValue = targetCoreColor,
        animationSpec = tween(durationMillis = 320),
        label = "coreColor"
    )
    val glowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 320),
        label = "glowColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(216.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val baseR = size.minDimension * 0.29f

            if (isRunning) {
                val outerR = baseR * (1.35f + 0.16f * breathPhase + 0.30f * smoothedMic)
                val midR = baseR * (1.16f + 0.10f * breathPhase + 0.18f * smoothedMic)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.24f),
                            glowColor.copy(alpha = 0.0f)
                        ),
                        center = center,
                        radius = outerR * 1.12f
                    ),
                    radius = outerR * 1.12f,
                    center = center
                )

                drawCircle(
                    color = glowColor.copy(alpha = 0.20f),
                    radius = outerR,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                drawCircle(
                    color = coreColor.copy(alpha = 0.32f),
                    radius = midR,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                drawCircle(
                    color = glowColor.copy(alpha = 0.35f),
                    radius = baseR * 1.15f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Tactile center orb button
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(126.dp)
                .shadow(
                    elevation = if (isRunning) 14.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = coreColor,
                    spotColor = coreColor
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isRunning) {
                            listOf(coreColor, glowColor)
                        } else {
                            listOf(coreColor, coreColor.copy(alpha = 0.85f))
                        }
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MicCanvasIcon(tint = Color.White, size = 32.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isRunning) {
                        when (flashType) {
                            PulseEventType.SELF_HIT -> "抓到了！"
                            PulseEventType.OTHER_FILTERED -> "已过滤旁人"
                            PulseEventType.NONE -> "守护中"
                        }
                    } else {
                        "点击开启"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isRunning) "轻触暂停" else "后台静默监听",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun LiveStreamPreviewBox(uiState: MonitorUiState) {
    val palette = LocalOriginPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.cardSubtleSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WaveBarsIcon(
                    tint = palette.accentBlue,
                    level = (uiState.micLevel * 8f).coerceIn(0.15f, 1f),
                    size = 18.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "实时离线听写（仅内存预览，绝不保存）",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary
                )
            }

            val sim = uiState.lastSimilarityScore
            val wasSelf = uiState.lastSimilarityWasSelf
            if (sim != null && wasSelf != null) {
                val badgeBg = if (wasSelf) palette.alertContainer else palette.shieldContainer
                val badgeFg = if (wasSelf) palette.alertCoral else palette.shieldEmerald
                val prefix = if (wasSelf) "本人" else "他人拦截"
                OriginPillBadge(backgroundColor = badgeBg) {
                    Text(
                        text = "$prefix ${(sim * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (uiState.livePreviewText.isNotBlank()) {
                uiState.livePreviewText
            } else {
                "正在聆听周围语音，说一句「没有」或「不是」试试…"
            },
            fontSize = 13.sp,
            color = if (uiState.livePreviewText.isNotBlank()) palette.textPrimary else palette.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActivityTimelineSection(
    recentHits: List<HitRecord>,
    selectedFilter: Int,
    onSelectFilter: (Int) -> Unit
) {
    val palette = LocalOriginPalette.current
    val filteredList = remember(recentHits, selectedFilter) {
        when (selectedFilter) {
            1 -> recentHits.filter { it.isSelfVoice }
            2 -> recentHits.filter { !it.isSelfVoice }
            else -> recentHits
        }
    }

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "实时拦截与抓包动态",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("全部", "本人", "已过滤")
                labels.forEachIndexed { idx, label ->
                    val active = selectedFilter == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) palette.accentBlue else palette.cardSubtleSurface
                            )
                            .clickable { onSelectFilter(idx) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else palette.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp)
            ) {
                Text(
                    text = when (selectedFilter) {
                        1 -> "还没有抓到你本人说口头禅，继续保持！"
                        2 -> "暂无被声纹护盾过滤的他人语音记录"
                        else -> "开启监听后，本人触发与旁人过滤记录会实时显示在这里"
                    },
                    fontSize = 13.sp,
                    color = palette.textMuted
                )
            }
        } else {
            filteredList.take(25).forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = palette.cardBorder.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                TimelineEventRow(item = item)
            }
        }
    }
}

@Composable
private fun TimelineEventRow(item: HitRecord) {
    val palette = LocalOriginPalette.current
    val isSelf = item.isSelfVoice
    val accent = if (isSelf) palette.alertCoral else palette.shieldEmerald
    val badgeBg = if (isSelf) palette.alertContainer else palette.shieldContainer

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSelf) {
                            "本人说出「${item.word}」"
                        } else {
                            "已过滤他人声音「${item.word}」"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelf) palette.textPrimary else palette.textSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OriginPillBadge(backgroundColor = badgeBg) {
                        val badgeLabel = buildString {
                            if (isSelf) {
                                append("第${item.todayCountAfterHit}次")
                                if (item.similarity != null) {
                                    append(" · ${(item.similarity * 100).roundToInt()}%")
                                }
                            } else {
                                append("相似度 ${((item.similarity ?: 0f) * 100).roundToInt()}%")
                            }
                        }
                        Text(
                            text = badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
                Text(
                    text = item.time,
                    fontSize = 11.sp,
                    color = palette.textMuted
                )
            }
            if (item.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "识别语境：「${item.snippet}」",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
        }
    }
}
