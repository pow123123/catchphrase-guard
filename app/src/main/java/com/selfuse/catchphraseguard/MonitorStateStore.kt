package com.selfuse.catchphraseguard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for v5. All mutations go through this object and are persisted
 * to SharedPreferences. Thread-safe via @Synchronized on mutators.
 */
object MonitorStateStore {
    private const val PREFS_NAME = "catchphrase_guard_prefs"

    // Keys carried over from v1–v4 (migrated in place)
    private const val KEY_KEYWORDS = "keywords_json"
    private const val KEY_HITS = "hits_json"
    private const val KEY_SETTINGS = "settings_json"
    private const val KEY_VOICEPRINT = "voiceprint_json"
    private const val KEY_STAT_DATE = "stat_date"
    private const val KEY_FILTERED_TODAY = "filtered_others_today"

    // New in v5
    private const val KEY_SESSIONS = "sessions_json_v5"
    private const val KEY_DAILY = "daily_json_v5"
    private const val KEY_ACTIVE_SESSION = "active_session_json_v5"
    private const val KEY_ONBOARDING_DONE = "onboarding_done_v5"

    private const val MAX_RECENT_HITS = 60
    private const val MAX_SESSIONS = 150
    private const val MAX_DAILY_DAYS = 120
    private const val MAX_NEGATIVE_VECTORS = 20
    private const val MAX_ENROLLED_VECTORS = 24
    private const val MIN_SESSION_SEC_FOR_SUMMARY = 30L

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    @Volatile
    private var initialized = false
    private lateinit var prefs: SharedPreferences

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------------
    // Init & day rollover
    // ---------------------------------------------------------------------

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) {
            rollOverDateIfNeeded()
            return
        }
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isExistingUser = prefs.contains(KEY_KEYWORDS)
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_STAT_DATE, null)

        var keywords = loadKeywords()
        var daily = loadDaily()

        // Migrate v4 "todayCount" into a daily record so history isn't lost.
        if (daily.isEmpty() && savedDate != null) {
            val legacyCounts = keywords.filter { it.todayCount > 0 }.associate { it.word to it.todayCount }
            if (legacyCounts.isNotEmpty()) {
                daily = listOf(
                    DailyStat(
                        date = savedDate,
                        perWordCounts = legacyCounts,
                        filteredOthers = prefs.getInt(KEY_FILTERED_TODAY, 0)
                    )
                )
                saveDaily(daily)
            }
        }

        // Keyword.todayCount is always derived from today's daily record.
        val todayStat = daily.firstOrNull { it.date == today }
        keywords = keywords.map { it.copy(todayCount = todayStat?.perWordCounts?.get(it.word) ?: 0) }
        saveKeywords(keywords)
        prefs.edit().putString(KEY_STAT_DATE, today).apply()

        val onboardingDone = if (prefs.contains(KEY_ONBOARDING_DONE)) {
            prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        } else {
            isExistingUser.also { prefs.edit().putBoolean(KEY_ONBOARDING_DONE, it).apply() }
        }

        _uiState.update {
            it.copy(
                initialized = true,
                onboardingCompleted = onboardingDone,
                keywords = keywords,
                recentHits = loadHits(),
                sessions = loadSessions(),
                dailyStats = daily,
                settings = loadSettings(),
                voiceprintProfile = loadVoiceprintProfile(),
                activeSession = loadActiveSession(),
                todayFilteredOthersCount = todayStat?.filteredOthers ?: 0
            )
        }
        initialized = true
    }

    @Synchronized
    fun rollOverDateIfNeeded() {
        if (!initialized) return
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_STAT_DATE, null) == today) return
        val todayStat = _uiState.value.dailyStats.firstOrNull { it.date == today }
        val keywords = _uiState.value.keywords.map {
            it.copy(todayCount = todayStat?.perWordCounts?.get(it.word) ?: 0)
        }
        saveKeywords(keywords)
        prefs.edit().putString(KEY_STAT_DATE, today).apply()
        _uiState.update {
            it.copy(
                keywords = keywords,
                todayFilteredOthersCount = todayStat?.filteredOthers ?: 0
            )
        }
    }

    @Synchronized
    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _uiState.update { it.copy(onboardingCompleted = true) }
    }

    // ---------------------------------------------------------------------
    // Live service state (not persisted)
    // ---------------------------------------------------------------------

    fun setRunningState(running: Boolean, statusMessage: String) {
        _uiState.update {
            it.copy(
                isRunning = running,
                isMicPausedForEnroll = if (!running) false else it.isMicPausedForEnroll,
                statusMessage = statusMessage,
                livePreviewText = if (running) it.livePreviewText else "",
                micLevel = if (running) it.micLevel else 0f
            )
        }
    }

    fun setMicPausedForEnroll(paused: Boolean) {
        _uiState.update {
            it.copy(
                isMicPausedForEnroll = paused,
                micLevel = if (paused) 0f else it.micLevel
            )
        }
    }

    fun updateLivePreview(text: String, micLevel: Float) {
        _uiState.update { it.copy(livePreviewText = text, micLevel = micLevel) }
    }

    // ---------------------------------------------------------------------
    // Session lifecycle (called by the foreground service)
    // ---------------------------------------------------------------------

    /** Starts a session, or returns the one already running. */
    @Synchronized
    fun startSession(plannedDurationMs: Long?, nowMs: Long = System.currentTimeMillis()): ActiveSession {
        _uiState.value.activeSession?.let { return it }
        val session = ActiveSession(
            id = nowMs,
            startAtMs = nowMs,
            plannedDurationMs = plannedDurationMs?.takeIf { it > 0L }
        )
        persistActiveSession(session)
        _uiState.update { it.copy(activeSession = session, pendingSummary = null) }
        return session
    }

    @Synchronized
    fun pauseSession(durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val s = _uiState.value.activeSession ?: return
        val base = foldPause(s, nowMs)
        val paused = base.copy(pauseStartedAtMs = nowMs, pausedUntilMs = nowMs + durationMs)
        persistActiveSession(paused)
        _uiState.update { it.copy(activeSession = paused) }
    }

    @Synchronized
    fun resumeSession(nowMs: Long = System.currentTimeMillis()) {
        val s = _uiState.value.activeSession ?: return
        if (s.pauseStartedAtMs == null) return
        val resumed = foldPause(s, nowMs)
        persistActiveSession(resumed)
        _uiState.update { it.copy(activeSession = resumed) }
    }

    /** Moves any current pause into pausedAccumMs and clears pause fields. */
    private fun foldPause(s: ActiveSession, nowMs: Long): ActiveSession {
        val start = s.pauseStartedAtMs ?: return s
        val until = s.pausedUntilMs ?: nowMs
        val pausedMs = (minOf(nowMs, until) - start).coerceAtLeast(0L)
        return s.copy(
            pausedAccumMs = s.pausedAccumMs + pausedMs,
            pauseStartedAtMs = null,
            pausedUntilMs = null
        )
    }

    /**
     * Ends the active session, writes it to history, and sets pendingSummary when the session
     * is long enough (or caught something). Returns the record, or null if nothing was active.
     */
    @Synchronized
    fun endSession(nowMs: Long = System.currentTimeMillis()): SessionRecord? {
        val active = _uiState.value.activeSession ?: return null
        val folded = foldPause(active, nowMs)
        val monitoredSec = folded.monitoredMs(nowMs) / 1000L

        val sessionHits = _uiState.value.recentHits
            .filter { it.sessionId == active.id }
            .sortedBy { it.timeMs }
            .map { it.copy(transientEmbedding = null) }

        val record = SessionRecord(
            id = active.id,
            startAtMs = active.startAtMs,
            endAtMs = nowMs,
            monitoredSec = monitoredSec,
            perWordCounts = folded.perWordCounts,
            filteredOthers = folded.filteredOthers,
            hits = sessionHits
        )

        val sessions = (listOf(record) + _uiState.value.sessions).take(MAX_SESSIONS)
        val dateKey = dateKeyOf(active.startAtMs)
        val daily = upsertDaily(_uiState.value.dailyStats, dateKey) {
            it.copy(
                monitoredSec = it.monitoredSec + monitoredSec,
                sessionCount = it.sessionCount + 1
            )
        }

        saveSessions(sessions)
        saveDaily(daily)
        persistActiveSession(null)

        val showSummary = monitoredSec >= MIN_SESSION_SEC_FOR_SUMMARY || record.totalCount > 0
        _uiState.update {
            it.copy(
                activeSession = null,
                sessions = sessions,
                dailyStats = daily,
                pendingSummary = if (showSummary) record else null
            )
        }
        return record
    }

    @Synchronized
    fun dismissSummary() {
        _uiState.update { it.copy(pendingSummary = null) }
    }

    /**
     * Drops the active session without writing history (e.g. the service failed to go
     * foreground right after starting). Counts already recorded to keywords/days are kept.
     */
    @Synchronized
    fun discardActiveSession() {
        persistActiveSession(null)
        _uiState.update { it.copy(activeSession = null) }
    }

    // ---------------------------------------------------------------------
    // Hits
    // ---------------------------------------------------------------------

    @Synchronized
    fun recordSelfKeywordHit(
        word: String,
        fullUtterance: String,
        similarity: Float?,
        embedding: FloatArray? = null,
        nowMs: Long = System.currentTimeMillis()
    ): HitRecord {
        rollOverDateIfNeeded()
        val state = _uiState.value
        val active = state.activeSession

        val keywords = state.keywords.map {
            if (it.word == word) it.copy(todayCount = it.todayCount + 1, totalCount = it.totalCount + 1) else it
        }
        val daily = upsertDaily(state.dailyStats, dateKeyOf(nowMs)) {
            it.copy(perWordCounts = it.perWordCounts.incremented(word, 1))
        }
        val updatedActive = active?.copy(perWordCounts = active.perWordCounts.incremented(word, 1))

        val record = HitRecord(
            id = nextHitId(nowMs),
            timeMs = nowMs,
            time = formatTime(nowMs),
            word = word,
            snippet = formatSnippet(fullUtterance),
            isSelfVoice = true,
            similarity = similarity,
            sessionId = active?.id,
            transientEmbedding = embedding
        )
        val hits = trimEmbeddings(listOf(record) + state.recentHits).take(MAX_RECENT_HITS)

        saveKeywords(keywords)
        saveDaily(daily)
        saveHits(hits)
        persistActiveSession(updatedActive)
        _uiState.update {
            it.copy(
                keywords = keywords,
                dailyStats = daily,
                recentHits = hits,
                activeSession = updatedActive,
                lastSimilarityScore = similarity,
                lastSimilarityWasSelf = true,
                lastEventPulseType = PulseEventType.SELF_HIT,
                lastEventTimestampMs = nowMs
            )
        }
        return record
    }

    @Synchronized
    fun recordFilteredOtherVoice(
        word: String,
        fullUtterance: String,
        similarity: Float,
        liveEmbedding: FloatArray? = null,
        nowMs: Long = System.currentTimeMillis()
    ): HitRecord {
        rollOverDateIfNeeded()
        val state = _uiState.value
        val active = state.activeSession

        val daily = upsertDaily(state.dailyStats, dateKeyOf(nowMs)) {
            it.copy(filteredOthers = it.filteredOthers + 1)
        }
        val updatedActive = active?.copy(filteredOthers = active.filteredOthers + 1)

        val record = HitRecord(
            id = nextHitId(nowMs),
            timeMs = nowMs,
            time = formatTime(nowMs),
            word = word,
            snippet = formatSnippet(fullUtterance),
            isSelfVoice = false,
            similarity = similarity,
            sessionId = active?.id,
            transientEmbedding = liveEmbedding
        )
        val hits = trimEmbeddings(listOf(record) + state.recentHits).take(MAX_RECENT_HITS)

        saveDaily(daily)
        saveHits(hits)
        persistActiveSession(updatedActive)
        _uiState.update {
            it.copy(
                dailyStats = daily,
                recentHits = hits,
                activeSession = updatedActive,
                todayFilteredOthersCount = daily.firstOrNull { d -> d.date == LocalDate.now().toString() }
                    ?.filteredOthers ?: it.todayFilteredOthersCount,
                lastSimilarityScore = similarity,
                lastSimilarityWasSelf = false,
                lastEventPulseType = PulseEventType.OTHER_FILTERED,
                lastEventTimestampMs = nowMs
            )
        }
        return record
    }

    /**
     * User says a counted self hit was not them. Removes it from every count (keyword, day,
     * active or finished session) and, if we still have its voiceprint, stores it as a
     * negative sample so similar voices are rejected next time.
     * Returns true if something changed.
     */
    @Synchronized
    fun markNotMe(hitId: Long): Boolean {
        val state = _uiState.value
        val hit = findHit(state, hitId) ?: return false
        if (!hit.isSelfVoice || hit.undone) return false

        val word = hit.word
        val isToday = dateKeyOf(hit.timeMs) == LocalDate.now().toString()

        val keywords = state.keywords.map {
            if (it.word == word) {
                it.copy(
                    todayCount = if (isToday) (it.todayCount - 1).coerceAtLeast(0) else it.todayCount,
                    totalCount = (it.totalCount - 1).coerceAtLeast(0)
                )
            } else {
                it
            }
        }
        val daily = upsertDaily(state.dailyStats, dateKeyOf(hit.timeMs)) {
            it.copy(perWordCounts = it.perWordCounts.incremented(word, -1))
        }
        val active = state.activeSession?.let { a ->
            if (a.id == hit.sessionId) a.copy(perWordCounts = a.perWordCounts.incremented(word, -1)) else a
        }
        val sessions = state.sessions.map { s ->
            if (s.id == hit.sessionId) undoInSession(s, hitId, word) else s
        }
        val pending = state.pendingSummary?.let { s ->
            if (s.id == hit.sessionId) undoInSession(s, hitId, word) else s
        }
        val hits = state.recentHits.map {
            if (it.id == hitId) it.copy(undone = true, transientEmbedding = null) else it
        }

        val embedding = state.recentHits.firstOrNull { it.id == hitId }?.transientEmbedding
        val profile = if (embedding != null) {
            val negatives = (state.voiceprintProfile.negativeVectors +
                listOf(VoiceprintManager.l2Normalize(embedding))).takeLast(MAX_NEGATIVE_VECTORS)
            state.voiceprintProfile.copy(negativeVectors = negatives).also { persistVoiceprintProfile(it) }
        } else {
            state.voiceprintProfile
        }

        saveKeywords(keywords)
        saveDaily(daily)
        saveSessions(sessions)
        saveHits(hits)
        persistActiveSession(active)
        _uiState.update {
            it.copy(
                keywords = keywords,
                dailyStats = daily,
                activeSession = active,
                sessions = sessions,
                pendingSummary = pending,
                recentHits = hits,
                voiceprintProfile = profile
            )
        }
        return true
    }

    /** Whether "不是我" can also teach the voiceprint model for this hit (embedding still in memory). */
    fun canLearnFromHit(hitId: Long): Boolean =
        _uiState.value.recentHits.firstOrNull { it.id == hitId }?.transientEmbedding != null

    /**
     * A filtered "other voice" hit was actually the user. Counts it and adds its voiceprint
     * to the enrolled set.
     */
    @Synchronized
    fun learnSelfFromFilteredHit(hitId: Long): Boolean {
        val state = _uiState.value
        val hit = state.recentHits.firstOrNull { it.id == hitId && !it.isSelfVoice } ?: return false
        val emb = hit.transientEmbedding ?: return false
        val word = hit.word
        val isToday = dateKeyOf(hit.timeMs) == LocalDate.now().toString()

        val enrolled = (state.voiceprintProfile.enrolledVectors +
            listOf(VoiceprintManager.l2Normalize(emb))).takeLast(MAX_ENROLLED_VECTORS)
        val profile = state.voiceprintProfile.copy(
            enrolledVectors = enrolled,
            sampleCount = state.voiceprintProfile.sampleCount + 1,
            updatedAtText = LocalDateTime.now().format(dateTimeFormatter)
        )
        persistVoiceprintProfile(profile)

        val keywords = state.keywords.map {
            if (it.word == word) {
                it.copy(
                    todayCount = if (isToday) it.todayCount + 1 else it.todayCount,
                    totalCount = it.totalCount + 1
                )
            } else {
                it
            }
        }
        val daily = upsertDaily(state.dailyStats, dateKeyOf(hit.timeMs)) {
            it.copy(
                perWordCounts = it.perWordCounts.incremented(word, 1),
                filteredOthers = (it.filteredOthers - 1).coerceAtLeast(0)
            )
        }
        val active = state.activeSession?.let { a ->
            if (a.id == hit.sessionId) {
                a.copy(
                    perWordCounts = a.perWordCounts.incremented(word, 1),
                    filteredOthers = (a.filteredOthers - 1).coerceAtLeast(0)
                )
            } else {
                a
            }
        }
        val hits = state.recentHits.map {
            if (it.id == hitId) {
                it.copy(isSelfVoice = true, calibratedManually = true, transientEmbedding = null)
            } else {
                it
            }
        }
        val sessions = state.sessions.map { s ->
            if (s.id == hit.sessionId) claimInSession(s, hitId, word) else s
        }
        val pending = state.pendingSummary?.let { s ->
            if (s.id == hit.sessionId) claimInSession(s, hitId, word) else s
        }

        saveKeywords(keywords)
        saveDaily(daily)
        saveHits(hits)
        saveSessions(sessions)
        persistActiveSession(active)
        _uiState.update {
            it.copy(
                voiceprintProfile = profile,
                keywords = keywords,
                dailyStats = daily,
                activeSession = active,
                recentHits = hits,
                sessions = sessions,
                pendingSummary = pending,
                todayFilteredOthersCount = daily.firstOrNull { d -> d.date == LocalDate.now().toString() }
                    ?.filteredOthers ?: it.todayFilteredOthersCount,
                lastSimilarityWasSelf = true
            )
        }
        return true
    }

    // ---------------------------------------------------------------------
    // Keywords, settings, voiceprint, data
    // ---------------------------------------------------------------------

    @Synchronized
    fun addKeyword(rawWord: String): Boolean {
        val cleaned = rawWord.trim()
        if (cleaned.isEmpty()) return false
        val current = _uiState.value.keywords
        if (current.any { it.word == cleaned }) return false
        val updated = current + KeywordItem(word = cleaned)
        saveKeywords(updated)
        _uiState.update { it.copy(keywords = updated) }
        return true
    }

    @Synchronized
    fun toggleKeyword(word: String, enabled: Boolean) {
        val updated = _uiState.value.keywords.map { if (it.word == word) it.copy(enabled = enabled) else it }
        saveKeywords(updated)
        _uiState.update { it.copy(keywords = updated) }
    }

    @Synchronized
    fun removeKeyword(word: String) {
        val updated = _uiState.value.keywords.filterNot { it.word == word }
        saveKeywords(updated)
        _uiState.update { it.copy(keywords = updated) }
    }

    @Synchronized
    fun updateSettings(transform: (MonitorSettings) -> MonitorSettings) {
        val updated = transform(_uiState.value.settings)
        saveSettings(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    @Synchronized
    fun saveEnrolledVoiceprint(allExtractedEmbeddings: List<FloatArray>, stepCount: Int = 3) {
        val referenceSet = VoiceprintManager.buildEnrolledReferenceSet(allExtractedEmbeddings)
        val profile = VoiceprintProfile(
            enrolledVectors = referenceSet,
            // Re-enrolling starts fresh; old "not me" samples may no longer apply.
            negativeVectors = emptyList(),
            sampleCount = stepCount,
            updatedAtText = LocalDateTime.now().format(dateTimeFormatter)
        )
        persistVoiceprintProfile(profile)
        val settings = _uiState.value.settings.copy(voiceprintEnabled = true)
        saveSettings(settings)
        _uiState.update { it.copy(voiceprintProfile = profile, settings = settings) }
    }

    @Synchronized
    fun clearVoiceprint() {
        val empty = VoiceprintProfile()
        persistVoiceprintProfile(empty)
        _uiState.update {
            it.copy(voiceprintProfile = empty, lastSimilarityScore = null, lastSimilarityWasSelf = null)
        }
    }

    @Synchronized
    fun clearNegativeSamples() {
        val profile = _uiState.value.voiceprintProfile.copy(negativeVectors = emptyList())
        persistVoiceprintProfile(profile)
        _uiState.update { it.copy(voiceprintProfile = profile) }
    }

    /** Clears history, counts and hits. Keeps keywords (counts reset), settings and voiceprint. */
    @Synchronized
    fun clearAllHistory() {
        val keywords = _uiState.value.keywords.map { it.copy(todayCount = 0, totalCount = 0) }
        saveKeywords(keywords)
        saveHits(emptyList())
        saveSessions(emptyList())
        saveDaily(emptyList())
        prefs.edit().putInt(KEY_FILTERED_TODAY, 0).apply()
        _uiState.update {
            it.copy(
                keywords = keywords,
                recentHits = emptyList(),
                sessions = emptyList(),
                dailyStats = emptyList(),
                pendingSummary = null,
                todayFilteredOthersCount = 0
            )
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun findHit(state: MonitorUiState, hitId: Long): HitRecord? =
        state.recentHits.firstOrNull { it.id == hitId }
            ?: state.pendingSummary?.hits?.firstOrNull { it.id == hitId }
            ?: state.sessions.asSequence().flatMap { it.hits.asSequence() }.firstOrNull { it.id == hitId }

    private fun undoInSession(s: SessionRecord, hitId: Long, word: String): SessionRecord = s.copy(
        perWordCounts = s.perWordCounts.incremented(word, -1),
        hits = s.hits.map { if (it.id == hitId) it.copy(undone = true) else it }
    )

    private fun claimInSession(s: SessionRecord, hitId: Long, word: String): SessionRecord = s.copy(
        perWordCounts = s.perWordCounts.incremented(word, 1),
        filteredOthers = (s.filteredOthers - 1).coerceAtLeast(0),
        hits = s.hits.map {
            if (it.id == hitId) it.copy(isSelfVoice = true, calibratedManually = true) else it
        }
    )

    private fun upsertDaily(
        stats: List<DailyStat>,
        dateKey: String,
        transform: (DailyStat) -> DailyStat
    ): List<DailyStat> {
        val existing = stats.firstOrNull { it.date == dateKey } ?: DailyStat(date = dateKey)
        val updated = transform(existing)
        val cutoff = LocalDate.now().minusDays(MAX_DAILY_DAYS.toLong()).toString()
        return (stats.filterNot { it.date == dateKey } + updated)
            .filter { it.date >= cutoff }
            .sortedBy { it.date }
    }

    private fun Map<String, Int>.incremented(word: String, delta: Int): Map<String, Int> {
        val next = ((this[word] ?: 0) + delta).coerceAtLeast(0)
        return if (next == 0) this - word else this + (word to next)
    }

    /** Keep embeddings only on the newest hits to bound memory. */
    private fun trimEmbeddings(hits: List<HitRecord>): List<HitRecord> =
        hits.mapIndexed { idx, h -> if (idx >= 50 && h.transientEmbedding != null) h.copy(transientEmbedding = null) else h }

    private var lastHitId = 0L
    private fun nextHitId(nowMs: Long): Long {
        val id = if (nowMs <= lastHitId) lastHitId + 1 else nowMs
        lastHitId = id
        return id
    }

    private fun dateKeyOf(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun formatTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

    private fun formatSnippet(s: String): String = if (s.length > 26) "…" + s.takeLast(26) else s

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private fun mapToJson(map: Map<String, Int>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }

    private fun jsonToMap(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optInt(k, 0)
        }
        return out
    }

    private fun hitToJson(h: HitRecord): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("timeMs", h.timeMs)
        put("time", h.time)
        put("word", h.word)
        put("snippet", h.snippet)
        put("isSelfVoice", h.isSelfVoice)
        put("undone", h.undone)
        put("calibratedManually", h.calibratedManually)
        h.similarity?.let { put("similarity", it.toDouble()) }
        h.sessionId?.let { put("sessionId", it) }
    }

    private fun jsonToHit(o: JSONObject, fallbackId: Long): HitRecord = HitRecord(
        id = o.optLong("id", fallbackId),
        // v4 hits had no timeMs; fall back to id (it was a timestamp then)
        timeMs = o.optLong("timeMs", o.optLong("id", fallbackId)),
        time = o.optString("time", ""),
        word = o.optString("word", ""),
        snippet = o.optString("snippet", ""),
        isSelfVoice = o.optBoolean("isSelfVoice", true),
        similarity = if (o.has("similarity") && !o.isNull("similarity")) o.optDouble("similarity").toFloat() else null,
        sessionId = if (o.has("sessionId") && !o.isNull("sessionId")) o.optLong("sessionId") else null,
        undone = o.optBoolean("undone", false),
        calibratedManually = o.optBoolean("calibratedManually", false)
    )

    private fun loadKeywords(): List<KeywordItem> {
        val defaults = listOf(KeywordItem("没有"), KeywordItem("不是"))
        val raw = prefs.getString(KEY_KEYWORDS, null)
        if (raw.isNullOrBlank()) {
            saveKeywords(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val w = o.optString("word").trim()
                if (w.isEmpty()) null else KeywordItem(
                    word = w,
                    enabled = o.optBoolean("enabled", true),
                    todayCount = o.optInt("todayCount", 0),
                    totalCount = o.optInt("totalCount", 0)
                )
            }
        } catch (_: Exception) {
            defaults
        }
    }

    private fun saveKeywords(list: List<KeywordItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("word", it.word)
                put("enabled", it.enabled)
                put("todayCount", it.todayCount)
                put("totalCount", it.totalCount)
            })
        }
        prefs.edit().putString(KEY_KEYWORDS, arr.toString()).apply()
    }

    private fun loadHits(): List<HitRecord> {
        val raw = prefs.getString(KEY_HITS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> jsonToHit(arr.getJSONObject(i), System.currentTimeMillis() + i) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHits(list: List<HitRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(hitToJson(it)) }
        prefs.edit().putString(KEY_HITS, arr.toString()).apply()
    }

    private fun loadSessions(): List<SessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val hitsArr = o.optJSONArray("hits") ?: JSONArray()
                SessionRecord(
                    id = o.optLong("id"),
                    startAtMs = o.optLong("startAtMs"),
                    endAtMs = o.optLong("endAtMs"),
                    monitoredSec = o.optLong("monitoredSec"),
                    perWordCounts = jsonToMap(o.optJSONObject("perWordCounts")),
                    filteredOthers = o.optInt("filteredOthers", 0),
                    hits = (0 until hitsArr.length()).map { j -> jsonToHit(hitsArr.getJSONObject(j), j.toLong()) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSessions(list: List<SessionRecord>) {
        val arr = JSONArray()
        list.forEach { s ->
            val hitsArr = JSONArray()
            s.hits.forEach { hitsArr.put(hitToJson(it)) }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("startAtMs", s.startAtMs)
                put("endAtMs", s.endAtMs)
                put("monitoredSec", s.monitoredSec)
                put("perWordCounts", mapToJson(s.perWordCounts))
                put("filteredOthers", s.filteredOthers)
                put("hits", hitsArr)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun loadDaily(): List<DailyStat> {
        val raw = prefs.getString(KEY_DAILY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DailyStat(
                    date = o.optString("date"),
                    perWordCounts = jsonToMap(o.optJSONObject("perWordCounts")),
                    monitoredSec = o.optLong("monitoredSec", 0L),
                    filteredOthers = o.optInt("filteredOthers", 0),
                    sessionCount = o.optInt("sessionCount", 0)
                )
            }.filter { it.date.isNotBlank() }.sortedBy { it.date }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveDaily(list: List<DailyStat>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("date", d.date)
                put("perWordCounts", mapToJson(d.perWordCounts))
                put("monitoredSec", d.monitoredSec)
                put("filteredOthers", d.filteredOthers)
                put("sessionCount", d.sessionCount)
            })
        }
        prefs.edit().putString(KEY_DAILY, arr.toString()).apply()
    }

    private fun loadActiveSession(): ActiveSession? {
        val raw = prefs.getString(KEY_ACTIVE_SESSION, null) ?: return null
        return try {
            val o = JSONObject(raw)
            ActiveSession(
                id = o.getLong("id"),
                startAtMs = o.getLong("startAtMs"),
                plannedDurationMs = if (o.has("plannedDurationMs")) o.getLong("plannedDurationMs") else null,
                pausedUntilMs = if (o.has("pausedUntilMs")) o.getLong("pausedUntilMs") else null,
                pauseStartedAtMs = if (o.has("pauseStartedAtMs")) o.getLong("pauseStartedAtMs") else null,
                pausedAccumMs = o.optLong("pausedAccumMs", 0L),
                perWordCounts = jsonToMap(o.optJSONObject("perWordCounts")),
                filteredOthers = o.optInt("filteredOthers", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun persistActiveSession(s: ActiveSession?) {
        if (s == null) {
            prefs.edit().remove(KEY_ACTIVE_SESSION).apply()
            return
        }
        val o = JSONObject().apply {
            put("id", s.id)
            put("startAtMs", s.startAtMs)
            s.plannedDurationMs?.let { put("plannedDurationMs", it) }
            s.pausedUntilMs?.let { put("pausedUntilMs", it) }
            s.pauseStartedAtMs?.let { put("pauseStartedAtMs", it) }
            put("pausedAccumMs", s.pausedAccumMs)
            put("perWordCounts", mapToJson(s.perWordCounts))
            put("filteredOthers", s.filteredOthers)
        }
        prefs.edit().putString(KEY_ACTIVE_SESSION, o.toString()).apply()
    }

    private fun loadSettings(): MonitorSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return MonitorSettings()
        return try {
            val o = JSONObject(raw)
            val vibration = when {
                o.has("vibrationStyle") -> runCatching {
                    VibrationStyle.valueOf(o.getString("vibrationStyle"))
                }.getOrDefault(VibrationStyle.TRIPLE_SHORT)
                // v4 had a boolean
                o.has("vibrateEnabled") && !o.optBoolean("vibrateEnabled", true) -> VibrationStyle.OFF
                else -> VibrationStyle.TRIPLE_SHORT
            }
            MonitorSettings(
                pushAlertEnabled = o.optBoolean("pushAlertEnabled", true),
                vibrationStyle = vibration,
                cooldownSeconds = o.optInt("cooldownSeconds", 3).coerceIn(1, 30),
                micBoost = o.optBoolean("micBoost", false),
                nearVoiceFilter = o.optBoolean("nearVoiceFilter", false),
                voiceprintEnabled = o.optBoolean("voiceprintEnabled", true),
                voiceprintThreshold = o.optDouble("voiceprintThreshold", 0.35).toFloat().coerceIn(0.20f, 0.68f),
                goalPerHour = o.optDouble("goalPerHour", 4.0).toFloat().coerceIn(0.5f, 60f),
                defaultDurationMinutes = o.optInt("defaultDurationMinutes", 0).coerceAtLeast(0)
            )
        } catch (_: Exception) {
            MonitorSettings()
        }
    }

    private fun saveSettings(s: MonitorSettings) {
        val o = JSONObject().apply {
            put("pushAlertEnabled", s.pushAlertEnabled)
            put("vibrationStyle", s.vibrationStyle.name)
            put("cooldownSeconds", s.cooldownSeconds)
            put("micBoost", s.micBoost)
            put("nearVoiceFilter", s.nearVoiceFilter)
            put("voiceprintEnabled", s.voiceprintEnabled)
            put("voiceprintThreshold", s.voiceprintThreshold.toDouble())
            put("goalPerHour", s.goalPerHour.toDouble())
            put("defaultDurationMinutes", s.defaultDurationMinutes)
        }
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    private fun vectorsToJson(vectors: List<FloatArray>): JSONArray {
        val arr = JSONArray()
        vectors.forEach { v ->
            val inner = JSONArray()
            v.forEach { inner.put(it.toDouble()) }
            arr.put(inner)
        }
        return arr
    }

    private fun jsonToVectors(arr: JSONArray?): List<FloatArray> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val inner = arr.getJSONArray(i)
            val v = FloatArray(inner.length()) { j -> inner.getDouble(j).toFloat() }
            if (v.isEmpty()) null else v
        }
    }

    private fun loadVoiceprintProfile(): VoiceprintProfile {
        val raw = prefs.getString(KEY_VOICEPRINT, null) ?: return VoiceprintProfile()
        return try {
            val o = JSONObject(raw)
            VoiceprintProfile(
                enrolledVectors = jsonToVectors(o.optJSONArray("vectors")),
                negativeVectors = jsonToVectors(o.optJSONArray("negativeVectors")),
                sampleCount = o.optInt("sampleCount", 0),
                updatedAtText = o.optString("updatedAtText", "")
            )
        } catch (_: Exception) {
            VoiceprintProfile()
        }
    }

    private fun persistVoiceprintProfile(p: VoiceprintProfile) {
        if (!p.isEnrolled && p.negativeVectors.isEmpty()) {
            prefs.edit().remove(KEY_VOICEPRINT).apply()
            return
        }
        val o = JSONObject().apply {
            put("sampleCount", p.sampleCount)
            put("updatedAtText", p.updatedAtText)
            put("vectors", vectorsToJson(p.enrolledVectors))
            put("negativeVectors", vectorsToJson(p.negativeVectors))
        }
        prefs.edit().putString(KEY_VOICEPRINT, o.toString()).apply()
    }
}
