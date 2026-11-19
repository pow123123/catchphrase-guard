package com.selfuse.catchphraseguard.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.selfuse.catchphraseguard.HitRecord
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.PulseEventType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RadarConsoleTab(
    uiState: MonitorUiState,
    onToggleMonitoring: () -> Unit,
    onJumpToVoiceprint: () -> Unit,
    onSendTestPush: () -> Unit
) {
    val palette = LocalVinylPalette.current
    var showHistoryDrawer by remember { mutableStateOf(false) }

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

    // Maintain 2-line lyric scroll history as utterances evolve
    var previousUtterance by remember { mutableStateOf("") }
    var lastSeenCurrent by remember { mutableStateOf("") }
    LaunchedEffect(uiState.livePreviewText) {
        val next = uiState.livePreviewText.trim()
        if (next.isNotEmpty() && lastSeenCurrent.isNotEmpty()) {
            // New sentence started after endpoint reset
            if (next.length < lastSeenCurrent.length && !lastSeenCurrent.startsWith(next)) {
                previousUtterance = lastSeenCurrent
            }
        }
        if (next.isNotEmpty()) {
            lastSeenCurrent = next
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        // 1) Top NetEase Player Header + VIP Voiceprint Shield Gold Badge
        TurntableTopBar(
            uiState = uiState,
            onJumpToVoiceprint = onJumpToVoiceprint
        )

        // 2) Center Spinning Black Vinyl + Whale-Cloud Rings + Pivoting Metallic Tonearm
        VinylTurntableStage(
            isRunning = uiState.isRunning,
            micLevel = uiState.micLevel,
            flashType = activeFlashType,
            onToggle = onToggleMonitoring
        )

        // 3) 3-Line NetEase Lyric-Scroll Live Transcript with Coral-Red Keyword Highlight
        LyricScrollStage(
            uiState = uiState,
            previousUtterance = previousUtterance,
            flashType = activeFlashType
        )

        // 4) Bottom Playback Control Row (Playlist Drawer | Glowing Red Play/Pause | Bell Test)
        TurntableControlsRow(
            isRunning = uiState.isRunning,
            hitBadgeCount = uiState.recentHits.size,
            onOpenDrawer = { showHistoryDrawer = true },
            onTogglePlay = onToggleMonitoring,
            onTestBell = onSendTestPush
        )
    }

    if (showHistoryDrawer) {
        CatchHistoryDrawerDialog(
            recentHits = uiState.recentHits,
            onDismiss = { showHistoryDrawer = false }
        )
    }
}

@Composable
private fun TurntableTopBar(
    uiState: MonitorUiState,
    onJumpToVoiceprint: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val enrolled = uiState.voiceprintProfile.isEnrolled
    val vpActive = enrolled && uiState.settings.voiceprintEnabled

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Left balance spacer matching badge width for clean centering
        Box(modifier = Modifier.width(88.dp)) {
            val todayTotal = uiState.keywords.sumOf { it.todayCount }
            if (todayTotal > 0) {
                VinylPillBadge(
                    backgroundColor = palette.neteaseRedContainer,
                    borderColor = palette.neteaseRed.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "今日 $todayTotal 次",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.neteaseRedBright
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "黑胶唱针监听台",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (uiState.isRunning) "实时离线声呐运转中" else "轻触唱盘开启静默守护",
                fontSize = 11.sp,
                color = palette.textMuted
            )
        }

        // Right Black-Vinyl VIP Gold Shield Capsule
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.width(96.dp)
        ) {
            if (vpActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFF3D599), Color(0xFFCFA25B))
                            )
                        )
                        .clickable(onClick = onJumpToVoiceprint)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VIP 声纹护盾",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF231608)
                    )
                }
            } else {
                VinylPillBadge(
                    backgroundColor = palette.vinylGoldContainer,
                    borderColor = palette.vinylGold.copy(alpha = 0.55f),
                    modifier = Modifier.clickable(onClick = onJumpToVoiceprint)
                ) {
                    Text(
                        text = if (enrolled) "护盾已暂停" else "录声纹 ›",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.vinylGold
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricScrollStage(
    uiState: MonitorUiState,
    previousUtterance: String,
    flashType: PulseEventType
) {
    val palette = LocalVinylPalette.current
    val activeWords = remember(uiState.keywords) {
        uiState.keywords.filter { it.enabled }.map { it.word }
    }

    val topHistoryText = when {
        !uiState.isRunning -> "纯手机离线语音与 CAMP++ 声纹引擎"
        previousUtterance.isNotBlank() -> previousUtterance
        uiState.recentHits.isNotEmpty() -> "上条记录：${uiState.recentHits.first().snippet}"
        else -> "正在捕捉周围对话声波…"
    }

    val mainLyricAnnotated: AnnotatedString = remember(uiState.isRunning, uiState.livePreviewText, activeWords) {
        when {
            !uiState.isRunning -> AnnotatedString("点击下方红键，唱针落盘即刻守护")
            uiState.livePreviewText.isBlank() -> AnnotatedString("试着随口说一句「${activeWords.firstOrNull() ?: "没有"}」…")
            else -> highlightKeywordsInLyric(
                raw = uiState.livePreviewText,
                keywords = activeWords,
                highlightColor = Color(0xFFFF5C5C)
            )
        }
    }

    val subStatusText = when {
        !uiState.isRunning -> {
            if (activeWords.isEmpty()) "暂未开启盯防热词 · 前往「声纹榜」勾选"
            else "当前盯防热词：${activeWords.joinToString(" · ")}"
        }
        flashType == PulseEventType.SELF_HIT -> {
            val simPct = ((uiState.lastSimilarityScore ?: 1f) * 100).roundToInt()
            if (uiState.lastSimilarityScore != null) {
                "本人声纹命中 ($simPct%) · 已发送强提醒推送"
            } else {
                "命中盯防热词 · 已发送强提醒推送"
            }
        }
        flashType == PulseEventType.OTHER_FILTERED -> {
            val simPct = ((uiState.lastSimilarityScore ?: 0f) * 100).roundToInt()
            "已静默过滤旁人声音（相似度 $simPct% · 未打扰）"
        }
        uiState.lastSimilarityScore != null && uiState.lastSimilarityWasSelf != null -> {
            val simPct = (uiState.lastSimilarityScore * 100).roundToInt()
            val tag = if (uiState.lastSimilarityWasSelf) "本人" else "旁人已拦"
            "最近一次声纹比对：$tag $simPct% · 今日拦截旁人 ${uiState.todayFilteredOthersCount} 次"
        }
        else -> "录音绝不保存与联网 · 仅本人声音触发提醒"
    }

    val subStatusColor = when (flashType) {
        PulseEventType.SELF_HIT -> palette.neteaseRedBright
        PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
        PulseEventType.NONE -> palette.textMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Line 1: Faded previous lyric
        Text(
            text = topHistoryText,
            fontSize = 13.sp,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Line 2: Highlighted current active lyric
        Text(
            text = mainLyricAnnotated,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isRunning && uiState.livePreviewText.isNotBlank()) {
                palette.textPrimary
            } else {
                palette.textSecondary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        // Line 3: Voiceprint status sub-lyric
        Text(
            text = subStatusText,
            fontSize = 12.sp,
            fontWeight = if (flashType != PulseEventType.NONE) FontWeight.SemiBold else FontWeight.Normal,
            color = subStatusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun highlightKeywordsInLyric(
    raw: String,
    keywords: List<String>,
    highlightColor: Color
): AnnotatedString {
    if (keywords.isEmpty() || raw.isEmpty()) return AnnotatedString(raw)

    val hitMask = BooleanArray(raw.length)
    for (kw in keywords) {
        if (kw.isEmpty()) continue
        var start = raw.indexOf(kw)
        while (start >= 0) {
            for (i in start until (start + kw.length).coerceAtMost(raw.length)) {
                hitMask[i] = true
            }
            start = raw.indexOf(kw, start + kw.length)
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            val isHit = hitMask[i]
            var j = i
            while (j < raw.length && hitMask[j] == isHit) {
                j++
            }
            val chunk = raw.substring(i, j)
            if (isHit) {
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append("「$chunk」")
                }
            } else {
                append(chunk)
            }
            i = j
        }
    }
}

@Composable
private fun TurntableControlsRow(
    isRunning: Boolean,
    hitBadgeCount: Int,
    onOpenDrawer: () -> Unit,
    onTogglePlay: () -> Unit,
    onTestBell: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Left: Catch History & Voiceprint Learning Drawer Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenDrawer)
        ) {
            PlaylistDrawerIcon(tint = palette.textSecondary, size = 24.dp)
            if (hitBadgeCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 4.dp)
                        .clip(CircleShape)
                        .background(palette.neteaseRed)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (hitBadgeCount > 99) "99+" else "$hitBadgeCount",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Center: Iconic Glowing NetEase Coral-Red Play / Pause Circle Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = palette.neteaseRed,
                    spotColor = palette.neteaseRed
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF5E58), palette.neteaseRed)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTogglePlay
                )
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val c = this.center
                if (isRunning) {
                    val barH = size.height * 0.62f
                    val gap = size.width * 0.20f
                    drawLine(
                        color = Color.White,
                        start = Offset(c.x - gap, c.y - barH / 2f),
                        end = Offset(c.x - gap, c.y + barH / 2f),
                        strokeWidth = 4.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(c.x + gap, c.y - barH / 2f),
                        end = Offset(c.x + gap, c.y + barH / 2f),
                        strokeWidth = 4.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val playPath = Path().apply {
                        moveTo(c.x - 6.dp.toPx(), c.y - 9.dp.toPx())
                        lineTo(c.x + 9.dp.toPx(), c.y)
                        lineTo(c.x - 6.dp.toPx(), c.y + 9.dp.toPx())
                        close()
                    }
                    drawPath(path = playPath, color = Color.White)
                }
            }
        }

        // Right: Test Push Notification Bell Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onTestBell)
        ) {
            BellOutlineIcon(tint = palette.textSecondary, size = 23.dp)
        }
    }
}

/**
 * NetEase "Current Playlist / Hot Comments" style dark modal drawer for Catch History & 1-Tap Voiceprint Learning.
 */
@Composable
private fun CatchHistoryDrawerDialog(
    recentHits: List<HitRecord>,
    onDismiss: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current
    var selectedFilter by remember { mutableIntStateOf(0) }

    val filteredList = remember(recentHits, selectedFilter) {
        when (selectedFilter) {
            1 -> recentHits.filter { it.isSelfVoice }
            2 -> recentHits.filter { !it.isSelfVoice }
            else -> recentHits
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFA181920))
                    .border(
                        width = 1.dp,
                        color = palette.cardBorder,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "实时抓包与声纹拦截榜",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${filteredList.size})",
                            fontSize = 13.sp,
                            color = palette.textMuted
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部动态", "本人触发", "护盾已拦").forEachIndexed { idx, label ->
                        val active = selectedFilter == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) palette.neteaseRed else palette.cardElevated)
                                .clickable { selectedFilter = idx }
                                .padding(horizontal = 13.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else palette.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "暂无对应声呐动态记录",
                            fontSize = 13.sp,
                            color = palette.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        itemsIndexed(filteredList) { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = palette.cardBorder.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                            DrawerHitRow(
                                item = item,
                                onLearnVoiceprint = {
                                    if (MonitorStateStore.learnSelfFromFilteredHit(item.id)) {
                                        Toast.makeText(
                                            context,
                                            "已将该条原声学入黑胶声纹库！",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHitRow(
    item: HitRecord,
    onLearnVoiceprint: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val isSelf = item.isSelfVoice
    val accent = if (isSelf) palette.neteaseRedBright else palette.shieldEmerald
    val badgeBg = if (isSelf) palette.neteaseRedContainer else palette.shieldContainer

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSelf) "本人「${item.word}」" else "拦截旁人「${item.word}」",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelf) palette.textPrimary else palette.textSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                VinylPillBadge(backgroundColor = badgeBg) {
                    val label = if (isSelf) {
                        buildString {
                            append("第${item.todayCountAfterHit}次")
                            if (item.calibratedManually) append(" · 已校准")
                            else if (item.similarity != null) append(" · ${(item.similarity * 100).roundToInt()}%")
                        }
                    } else {
                        "相似度 ${((item.similarity ?: 0f) * 100).roundToInt()}%"
                    }
                    Text(
                        text = label,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "「${item.snippet}」",
                fontSize = 12.sp,
                color = palette.textSecondary
            )
        }

        if (!isSelf && item.transientEmbedding != null) {
            Spacer(modifier = Modifier.height(6.dp))
            VinylPillBadge(
                backgroundColor = palette.vinylGoldContainer,
                borderColor = palette.vinylGold.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onLearnVoiceprint)
            ) {
                Text(
                    text = "其实是我说的 · 点此加入声纹库",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.vinylGold
                )
            }
        }
    }
}
