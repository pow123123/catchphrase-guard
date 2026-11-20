@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfuse.catchphraseguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.HitRecord
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.SessionRecord
import com.selfuse.catchphraseguard.StatsCalculator
import com.selfuse.catchphraseguard.ui.components.Fmt
import kotlinx.coroutines.launch

/**
 * Shown when a session ends. [summary] must be read from state each recomposition so that
 * "不是我" corrections are reflected immediately.
 */
@Composable
fun SessionSummarySheet(
    state: MonitorUiState,
    summary: SessionRecord,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // pendingSummary is kept up to date by markNotMe / learnSelfFromFilteredHit.
    val hits = summary.hits
    val change = StatsCalculator.changeVsPreviousSession(state.sessions, summary)
    val rate = summary.ratePerHour

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "本次监听结束 · ${Fmt.duration(summary.monitoredSec)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Text(
                        text = "${Fmt.dateTime(summary.startAtMs)} – ${Fmt.timeOfDay(summary.endAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(summary.totalCount.toString(), style = MediumNumberStyle)
                        Text("次", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (rate != null) "每小时 ${Fmt.rate(rate)} 次" else "时间太短，不算频率",
                            style = MaterialTheme.typography.titleMedium.tabular()
                        )
                        val changeText = Fmt.change(change, "上次")
                        if (changeText != null) {
                            Text(
                                text = changeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if ((change ?: 0f) <= 0f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val parts = summary.perWordCounts.entries
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .map { "${it.key} ${it.value}" }
                        .toMutableList()
                    if (summary.filteredOthers > 0) parts.add("已忽略他人 ${summary.filteredOthers} 次")
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (hits.isEmpty()) "这次没有听到盯防词" else "逐条回顾",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
                    )
                    if (hits.any { it.countsAsSelf }) {
                        Text(
                            text = "误报了就点「不是我」，会撤销这次计数，并让下次少认错",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )
                    }
                }
            }
            items(hits, key = { it.id }) { hit ->
                SummaryHitRow(hit)
            }
            item {
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .heightIn(min = 52.dp)
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun SummaryHitRow(hit: HitRecord) {
    val clock = hit.time.take(5)
    when {
        hit.isSelfVoice && !hit.undone -> ListItem(
            overlineContent = {
                Text(if (hit.calibratedManually) "$clock · ${hit.word} · 已改为我" else "$clock · ${hit.word}")
            },
            headlineContent = { SnippetText(hit.snippet) },
            trailingContent = {
                TextButton(onClick = { MonitorStateStore.markNotMe(hit.id) }) { Text("不是我") }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        )

        hit.isSelfVoice && hit.undone -> ListItem(
            overlineContent = { Text("$clock · ${hit.word}") },
            headlineContent = { SnippetText(hit.snippet, struck = true) },
            trailingContent = {
                Text(
                    "已撤销",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.alpha(0.5f)
        )

        else -> ListItem(
            overlineContent = { Text("$clock · ${hit.word} · 他人的声音，未计入") },
            headlineContent = { SnippetText(hit.snippet) },
            trailingContent = if (MonitorStateStore.canLearnFromHit(hit.id)) {
                {
                    TextButton(onClick = { MonitorStateStore.learnSelfFromFilteredHit(hit.id) }) {
                        Text("其实是我")
                    }
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.alpha(0.75f)
        )
    }
}

@Composable
private fun SnippetText(snippet: String, struck: Boolean = false) {
    Text(
        text = "“$snippet”",
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (struck) TextDecoration.LineThrough else null
    )
}
