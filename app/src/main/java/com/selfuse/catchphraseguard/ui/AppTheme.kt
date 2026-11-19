package com.selfuse.catchphraseguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class OriginPalette(
    val isDark: Boolean,
    val backgroundBrush: Brush,
    val cardSurface: Color,
    val cardSubtleSurface: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlue: Color,
    val accentCyan: Color,
    val shieldEmerald: Color,
    val shieldContainer: Color,
    val alertCoral: Color,
    val alertContainer: Color,
    val warningAmber: Color,
    val warningContainer: Color,
    val navBarSurface: Color
)

private val LightOriginPalette = OriginPalette(
    isDark = false,
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE6F3FF),
            Color(0xFFF3F8FD),
            Color(0xFFF8FAFC)
        )
    ),
    cardSurface = Color(0xF5FFFFFF),
    cardSubtleSurface = Color(0xFFF1F5F9),
    cardBorder = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    accentBlue = Color(0xFF0284C7),
    accentCyan = Color(0xFF06B6D4),
    shieldEmerald = Color(0xFF059669),
    shieldContainer = Color(0xFFECFDF5),
    alertCoral = Color(0xFFE11D48),
    alertContainer = Color(0xFFFFF1F2),
    warningAmber = Color(0xFFD97706),
    warningContainer = Color(0xFFFFFBEB),
    navBarSurface = Color(0xFAFFFFFF)
)

private val DarkOriginPalette = OriginPalette(
    isDark = true,
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B1324),
            Color(0xFF0F172A),
            Color(0xFF111827)
        )
    ),
    cardSurface = Color(0xEB1E293B),
    cardSubtleSurface = Color(0xFF0F172A),
    cardBorder = Color(0xFF334155),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF64748B),
    accentBlue = Color(0xFF38BDF8),
    accentCyan = Color(0xFF22D3EE),
    shieldEmerald = Color(0xFF34D399),
    shieldContainer = Color(0xFF064E3B),
    alertCoral = Color(0xFFFB7185),
    alertContainer = Color(0xFF4C0519),
    warningAmber = Color(0xFFFBBF24),
    warningContainer = Color(0xFF451A03),
    navBarSurface = Color(0xF51E293B)
)

val LocalOriginPalette = staticCompositionLocalOf { LightOriginPalette }

private val LightM3Scheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

private val DarkM3Scheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC)
)

@Composable
fun CatchphraseOriginTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkOriginPalette else LightOriginPalette
    val m3Scheme = if (darkTheme) DarkM3Scheme else LightM3Scheme

    CompositionLocalProvider(LocalOriginPalette provides palette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}

@Composable
fun OriginGlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalOriginPalette.current.cardSurface,
    borderColor: Color = LocalOriginPalette.current.cardBorder,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun OriginPillBadge(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
fun MicCanvasIcon(
    tint: Color,
    size: Dp = 26.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.09f

        // Mic capsule
        val capsuleW = w * 0.28f
        val capsuleH = h * 0.46f
        drawRoundRect(
            color = tint,
            topLeft = Offset((w - capsuleW) / 2f, h * 0.10f),
            size = Size(capsuleW, capsuleH),
            cornerRadius = CornerRadius(capsuleW / 2f, capsuleW / 2f)
        )

        // U-arc cradle
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.26f),
            size = Size(w * 0.52f, h * 0.46f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Stem + Base
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.88f),
            end = Offset(w * 0.66f, h * 0.88f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ShieldCanvasIcon(
    tint: Color,
    size: Dp = 20.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.50f)
            cubicTo(
                w * 0.86f, h * 0.74f,
                w * 0.68f, h * 0.88f,
                w * 0.5f, h * 0.94f
            )
            cubicTo(
                w * 0.32f, h * 0.88f,
                w * 0.14f, h * 0.74f,
                w * 0.14f, h * 0.50f
            )
            lineTo(w * 0.14f, h * 0.22f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = w * 0.095f, cap = StrokeCap.Round)
        )
        // Checkmark inside shield
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.51f),
            end = Offset(w * 0.47f, h * 0.62f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.47f, h * 0.62f),
            end = Offset(w * 0.66f, h * 0.41f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun WaveBarsIcon(
    tint: Color,
    level: Float = 0.4f,
    size: Dp = 22.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.11f
        val baseRatios = floatArrayOf(0.32f, 0.68f, 0.92f, 0.58f, 0.36f)
        val boost = level.coerceIn(0.15f, 1f)
        for (i in baseRatios.indices) {
            val x = w * (0.14f + i * 0.18f)
            val barH = (h * baseRatios[i] * (0.45f + 0.55f * boost)).coerceIn(h * 0.18f, h * 0.92f)
            drawLine(
                color = tint,
                start = Offset(x, (h - barH) / 2f),
                end = Offset(x, (h + barH) / 2f),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }
    }
}
