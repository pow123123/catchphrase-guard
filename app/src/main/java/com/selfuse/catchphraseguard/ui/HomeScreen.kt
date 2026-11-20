@file:OptIn(ExperimentalLayoutApi::class)

package com.selfuse.catchphraseguard.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.ActiveSession
import com.selfuse.catchphraseguard.MonitorController
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorTileService
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.StatsCalculator
import com.selfuse.catchphraseguard.ui.components.Fmt
import com.selfuse.catchphraseguard.ui.components.SystemChecks
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: MonitorUiState,
    onEditWords: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.activeSession
    if (session == null) {
        HomeIdle(state, onEditWords, onOpenVoice, modifier)
    } else {
        HomeRunning(state, session, onOpenVoice, modifier)
    }
}

// ---------------------------------------------------------------------------
// Idle
// ---------------------------------------------------------------------------

@Composable
private fun HomeIdle(
    state: MonitorUiState,
    onEditWords: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val todayCount = StatsCalculator.todayCount(state)
    val weekRate = StatsCalculator.averageRate(state.dailyStats, 7)
    val duration = state.settings.defaultDurationMinutes

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            MonitorController.hasMicPermission(context)
        if (micOk) {
            MonitorController.start(context, MonitorStateStore.uiState.value.settings.defaultDurationMinutes)
        } else {
            Toast.makeText(context, "需要麦克风权限才能开始监听", Toast.LENGTH_LONG).show()
        }
    }

    val onStart: () -> Unit = {
        if (MonitorController.hasMicPermission(context) && MonitorController.hasNotificationPermission(context)) {
            MonitorController.start(context, duration)
        } else {
            permissionLauncher.launch(SystemChecks.startPermissions())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "今天",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(text = todayCount.toString(), style = MediumNumberStyle)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "次",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Text(
            text = if (weekRate != null) {
                "近 7 天平均  每小时 ${Fmt.rate(weekRate)} 次"
            } else {
                "监听一次后，这里会显示你每小时说几次"
            },
            style = MaterialTheme.typography.bodyLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(56.dp))

        Button(
            onClick = onStart,
            enabled = state.keywords.any { it.enabled },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
        ) {
            Text("开始监听", style = MaterialTheme.typography.titleMedium)
        }
        if (state.keywords.none { it.enabled }) {
            Text(
                text = "还没有要盯的词，先添加一个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "监听时长",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fmt.DURATION_OPTIONS.forEach { minutes ->
                val selected = duration == minutes
                FilterChip(
                    selected = selected,
                    onClick = {
                        MonitorStateStore.updateSettings { it.copy(defaultDurationMinutes = minutes) }
                    },
                    label = { Text(Fmt.durationOption(minutes)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
        Text(
            text = if (duration > 0) "到点自动结束，并给你一份小结" else "点「结束」或在通知栏结束后给你一份小结",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))
        HorizontalDivider()

        val enabledWords = state.keywords.filter { it.enabled }.map { it.word }
        ListItem(
            headlineContent = { Text("盯防") },
            supportingContent = {
                Text(if (enabledWords.isEmpty()) "还没有" else enabledWords.joinToString(" · "))
            },
            trailingContent = { TextButton(onClick = onEditWords) { Text("编辑") } },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
        val profile = state.voiceprintProfile
        ListItem(
            headlineContent = { Text("我的声音") },
            supportingContent = {
                Text(
                    when {
                        !profile.isEnrolled -> "未录入，任何人说都会提醒"
                        !state.settings.voiceprintEnabled -> "已录入，但已关闭区分"
                        else -> "已录入，只算你自己说的"
                    }
                )
            },
            trailingContent = {
                TextButton(onClick = onOpenVoice) { Text(if (profile.isEnrolled) "管理" else "去录入") }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Running
// ---------------------------------------------------------------------------

@Composable
private fun HomeRunning(
    state: MonitorUiState,
    session: ActiveSession,
    onOpenVoice: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    // A persisted session whose service is gone (process killed). Give the service a moment
    // after start before calling it interrupted.
    var interrupted by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(session.id, state.isRunning) {
        interrupted = false
        if (!state.isRunning) {
            delay(3000L)
            interrupted = true
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id, interrupted) {
        while (!interrupted) {
            now = System.currentTimeMillis()
            delay(1000L - (now % 1000L))
        }
    }

    val paused = session.isPaused(now)
    val monitoredMs = session.monitoredMs(now)
    val total = session.totalCount
    val plannedEnd = session.plannedEndAtMs
    val profile = state.voiceprintProfile
    val voiceFilterOn = profile.isEnrolled && state.settings.voiceprintEnabled

    // Count bump: only when the number goes up, never on first show.
    val bump = remember { Animatable(1f) }
    var lastTotal by remember(session.id) { mutableIntStateOf(total) }
    LaunchedEffect(total) {
        if (total > lastTotal) {
            bump.snapTo(1f)
            bump.animateTo(1.12f, tween(durationMillis = 110))
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else if (bump.value != 1f) {
            bump.snapTo(1f)
        }
        lastTotal = total
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        // Status line
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor = when {
                interrupted -> MaterialTheme.colorScheme.error
                paused -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.primary
            }
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(dotColor) }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    interrupted -> "已中断"
                    paused -> "已暂停"
                    else -> "监听中"
                },
                style = MaterialTheme.typography.titleMedium
            )
            if (!interrupted) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Fmt.clock(monitoredMs),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val pausedUntil = session.pausedUntilMs
        val subStatus = when {
            interrupted -> "监听服务在后台被系统停止，结束后可重新开始"
            paused && pausedUntil != null -> "${Fmt.timeOfDay(pausedUntil)} 自动继续"
            plannedEnd != null -> {
                val leftMs = (plannedEnd - now).coerceAtLeast(0L)
                "还剩 ${Fmt.duration((leftMs + 59_999L) / 1000L)} · ${Fmt.timeOfDay(plannedEnd)} 结束"
            }
            else -> "直到你结束"
        }
        Text(
            text = subStatus,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = total.toString(),
            style = BigNumberStyle,
            color = if (paused || interrupted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.scale(bump.value)
        )
        Text(
            text = "本次说了 $total 次",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = Fmt.interval(monitoredMs, total) ?: if (total == 0) "还没有听到" else "再听一会儿算频率",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(28.dp))

        val words = (state.keywords.filter { it.enabled }.map { it.word } + session.perWordCounts.keys).distinct()
        if (words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                words.forEach { word ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(word, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = (session.perWordCounts[word] ?: 0).toString(),
                            style = MaterialTheme.typography.titleLarge.tabular()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (voiceFilterOn) {
            Text(
                text = "已忽略他人的声音 ${session.filteredOthers} 次",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = {
                    Text(if (profile.isEnrolled) "声音区分已关闭" else "还没录入你的声音")
                },
                supportingContent = { Text("现在别人说也会被计入") },
                trailingContent = { TextButton(onClick = onOpenVoice) { Text("去设置") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }

        Spacer(Modifier.height(40.dp))

        if (interrupted) {
            Button(
                onClick = {
                    MonitorStateStore.endSession()
                    MonitorTileService.requestUpdate(context)
                    MonitorController.stop(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("结束并查看小结")
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (paused) MonitorController.resume(context) else MonitorController.pause(context)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text(if (paused) "继续" else "暂停 ${MonitorController.DEFAULT_PAUSE_MINUTES} 分钟")
                }
                Button(
                    onClick = { MonitorController.stop(context) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text("结束")
                }
            }
            Text(
                text = "可以锁屏或把手机放进口袋，通知栏也能结束",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }
}
