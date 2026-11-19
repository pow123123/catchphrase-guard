package com.selfuse.catchphraseguard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Immutable
data class OriginPalette(
    val isDark: Boolean,
    val baseBackground: Color,
    val backgroundBrush: Brush,
    val cardSurface: Color,
    val cardSubtleSurface: Color,
    val cardBorder: Color,
    val glassRimBrush: Brush,
    val glassShadowColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlue: Color,
    val accentCyan: Color,
    val accentLavender: Color,
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
    baseBackground = Color(0xFFF2F7FD),
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEAF4FF),
            Color(0xFFF3F0FF),
            Color(0xFFF5FAFD)
        )
    ),
    cardSurface = Color(0xBDFFFFFF), // Frosted translucent liquid glass
    cardSubtleSurface = Color(0x94FFFFFF),
    cardBorder = Color(0xE6FFFFFF),
    glassRimBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0x88FFFFFF),
            Color(0xB3D8F3FF)
        )
    ),
    glassShadowColor = Color(0x2238BDF8),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF8899AE),
    accentBlue = Color(0xFF0284C7),
    accentCyan = Color(0xFF06B6D4),
    accentLavender = Color(0xFF818CF8),
    shieldEmerald = Color(0xFF059669),
    shieldContainer = Color(0xB8ECFDF5),
    alertCoral = Color(0xFFE11D48),
    alertContainer = Color(0xC4FFF1F2),
    warningAmber = Color(0xFFD97706),
    warningContainer = Color(0xC4FFFBEB),
    navBarSurface = Color(0xCCFFFFFF)
)

private val DarkOriginPalette = OriginPalette(
    isDark = true,
    baseBackground = Color(0xFF070D19),
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF081226),
            Color(0xFF10172E),
            Color(0xFF090F1D)
        )
    ),
    cardSurface = Color(0xBA18253D),
    cardSubtleSurface = Color(0x990F192C),
    cardBorder = Color(0x557DD3FC),
    glassRimBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x77E0F2FE),
            Color(0x2694A3B8),
            Color(0x55818CF8)
        )
    ),
    glassShadowColor = Color(0x400284C7),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF64748B),
    accentBlue = Color(0xFF38BDF8),
    accentCyan = Color(0xFF22D3EE),
    accentLavender = Color(0xFFA5B4FC),
    shieldEmerald = Color(0xFF34D399),
    shieldContainer = Color(0x88064E3B),
    alertCoral = Color(0xFFFB7185),
    alertContainer = Color(0x884C0519),
    warningAmber = Color(0xFFFBBF24),
    warningContainer = Color(0x88451A03),
    navBarSurface = Color(0xD9162238)
)

val LocalOriginPalette = staticCompositionLocalOf { LightOriginPalette }

private val LightM3Scheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    background = Color(0xFFF2F7FD),
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

private val DarkM3Scheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399),
    background = Color(0xFF070D19),
    surface = Color(0xFF18253D),
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

/**
 * Full-screen ethereal pastel mesh aurora canvas matching the Style A "Aura Liquid Glass" artwork.
 */
@Composable
fun AuraMeshBackdrop(modifier: Modifier = Modifier) {
    val palette = LocalOriginPalette.current
    val infinite = rememberInfiniteTransition(label = "auroraDrift")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftFloat"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.baseBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cyanBlob = if (palette.isDark) Color(0x440369A1) else Color(0x75BAE6FD)
            val lavenderBlob = if (palette.isDark) Color(0x3D4338CA) else Color(0x68DDD6FE)
            val mintBlob = if (palette.isDark) Color(0x330F766E) else Color(0x60CCFBF1)
            val blushBlob = if (palette.isDark) Color(0x299D174D) else Color(0x4DFCE7F3)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyanBlob, Color.Transparent),
                    center = Offset(w * (0.22f + 0.14f * drift), h * 0.16f),
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = Offset(w * (0.22f + 0.14f * drift), h * 0.16f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lavenderBlob, Color.Transparent),
                    center = Offset(w * (0.82f - 0.12f * drift), h * 0.36f),
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = Offset(w * (0.82f - 0.12f * drift), h * 0.36f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(mintBlob, Color.Transparent),
                    center = Offset(w * 0.25f, h * (0.72f - 0.08f * drift)),
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = Offset(w * 0.25f, h * (0.72f - 0.08f * drift))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blushBlob, Color.Transparent),
                    center = Offset(w * 0.75f, h * 0.84f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.75f, h * 0.84f)
            )
        }
    }
}

@Composable
fun OriginGlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalOriginPalette.current.cardSurface,
    borderColor: Color? = null,
    cornerRadius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalOriginPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val stroke = if (borderColor != null) {
        BorderStroke(1.2.dp, borderColor)
    } else {
        BorderStroke(1.2.dp, palette.glassRimBrush)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = stroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = palette.glassShadowColor,
            spotColor = palette.glassShadowColor
        )
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
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        content()
    }
}

/**
 * Center-mirrored fine-line symmetric harmonic waveform matching the Style A mockup.
 */
@Composable
fun SymmetricHarmonicWaveform(
    level: Float,
    isActive: Boolean,
    accentColor: Color = LocalOriginPalette.current.accentBlue,
    secondaryColor: Color = LocalOriginPalette.current.accentLavender,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "waveHarmonic")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val brush = Brush.horizontalGradient(
        colors = listOf(
            secondaryColor.copy(alpha = 0.45f),
            accentColor.copy(alpha = 0.92f),
            secondaryColor.copy(alpha = 0.45f)
        )
    )

    Canvas(modifier = modifier) {
        val barCount = 33
        val centerIdx = barCount / 2f
        val stepX = size.width / (barCount + 1)
        val strokeW = (stepX * 0.42f).coerceIn(2.dp.toPx(), 4.dp.toPx())
        val amp = if (isActive) (level * 7.8f).coerceIn(0.12f, 1.0f) else 0.08f

        for (i in 0 until barCount) {
            val distFromCenter = abs(i - centerIdx) / centerIdx // 0 at center, 1 at edge
            val envelope = (1f - distFromCenter * distFromCenter).coerceAtLeast(0.08f)
            val wave = if (isActive) {
                0.55f + 0.45f * abs(sin(phase + distFromCenter * 5.2f))
            } else {
                0.35f
            }
            val barHeight = (size.height * envelope * wave * (0.22f + 0.78f * amp))
                .coerceIn(size.height * 0.08f, size.height * 0.92f)

            val x = stepX * (i + 1)
            drawLine(
                brush = brush,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
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

        val capsuleW = w * 0.28f
        val capsuleH = h * 0.46f
        drawRoundRect(
            color = tint,
            topLeft = Offset((w - capsuleW) / 2f, h * 0.10f),
            size = Size(capsuleW, capsuleH),
            cornerRadius = CornerRadius(capsuleW / 2f, capsuleW / 2f)
        )

        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.26f),
            size = Size(w * 0.52f, h * 0.46f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

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
