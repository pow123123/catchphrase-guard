@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfuse.catchphraseguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.DailyPoint
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.StatsCalculator
import com.selfuse.catchphraseguard.ui.components.Fmt
import com.selfuse.catchphraseguard.ui.components.SectionHeader
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private const val GOAL_MIN = 0.5f
private const val GOAL_MAX = 30f
private const val GOAL_STEP = 0.5f

@Composable
fun TrendScreen(state: MonitorUiState, modifier: Modifier = Modifier) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val today = LocalDate.now()
    val stats = state.dailyStats
    val goal = state.settings.goalPerHour
    val series = StatsCalculator.dailySeries(stats, days, today)
    val average = StatsCalculator.averageRate(stats, days, today)
    val streak = StatsCalculator.goalStreakDays(stats, goal, today)
    val suggested = StatsCalculator.suggestedGoal(stats, today)
    val hasAnyData = series.any { it.monitoredSec > 0 }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "每小时次数",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                SingleChoiceSegmentedButtonRow {
                    listOf(7, 30).forEachIndexed { index, d ->
                        SegmentedButton(
                            selected = days == d,
                            onClick = { days = d },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                        ) {
                            Text("$d 天")
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                RateBarChart(
                    points = series,
                    goal = goal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp)
                )
                if (!hasAnyData) {
                    Text(
                        text = "这段时间还没有监听记录。开始一次监听后，这里会画出每天的频率。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric(value = Fmt.rate(average), label = "$days 天平均 · 每小时")
                Metric(value = streak.toString(), label = "连续达标天数")
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("目标")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "每小时不超过",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = { setGoal(goal - GOAL_STEP) },
                        enabled = goal > GOAL_MIN
                    ) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = Fmt.goal(goal),
                        style = MaterialTheme.typography.headlineSmall.tabular(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 64.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { setGoal(goal + GOAL_STEP) },
                        enabled = goal < GOAL_MAX
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("次", style = MaterialTheme.typography.bodyLarge)
                }
                if (suggested != null) {
                    val rounded = roundToStep(suggested)
                    if (abs(rounded - goal) >= 0.01f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "按你近 7 天的情况，建议设为 ${Fmt.goal(rounded)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { setGoal(rounded) }) { Text("采用") }
                        }
                    }
                }
            }
        }

        val words = (state.keywords.map { it.word } +
            stats.flatMap { it.perWordCounts.keys }).distinct()
        if (words.isNotEmpty()) {
            item { SectionHeader("各个词") }
            items(words, key = { "word_$it" }) { word ->
                val rate = StatsCalculator.wordRate(stats, word, days, today)
                val change = StatsCalculator.wordRateChange(stats, word, 7, today)
                ListItem(
                    headlineContent = { Text(word) },
                    supportingContent = {
                        Text(
                            text = if (rate != null) "每小时 ${Fmt.rate(rate)} 次" else "还没有数据",
                            style = MaterialTheme.typography.bodyMedium.tabular()
                        )
                    },
                    trailingContent = {
                        val text = Fmt.change(change, "上周")
                        if (text != null) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if ((change ?: 0f) <= 0f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                )
            }
        }

        val recent = state.sessions.take(10)
        if (recent.isNotEmpty()) {
            item { SectionHeader("最近的监听") }
            items(recent, key = { "session_${it.id}" }) { s ->
                val rate = s.ratePerHour
                ListItem(
                    headlineContent = { Text("${Fmt.dateTime(s.startAtMs)} · ${Fmt.duration(s.monitoredSec)}") },
                    supportingContent = {
                        val parts = s.perWordCounts.entries.filter { it.value > 0 }
                            .sortedByDescending { it.value }
                            .joinToString(" · ") { "${it.key} ${it.value}" }
                        Text(if (parts.isEmpty()) "没有说到" else parts)
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${s.totalCount} 次", style = MaterialTheme.typography.titleMedium.tabular())
                            if (rate != null) {
                                Text(
                                    "每小时 ${Fmt.rate(rate)}",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun roundToStep(value: Float): Float =
    ((value / GOAL_STEP).roundToInt() * GOAL_STEP).coerceIn(GOAL_MIN, GOAL_MAX)

private fun setGoal(value: Float) {
    val v = roundToStep(value)
    MonitorStateStore.updateSettings { it.copy(goalPerHour = v) }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium.tabular())
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun weekdayLabel(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

/** Daily rate bars with a dashed goal line. Days without listening are left empty. */
@Composable
private fun RateBarChart(points: List<DailyPoint>, goal: Float, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxRate = maxOf(goal * 1.3f, points.maxOfOrNull { it.ratePerHour ?: 0f } ?: 0f, 1f)
    val many = points.size > 10

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                val n = points.size.coerceAtLeast(1)
                val slot = size.width / n
                val barWidth = slot * if (many) 0.6f else 0.5f
                val chartHeight = size.height

                points.forEachIndexed { i, p ->
                    val rate = p.ratePerHour ?: return@forEachIndexed
                    val h = (rate / maxRate * chartHeight).coerceAtLeast(2.dp.toPx())
                    val left = i * slot + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = if (rate <= goal) barColor else overColor,
                        topLeft = Offset(left, chartHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 4f, barWidth / 4f)
                    )
                }
                // Baseline
                drawLine(
                    color = baseColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )
                // Goal line
                val goalY = chartHeight - goal / maxRate * chartHeight
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            }
        }
        if (!many) {
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { p ->
                    Text(
                        text = weekdayLabel(p.date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else if (points.isNotEmpty()) {
            val first = points.first().date
            val mid = points[points.size / 2].date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("${first.monthValue}/${first.dayOfMonth}", "${mid.monthValue}/${mid.dayOfMonth}", "今天")
                    .forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }
        Text(
            text = "虚线：目标 每小时 ${Fmt.goal(goal)} 次；浅色柱表示超过目标",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
