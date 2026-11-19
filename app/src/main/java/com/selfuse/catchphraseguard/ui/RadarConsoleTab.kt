package com.selfuse.catchphraseguard.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfuse.catchphraseguard.HitRecord
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.PulseEventType
import com.selfuse.catchphraseguard.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RadarConsoleTab(
    uiState: MonitorUiState,
    onToggleMonitoring: () -> Unit,
    onJumpToVoiceprint: () -> Unit
) {
    val palette = LocalOriginPalette.current
    var selectedFilter by remember { mutableIntStateOf(0) }

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
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = 112.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            VoiceprintAuraPill(
                uiState = uiState,
                onClick = onJumpToVoiceprint
            )
        }

        item {
            // Open-air 3D Iridescent Liquid Glass Sphere stage (matching Style A mockup)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraLiquidGlassOrb(
                    isRunning = uiState.isRunning,
                    micLevel = uiState.micLevel,
                    flashType = activeFlashType,
                    onToggle = onToggleMonitoring
                )

                Spacer(modifier = Modifier.height(6.dp))

                val todaySelfTotal = uiState.keywords.sumOf { it.todayCount }
                val activeWordList = uiState.keywords.filter { it.enabled }.map { it.word }
                Text(
                    text = when {
                        !uiState.isRunning -> "轻触水晶球 · 开启静默守护"
                        activeFlashType == PulseEventType.SELF_HIT -> "刚刚抓到你说口头禅了！"
                        activeFlashType == PulseEventType.OTHER_FILTERED -> "已静默拦截旁人声音"
                        else -> "正在静默守护 · 轻触球体暂停"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (activeWordList.isEmpty()) {
                        "暂未开启盯防词 · 前往「声纹工坊」勾选"
                    } else {
                        "盯防「${activeWordList.joinToString(" · ")}」 · 今日自说 $todaySelfTotal 次"
                    },
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            FrostedWaveformCard(
                uiState = uiState,
                flashType = activeFlashType
            )
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
private fun VoiceprintAuraPill(
    uiState: MonitorUiState,
    onClick: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val enrolled = uiState.voiceprintProfile.isEnrolled
    val vpActive = enrolled && uiState.settings.voiceprintEnabled

    val dotColor = when {
        vpActive -> palette.shieldEmerald
        !enrolled -> palette.warningAmber
        else -> palette.textMuted
    }
    val label = when {
        vpActive -> "本人声纹护盾已开启 · 今日拦截他人 ${uiState.todayFilteredOthersCount} 次"
        !enrolled -> "尚未建立本人声纹 · 点此 15 秒闲聊激活 ›"
        else -> "声纹过滤已暂停 · 点此恢复仅本人提醒 ›"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(50),
                ambientColor = palette.glassShadowColor,
                spotColor = palette.glassShadowColor
            )
            .clip(RoundedCornerShape(50))
            .background(palette.cardSurface)
            .border(BorderStroke(1.dp, palette.glassRimBrush), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary
        )
    }
}

@Composable
private fun AuraLiquidGlassOrb(
    isRunning: Boolean,
    micLevel: Float,
    flashType: PulseEventType,
    onToggle: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val infinite = rememberInfiniteTransition(label = "liquidGlassOrb")

    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )

    val slowRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "causticRotation"
    )

    val smoothedMic by animateFloatAsState(
        targetValue = if (isRunning) (micLevel * 7.5f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "micScale"
    )

    val auraTintTarget = when {
        !isRunning -> Color.Transparent
        flashType == PulseEventType.SELF_HIT -> palette.alertCoral.copy(alpha = 0.28f)
        flashType == PulseEventType.OTHER_FILTERED -> palette.shieldEmerald.copy(alpha = 0.25f)
        else -> palette.accentCyan.copy(alpha = 0.10f)
    }
    val auraTint by animateColorAsState(
        targetValue = auraTintTarget,
        animationSpec = tween(durationMillis = 350),
        label = "orbAuraTint"
    )

    val ringColorTarget = when {
        !isRunning -> Color.White.copy(alpha = if (palette.isDark) 0.16f else 0.55f)
        flashType == PulseEventType.SELF_HIT -> palette.alertCoral
        flashType == PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
        else -> Color.White
    }
    val ringColor by animateColorAsState(
        targetValue = ringColorTarget,
        animationSpec = tween(durationMillis = 350),
        label = "orbRingColor"
    )

    val orbScale = if (isRunning) {
        1.0f + 0.035f * breathPhase + 0.09f * smoothedMic
    } else {
        0.96f + 0.015f * breathPhase
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(252.dp)
    ) {
        // Concentric delicate glass ripples matching Style A mockup
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = this.center
            val rBase = size.minDimension * 0.31f

            val r1 = rBase * (1.18f + 0.06f * breathPhase + 0.14f * smoothedMic)
            val r2 = rBase * (1.38f + 0.10f * breathPhase + 0.22f * smoothedMic)
            val r3 = rBase * (1.56f + 0.14f * breathPhase + 0.28f * smoothedMic)

            if (isRunning) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accentCyan.copy(alpha = if (palette.isDark) 0.26f else 0.22f),
                            palette.accentLavender.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = c,
                        radius = r3
                    ),
                    radius = r3,
                    center = c
                )
            }

            drawCircle(
                color = ringColor.copy(alpha = if (isRunning) 0.65f else 0.38f),
                radius = r1,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = if (isRunning) 0.38f else 0.20f),
                radius = r2,
                center = c,
                style = Stroke(width = 1.1.dp.toPx())
            )
            if (isRunning) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.18f),
                    radius = r3,
                    center = c,
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }
        }

        // Genuine 3D Iridescent Liquid-Glass Sphere asset
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(162.dp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .shadow(
                    elevation = if (isRunning) 22.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = palette.accentCyan.copy(alpha = 0.35f),
                    spotColor = palette.accentLavender.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        width = 1.6.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.30f),
                                palette.accentCyan.copy(alpha = 0.65f)
                            )
                        )
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.aura_orb_sphere),
                contentDescription = "开启或暂停语音守护",
                contentScale = ContentScale.Crop,
                colorFilter = if (auraTint != Color.Transparent) {
                    ColorFilter.tint(auraTint, BlendMode.SrcAtop)
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (isRunning) slowRotation else 0f
                        alpha = if (isRunning) 1.0f else 0.80f
                    }
            )
        }
    }
}

@Composable
private fun FrostedWaveformCard(
    uiState: MonitorUiState,
    flashType: PulseEventType
) {
    val palette = LocalOriginPalette.current
    val waveAccent = when (flashType) {
        PulseEventType.SELF_HIT -> palette.alertCoral
        PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
        PulseEventType.NONE -> palette.accentBlue
    }

    OriginGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        SymmetricHarmonicWaveform(
            level = uiState.micLevel,
            isActive = uiState.isRunning,
            accentColor = waveAccent,
            secondaryColor = palette.accentLavender,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    !uiState.isRunning -> "离线语音声呐已就绪 · 零上传云端"
                    uiState.livePreviewText.isNotBlank() -> uiState.livePreviewText
                    else -> "正在聆听周围对话，说一句「没有」或「不是」试试…"
                },
                fontSize = 13.sp,
                color = if (uiState.isRunning && uiState.livePreviewText.isNotBlank()) {
                    palette.textPrimary
                } else {
                    palette.textMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val sim = uiState.lastSimilarityScore
            val wasSelf = uiState.lastSimilarityWasSelf
            if (uiState.isRunning && sim != null && wasSelf != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val badgeBg = if (wasSelf) palette.alertContainer else palette.shieldContainer
                val badgeFg = if (wasSelf) palette.alertCoral else palette.shieldEmerald
                val prefix = if (wasSelf) "本人" else "拦截"
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
                text = "声呐记录",
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
                            .padding(horizontal = 11.dp, vertical = 4.dp)
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
                    .padding(vertical = 20.dp)
            ) {
                Text(
                    text = when (selectedFilter) {
                        1 -> "还没有抓到你本人说口头禅，继续保持！"
                        2 -> "暂无被声纹护盾过滤的他人语音"
                        else -> "开启守护后，本人触发与旁人过滤记录将显示在这里"
                    },
                    fontSize = 13.sp,
                    color = palette.textMuted
                )
            }
        } else {
            filteredList.take(25).forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = palette.cardBorder.copy(alpha = 0.45f),
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
    val context = LocalContext.current
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
                            "本人「${item.word}」"
                        } else {
                            "过滤他人「${item.word}」"
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
                                if (item.calibratedManually) {
                                    append(" · 已学习")
                                } else if (item.similarity != null) {
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
                    text = "「${item.snippet}」",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
            if (!isSelf && item.transientEmbedding != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OriginPillBadge(
                    backgroundColor = palette.accentBlue.copy(alpha = if (palette.isDark) 0.22f else 0.12f),
                    modifier = Modifier.clickable {
                        val ok = MonitorStateStore.learnSelfFromFilteredHit(item.id)
                        if (ok) {
                            Toast.makeText(
                                context,
                                "已将这段原声加入声纹库，同样语气下次必中！",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text(
                        text = "其实是我说的 · 点此学习声纹",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentBlue
                    )
                }
            }
        }
    }
}
