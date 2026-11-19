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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

private data class CasualChatTopic(
    val stageBadge: String,
    val subtitleHint: String,
    val questions: List<String>
)

/**
 * Zero-script conversational prompts: NO sentences to read aloud!
 * Elicits 100% spontaneous everyday voice and natural "没有 / 不是" rebuttals.
 */
private val CASUAL_CHAT_TOPICS = listOf(
    CasualChatTopic(
        stageBadge = "闲聊 1 / 3 · 日常松弛语气",
        subtitleHint = "像回朋友微信语音一样张嘴随口答，不限字数，说完松手或停顿即止",
        questions = listOf(
            "你今天上一顿饭吃的啥？味道咋样？",
            "这周末你更想在家睡懒觉，还是出去溜达？",
            "随口吐槽两句：今天外面的天气舒服吗？",
            "你平时犯困的时候，一般爱喝咖啡、茶还是冰水？"
        )
    ),
    CasualChatTopic(
        stageBadge = "闲聊 2 / 3 · 诱发自然反驳腔",
        subtitleHint = "不用想词，用你平时带「没有」或「不是」的口吻随口否认我一句！",
        questions = listOf(
            "老实交代，你是不是每天凌晨四点半起床去跑十公里？",
            "听说你刚才准备把手机丢进火锅里涮一下，真的假的？",
            "有人说你一开口聊天能连说十个「没有」，你承不承认？",
            "你昨晚是不是偷偷打游戏通宵到天亮才睡？"
        )
    ),
    CasualChatTopic(
        stageBadge = "闲聊 3 / 3 · 放远距离随口聊",
        subtitleHint = "把手机拿远一臂（或直接放桌上），随口聊一句就能兼容桌面与口袋距离",
        questions = listOf(
            "手机放远一点，随口说说你现在是坐着、站着还是靠着？",
            "离手机远一点，随口聊聊你周围现在吵不吵？",
            "把手机放桌上，随口说说今天手头事情多不多？"
        )
    )
)

private val QUICK_SUGGESTIONS = listOf("那个", "然后", "其实", "就是说", "怎么说呢", "讲真")

@Composable
fun VoiceprintAndWordsTab(
    uiState: MonitorUiState
) {
    val context = LocalContext.current
    var showChatStudio by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showChatStudio = true
        } else {
            Toast.makeText(context, "采集声纹需要麦克风权限", Toast.LENGTH_LONG).show()
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            VoiceprintStudioCard(
                uiState = uiState,
                onOpenStudio = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        showChatStudio = true
                    } else {
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }

        item {
            CatchphrasePillsCard(uiState = uiState)
        }
    }

    if (showChatStudio) {
        ConversationalVoiceprintDialog(
            onDismiss = { showChatStudio = false },
            onFinished = { allEmbeddings ->
                MonitorStateStore.saveEnrolledVoiceprint(
                    allExtractedEmbeddings = allEmbeddings,
                    stepCount = CASUAL_CHAT_TOPICS.size
                )
                showChatStudio = false
                Toast.makeText(context, "自然声纹已建立！现在只认你本人的声音", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
private fun VoiceprintStudioCard(
    uiState: MonitorUiState,
    onOpenStudio: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = uiState.voiceprintProfile
    val settings = uiState.settings

    var isTesting by remember { mutableStateOf(false) }
    var testLiveRms by remember { mutableFloatStateOf(0f) }
    var testScore by remember { mutableStateOf<Float?>(null) }
    var testHint by remember { mutableStateOf<String?>(null) }
    var testStopDeferred by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ShieldCanvasIcon(
                    tint = if (profile.isEnrolled) palette.shieldEmerald else palette.accentBlue,
                    size = 24.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "声纹工坊 · Voiceprint Studio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (profile.isEnrolled) {
                            "已激活自然声纹 (${profile.enrolledVectors.size} 维子特征) · ${profile.updatedAtText}"
                        } else {
                            "零念稿闲聊采集 · 只让你的声音触发提醒"
                        },
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                }
            }

            if (profile.isEnrolled) {
                Switch(
                    checked = settings.voiceprintEnabled,
                    onCheckedChange = { enabled ->
                        MonitorStateStore.updateSettings { it.copy(voiceprintEnabled = enabled) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onOpenStudio,
                enabled = !isTesting,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentBlue
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = if (profile.isEnrolled) "闲聊重录 / 校准声纹" else "像聊微信一样建立我的声纹",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (profile.isEnrolled) {
                OutlinedButton(
                    onClick = {
                        MonitorStateStore.clearVoiceprint()
                        testScore = null
                        Toast.makeText(context, "已清空声纹档案", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isTesting,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("清空", fontSize = 13.sp, color = palette.alertCoral)
                }
            }
        }

        if (profile.isEnrolled) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Free-Length Test Sandbox
            Text(
                text = "随口试音验真身（不限秒数，说完自动出结果）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            IridescentSpeakCapsule(
                isRecording = isTesting,
                liveRms = testLiveRms,
                idleLabel = "按住随口说一句（或轻点免提试音）",
                recordingLabel = "正在听… 松手或停顿即出结果",
                onStartCapture = {
                    if (!isTesting) {
                        val deferred = CompletableDeferred<Unit>()
                        testStopDeferred = deferred
                        isTesting = true
                        testHint = null
                        scope.launch {
                            val res = VoiceprintManager.captureInteractiveUtterance(
                                context = context,
                                stopSignal = deferred,
                                autoStopOnSilence = true,
                                multiScale = false,
                                onLiveFeedback = { _, rms, _ ->
                                    testLiveRms = rms
                                }
                            )
                            isTesting = false
                            testLiveRms = 0f
                            when (res) {
                                is VoiceCaptureResult.Success -> {
                                    testScore = VoiceprintManager.computeBestSimilarity(
                                        liveEmbedding = res.primaryEmbedding,
                                        enrolledVectors = profile.enrolledVectors
                                    )
                                }
                                is VoiceCaptureResult.TooQuiet -> {
                                    testHint = "声音有点太短啦，像平时说话一样随口说半句试试～"
                                }
                                is VoiceCaptureResult.Error -> {
                                    testHint = res.message
                                }
                            }
                        }
                    }
                },
                onFinishCapture = {
                    testStopDeferred?.complete(Unit)
                }
            )

            if (testHint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testHint ?: "",
                    fontSize = 12.sp,
                    color = palette.warningAmber
                )
            }

            val currentScore = testScore
            if (currentScore != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val passed = currentScore >= settings.voiceprintThreshold
                val animatedRatio by animateFloatAsState(
                    targetValue = currentScore.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 360),
                    label = "sandboxScore"
                )
                val barColor = if (passed) palette.shieldEmerald else palette.alertCoral

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "声纹匹配度 ${(currentScore * 100).roundToInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                    OriginPillBadge(
                        backgroundColor = if (passed) palette.shieldContainer else palette.alertContainer
                    ) {
                        Text(
                            text = if (passed) "是你本人 · 会触发提醒" else "他人声音 · 已静默拦截",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.cardSubtleSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "本人判定门槛：${(settings.voiceprintThreshold * 100).roundToInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                )
                Text(
                    text = when {
                        settings.voiceprintThreshold < 0.29f -> "宽松（高灵敏防漏报）"
                        settings.voiceprintThreshold > 0.45f -> "严格（强防旁人误报）"
                        else -> "黄金推荐 (32%~40%)"
                    },
                    fontSize = 12.sp,
                    color = palette.accentBlue
                )
            }
            Slider(
                value = settings.voiceprintThreshold,
                onValueChange = { v ->
                    MonitorStateStore.updateSettings { it.copy(voiceprintThreshold = v) }
                },
                valueRange = 0.20f..0.68f,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accentBlue,
                    activeTrackColor = palette.accentBlue
                )
            )
        }
    }
}

/**
 * Full Aura Liquid Glass Conversational Q&A Voiceprint Studio Dialog
 * matching the right-hand screen of Style A ("Voiceprint Studio" chat bubble + glowing speak pill).
 */
@Composable
private fun ConversationalVoiceprintDialog(
    onDismiss: () -> Unit,
    onFinished: (List<FloatArray>) -> Unit
) {
    val palette = LocalOriginPalette.current
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
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            OriginGlassCard(
                cornerRadius = 30.dp,
                containerColor = if (palette.isDark) Color(0xF0152238) else Color(0xEEFFFFFF),
                contentPadding = PaddingValues(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header + Step pill indicator (`·  ━━  ·` matching Style A mockup)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Voiceprint Studio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in CASUAL_CHAT_TOPICS.indices) {
                            val active = i == stepIdx
                            val done = i < stepIdx
                            Box(
                                modifier = Modifier
                                    .width(if (active) 24.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            active -> palette.accentBlue
                                            done -> palette.shieldEmerald
                                            else -> palette.textMuted.copy(alpha = 0.35f)
                                        }
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = topic.stageBadge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentBlue
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Frosted Glass Speech-Bubble Question Card (NO reading script!)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 22.dp,
                                    topEnd = 22.dp,
                                    bottomEnd = 22.dp,
                                    bottomStart = 6.dp
                                )
                            )
                            .background(
                                brush = Brush.linearGradient(
                                    colors = if (palette.isDark) {
                                        listOf(Color(0xFF1E3252), Color(0xFF1A2844))
                                    } else {
                                        listOf(Color(0xFFE8F4FF), Color(0xFFF3EEFF))
                                    }
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = if (palette.isDark) 0.20f else 0.85f),
                                shape = RoundedCornerShape(
                                    topStart = 22.dp,
                                    topEnd = 22.dp,
                                    bottomEnd = 22.dp,
                                    bottomStart = 6.dp
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "随便聊一句（不用照字念）",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentBlue
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
                                        tint = palette.accentBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "换个问题",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accentBlue
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentQuestion,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                lineHeight = 25.sp
                            )
                        }
                    }
                    // Subtle speech-bubble tail triangle
                    Canvas(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(width = 14.dp, height = 8.dp)
                    ) {
                        val tail = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width * 0.2f, size.height)
                            close()
                        }
                        drawPath(
                            path = tail,
                            color = if (palette.isDark) Color(0xFF1E3252) else Color(0xFFE8F4FF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = topic.subtitleHint,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    lineHeight = 17.sp
                )

                if (gentleTip != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = gentleTip ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.warningAmber
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Glowing Iridescent Liquid-Glass Speak Capsule (matching Style A right screen)
                IridescentSpeakCapsule(
                    isRecording = isRecording,
                    liveRms = liveRms,
                    idleLabel = "按住随口答 · 或轻点说完自动停",
                    recordingLabel = if (voicedSec >= 0.8f) {
                        "已捕捉自然声纹 · 松手或停顿即完成"
                    } else {
                        "正在聆听你说话…"
                    },
                    onStartCapture = {
                        if (!isRecording) {
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
                                        gentleTip = "刚才声音有点短，像跟熟人唠嗑一样随便多说几个字就好啦～"
                                    }
                                    is VoiceCaptureResult.Error -> {
                                        gentleTip = res.message
                                    }
                                }
                            }
                        }
                    },
                    onFinishCapture = {
                        stopDeferred?.complete(Unit)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isRecording
                    ) {
                        Text("稍后再录", fontSize = 12.sp, color = palette.textMuted)
                    }

                    if (collectedEmbeddings.isNotEmpty() && !isRecording) {
                        TextButton(
                            onClick = { onFinished(collectedEmbeddings.toList()) }
                        ) {
                            Text(
                                text = "已录 ${stepIdx} 题，直接保存启用 ›",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dual-gesture glowing iridescent glass speak pill matching the Style A mockup:
 * - Press & hold (>= 0.38s): finishes immediately when finger lifts (WeChat voice style).
 * - Quick tap (< 0.38s): hands-free mode, finishes automatically after 0.72s silence pause or on second tap!
 */
@Composable
private fun IridescentSpeakCapsule(
    isRecording: Boolean,
    liveRms: Float,
    idleLabel: String,
    recordingLabel: String,
    onStartCapture: () -> Unit,
    onFinishCapture: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val pillShape = RoundedCornerShape(50)

    val capsuleBrush = if (isRecording) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8),
                Color(0xFF818CF8),
                Color(0xFFF472B6)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = if (palette.isDark) {
                listOf(
                    Color(0xFF1E3A5F),
                    Color(0xFF2B2D5C),
                    Color(0xFF1E3A5F)
                )
            } else {
                listOf(
                    Color(0xFFDFF2FF),
                    Color(0xFFEDE9FE),
                    Color(0xFFE0F7FA)
                )
            }
        )
    }

    val contentTint = if (isRecording) {
        Color.White
    } else {
        palette.accentBlue
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                elevation = if (isRecording) 18.dp else 8.dp,
                shape = pillShape,
                ambientColor = palette.accentCyan.copy(alpha = 0.35f),
                spotColor = palette.accentLavender.copy(alpha = 0.45f)
            )
            .clip(pillShape)
            .background(brush = capsuleBrush)
            .border(
                BorderStroke(
                    width = 1.4.dp,
                    color = Color.White.copy(alpha = if (isRecording) 0.85f else 0.90f)
                ),
                pillShape
            )
            .pointerInput(isRecording) {
                detectTapGestures(
                    onPress = {
                        if (!isRecording) {
                            val downAt = SystemClock.elapsedRealtime()
                            onStartCapture()
                            val released = tryAwaitRelease()
                            val heldMs = SystemClock.elapsedRealtime() - downAt
                            if (released && heldMs >= 380L) {
                                onFinishCapture()
                            }
                        } else {
                            onFinishCapture()
                        }
                    }
                )
            }
            .padding(horizontal = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WaveBarsIcon(
                tint = contentTint.copy(alpha = if (isRecording) 0.95f else 0.55f),
                level = if (isRecording) (liveRms * 8f).coerceIn(0.25f, 1f) else 0.25f,
                size = 20.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            MicCanvasIcon(tint = contentTint, size = 22.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRecording) recordingLabel else idleLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = contentTint
            )
            Spacer(modifier = Modifier.width(10.dp))
            WaveBarsIcon(
                tint = contentTint.copy(alpha = if (isRecording) 0.95f else 0.55f),
                level = if (isRecording) (liveRms * 8f).coerceIn(0.25f, 1f) else 0.25f,
                size = 20.dp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatchphrasePillsCard(uiState: MonitorUiState) {
    val palette = LocalOriginPalette.current
    val context = LocalContext.current
    var inputWord by remember { mutableStateOf("") }

    val existingSet = remember(uiState.keywords) {
        uiState.keywords.map { it.word }.toSet()
    }
    val remainingSuggestions = remember(existingSet) {
        QUICK_SUGGESTIONS.filterNot { it in existingSet }
    }

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "盯防口头禅胶囊",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            text = "随时增删想戒掉的口头禅，监听中实时热更新",
            fontSize = 12.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = inputWord,
                onValueChange = { inputWord = it },
                placeholder = { Text("输入新口头禅，如：那个", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (MonitorStateStore.addKeyword(inputWord)) {
                        inputWord = ""
                    } else {
                        Toast.makeText(context, "请输入不重复的口头禅", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("添加", fontWeight = FontWeight.Bold)
            }
        }

        if (remainingSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                remainingSuggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.cardSubtleSurface)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = if (palette.isDark) 0.14f else 0.75f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { MonitorStateStore.addKeyword(suggestion) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "+ $suggestion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentBlue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        uiState.keywords.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    color = palette.cardBorder.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "「${item.word}」",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.enabled) palette.textPrimary else palette.textMuted
                    )
                    Text(
                        text = "今日 ${item.todayCount} 次 · 累计 ${item.totalCount} 次",
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { checked ->
                        MonitorStateStore.toggleKeyword(item.word, checked)
                    }
                )
                IconButton(onClick = { MonitorStateStore.removeKeyword(item.word) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = palette.textMuted
                    )
                }
            }
        }
    }
}
