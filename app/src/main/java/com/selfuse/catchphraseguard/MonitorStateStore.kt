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
    val todayCountAfterHit: Int
)

data class MonitorSettings(
    val pushAlertEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val cooldownSeconds: Int = 3,
    val micBoost: Boolean = false,
    val nearVoiceFilter: Boolean = false
)

data class MonitorUiState(
    val isRunning: Boolean = false,
    val statusMessage: String = "未开启监听",
    val livePreviewText: String = "",
    val micLevel: Float = 0f,
    val keywords: List<KeywordItem> = emptyList(),
    val recentHits: List<HitRecord> = emptyList(),
    val settings: MonitorSettings = MonitorSettings()
)

object MonitorStateStore {
    private const val PREFS_NAME = "catchphrase_guard_prefs"
    private const val KEY_KEYWORDS = "keywords_json"
    private const val KEY_HITS = "hits_json"
    private const val KEY_SETTINGS = "settings_json"
    private const val KEY_STAT_DATE = "stat_date"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

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
        if (savedDate != null && savedDate != today) {
            keywords = keywords.map { it.copy(todayCount = 0) }
            saveKeywords(keywords)
        }
        prefs.edit().putString(KEY_STAT_DATE, today).apply()

        val hits = loadHits()
        val settings = loadSettings()

        _uiState.update { current ->
            current.copy(
                keywords = keywords,
                recentHits = hits,
                settings = settings
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
            prefs.edit().putString(KEY_STAT_DATE, today).apply()
            _uiState.update { it.copy(keywords = resetKeywords) }
        }
    }

    fun setRunningState(running: Boolean, statusMessage: String) {
        _uiState.update {
            it.copy(
                isRunning = running,
                statusMessage = statusMessage,
                livePreviewText = if (running) it.livePreviewText else "",
                micLevel = if (running) it.micLevel else 0f
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
        _uiState.update { it.copy(keywords = updated, recentHits = emptyList()) }
    }

    @Synchronized
    fun updateSettings(transform: (MonitorSettings) -> MonitorSettings) {
        val updated = transform(_uiState.value.settings)
        saveSettings(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    @Synchronized
    fun recordKeywordHit(word: String, fullUtterance: String): HitRecord {
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
        val snippet = if (fullUtterance.length > 24) {
            "…" + fullUtterance.takeLast(24)
        } else {
            fullUtterance
        }
        val record = HitRecord(
            id = System.currentTimeMillis(),
            time = LocalTime.now().format(timeFormatter),
            word = word,
            snippet = snippet,
            todayCountAfterHit = newTodayCount
        )
        val updatedHits = (listOf(record) + _uiState.value.recentHits).take(30)
        saveKeywords(updatedKeywords)
        saveHits(updatedHits)
        _uiState.update {
            it.copy(
                keywords = updatedKeywords,
                recentHits = updatedHits
            )
        }
        return record
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
                    add(
                        HitRecord(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            time = obj.optString("time", ""),
                            word = obj.optString("word", ""),
                            snippet = obj.optString("snippet", ""),
                            todayCountAfterHit = obj.optInt("todayCountAfterHit", 1)
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
                nearVoiceFilter = obj.optBoolean("nearVoiceFilter", false)
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
        }
        prefs.edit().putString(KEY_SETTINGS, obj.toString()).apply()
    }
}
