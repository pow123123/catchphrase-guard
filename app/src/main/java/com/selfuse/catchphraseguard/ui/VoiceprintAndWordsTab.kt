package com.selfuse.catchphraseguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.VoiceCaptureResult
import com.selfuse.catchphraseguard.VoiceprintManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class EnrollStepSpec(
    val stepTitle: String,
    val distanceHint: String,
    val scriptToRead: String
)

private val ENROLL_STEPS = listOf(
    EnrollStepSpec(
        stepTitle = "第 1/3 步 · 放松聊天语气",
        distanceHint = "不要用朗读腔，就像平时跟朋友随口闲聊一样自然说出：",
        scriptToRead = "“没有啊，我觉得这个事情其实还好吧。”"
    ),
    EnrollStepSpec(
        stepTitle = "第 2/3 步 · 随口快说口头禅",
        distanceHint = "模仿你平时脱口而出、稍微含糊连读的习惯快速说：",
        scriptToRead = "“不是，没有没有，真不是你想的那样。”"
    ),
    EnrollStepSpec(
        stepTitle = "第 3/3 步 · 放桌面/拿远距离",
        distanceHint = "把手机拿远一臂（或直接放桌上），用日常音量随口说：",
        scriptToRead = "“不是这个问题，我刚才真没有看到。”"
    )
)

private val QUICK_SUGGESTIONS = listOf("那个", "然后", "其实", "就是说", "怎么说呢", "讲真")

@Composable
fun VoiceprintAndWordsTab(
    uiState: MonitorUiState
) {
    val context = LocalContext.current
    var showEnrollDialog by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showEnrollDialog = true
        } else {
            Toast.makeText(context, "录入声纹需要麦克风权限", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            VoiceprintCard(
                uiState = uiState,
                onStartEnroll = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        showEnrollDialog = true
                    } else {
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }

        item {
            CatchphraseLibraryCard(uiState = uiState)
        }
    }

    if (showEnrollDialog) {
        VoiceprintEnrollWizardDialog(
            onDismiss = { showEnrollDialog = false },
            onFinished = { collectedEmbeddings ->
                MonitorStateStore.saveEnrolledVoiceprint(
                    allExtractedEmbeddings = collectedEmbeddings,
                    stepCount = ENROLL_STEPS.size
                )
                showEnrollDialog = false
                Toast.makeText(context, "多尺度声纹已校准完成！精准度大幅提升", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
private fun VoiceprintCard(
    uiState: MonitorUiState,
    onStartEnroll: () -> Unit
) {
    val palette = LocalOriginPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = uiState.voiceprintProfile
    val settings = uiState.settings

    var isTestingVoice by remember { mutableStateOf(false) }
    var testProgress by remember { mutableFloatStateOf(0f) }
    var testRms by remember { mutableFloatStateOf(0f) }
    var testScore by remember { mutableStateOf<Float?>(null) }
    var testErrorMsg by remember { mutableStateOf<String?>(null) }

    OriginGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldCanvasIcon(
                    tint = if (profile.isEnrolled) palette.shieldEmerald else palette.warningAmber,
                    size = 24.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "本人声纹档案",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (profile.isEnrolled) {
                            "已采集 ${profile.sampleCount} 段多距离声纹 · 更新于 ${profile.updatedAtText}"
                        } else {
                            "未录入声纹 · 录入后自动屏蔽他人与视频声音"
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

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onStartEnroll,
                enabled = !isTestingVoice,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (profile.isEnrolled) palette.accentBlue else palette.shieldEmerald
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Text(
                    text = if (profile.isEnrolled) "重新校准我的声纹" else "开始 10 秒录入我的声纹",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (profile.isEnrolled) {
                OutlinedButton(
                    onClick = {
                        MonitorStateStore.clearVoiceprint()
                        testScore = null
                        Toast.makeText(context, "已清空本地声纹档案", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isTestingVoice,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("清空", fontSize = 13.sp, color = palette.alertCoral)
                }
            }
        }

        if (profile.isEnrolled) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.65f))
            Spacer(modifier = Modifier.height(14.dp))

            // Live Voiceprint Verification Sandbox
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.cardSubtleSurface)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "声纹识别实时测一测",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "自己说一句 vs 让旁人/视频说一句，当场对比得分",
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!isTestingVoice) {
                                isTestingVoice = true
                                testErrorMsg = null
                                scope.launch {
                                    val res = VoiceprintManager.captureVoiceClipForEmbedding(
                                        context = context,
                                        durationMs = 2500L,
                                        onProgress = { p, rms ->
                                            testProgress = p
                                            testRms = rms
                                        }
                                    )
                                    isTestingVoice = false
                                    when (res) {
                                        is VoiceCaptureResult.Success -> {
                                            testScore = VoiceprintManager.computeBestSimilarity(
                                                liveEmbedding = res.primaryEmbedding,
                                                enrolledVectors = profile.enrolledVectors
                                            )
                                        }
                                        is VoiceCaptureResult.TooQuiet -> {
                                            testErrorMsg = "未检测到足够清晰的人声，请正常音量说一句完整的话"
                                        }
                                        is VoiceCaptureResult.Error -> {
                                            testErrorMsg = res.message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isTestingVoice,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (isTestingVoice) {
                            WaveBarsIcon(
                                tint = Color.White,
                                level = (testRms * 8f).coerceIn(0.2f, 1f),
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("聆听中…", fontSize = 12.sp)
                        } else {
                            Text("试音 2.5 秒", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isTestingVoice) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { testProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = palette.accentBlue,
                        trackColor = palette.cardBorder
                    )
                }

                if (testErrorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testErrorMsg ?: "",
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
                        label = "scoreBar"
                    )
                    val barColor = if (passed) palette.shieldEmerald else palette.alertCoral

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "实测声纹相似度：${(currentScore * 100).roundToInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                        OriginPillBadge(
                            backgroundColor = if (passed) palette.shieldContainer else palette.alertContainer
                        ) {
                            Text(
                                text = if (passed) "判定：本人（正常提醒）" else "判定：他人（静默拦截）",
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
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.cardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(barColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Threshold Slider
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "本人判定严格度阈值：${(settings.voiceprintThreshold * 100).roundToInt()}%",
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
}

@Composable
private fun VoiceprintEnrollWizardDialog(
    onDismiss: () -> Unit,
    onFinished: (List<FloatArray>) -> Unit
) {
    val palette = LocalOriginPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var liveRms by remember { mutableFloatStateOf(0f) }
    var hintError by remember { mutableStateOf<String?>(null) }
    val collected = remember { mutableStateListOf<FloatArray>() }

    val stepSpec = ENROLL_STEPS[currentStepIndex]

    Dialog(onDismissRequest = { if (!isRecording) onDismiss() }) {
        OriginGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(22.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stepSpec.stepTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentBlue
                )
                Text(
                    text = "纯本地提取",
                    fontSize = 11.sp,
                    color = palette.textMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-step indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (i in ENROLL_STEPS.indices) {
                    val active = i <= currentStepIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) palette.accentBlue else palette.cardBorder)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stepSpec.distanceHint,
                fontSize = 13.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.cardSubtleSurface)
                    .border(1.dp, palette.accentBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stepSpec.scriptToRead,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    lineHeight = 25.sp
                )
            }

            if (isRecording) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WaveBarsIcon(
                        tint = palette.accentBlue,
                        level = (liveRms * 8f).coerceIn(0.2f, 1f),
                        size = 22.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "正在采集声纹特征 (${((1f - progress) * 3.5f).coerceAtLeast(0f).let { "%.1f".format(it) }}s)…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accentBlue
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = palette.accentBlue,
                    trackColor = palette.cardBorder
                )
            }

            if (hintError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = hintError ?: "",
                    fontSize = 12.sp,
                    color = palette.alertCoral
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isRecording,
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("取消", color = palette.textSecondary)
                }

                Button(
                    onClick = {
                        isRecording = true
                        hintError = null
                        scope.launch {
                            val result = VoiceprintManager.captureVoiceClipForEmbedding(
                                context = context,
                                durationMs = 3500L,
                                multiScale = true,
                                onProgress = { p, rms ->
                                    progress = p
                                    liveRms = rms
                                }
                            )
                            isRecording = false
                            when (result) {
                                is VoiceCaptureResult.Success -> {
                                    collected.addAll(result.embeddings)
                                    if (currentStepIndex < ENROLL_STEPS.lastIndex) {
                                        currentStepIndex++
                                        progress = 0f
                                    } else {
                                        onFinished(collected.toList())
                                    }
                                }
                                is VoiceCaptureResult.TooQuiet -> {
                                    hintError = "刚才录到的声音偏轻，请看着上方句子清晰完整读完 3.5 秒"
                                }
                                is VoiceCaptureResult.Error -> {
                                    hintError = result.message
                                }
                            }
                        }
                    },
                    enabled = !isRecording,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(
                        text = if (isRecording) "正在录音…" else "点击开始朗读 (3.5秒)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatchphraseLibraryCard(uiState: MonitorUiState) {
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
            text = "盯防口头禅词库",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            text = "支持任意中文词汇，监听过程中增删实时生效",
            fontSize = 12.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = inputWord,
                onValueChange = { inputWord = it },
                placeholder = { Text("输入想改掉的词，如：那个", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("添加", fontWeight = FontWeight.Bold)
            }
        }

        if (remainingSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
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
                            .clickable { MonitorStateStore.addKeyword(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ $suggestion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
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
                    color = palette.cardBorder.copy(alpha = 0.6f),
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
                        text = "今日 ${item.todayCount} 次 · 历史累计 ${item.totalCount} 次",
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
