package com.selfuse.catchphraseguard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class KeywordItem(
    val word: String,
    val enabled: Boolean = true,
    val todayCount: Int = 0,
    val totalCount: Int = 0
)

data class HitRecord(
    val id: Long,
    val time: String,
    val word: String,
    val snippet: String,
    val todayCountAfterHit: Int,
    val isSelfVoice: Boolean = true,
    val similarity: Float? = null
)

data class MonitorSettings(
    val pushAlertEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val cooldownSeconds: Int = 3,
    val micBoost: Boolean = false,
    val nearVoiceFilter: Boolean = false,
    val voiceprintEnabled: Boolean = true,
    val voiceprintThreshold: Float = 0.48f
)

data class VoiceprintProfile(
    val enrolledVectors: List<FloatArray> = emptyList(),
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
    val isRunning: Boolean = false,
    val isMicPausedForEnroll: Boolean = false,
    val statusMessage: String = "待机中 · 点击下方开启守护",
    val livePreviewText: String = "",
    val micLevel: Float = 0f,
    val lastSimilarityScore: Float? = null,
    val lastSimilarityWasSelf: Boolean? = null,
    val lastEventPulseType: PulseEventType = PulseEventType.NONE,
    val lastEventTimestampMs: Long = 0L,
    val todayFilteredOthersCount: Int = 0,
    val voiceprintProfile: VoiceprintProfile = VoiceprintProfile(),
    val keywords: List<KeywordItem> = emptyList(),
    val recentHits: List<HitRecord> = emptyList(),
    val settings: MonitorSettings = MonitorSettings()
)

object MonitorStateStore {
    private const val PREFS_NAME = "catchphrase_guard_prefs"
    private const val KEY_KEYWORDS = "keywords_json"
    private const val KEY_HITS = "hits_json"
    private const val KEY_SETTINGS = "settings_json"
    private const val KEY_VOICEPRINT = "voiceprint_json"
    private const val KEY_STAT_DATE = "stat_date"
    private const val KEY_FILTERED_TODAY = "filtered_others_today"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    @Volatile
    private var initialized = false
    private lateinit var prefs: SharedPreferences

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) {
            rollOverDateIfNeeded()
            return
        }
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_STAT_DATE, null)

        var keywords = loadKeywords()
        var filteredToday = prefs.getInt(KEY_FILTERED_TODAY, 0)
        if (savedDate != null && savedDate != today) {
            keywords = keywords.map { it.copy(todayCount = 0) }
            filteredToday = 0
            saveKeywords(keywords)
            prefs.edit().putInt(KEY_FILTERED_TODAY, 0).apply()
        }
        prefs.edit().putString(KEY_STAT_DATE, today).apply()

        val hits = loadHits()
        val settings = loadSettings()
        val vpProfile = loadVoiceprintProfile()

        _uiState.update { current ->
            current.copy(
                keywords = keywords,
                recentHits = hits,
                settings = settings,
                voiceprintProfile = vpProfile,
                todayFilteredOthersCount = filteredToday
            )
        }
        initialized = true
    }

    @Synchronized
    fun rollOverDateIfNeeded() {
        if (!initialized) return
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_STAT_DATE, null)
        if (savedDate != today) {
            val resetKeywords = _uiState.value.keywords.map { it.copy(todayCount = 0) }
            saveKeywords(resetKeywords)
            prefs.edit()
                .putString(KEY_STAT_DATE, today)
                .putInt(KEY_FILTERED_TODAY, 0)
                .apply()
            _uiState.update {
                it.copy(
                    keywords = resetKeywords,
                    todayFilteredOthersCount = 0
                )
            }
        }
    }

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
                statusMessage = if (paused) "声纹采集中（后台监听已暂时让位）" else "正在后台静默监听中",
                micLevel = if (paused) 0f else it.micLevel
            )
        }
    }

    fun updateLivePreview(text: String, micLevel: Float) {
        _uiState.update {
            it.copy(
                livePreviewText = text,
                micLevel = micLevel
            )
        }
    }

    @Synchronized
    fun saveEnrolledVoiceprint(stepEmbeddings: List<FloatArray>) {
        val referenceSet = VoiceprintManager.buildEnrolledReferenceSet(stepEmbeddings)
        val profile = VoiceprintProfile(
            enrolledVectors = referenceSet,
            sampleCount = stepEmbeddings.size,
            updatedAtText = LocalDateTime.now().format(dateTimeFormatter)
        )
        persistVoiceprintProfile(profile)
        val updatedSettings = _uiState.value.settings.copy(voiceprintEnabled = true)
        saveSettings(updatedSettings)
        _uiState.update {
            it.copy(
                voiceprintProfile = profile,
                settings = updatedSettings
            )
        }
    }

    @Synchronized
    fun clearVoiceprint() {
        val emptyProfile = VoiceprintProfile()
        persistVoiceprintProfile(emptyProfile)
        _uiState.update {
            it.copy(
                voiceprintProfile = emptyProfile,
                lastSimilarityScore = null,
                lastSimilarityWasSelf = null
            )
        }
    }

    @Synchronized
    fun addKeyword(rawWord: String): Boolean {
        val cleaned = rawWord.trim()
        if (cleaned.isEmpty()) return false
        val current = _uiState.value.keywords
        if (current.any { it.word == cleaned }) return false
        val updated = current + KeywordItem(word = cleaned, enabled = true)
        saveKeywords(updated)
        _uiState.update { it.copy(keywords = updated) }
        return true
    }

    @Synchronized
    fun toggleKeyword(word: String, enabled: Boolean) {
        val updated = _uiState.value.keywords.map {
            if (it.word == word) it.copy(enabled = enabled) else it
        }
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
    fun resetTodayCounts() {
        val updated = _uiState.value.keywords.map { it.copy(todayCount = 0) }
        saveKeywords(updated)
        saveHits(emptyList())
        prefs.edit().putInt(KEY_FILTERED_TODAY, 0).apply()
        _uiState.update {
            it.copy(
                keywords = updated,
                recentHits = emptyList(),
                todayFilteredOthersCount = 0
            )
        }
    }

    @Synchronized
    fun updateSettings(transform: (MonitorSettings) -> MonitorSettings) {
        val updated = transform(_uiState.value.settings)
        saveSettings(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    @Synchronized
    fun recordSelfKeywordHit(word: String, fullUtterance: String, similarity: Float?): HitRecord {
        rollOverDateIfNeeded()
        var newTodayCount = 1
        val updatedKeywords = _uiState.value.keywords.map { item ->
            if (item.word == word) {
                newTodayCount = item.todayCount + 1
                item.copy(
                    todayCount = newTodayCount,
                    totalCount = item.totalCount + 1
                )
            } else {
                item
            }
        }
        val snippet = formatSnippet(fullUtterance)
        val nowMs = System.currentTimeMillis()
        val record = HitRecord(
            id = nowMs,
            time = LocalTime.now().format(timeFormatter),
            word = word,
            snippet = snippet,
            todayCountAfterHit = newTodayCount,
            isSelfVoice = true,
            similarity = similarity
        )
        val updatedHits = (listOf(record) + _uiState.value.recentHits).take(50)
        saveKeywords(updatedKeywords)
        saveHits(updatedHits)
        _uiState.update {
            it.copy(
                keywords = updatedKeywords,
                recentHits = updatedHits,
                lastSimilarityScore = similarity,
                lastSimilarityWasSelf = true,
                lastEventPulseType = PulseEventType.SELF_HIT,
                lastEventTimestampMs = nowMs
            )
        }
        return record
    }

    @Synchronized
    fun recordFilteredOtherVoice(word: String, fullUtterance: String, similarity: Float): HitRecord {
        rollOverDateIfNeeded()
        val currentWordCount = _uiState.value.keywords.firstOrNull { it.word == word }?.todayCount ?: 0
        val newFilteredCount = _uiState.value.todayFilteredOthersCount + 1
        prefs.edit().putInt(KEY_FILTERED_TODAY, newFilteredCount).apply()

        val snippet = formatSnippet(fullUtterance)
        val nowMs = System.currentTimeMillis()
        val record = HitRecord(
            id = nowMs,
            time = LocalTime.now().format(timeFormatter),
            word = word,
            snippet = snippet,
            todayCountAfterHit = currentWordCount,
            isSelfVoice = false,
            similarity = similarity
        )
        val updatedHits = (listOf(record) + _uiState.value.recentHits).take(50)
        saveHits(updatedHits)
        _uiState.update {
            it.copy(
                recentHits = updatedHits,
                todayFilteredOthersCount = newFilteredCount,
                lastSimilarityScore = similarity,
                lastSimilarityWasSelf = false,
                lastEventPulseType = PulseEventType.OTHER_FILTERED,
                lastEventTimestampMs = nowMs
            )
        }
        return record
    }

    private fun formatSnippet(fullUtterance: String): String {
        return if (fullUtterance.length > 26) {
            "…" + fullUtterance.takeLast(26)
        } else {
            fullUtterance
        }
    }

    private fun loadKeywords(): List<KeywordItem> {
        val raw = prefs.getString(KEY_KEYWORDS, null)
        if (raw.isNullOrBlank()) {
            val defaults = listOf(
                KeywordItem(word = "没有", enabled = true),
                KeywordItem(word = "不是", enabled = true)
            )
            saveKeywords(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val w = obj.optString("word").trim()
                    if (w.isNotEmpty()) {
                        add(
                            KeywordItem(
                                word = w,
                                enabled = obj.optBoolean("enabled", true),
                                todayCount = obj.optInt("todayCount", 0),
                                totalCount = obj.optInt("totalCount", 0)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            listOf(
                KeywordItem(word = "没有", enabled = true),
                KeywordItem(word = "不是", enabled = true)
            )
        }
    }

    private fun saveKeywords(list: List<KeywordItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("word", item.word)
                    put("enabled", item.enabled)
                    put("todayCount", item.todayCount)
                    put("totalCount", item.totalCount)
                }
            )
        }
        prefs.edit().putString(KEY_KEYWORDS, arr.toString()).apply()
    }

    private fun loadHits(): List<HitRecord> {
        val raw = prefs.getString(KEY_HITS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val hasSim = obj.has("similarity") && !obj.isNull("similarity")
                    add(
                        HitRecord(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            time = obj.optString("time", ""),
                            word = obj.optString("word", ""),
                            snippet = obj.optString("snippet", ""),
                            todayCountAfterHit = obj.optInt("todayCountAfterHit", 0),
                            isSelfVoice = obj.optBoolean("isSelfVoice", true),
                            similarity = if (hasSim) obj.optDouble("similarity", 0.0).toFloat() else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHits(list: List<HitRecord>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("time", item.time)
                    put("word", item.word)
                    put("snippet", item.snippet)
                    put("todayCountAfterHit", item.todayCountAfterHit)
                    put("isSelfVoice", item.isSelfVoice)
                    if (item.similarity != null) {
                        put("similarity", item.similarity.toDouble())
                    }
                }
            )
        }
        prefs.edit().putString(KEY_HITS, arr.toString()).apply()
    }

    private fun loadSettings(): MonitorSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return MonitorSettings()
        return try {
            val obj = JSONObject(raw)
            MonitorSettings(
                pushAlertEnabled = obj.optBoolean("pushAlertEnabled", true),
                vibrateEnabled = obj.optBoolean("vibrateEnabled", true),
                cooldownSeconds = obj.optInt("cooldownSeconds", 3).coerceIn(1, 30),
                micBoost = obj.optBoolean("micBoost", false),
                nearVoiceFilter = obj.optBoolean("nearVoiceFilter", false),
                voiceprintEnabled = obj.optBoolean("voiceprintEnabled", true),
                voiceprintThreshold = obj.optDouble("voiceprintThreshold", 0.48).toFloat().coerceIn(0.30f, 0.75f)
            )
        } catch (_: Exception) {
            MonitorSettings()
        }
    }

    private fun saveSettings(s: MonitorSettings) {
        val obj = JSONObject().apply {
            put("pushAlertEnabled", s.pushAlertEnabled)
            put("vibrateEnabled", s.vibrateEnabled)
            put("cooldownSeconds", s.cooldownSeconds)
            put("micBoost", s.micBoost)
            put("nearVoiceFilter", s.nearVoiceFilter)
            put("voiceprintEnabled", s.voiceprintEnabled)
            put("voiceprintThreshold", s.voiceprintThreshold.toDouble())
        }
        prefs.edit().putString(KEY_SETTINGS, obj.toString()).apply()
    }

    private fun loadVoiceprintProfile(): VoiceprintProfile {
        val raw = prefs.getString(KEY_VOICEPRINT, null) ?: return VoiceprintProfile()
        return try {
            val obj = JSONObject(raw)
            val sampleCount = obj.optInt("sampleCount", 0)
            val updatedAt = obj.optString("updatedAtText", "")
            val vecArr = obj.optJSONArray("vectors") ?: return VoiceprintProfile()
            val vectors = ArrayList<FloatArray>(vecArr.length())
            for (i in 0 until vecArr.length()) {
                val inner = vecArr.getJSONArray(i)
                val vec = FloatArray(inner.length())
                for (j in 0 until inner.length()) {
                    vec[j] = inner.getDouble(j).toFloat()
                }
                if (vec.isNotEmpty()) {
                    vectors.add(vec)
                }
            }
            VoiceprintProfile(
                enrolledVectors = vectors,
                sampleCount = sampleCount,
                updatedAtText = updatedAt
            )
        } catch (_: Exception) {
            VoiceprintProfile()
        }
    }

    private fun persistVoiceprintProfile(profile: VoiceprintProfile) {
        if (!profile.isEnrolled) {
            prefs.edit().remove(KEY_VOICEPRINT).apply()
            return
        }
        val vecArr = JSONArray()
        for (vec in profile.enrolledVectors) {
            val inner = JSONArray()
            for (v in vec) {
                inner.put(v.toDouble())
            }
            vecArr.put(inner)
        }
        val obj = JSONObject().apply {
            put("sampleCount", profile.sampleCount)
            put("updatedAtText", profile.updatedAtText)
            put("vectors", vecArr)
        }
        prefs.edit().putString(KEY_VOICEPRINT, obj.toString()).apply()
    }
}
