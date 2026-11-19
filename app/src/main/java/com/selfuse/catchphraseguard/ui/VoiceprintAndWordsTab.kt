package com.selfuse.catchphraseguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.VoiceCaptureResult
import com.selfuse.catchphraseguard.VoiceprintManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class CasualChatTopic(
    val stageBadge: String,
    val subtitleHint: String,
    val questions: List<String>
)

private val CASUAL_CHAT_TOPICS = listOf(
    CasualChatTopic(
        stageBadge = "录音棚 01 / 03 · 日常松弛声线",
        subtitleHint = "像回朋友微信语音一样张嘴随口答，不限时长，说完松手或自然停顿即止",
        questions = listOf(
            "随口说说你今天上一顿饭吃的啥？味道咋样？",
            "这周末你更想在家睡懒觉，还是出去转转？",
            "随口吐槽一句：今天外面的天气舒服吗？",
            "你平时困的时候一般爱喝冰美式、奶茶还是白水？"
        )
    ),
    CasualChatTopic(
        stageBadge = "录音棚 02 / 03 · 诱发自然反驳腔",
        subtitleHint = "不用想词，用你平时带「没有」或「不是」的语气随口反驳我一句！",
        questions = listOf(
            "老实交代，你是不是每天凌晨四点半起床去跑十公里？",
            "听说你刚才准备把手机丢进火锅里涮一下，真的假的？",
            "有人说你一开口聊天能连说十个「没有」，你承不承认？",
            "你昨晚是不是偷偷打游戏通宵到天亮才睡？"
        )
    ),
    CasualChatTopic(
        stageBadge = "录音棚 03 / 03 · 桌面远场声学采样",
        subtitleHint = "把手机拿远一臂（或放桌上），随口说一句即可兼容桌面与口袋距离",
        questions = listOf(
            "手机放远一点，随口告诉我你现在是坐着还是靠着？",
            "离手机远一点，随口聊聊你周围现在吵不吵？",
            "把手机放桌上，随口说说今天手头事情忙不忙？"
        )
    )
)

private val QUICK_SUGGESTIONS = listOf("那个", "然后", "其实", "就是说", "怎么说呢", "讲真", "说白了", "坦白讲")

@Composable
fun VoiceprintAndWordsTab(
    uiState: MonitorUiState
) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current
    var showStudioModal by remember { mutableStateOf(false) }
    var showAddWordModal by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showStudioModal = true
        } else {
            Toast.makeText(context, "采集声纹需要麦克风权限", Toast.LENGTH_LONG).show()
        }
    }

    val sortedKeywords = remember(uiState.keywords) {
        uiState.keywords.sortedWith(
            compareByDescending<com.selfuse.catchphraseguard.KeywordItem> { it.todayCount }
                .thenByDescending { it.totalCount }
        )
    }

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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "声纹歌单榜",
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
            BlackVinylVipStudioCard(
                uiState = uiState,
                onOpenStudio = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        showStudioModal = true
                    } else {
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column {
                    Text(
                        text = "热词盯防榜",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "按触发热度实时排榜 · 监听中随时生效",
                        fontSize = 11.sp,
                        color = palette.textMuted
                    )
                }

                // + 新增热词 capsule button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.neteaseRedContainer)
                        .border(1.dp, palette.neteaseRed.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { showAddWordModal = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = palette.neteaseRedBright,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "新增热词",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.neteaseRedBright
                    )
                }
            }
        }

        if (sortedKeywords.isEmpty()) {
            item {
                VinylSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "歌单里还没有盯防热词，点击右上角「+ 新增热词」添加吧",
                        fontSize = 13.sp,
                        color = palette.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = sortedKeywords,
                key = { _, item -> item.word }
            ) { index, item ->
                HotWordChartRow(
                    rank = index + 1,
                    item = item,
                    onToggle = { enabled ->
                        MonitorStateStore.toggleKeyword(item.word, enabled)
                    },
                    onDelete = {
                        MonitorStateStore.removeKeyword(item.word)
                    }
                )
            }
        }
    }

    if (showStudioModal) {
        ConversationalVoiceprintDialog(
            onDismiss = { showStudioModal = false },
            onFinished = { allEmbeddings ->
                MonitorStateStore.saveEnrolledVoiceprint(
                    allExtractedEmbeddings = allEmbeddings,
                    stepCount = CASUAL_CHAT_TOPICS.size
                )
                showStudioModal = false
                Toast.makeText(context, "黑胶 VIP 专属声纹已锁定！", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showAddWordModal) {
        AddHotwordSheetDialog(
            existingWords = uiState.keywords.map { it.word }.toSet(),
            onDismiss = { showAddWordModal = false },
            onAddWord = { word ->
                if (MonitorStateStore.addKeyword(word)) {
                    Toast.makeText(context, "已加入盯防榜：「$word」", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "请输入不重复的热词", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * NetEase Black-Vinyl VIP Dark-Bronze & Gold Voiceprint Card matching Screen 2 mockup!
 */
@Composable
private fun BlackVinylVipStudioCard(
    uiState: MonitorUiState,
    onOpenStudio: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = uiState.voiceprintProfile
    val settings = uiState.settings

    var isQuickTesting by remember { mutableStateOf(false) }
    var testRms by remember { mutableFloatStateOf(0f) }
    var testScore by remember { mutableStateOf<Float?>(null) }
    var testHint by remember { mutableStateOf<String?>(null) }
    var testStopDeferred by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = cardShape, spotColor = Color(0x66000000))
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF332319),
                        Color(0xFF1C1417),
                        Color(0xFF2E1519)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.vinylGold.copy(alpha = 0.72f),
                            palette.vinylGoldDeep.copy(alpha = 0.28f),
                            palette.neteaseRed.copy(alpha = 0.45f)
                        )
                    )
                ),
                cardShape
            )
    ) {
        // Decorative subtle golden acoustic wave across card background
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            val midY = size.height * 0.50f
            val bars = 46
            val step = size.width / (bars + 1)
            for (i in 0 until bars) {
                val ratio = abs(sin(i * 0.35f)) * (if (i in 12..34) 0.25f else 0.75f)
                val bh = size.height * 0.34f * ratio
                val x = step * (i + 1)
                drawLine(
                    color = palette.vinylGold.copy(alpha = 0.09f),
                    start = Offset(x, midY - bh / 2f),
                    end = Offset(x, midY + bh / 2f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                VinylPillBadge(
                    backgroundColor = Color(0x33E6C17A),
                    borderColor = palette.vinylGold.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "BLACK VINYL · 声纹鉴定",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.vinylGold
                    )
                }

                if (profile.isEnrolled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (settings.voiceprintEnabled) "仅本人触发" else "已暂停护盾",
                            fontSize = 11.sp,
                            color = palette.vinylGold.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NetEaseCapsuleToggle(
                            checked = settings.voiceprintEnabled,
                            onCheckedChange = { v ->
                                MonitorStateStore.updateSettings { it.copy(voiceprintEnabled = v) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "黑胶 VIP 声纹工坊",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF6DEB0)
            )

            Spacer(modifier = Modifier.height(8.dp))

            VinylPillBadge(
                backgroundColor = Color(0x66121010),
                borderColor = palette.vinylGold.copy(alpha = 0.40f)
            ) {
                val statusStr = if (profile.isEnrolled) {
                    "声纹已锁定 · 门槛 ${(settings.voiceprintThreshold * 100).roundToInt()}% · ${profile.enrolledVectors.size} 维特征"
                } else {
                    "尚未录入专属声纹 · 零念稿随口聊两句即可锁定"
                }
                Text(
                    text = statusStr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.vinylGold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Coral-Red Studio Button + Quick Test Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(42.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = palette.neteaseRed,
                            spotColor = palette.neteaseRed
                        )
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF5E58), palette.neteaseRed)
                            )
                        )
                        .clickable(enabled = !isQuickTesting, onClick = onOpenStudio)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = if (profile.isEnrolled) "重录 / 校准声纹" else "进入录音棚采集声纹",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (profile.isEnrolled) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isQuickTesting) palette.neteaseRedContainer else Color(0x4D000000)
                            )
                            .border(
                                1.dp,
                                if (isQuickTesting) palette.neteaseRedBright else palette.vinylGold.copy(alpha = 0.5f),
                                RoundedCornerShape(50)
                            )
                            .pointerInput(isQuickTesting) {
                                detectTapGestures(
                                    onPress = {
                                        if (!isQuickTesting) {
                                            val downMs = SystemClock.elapsedRealtime()
                                            val deferred = CompletableDeferred<Unit>()
                                            testStopDeferred = deferred
                                            isQuickTesting = true
                                            testHint = null
                                            scope.launch {
                                                val res = VoiceprintManager.captureInteractiveUtterance(
                                                    context = context,
                                                    stopSignal = deferred,
                                                    autoStopOnSilence = true,
                                                    multiScale = false,
                                                    onLiveFeedback = { _, rms, _ -> testRms = rms }
                                                )
                                                isQuickTesting = false
                                                testRms = 0f
                                                when (res) {
                                                    is VoiceCaptureResult.Success -> {
                                                        testScore = VoiceprintManager.computeBestSimilarity(
                                                            liveEmbedding = res.primaryEmbedding,
                                                            enrolledVectors = profile.enrolledVectors
                                                        )
                                                    }
                                                    is VoiceCaptureResult.TooQuiet -> {
                                                        testHint = "声音偏短，随口多说半句试试～"
                                                    }
                                                    is VoiceCaptureResult.Error -> {
                                                        testHint = res.message
                                                    }
                                                }
                                            }
                                            val released = tryAwaitRelease()
                                            if (released && (SystemClock.elapsedRealtime() - downMs) >= 380L) {
                                                deferred.complete(Unit)
                                            }
                                        } else {
                                            testStopDeferred?.complete(Unit)
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (isQuickTesting) "聆听中…点击结束" else "随口试音",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isQuickTesting) palette.neteaseRedBright else palette.vinylGold
                        )
                    }
                }
            }

            if (isQuickTesting) {
                Spacer(modifier = Modifier.height(12.dp))
                SymmetricHarmonicWaveform(
                    level = testRms,
                    isActive = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            }

            if (testHint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = testHint ?: "", fontSize = 11.sp, color = palette.vinylGold)
            }

            val score = testScore
            if (score != null && !isQuickTesting) {
                Spacer(modifier = Modifier.height(12.dp))
                val isSelf = score >= settings.voiceprintThreshold
                val animScore by animateFloatAsState(
                    targetValue = score.coerceIn(0f, 1f),
                    animationSpec = tween(320),
                    label = "vipTestBar"
                )
                val resultColor = if (isSelf) palette.shieldEmerald else palette.neteaseRedBright

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "试音匹配度 ${(score * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = resultColor
                    )
                    Text(
                        text = if (isSelf) "鉴定：本人真声（正常提醒）" else "鉴定：他人声线（静默拦截）",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = resultColor
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x55000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animScore)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(resultColor)
                    )
                }
            }
        }
    }
}

/**
 * NetEase Hot Song Chart Row (`01`, `02`, `03` in bold coral-red + flame popularity + capsule toggle).
 */
@Composable
private fun HotWordChartRow(
    rank: Int,
    item: com.selfuse.catchphraseguard.KeywordItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalVinylPalette.current
    val isTopThree = rank <= 3
    val rankString = "%02d".format(rank)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardSurface)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(
            text = rankString,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isTopThree) palette.neteaseRed else palette.textMuted,
            modifier = Modifier.width(34.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.word,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.enabled) palette.textPrimary else palette.textMuted
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlameMiniIcon(
                    tint = if (item.todayCount > 0) palette.neteaseRedBright else palette.textMuted,
                    size = 12.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "今日触发 ${item.todayCount} 次 · 累计 ${item.totalCount} 次",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        }

        NetEaseCapsuleToggle(
            checked = item.enabled,
            onCheckedChange = onToggle
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除热词",
                tint = palette.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Custom Dark-Frosted Sheet for Adding Catchphrases (recessed capsule input + hot suggestion cloud).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHotwordSheetDialog(
    existingWords: Set<String>,
    onDismiss: () -> Unit,
    onAddWord: (String) -> Unit
) {
    val palette = LocalVinylPalette.current
    var inputText by remember { mutableStateOf("") }
    val remaining = remember(existingWords) {
        QUICK_SUGGESTIONS.filterNot { it in existingWords }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        ) {
            VinylSectionCard(
                containerColor = Color(0xFA1B1C24),
                borderColor = Color(0xFF343746),
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "添加盯防热词",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Recessed Dark Capsule Input + Red Add Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.cardRecessed)
                        .border(1.dp, Color(0xFF2F3240), RoundedCornerShape(50))
                        .padding(start = 16.dp, end = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "输入你想戒掉的口头禅…",
                                fontSize = 13.sp,
                                color = palette.textMuted
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(palette.neteaseRed),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.neteaseRed)
                            .clickable {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddWord(trimmed)
                                    inputText = ""
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "加入榜单",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (remaining.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "高频口头禅一键点选",
                        fontSize = 12.sp,
                        color = palette.textMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        remaining.forEach { word ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(palette.cardElevated)
                                    .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
                                    .clickable {
                                        onAddWord(word)
                                    }
                                    .padding(horizontal = 13.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "+ $word",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.vinylGold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dark Studio Conversational Voiceprint Recording Sheet matching Screen 4 mockup!
 */
@Composable
private fun ConversationalVoiceprintDialog(
    onDismiss: () -> Unit,
    onFinished: (List<FloatArray>) -> Unit
) {
    val palette = LocalVinylPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stepIdx by remember { mutableIntStateOf(0) }
    var questionVariant by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var liveRms by remember { mutableFloatStateOf(0f) }
    var voicedSec by remember { mutableFloatStateOf(0f) }
    var gentleTip by remember { mutableStateOf<String?>(null) }
    var stopDeferred by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    val collectedEmbeddings = remember { mutableStateListOf<FloatArray>() }
    val topic = CASUAL_CHAT_TOPICS[stepIdx]
    val currentQuestion = topic.questions[questionVariant % topic.questions.size]

    Dialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFA221A1D), Color(0xFC14151B))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF3A2D30),
                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.width(28.dp))
                    Text(
                        text = "零念稿 · 快问快答录音棚",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    IconButton(
                        onClick = { if (!isRecording) onDismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Glowing Vintage Gold Studio Condenser Mic Emblem
                StudioCondenserMicEmblem(isRecording = isRecording)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = topic.stageBadge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.vinylGold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Dark Speech-Bubble Casual Prompt Card
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomEnd = 20.dp,
                                    bottomStart = 6.dp
                                )
                            )
                            .background(Color(0xFF24252E))
                            .border(
                                1.dp,
                                Color(0xFF363846),
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomEnd = 20.dp,
                                    bottomStart = 6.dp
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "随便聊一句就行（绝不念稿）",
                                    fontSize = 11.sp,
                                    color = palette.vinylGold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(enabled = !isRecording) {
                                            questionVariant++
                                            gentleTip = null
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "换一题",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("换个话题", fontSize = 11.sp, color = palette.textSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentQuestion,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                lineHeight = 24.sp
                            )
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .size(width = 14.dp, height = 8.dp)
                    ) {
                        val tail = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width * 0.25f, size.height)
                            close()
                        }
                        drawPath(path = tail, color = Color(0xFF24252E))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = topic.subtitleHint,
                    fontSize = 11.sp,
                    color = palette.textMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Wide Red & Gold Studio Waveform
                SymmetricHarmonicWaveform(
                    level = liveRms,
                    isActive = isRecording,
                    accentColor = palette.neteaseRedBright,
                    secondaryColor = palette.vinylGold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )

                if (gentleTip != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gentleTip ?: "",
                        fontSize = 12.sp,
                        color = palette.vinylGold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Glowing NetEase Coral-Red Speak Capsule (Press-to-Talk OR Tap Auto-Stop!)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(
                            elevation = if (isRecording) 22.dp else 12.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = palette.neteaseRed,
                            spotColor = palette.neteaseRed
                        )
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (isRecording) {
                                    listOf(Color(0xFFFF786E), palette.neteaseRed)
                                } else {
                                    listOf(Color(0xFFF04A46), Color(0xFFD63232))
                                }
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                        .pointerInput(isRecording) {
                            detectTapGestures(
                                onPress = {
                                    if (!isRecording) {
                                        val downAt = SystemClock.elapsedRealtime()
                                        val deferred = CompletableDeferred<Unit>()
                                        stopDeferred = deferred
                                        isRecording = true
                                        gentleTip = null
                                        voicedSec = 0f
                                        scope.launch {
                                            val res = VoiceprintManager.captureInteractiveUtterance(
                                                context = context,
                                                stopSignal = deferred,
                                                autoStopOnSilence = true,
                                                multiScale = true,
                                                onLiveFeedback = { _, rms, vSec ->
                                                    liveRms = rms
                                                    voicedSec = vSec
                                                }
                                            )
                                            isRecording = false
                                            liveRms = 0f
                                            when (res) {
                                                is VoiceCaptureResult.Success -> {
                                                    collectedEmbeddings.addAll(res.embeddings)
                                                    if (stepIdx < CASUAL_CHAT_TOPICS.lastIndex) {
                                                        stepIdx++
                                                        questionVariant = 0
                                                    } else {
                                                        onFinished(collectedEmbeddings.toList())
                                                    }
                                                }
                                                is VoiceCaptureResult.TooQuiet -> {
                                                    gentleTip = "刚才声音稍微有点短，随便多聊半句话就好啦～"
                                                }
                                                is VoiceCaptureResult.Error -> {
                                                    gentleTip = res.message
                                                }
                                            }
                                        }
                                        val released = tryAwaitRelease()
                                        if (released && (SystemClock.elapsedRealtime() - downAt) >= 380L) {
                                            deferred.complete(Unit)
                                        }
                                    } else {
                                        stopDeferred?.complete(Unit)
                                    }
                                }
                            )
                        }
                ) {
                    Text(
                        text = when {
                            !isRecording -> "按住说话 · 或轻点免提录入声纹"
                            voicedSec >= 0.85f -> "已捕捉真实声线 · 松手或停顿即完成"
                            else -> "正在录音棚收音…"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (collectedEmbeddings.isNotEmpty() && !isRecording) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { onFinished(collectedEmbeddings.toList()) }) {
                        Text(
                            text = "已采集 $stepIdx 段，直接完成锁定 ›",
                            fontSize = 12.sp,
                            color = palette.vinylGold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun StudioCondenserMicEmblem(isRecording: Boolean) {
    val palette = LocalVinylPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .shadow(
                elevation = if (isRecording) 18.dp else 8.dp,
                shape = CircleShape,
                ambientColor = palette.vinylGold,
                spotColor = palette.neteaseRed
            )
            .clip(CircleShape)
            .background(Color(0xFF2B201D))
            .border(
                1.4.dp,
                Brush.linearGradient(
                    colors = listOf(palette.vinylGold, palette.neteaseRed.copy(alpha = 0.65f))
                ),
                CircleShape
            )
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            val gold = palette.vinylGold
            // Vintage condenser mic capsule with horizontal grille lines
            val capW = w * 0.36f
            val capH = h * 0.50f
            drawRoundRect(
                color = gold,
                topLeft = Offset((w - capW) / 2f, h * 0.08f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(capW / 2f, capW / 2f),
                style = Stroke(width = 1.8.dp.toPx())
            )
            for (g in 1..3) {
                val gy = h * (0.16f + g * 0.09f)
                drawLine(
                    color = gold.copy(alpha = 0.65f),
                    start = Offset(w * 0.36f, gy),
                    end = Offset(w * 0.64f, gy),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            drawArc(
                color = gold,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.22f, h * 0.26f),
                size = Size(w * 0.56f, h * 0.48f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawLine(
                color = gold,
                start = Offset(w * 0.5f, h * 0.74f),
                end = Offset(w * 0.5f, h * 0.90f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = gold,
                start = Offset(w * 0.32f, h * 0.90f),
                end = Offset(w * 0.68f, h * 0.90f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
