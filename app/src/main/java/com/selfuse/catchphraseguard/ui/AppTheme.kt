package com.selfuse.catchphraseguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.PulseEventType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class VinylPalette(
    val baseObsidian: Color = Color(0xFF0B0C10),
    val cardSurface: Color = Color(0xFF181920),
    val cardElevated: Color = Color(0xFF21232C),
    val cardRecessed: Color = Color(0xFF121318),
    val cardBorder: Color = Color(0xFF2B2D38),
    val neteaseRed: Color = Color(0xFFEC4141),
    val neteaseRedBright: Color = Color(0xFFFF5C5C),
    val neteaseRedContainer: Color = Color(0xFF33161A),
    val vinylGold: Color = Color(0xFFE6C17A),
    val vinylGoldDeep: Color = Color(0xFF9E7B42),
    val vinylGoldContainer: Color = Color(0xFF2B2218),
    val shieldEmerald: Color = Color(0xFF22C55E),
    val shieldContainer: Color = Color(0xFF132A1E),
    val textPrimary: Color = Color(0xFFF6F7FB),
    val textSecondary: Color = Color(0xFFA2A6B8),
    val textMuted: Color = Color(0xFF5E6274),
    val bottomBarSurface: Color = Color(0xF2121318)
)

val LocalVinylPalette = staticCompositionLocalOf { VinylPalette() }

private val NetEaseDarkM3Scheme = darkColorScheme(
    primary = Color(0xFFEC4141),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B181C),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE6C17A),
    background = Color(0xFF0B0C10),
    surface = Color(0xFF181920),
    onSurface = Color(0xFFF6F7FB)
)

@Composable
fun CatchphraseOriginTheme(
    content: @Composable () -> Unit
) {
    val palette = remember { VinylPalette() }
    CompositionLocalProvider(LocalVinylPalette provides palette) {
        MaterialTheme(
            colorScheme = NetEaseDarkM3Scheme,
            content = content
        )
    }
}

/**
 * Deep NetEase Cloud Music dark crimson-to-obsidian atmospheric backdrop.
 */
@Composable
fun VinylAmbientBackdrop(
    isRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalVinylPalette.current
    val infinite = rememberInfiniteTransition(label = "vinylBackdrop")
    val glowPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.baseObsidian)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaScale = if (isRunning) (0.85f + 0.25f * glowPulse) else 0.55f

            // Top crimson vinyl stage glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4B161B).copy(alpha = 0.72f * alphaScale),
                        Color(0xFF281116).copy(alpha = 0.40f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.48f, h * 0.22f),
                    radius = w * 0.95f
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.48f, h * 0.22f)
            )

            // Subtle warm bronze-gold accent aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3B2818).copy(alpha = 0.36f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.82f, h * 0.45f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.82f, h * 0.45f)
            )
        }
    }
}

@Composable
fun VinylSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalVinylPalette.current.cardSurface,
    borderColor: Color = LocalVinylPalette.current.cardBorder,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(cornerRadius),
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
fun VinylPillBadge(
    backgroundColor: Color,
    borderColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val baseMod = modifier
        .clip(shape)
        .background(backgroundColor)
        .then(
            if (borderColor != null) Modifier.border(0.8.dp, borderColor, shape) else Modifier
        )
        .padding(horizontal = 10.dp, vertical = 4.dp)

    Box(modifier = baseMod, contentAlignment = Alignment.Center) {
        content()
    }
}

/**
 * Custom NetEase-style warm crimson & champagne-cream capsule toggle (zero native Material Switch!).
 */
@Composable
fun NetEaseCapsuleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVinylPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF7C322E) else Color(0xFF282A35),
        animationSpec = tween(durationMillis = 200),
        label = "toggleTrack"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) palette.neteaseRed.copy(alpha = 0.7f) else Color(0xFF383B4A),
        animationSpec = tween(durationMillis = 200),
        label = "toggleBorder"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color(0xFFFFDFC4) else Color(0xFF666A7C),
        animationSpec = tween(durationMillis = 200),
        label = "toggleThumb"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "toggleOffset"
    )

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .width(46.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(19.dp)
                .shadow(elevation = if (checked) 4.dp else 0.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

/**
 * Signature NetEase Vinyl Record Disc + Whale-Cloud Harmonic Rings + Pivoting Metallic S-Tonearm!
 */
@Composable
fun VinylTurntableStage(
    isRunning: Boolean,
    micLevel: Float,
    flashType: PulseEventType,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVinylPalette.current

    // Continuous angle accumulator that pauses smoothly in-place without snapping to 0°!
    var discAngle by remember { mutableFloatStateOf(22f) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastFrameMs = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val nowMs = withInfiniteAnimationFrameMillis { it }
                val deltaMs = (nowMs - lastFrameMs).coerceIn(0L, 64L)
                lastFrameMs = nowMs
                // ~33 degrees per second (~11s per full revolution)
                discAngle = (discAngle + deltaMs * 0.033f) % 360f
            }
        }
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isRunning) 0f else -27f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "tonearmSwing"
    )

    val infinite = rememberInfiniteTransition(label = "whaleWave")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "whalePhase"
    )

    val smoothedMic by animateFloatAsState(
        targetValue = if (isRunning) (micLevel * 7.5f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "vinylMic"
    )

    val ringPrimary by animateColorAsState(
        targetValue = when {
            !isRunning -> palette.vinylGold.copy(alpha = 0.16f)
            flashType == PulseEventType.SELF_HIT -> palette.neteaseRedBright
            flashType == PulseEventType.OTHER_FILTERED -> palette.shieldEmerald
            else -> palette.neteaseRed
        },
        animationSpec = tween(300),
        label = "ringPrimary"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(292.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
    ) {
        // 1) Whale-Cloud undulating harmonic rings + Realistic Black Vinyl Disc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.47f
            val cy = size.height * 0.53f
            val discCenter = Offset(cx, cy)
            val discRadius = size.minDimension * 0.37f

            // Ambient warm crimson-gold glow under vinyl
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringPrimary.copy(alpha = if (isRunning) 0.32f else 0.10f),
                        palette.vinylGold.copy(alpha = if (isRunning) 0.12f else 0.04f),
                        Color.Transparent
                    ),
                    center = discCenter,
                    radius = discRadius * 1.45f
                ),
                radius = discRadius * 1.45f,
                center = discCenter
            )

            // Whale-Cloud multi-harmonic deformed soundwave rings when active
            if (isRunning) {
                for (ringIdx in 0..2) {
                    val path = Path()
                    val steps = 90
                    val baseR = discRadius * (1.06f + ringIdx * 0.065f + 0.08f * smoothedMic)
                    val wobbleAmp = discRadius * (0.025f + 0.055f * smoothedMic) * (1f + ringIdx * 0.25f)
                    val phaseShift = wavePhase * (if (ringIdx % 2 == 0) 1f else -1.3f) + ringIdx * 1.1f

                    for (s in 0..steps) {
                        val theta = (s.toFloat() / steps) * (2.0 * PI).toFloat()
                        val deform = sin(3f * theta + phaseShift) * 0.6f +
                            cos(5f * theta - phaseShift * 1.4f) * 0.4f
                        val r = baseR + wobbleAmp * deform
                        val px = cx + r * cos(theta)
                        val py = cy + r * sin(theta)
                        if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()

                    val ringColor = if (ringIdx == 1) {
                        palette.vinylGold.copy(alpha = 0.55f)
                    } else {
                        ringPrimary.copy(alpha = if (ringIdx == 0) 0.78f else 0.36f)
                    }
                    drawPath(
                        path = path,
                        color = ringColor,
                        style = Stroke(width = (1.8f - ringIdx * 0.4f).dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = discRadius * 1.05f,
                    center = discCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Outer vinyl dark rim
            drawCircle(
                color = Color(0xFF1C1D24),
                radius = discRadius * 1.02f,
                center = discCenter
            )

            // Main obsidian vinyl disc
            drawCircle(
                color = Color(0xFF090A0E),
                radius = discRadius,
                center = discCenter
            )

            // Stationary anisotropic studio light sheen across vinyl grooves
            drawCircle(
                brush = Brush.sweepGradient(
                    0.00f to Color.White.copy(alpha = 0.02f),
                    0.12f to Color.White.copy(alpha = 0.16f),
                    0.22f to Color.White.copy(alpha = 0.02f),
                    0.50f to Color.White.copy(alpha = 0.01f),
                    0.62f to Color.White.copy(alpha = 0.14f),
                    0.72f to Color.White.copy(alpha = 0.02f),
                    1.00f to Color.White.copy(alpha = 0.02f),
                    center = discCenter
                ),
                radius = discRadius * 0.98f,
                center = discCenter
            )

            // Concentric vinyl micro-grooves
            for (g in 1..11) {
                val gr = discRadius * (0.42f + g * 0.048f)
                drawCircle(
                    color = Color.White.copy(alpha = if (g % 3 == 0) 0.07f else 0.035f),
                    radius = gr,
                    center = discCenter,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // Rotating Half-Gold / Half-Crimson Center Record Label (33 1/3 RPM!)
            val labelR = discRadius * 0.36f
            rotate(degrees = discAngle, pivot = discCenter) {
                drawArc(
                    color = palette.vinylGold,
                    startAngle = 135f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(cx - labelR, cy - labelR),
                    size = Size(labelR * 2f, labelR * 2f)
                )
                drawArc(
                    color = palette.neteaseRed,
                    startAngle = 315f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(cx - labelR, cy - labelR),
                    size = Size(labelR * 2f, labelR * 2f)
                )
                // Inner decorative gold ring on label
                drawCircle(
                    color = Color(0xFF181210).copy(alpha = 0.25f),
                    radius = labelR * 0.76f,
                    center = discCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Center spindle hole
            drawCircle(
                color = Color(0xFF1D1E26),
                radius = discRadius * 0.085f,
                center = discCenter
            )
            drawCircle(
                color = Color(0xFF090A0E),
                radius = discRadius * 0.052f,
                center = discCenter
            )
        }

        // 2) Pivoting Metallic Silver S-Curved Tonearm (anchored at top-right gimbal!)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.83f, 0.15f)
                    rotationZ = tonearmAngle
                }
        ) {
            val px = size.width * 0.83f
            val py = size.height * 0.15f
            val pivot = Offset(px, py)

            // Gimbal base shadow & machined metallic rings
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = 22.dp.toPx(),
                center = pivot + Offset(2.dp.toPx(), 3.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF2A2C36),
                radius = 20.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = Color(0xFF8E93A4),
                radius = 16.dp.toPx(),
                center = pivot,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFD8DCE6),
                radius = 9.dp.toPx(),
                center = pivot
            )

            // Counterweight stub above pivot
            drawLine(
                color = Color(0xFFB4B9C8),
                start = pivot,
                end = Offset(px + 8.dp.toPx(), py - 20.dp.toPx()),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )

            // S-curved tubular silver tonearm reaching down onto the vinyl groove
            val armPath = Path().apply {
                moveTo(px, py)
                cubicTo(
                    px - 6.dp.toPx(), py + 58.dp.toPx(),
                    px - 10.dp.toPx(), py + 115.dp.toPx(),
                    px - 44.dp.toPx(), py + 158.dp.toPx()
                )
            }
            // Drop shadow under arm
            val shadowPath = Path().apply {
                moveTo(px + 3.dp.toPx(), py + 4.dp.toPx())
                cubicTo(
                    px - 3.dp.toPx(), py + 62.dp.toPx(),
                    px - 7.dp.toPx(), py + 119.dp.toPx(),
                    px - 41.dp.toPx(), py + 162.dp.toPx()
                )
            }
            drawPath(
                path = shadowPath,
                color = Color.Black.copy(alpha = 0.45f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = armPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFEAECEF),
                        Color(0xFF9EA4B3),
                        Color(0xFFDADDE6)
                    )
                ),
                style = Stroke(width = 4.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Cartridge headshell at stylus tip
            val tipX = px - 44.dp.toPx()
            val tipY = py + 158.dp.toPx()
            rotate(degrees = 36f, pivot = Offset(tipX, tipY)) {
                drawRoundRect(
                    color = Color(0xFFD0D4DF),
                    topLeft = Offset(tipX - 6.dp.toPx(), tipY - 3.dp.toPx()),
                    size = Size(13.dp.toPx(), 21.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF23252E),
                    radius = 1.6.dp.toPx(),
                    center = Offset(tipX, tipY + 13.dp.toPx())
                )
            }
        }
    }
}

/**
 * Persistent NetEase Mini Vinyl Player Bar hovering above the bottom navigation tabs.
 */
@Composable
fun MiniVinylPlayerBar(
    uiState: MonitorUiState,
    onOpenTurntable: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVinylPalette.current
    val infinite = rememberInfiniteTransition(label = "miniVinylSpin")
    val spinDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "miniSpin"
    )

    val todayTotal = uiState.keywords.sumOf { it.todayCount }
    val displayLine = when {
        !uiState.isRunning -> "黑胶声呐待机中 · 轻触右侧开启守护"
        uiState.livePreviewText.isNotBlank() -> "正在监听：${uiState.livePreviewText}"
        else -> "正在静默守护中 · 今日已捕获 $todayTotal 次"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(54.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(27.dp))
            .clip(RoundedCornerShape(27.dp))
            .background(Color(0xF21E2029))
            .border(1.dp, Color(0xFF323544), RoundedCornerShape(27.dp))
            .clickable(onClick = onOpenTurntable)
            .padding(start = 8.dp, end = 12.dp)
    ) {
        // Spinning Mini Vinyl Disc
        Canvas(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    rotationZ = if (uiState.isRunning) spinDeg else 25f
                }
        ) {
            val c = this.center
            val r = size.minDimension / 2f
            drawCircle(color = Color(0xFF0A0B0F), radius = r, center = c)
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = r * 0.76f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
            val lr = r * 0.40f
            drawArc(
                color = palette.vinylGold,
                startAngle = 135f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(c.x - lr, c.y - lr),
                size = Size(lr * 2f, lr * 2f)
            )
            drawArc(
                color = palette.neteaseRed,
                startAngle = 315f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(c.x - lr, c.y - lr),
                size = Size(lr * 2f, lr * 2f)
            )
            drawCircle(color = Color(0xFF0A0B0F), radius = r * 0.11f, center = c)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = displayLine,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Coral-Red Circular Ring Play/Pause Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePlay)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = this.center
                val r = size.minDimension / 2f - 1.5.dp.toPx()
                drawCircle(
                    color = palette.neteaseRed,
                    radius = r,
                    center = c,
                    style = Stroke(width = 1.8.dp.toPx())
                )
                if (uiState.isRunning) {
                    val barH = size.height * 0.34f
                    val gap = size.width * 0.11f
                    drawLine(
                        color = Color.White,
                        start = Offset(c.x - gap, c.y - barH / 2f),
                        end = Offset(c.x - gap, c.y + barH / 2f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(c.x + gap, c.y - barH / 2f),
                        end = Offset(c.x + gap, c.y + barH / 2f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val playPath = Path().apply {
                        moveTo(c.x - 3.dp.toPx(), c.y - 5.dp.toPx())
                        lineTo(c.x + 5.5.dp.toPx(), c.y)
                        lineTo(c.x - 3.dp.toPx(), c.y + 5.dp.toPx())
                        close()
                    }
                    drawPath(path = playPath, color = Color.White)
                }
            }
        }
    }
}

/**
 * Red & Champagne-Gold Studio Symmetric Harmonic Waveform.
 */
@Composable
fun SymmetricHarmonicWaveform(
    level: Float,
    isActive: Boolean,
    accentColor: Color = LocalVinylPalette.current.neteaseRed,
    secondaryColor: Color = LocalVinylPalette.current.vinylGold,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "studioWave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "studioWavePhase"
    )

    val brush = Brush.horizontalGradient(
        colors = listOf(
            secondaryColor.copy(alpha = 0.45f),
            accentColor,
            secondaryColor,
            accentColor,
            secondaryColor.copy(alpha = 0.45f)
        )
    )

    Canvas(modifier = modifier) {
        val barCount = 35
        val centerIdx = barCount / 2f
        val stepX = size.width / (barCount + 1)
        val strokeW = (stepX * 0.42f).coerceIn(2.dp.toPx(), 3.8.dp.toPx())
        val amp = if (isActive) (level * 8.2f).coerceIn(0.16f, 1.0f) else 0.12f

        for (i in 0 until barCount) {
            val dist = abs(i - centerIdx) / centerIdx
            val env = (1f - dist * dist).coerceAtLeast(0.06f)
            val wave = if (isActive) {
                0.50f + 0.50f * abs(sin(phase + dist * 5.6f))
            } else {
                0.30f
            }
            val barH = (size.height * env * wave * (0.20f + 0.80f * amp))
                .coerceIn(size.height * 0.06f, size.height * 0.94f)
            val x = stepX * (i + 1)
            drawLine(
                brush = brush,
                start = Offset(x, (size.height - barH) / 2f),
                end = Offset(x, (size.height + barH) / 2f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

// --- Custom Music App Vector Icons ---

@Composable
fun VinylDiscTabIcon(tint: Color, selected: Boolean, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val c = this.center
        val r = this.size.minDimension * 0.45f
        if (selected) {
            drawCircle(color = tint, radius = r, center = c)
            drawCircle(color = Color(0xFF121318), radius = r * 0.34f, center = c)
            drawCircle(color = Color.White, radius = r * 0.12f, center = c)
        } else {
            drawCircle(color = tint, radius = r, center = c, style = Stroke(width = 1.8.dp.toPx()))
            drawCircle(color = tint, radius = r * 0.32f, center = c, style = Stroke(width = 1.4.dp.toPx()))
        }
    }
}

@Composable
fun ChartBarsTabIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.11f
        val heights = floatArrayOf(0.42f, 0.82f, 0.58f, 0.36f)
        for (i in heights.indices) {
            val x = w * (0.20f + i * 0.20f)
            val bh = h * heights[i]
            drawLine(
                color = tint,
                start = Offset(x, h * 0.86f - bh),
                end = Offset(x, h * 0.86f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FaderSlidersTabIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()
        val knobYs = floatArrayOf(0.36f, 0.64f, 0.42f)
        for (i in 0..2) {
            val x = w * (0.24f + i * 0.26f)
            drawLine(
                color = tint,
                start = Offset(x, h * 0.16f),
                end = Offset(x, h * 0.84f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = tint,
                radius = 2.6.dp.toPx(),
                center = Offset(x, h * knobYs[i])
            )
        }
    }
}

@Composable
fun MicCanvasIcon(tint: Color, size: Dp = 24.dp) {
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
fun ShieldCanvasIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.50f)
            cubicTo(w * 0.86f, h * 0.74f, w * 0.68f, h * 0.88f, w * 0.5f, h * 0.94f)
            cubicTo(w * 0.32f, h * 0.88f, w * 0.14f, h * 0.74f, w * 0.14f, h * 0.50f)
            lineTo(w * 0.14f, h * 0.22f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = w * 0.095f, cap = StrokeCap.Round))
        drawLine(color = tint, start = Offset(w * 0.36f, h * 0.51f), end = Offset(w * 0.47f, h * 0.62f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.47f, h * 0.62f), end = Offset(w * 0.66f, h * 0.41f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    }
}

@Composable
fun BellOutlineIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()
        val bell = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            cubicTo(w * 0.28f, h * 0.14f, w * 0.22f, h * 0.36f, w * 0.22f, h * 0.58f)
            lineTo(w * 0.14f, h * 0.72f)
            lineTo(w * 0.86f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.58f)
            cubicTo(w * 0.78f, h * 0.36f, w * 0.72f, h * 0.14f, w * 0.5f, h * 0.14f)
            close()
        }
        drawPath(path = bell, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.38f, h * 0.70f),
            size = Size(w * 0.24f, h * 0.18f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PlaylistDrawerIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.9.dp.toPx()
        for (i in 0..2) {
            val y = h * (0.26f + i * 0.24f)
            drawCircle(color = tint, radius = 1.5.dp.toPx(), center = Offset(w * 0.18f, y))
            drawLine(
                color = tint,
                start = Offset(w * 0.34f, y),
                end = Offset(w * 0.84f, y),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun HapticPulseIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.33f, h * 0.18f),
            size = Size(w * 0.34f, h * 0.64f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = sw)
        )
        drawLine(color = tint, start = Offset(w * 0.16f, h * 0.32f), end = Offset(w * 0.16f, h * 0.68f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.84f, h * 0.32f), end = Offset(w * 0.84f, h * 0.68f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun NoiseGateIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()
        val spk = Path().apply {
            moveTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.34f, h * 0.38f)
            lineTo(w * 0.54f, h * 0.20f)
            lineTo(w * 0.54f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.62f)
            lineTo(w * 0.18f, h * 0.62f)
            close()
        }
        drawPath(path = spk, color = tint, style = Stroke(width = sw))
        drawLine(color = tint, start = Offset(w * 0.66f, h * 0.38f), end = Offset(w * 0.84f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.84f, h * 0.38f), end = Offset(w * 0.66f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun FlameMiniIcon(tint: Color, size: Dp = 13.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.52f, h * 0.08f)
            cubicTo(w * 0.82f, h * 0.34f, w * 0.88f, h * 0.65f, w * 0.68f, h * 0.86f)
            cubicTo(w * 0.56f, h * 0.96f, w * 0.36f, h * 0.95f, w * 0.26f, h * 0.82f)
            cubicTo(w * 0.12f, h * 0.64f, w * 0.24f, h * 0.38f, w * 0.42f, h * 0.26f)
            cubicTo(w * 0.38f, h * 0.45f, w * 0.48f, h * 0.50f, w * 0.52f, h * 0.08f)
            close()
        }
        drawPath(path = p, color = tint)
    }
}
