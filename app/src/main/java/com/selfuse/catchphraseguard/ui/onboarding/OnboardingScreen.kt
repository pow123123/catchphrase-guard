@file:OptIn(ExperimentalLayoutApi::class)

package com.selfuse.catchphraseguard.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.ui.components.SystemChecks
import com.selfuse.catchphraseguard.ui.components.rememberResumeCount

private const val STEP_COUNT = 4

/** Presets shown first, then common suggestions. */
val PRESET_WORDS = listOf("没有", "不是")
val SUGGESTED_WORDS = listOf("那个", "然后", "其实", "就是说")

@Composable
fun OnboardingScreen(state: MonitorUiState) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = step > 0) { step-- }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${step + 1} / $STEP_COUNT",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (step) {
                    0 -> IntroStep()
                    1 -> PermissionStep()
                    2 -> WordsStep(state)
                    else -> VoiceStep(
                        onDone = { embeddings, steps ->
                            MonitorStateStore.saveEnrolledVoiceprint(embeddings, steps)
                            MonitorStateStore.completeOnboarding()
                        },
                        onSkip = { MonitorStateStore.completeOnboarding() }
                    )
                }
            }
            if (step < STEP_COUNT - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        TextButton(onClick = { step-- }) { Text("上一步") }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    Button(
                        onClick = { step++ },
                        enabled = step != 2 || state.keywords.any { it.enabled }
                    ) {
                        Text(if (step == 0) "开始设置" else "下一步")
                    }
                }
            } else {
                TextButton(onClick = { step-- }) { Text("上一步") }
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("口头禅猎手", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "开会、聊天、打电话前打开它。你一说出口头禅，手机就轻轻震一下；结束后给你一份小结，看看是不是在变好。",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        ListItem(
            leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
            headlineContent = { Text("全部在手机本地处理") },
            supportingContent = { Text("录音不保存、不上传，识别和声纹比对都在这台手机上完成。") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        )
    }
}

@Composable
private fun PermissionStep() {
    val context = LocalContext.current
    val resumeCount = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    var micDenied by remember { mutableStateOf(false) }
    var notifDenied by remember { mutableStateOf(false) }

    val micGranted = remember(resumeCount, refresh) {
        SystemChecks.hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }
    val notifGranted = remember(resumeCount, refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            SystemChecks.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val batteryOk = remember(resumeCount, refresh) { SystemChecks.isIgnoringBatteryOptimizations(context) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micDenied = !ok
        refresh++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifDenied = !ok
        refresh++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Text("先把权限一次办完", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "只在你开始监听时才会用到麦克风。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        PermissionRow(
            title = "麦克风",
            description = "用来听你说话，必须开启",
            granted = micGranted,
            actionLabel = if (micDenied) "去设置" else "允许",
            onAction = {
                if (micDenied) SystemChecks.openAppDetails(context)
                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = "通知",
                description = "说出口头禅时提醒你，并能在通知上点「不是我」",
                granted = notifGranted,
                actionLabel = if (notifDenied) "去设置" else "允许",
                onAction = {
                    if (notifDenied) SystemChecks.openAppDetails(context)
                    else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }
        PermissionRow(
            title = "后台不限制",
            description = "锁屏或放进口袋后也能继续听",
            granted = batteryOk,
            actionLabel = "去设置",
            onAction = { SystemChecks.requestIgnoreBatteryOptimizations(context) }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            if (granted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已授权",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    )
}

@Composable
private fun WordsStep(state: MonitorUiState) {
    var custom by rememberSaveable { mutableStateOf("") }
    val existing = state.keywords
    val chipWords = (PRESET_WORDS + SUGGESTED_WORDS + existing.map { it.word }).distinct()

    fun submitCustom() {
        val w = custom.trim()
        if (w.isNotEmpty()) {
            val item = existing.firstOrNull { it.word == w }
            if (item == null) MonitorStateStore.addKeyword(w)
            else if (!item.enabled) MonitorStateStore.toggleKeyword(w, true)
            custom = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Text("你想戒掉哪些词？", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点选即可，之后随时能在设置里改。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chipWords.forEach { word ->
                val item = existing.firstOrNull { it.word == word }
                val selected = item != null && item.enabled
                FilterChip(
                    selected = selected,
                    onClick = {
                        when {
                            item == null -> MonitorStateStore.addKeyword(word)
                            item.enabled -> MonitorStateStore.removeKeyword(word)
                            else -> MonitorStateStore.toggleKeyword(word, true)
                        }
                    },
                    label = { Text(word) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.take(12) },
            label = { Text("自己输入一个词") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitCustom() }),
            trailingIcon = {
                IconButton(onClick = { submitCustom() }, enabled = custom.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "添加")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VoiceStep(
    onDone: (List<FloatArray>, Int) -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Text("录一下你的声音", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "随口回答 3 个问题，不用念稿。之后别人说这些词就不会算到你头上。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        VoiceEnrollment(
            onFinished = onDone,
            modifier = Modifier.fillMaxWidth(),
            footer = {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onSkip) { Text("先跳过") }
                Text(
                    text = "跳过后，任何人说这些词都会提醒。可以之后在 设置 → 我的声音 里录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
