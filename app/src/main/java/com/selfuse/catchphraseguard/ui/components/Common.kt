package com.selfuse.catchphraseguard.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Small shared composables
// ---------------------------------------------------------------------------

/** Group header used in settings and trend lists. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Returns a counter that increases every time the host lifecycle hits ON_RESUME.
 * Use it as a `remember` key to refresh permission/battery checks after returning
 * from system settings.
 */
@Composable
fun rememberResumeCount(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return count
}

/** Simple bar-chart glyph (material-icons-core has none). */
val BarChartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BarChartGlyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 9.2f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(10.6f, 5f)
            horizontalLineTo(13.4f)
            verticalLineTo(19f)
            horizontalLineTo(10.6f)
            close()
            moveTo(16.2f, 13f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(16.2f)
            close()
        }
    }.build()
}

// ---------------------------------------------------------------------------
// Permissions / system helpers
// ---------------------------------------------------------------------------

object SystemChecks {
    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Permissions we ask for before starting a session. */
    fun startPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        val ok = runCatching { context.startActivity(direct) }.isSuccess
        if (!ok) {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }
    }

    fun isVivoFamily(): Boolean {
        val m = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val b = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        return m.contains("vivo") || b.contains("vivo") || b.contains("iqoo")
    }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val mdhm = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    /** 00:23:41 */
    fun clock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    }

    /** "42 分钟", "1 小时 5 分钟", "不到 1 分钟" */
    fun duration(sec: Long): String {
        val minutes = sec / 60
        return when {
            minutes < 1 -> "不到 1 分钟"
            minutes < 60 -> "$minutes 分钟"
            minutes % 60 == 0L -> "${minutes / 60} 小时"
            else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
        }
    }

    /** One decimal, "—" when unknown. */
    fun rate(value: Float?): String =
        if (value == null) "—" else String.format(Locale.ROOT, "%.1f", value)

    /** Goal values step by 0.5: show "4" or "3.5". */
    fun goal(value: Float): String =
        if (abs(value - value.roundToInt()) < 0.01f) value.roundToInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)

    /** "比上次少 30%" / "比上周多 5%" / "和上次差不多"; null when unknown. */
    fun change(value: Float?, against: String): String? {
        if (value == null) return null
        val pct = (abs(value) * 100).roundToInt()
        return when {
            pct < 3 -> "和${against}差不多"
            value < 0 -> "比${against}少 $pct%"
            else -> "比${against}多 $pct%"
        }
    }

    fun timeOfDay(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hm)

    fun dateTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(mdhm)

    /** "约每 8 分钟 1 次", or null when there's nothing to say yet. */
    fun interval(monitoredMs: Long, count: Int): String? {
        if (count <= 0 || monitoredMs < 60_000L) return null
        val minutesPer = monitoredMs / 60_000f / count
        return if (minutesPer < 1f) {
            "约每分钟 ${String.format(Locale.ROOT, "%.0f", 1f / minutesPer)} 次"
        } else {
            "约每 ${minutesPer.roundToInt()} 分钟 1 次"
        }
    }

    fun durationOption(minutes: Int): String = when (minutes) {
        0 -> "直到我结束"
        30 -> "30 分钟"
        60 -> "1 小时"
        120 -> "2 小时"
        else -> if (minutes % 60 == 0) "${minutes / 60} 小时" else "$minutes 分钟"
    }

    val DURATION_OPTIONS = listOf(0, 30, 60, 120)
}
