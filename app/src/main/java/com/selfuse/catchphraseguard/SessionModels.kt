package com.selfuse.catchphraseguard

import java.time.LocalDate

/**
 * v5 shared data models. This file is the contract between the data layer,
 * the foreground service / system integrations, and the UI.
 */

data class KeywordItem(
    val word: String,
    val enabled: Boolean = true,
    val todayCount: Int = 0,
    val totalCount: Int = 0
)

data class HitRecord(
    val id: Long,
    val timeMs: Long,
    val time: String,
    val word: String,
    val snippet: String,
    val isSelfVoice: Boolean = true,
    val similarity: Float? = null,
    val sessionId: Long? = null,
    /** User tapped "不是我" on this self hit; it no longer counts. */
    val undone: Boolean = false,
    /** A filtered hit the user claimed as their own voice. */
    val calibratedManually: Boolean = false,
    /** In-memory only (never persisted). Needed for "不是我" / "其实是我" learning. */
    val transientEmbedding: FloatArray? = null
) {
    val countsAsSelf: Boolean get() = isSelfVoice && !undone
}

data class ActiveSession(
    val id: Long,
    val startAtMs: Long,
    /** null = run until the user stops it. */
    val plannedDurationMs: Long? = null,
    val pausedUntilMs: Long? = null,
    val pauseStartedAtMs: Long? = null,
    val pausedAccumMs: Long = 0L,
    val perWordCounts: Map<String, Int> = emptyMap(),
    val filteredOthers: Int = 0
) {
    val totalCount: Int get() = perWordCounts.values.sum()

    val plannedEndAtMs: Long? get() = plannedDurationMs?.let { startAtMs + it }

    fun isPaused(nowMs: Long): Boolean = pausedUntilMs != null && nowMs < pausedUntilMs

    /** Wall-clock time spent actually listening (pauses excluded). */
    fun monitoredMs(nowMs: Long): Long {
        val currentPause = if (pauseStartedAtMs != null && pausedUntilMs != null) {
            (minOf(nowMs, pausedUntilMs) - pauseStartedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return (nowMs - startAtMs - pausedAccumMs - currentPause).coerceAtLeast(0L)
    }
}

data class SessionRecord(
    val id: Long,
    val startAtMs: Long,
    val endAtMs: Long,
    val monitoredSec: Long,
    val perWordCounts: Map<String, Int>,
    val filteredOthers: Int,
    /** Hits of this session, oldest first, persisted without embeddings. */
    val hits: List<HitRecord> = emptyList()
) {
    val totalCount: Int get() = perWordCounts.values.sum()
    val ratePerHour: Float? get() = StatsCalculator.ratePerHour(totalCount, monitoredSec)
}

data class DailyStat(
    /** ISO date, e.g. 2026-11-20 */
    val date: String,
    val perWordCounts: Map<String, Int> = emptyMap(),
    val monitoredSec: Long = 0L,
    val filteredOthers: Int = 0,
    val sessionCount: Int = 0
) {
    val totalCount: Int get() = perWordCounts.values.sum()
    val ratePerHour: Float? get() = StatsCalculator.ratePerHour(totalCount, monitoredSec)
    val localDate: LocalDate get() = LocalDate.parse(date)
}

enum class VibrationStyle(val label: String) {
    TRIPLE_SHORT("短震三下"),
    SINGLE_LONG("长震一下"),
    OFF("不震动")
}

data class MonitorSettings(
    val pushAlertEnabled: Boolean = true,
    val vibrationStyle: VibrationStyle = VibrationStyle.TRIPLE_SHORT,
    val cooldownSeconds: Int = 3,
    val micBoost: Boolean = false,
    val nearVoiceFilter: Boolean = false,
    val voiceprintEnabled: Boolean = true,
    val voiceprintThreshold: Float = 0.35f,
    /** Goal: at most this many catchphrases per hour of listening. */
    val goalPerHour: Float = 4f,
    /** Default session length offered on the home screen. 0 = until I stop. */
    val defaultDurationMinutes: Int = 0
) {
    val vibrateEnabled: Boolean get() = vibrationStyle != VibrationStyle.OFF
}

data class VoiceprintProfile(
    val enrolledVectors: List<FloatArray> = emptyList(),
    /** Voices the user marked as "不是我". Used to reject similar voices. */
    val negativeVectors: List<FloatArray> = emptyList(),
    val sampleCount: Int = 0,
    val updatedAtText: String = ""
) {
    val isEnrolled: Boolean get() = enrolledVectors.isNotEmpty()
}

enum class PulseEventType {
    NONE,
    SELF_HIT,
    OTHER_FILTERED
}

data class MonitorUiState(
    val initialized: Boolean = false,
    val onboardingCompleted: Boolean = false,

    /** Foreground service is alive (listening or paused). */
    val isRunning: Boolean = false,
    val isMicPausedForEnroll: Boolean = false,
    val statusMessage: String = "",
    val livePreviewText: String = "",
    val micLevel: Float = 0f,

    val activeSession: ActiveSession? = null,
    /** Set when a session ends; UI shows the summary sheet, then calls dismissSummary(). */
    val pendingSummary: SessionRecord? = null,

    val lastSimilarityScore: Float? = null,
    val lastSimilarityWasSelf: Boolean? = null,
    val lastEventPulseType: PulseEventType = PulseEventType.NONE,
    val lastEventTimestampMs: Long = 0L,
    val todayFilteredOthersCount: Int = 0,

    val voiceprintProfile: VoiceprintProfile = VoiceprintProfile(),
    val keywords: List<KeywordItem> = emptyList(),
    /** Newest first, max 60, across sessions. */
    val recentHits: List<HitRecord> = emptyList(),
    /** Newest first, max 150. */
    val sessions: List<SessionRecord> = emptyList(),
    /** Ascending by date, last 120 days. */
    val dailyStats: List<DailyStat> = emptyList(),
    val settings: MonitorSettings = MonitorSettings()
)

data class DailyPoint(
    val date: LocalDate,
    val count: Int,
    val monitoredSec: Long,
    val ratePerHour: Float?
)

/**
 * Pure, side-effect-free statistics helpers for the UI.
 */
object StatsCalculator {
    /** Below this much listening time a rate is too noisy to show. */
    const val MIN_MONITORED_SEC_FOR_RATE = 60L
    /** A day needs at least this much listening to count toward the goal streak. */
    const val MIN_MONITORED_SEC_FOR_STREAK = 5 * 60L

    fun ratePerHour(count: Int, monitoredSec: Long): Float? {
        if (monitoredSec < MIN_MONITORED_SEC_FOR_RATE) return null
        return count / (monitoredSec / 3600f)
    }

    fun statFor(stats: List<DailyStat>, date: LocalDate): DailyStat? {
        val key = date.toString()
        return stats.firstOrNull { it.date == key }
    }

    /** One point per day for the last [days] days ending at [today], missing days filled with zeros. */
    fun dailySeries(stats: List<DailyStat>, days: Int, today: LocalDate = LocalDate.now()): List<DailyPoint> {
        val byDate = stats.associateBy { it.date }
        return (days - 1 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            val s = byDate[d.toString()]
            DailyPoint(
                date = d,
                count = s?.totalCount ?: 0,
                monitoredSec = s?.monitoredSec ?: 0L,
                ratePerHour = s?.ratePerHour
            )
        }
    }

    /** Pooled rate over the window: total count / total listening hours. */
    fun averageRate(stats: List<DailyStat>, days: Int, today: LocalDate = LocalDate.now()): Float? {
        val window = dailySeries(stats, days, today)
        return ratePerHour(window.sumOf { it.count }, window.sumOf { it.monitoredSec })
    }

    fun wordRate(
        stats: List<DailyStat>,
        word: String,
        days: Int,
        today: LocalDate = LocalDate.now()
    ): Float? {
        val from = today.minusDays((days - 1).toLong())
        var count = 0
        var sec = 0L
        for (s in stats) {
            val d = s.localDate
            if (!d.isBefore(from) && !d.isAfter(today)) {
                count += s.perWordCounts[word] ?: 0
                sec += s.monitoredSec
            }
        }
        return ratePerHour(count, sec)
    }

    /**
     * Relative change of a word's rate: last [windowDays] vs the [windowDays] before that.
     * -0.22 means 22% fewer per hour. null when either window lacks data.
     */
    fun wordRateChange(
        stats: List<DailyStat>,
        word: String,
        windowDays: Int = 7,
        today: LocalDate = LocalDate.now()
    ): Float? {
        val current = wordRate(stats, word, windowDays, today) ?: return null
        val previous = wordRate(stats, word, windowDays, today.minusDays(windowDays.toLong())) ?: return null
        if (previous <= 0f) return null
        return (current - previous) / previous
    }

    /**
     * Consecutive days (most recent first) meeting the goal. Days without enough listening are
     * skipped (they neither count nor break the streak). Today is included only if it has data.
     */
    fun goalStreakDays(stats: List<DailyStat>, goalPerHour: Float, today: LocalDate = LocalDate.now()): Int {
        val eligible = stats
            .filter { it.monitoredSec >= MIN_MONITORED_SEC_FOR_STREAK && !it.localDate.isAfter(today) }
            .sortedByDescending { it.date }
        var streak = 0
        for (s in eligible) {
            val rate = s.ratePerHour ?: break
            if (rate <= goalPerHour) streak++ else break
        }
        return streak
    }

    /** Relative change of this session's rate vs the previous session that has a rate. */
    fun changeVsPreviousSession(sessions: List<SessionRecord>, session: SessionRecord): Float? {
        val current = session.ratePerHour ?: return null
        val previous = sessions
            .filter { it.id != session.id && it.endAtMs <= session.startAtMs }
            .sortedByDescending { it.endAtMs }
            .firstOrNull { it.ratePerHour != null }
            ?.ratePerHour ?: return null
        if (previous <= 0f) return null
        return (current - previous) / previous
    }

    /** Today's live rate including the running session. */
    fun todayRate(state: MonitorUiState, nowMs: Long = System.currentTimeMillis()): Float? {
        val today = statFor(state.dailyStats, LocalDate.now())
        val activeSec = state.activeSession?.monitoredMs(nowMs)?.div(1000L) ?: 0L
        return ratePerHour(today?.totalCount ?: 0, (today?.monitoredSec ?: 0L) + activeSec)
    }

    fun todayCount(state: MonitorUiState): Int =
        statFor(state.dailyStats, LocalDate.now())?.totalCount ?: 0

    /** Suggests a goal from the user's own recent data (about 20% below their average). */
    fun suggestedGoal(stats: List<DailyStat>, today: LocalDate = LocalDate.now()): Float? {
        val avg = averageRate(stats, 7, today) ?: return null
        return (avg * 0.8f).coerceAtLeast(0.5f)
    }
}
