package com.selfuse.catchphraseguard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Accent taken from the launcher icon. */
private val Coral = Color(0xFFE8603C)
private val CoralLight = Color(0xFFFFB59F)

private val LightNeutralScheme: ColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF5F5E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E2E0),
    onSecondaryContainer = Color(0xFF1C1B1B),
    tertiary = Color(0xFF5F5E5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E2E0),
    onTertiaryContainer = Color(0xFF1C1B1B),
    background = Color(0xFFFCFBFA),
    onBackground = Color(0xFF1C1B1B),
    surface = Color(0xFFFCFBFA),
    onSurface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFFE9E6E4),
    onSurfaceVariant = Color(0xFF5C5957),
    outline = Color(0xFF8D8986),
    outlineVariant = Color(0xFFD3CFCC),
    surfaceTint = Coral,
    surfaceBright = Color(0xFFFCFBFA),
    surfaceDim = Color(0xFFDCD9D8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4F3),
    surfaceContainer = Color(0xFFF1EEED),
    surfaceContainerHigh = Color(0xFFEBE8E7),
    surfaceContainerHighest = Color(0xFFE5E2E1)
)

private val DarkNeutralScheme: ColorScheme = darkColorScheme(
    primary = CoralLight,
    onPrimary = Color(0xFF5E1500),
    primaryContainer = Color(0xFF85260A),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFC9C6C4),
    onSecondary = Color(0xFF313030),
    secondaryContainer = Color(0xFF474645),
    onSecondaryContainer = Color(0xFFE6E2E0),
    tertiary = Color(0xFFC9C6C4),
    onTertiary = Color(0xFF313030),
    tertiaryContainer = Color(0xFF474645),
    onTertiaryContainer = Color(0xFFE6E2E0),
    background = Color(0xFF141313),
    onBackground = Color(0xFFE6E2E0),
    surface = Color(0xFF141313),
    onSurface = Color(0xFFE6E2E0),
    surfaceVariant = Color(0xFF474543),
    onSurfaceVariant = Color(0xFFC9C5C2),
    outline = Color(0xFF928F8C),
    outlineVariant = Color(0xFF474543),
    surfaceTint = CoralLight,
    surfaceBright = Color(0xFF3A3938),
    surfaceDim = Color(0xFF141313),
    surfaceContainerLowest = Color(0xFF0F0E0E),
    surfaceContainerLow = Color(0xFF1C1B1B),
    surfaceContainer = Color(0xFF201F1F),
    surfaceContainerHigh = Color(0xFF2B2A29),
    surfaceContainerHighest = Color(0xFF363434)
)

@Composable
fun CatchphraseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkNeutralScheme
        else -> LightNeutralScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

/** Tabular (fixed-width) digits so counters don't jitter when they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Level 1 of the three text levels: the huge number. */
val BigNumberStyle: TextStyle = TextStyle(
    fontSize = 112.sp,
    lineHeight = 120.sp,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = "tnum"
)

/** Medium-size numbers inside cards/summary. */
val MediumNumberStyle: TextStyle = TextStyle(
    fontSize = 44.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = "tnum"
)
