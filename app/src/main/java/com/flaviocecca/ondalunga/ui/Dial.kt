package com.flaviocecca.ondalunga.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.flaviocecca.ondalunga.LocalSfx
import com.flaviocecca.ondalunga.Sound
import com.flaviocecca.ondalunga.game.Rules
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** What a finger on the device does: nothing, spin the wheel, or turn the needle. */
enum class DialMode { LOCKED, SPIN, AIM }

private const val ASPECT_RATIO = 1.35f

private val Zones = listOf(
    -2 to 2,
    -1 to 3,
    0 to 4,
    1 to 3,
    2 to 2,
)

// (x, y, size) in units of the casing radius, relative to the centre
private val Stars = listOf(
    Triple(-0.78f, 0.12f, 0.012f), Triple(-0.62f, 0.27f, 0.008f), Triple(-0.47f, 0.10f, 0.007f),
    Triple(-0.36f, 0.31f, 0.011f), Triple(-0.70f, 0.20f, 0.006f), Triple(0.33f, 0.09f, 0.008f),
    Triple(0.44f, 0.29f, 0.012f), Triple(0.58f, 0.15f, 0.007f), Triple(0.71f, 0.26f, 0.008f),
    Triple(0.82f, 0.10f, 0.011f), Triple(0.29f, 0.33f, 0.006f), Triple(-0.28f, 0.19f, 0.006f),
)

private val CasingLight = Color(0xFF3257B8)
private val CasingDark = Color(0xFF13245A)
private val Well = Color(0xFF0A1330)

private fun zoneColor(points: Int): Color = when (points) {
    4 -> Palette.Sky
    3 -> Palette.Ember
    else -> Palette.Sun
}

/** The device is a round casing cut flat below the centre; the wheel shows through its top half. */
private class DialGeometry(width: Float) {
    val radius = width * 0.485f
    val center = Offset(width / 2f, width * 0.5f)
    val cutY = center.y + radius * 0.4f
    val rimRadius = radius * 0.92f
    val faceRadius = radius * 0.8f
    val knobRadius = radius * 0.2f

    fun pointAt(position: Float, distance: Float): Offset {
        val radians = Math.toRadians(180.0 + position)
        return Offset(
            center.x + distance * cos(radians).toFloat(),
            center.y + distance * sin(radians).toFloat(),
        )
    }
}

/** Clockwise angle of [touch] around [center], in degrees. */
private fun angleAround(center: Offset, touch: Offset): Float =
    Math.toDegrees(atan2((touch.y - center.y).toDouble(), (touch.x - center.x).toDouble())).toFloat()

private fun positionAt(center: Offset, touch: Offset): Float {
    val degrees = -angleAround(center, touch)
    return when {
        degrees < -90f -> 0f
        degrees < 0f -> 180f
        else -> 180f - degrees
    }
}

/**
 * The game device. [target] and [guess] are degrees from the left edge (0..180).
 * The target is hidden behind a sliding screen unless [revealed]. In [DialMode.SPIN] the
 * wheel can be flicked around ([onSpinEnd] fires once it comes to rest); in [DialMode.AIM]
 * the needle follows the finger. With [showNeedle] off only the knob is drawn, so the
 * needle cannot sit on top of the target while the psychic studies it. [sideBet] draws an
 * arrow on the closed screen either side of the needle: the two answers to the opponents' bet.
 * A scripted demo turns the wheel through [spin] (extra degrees) and keeps the device [silent].
 */
@Composable
fun Dial(
    target: Float,
    guess: Float,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    mode: DialMode = DialMode.LOCKED,
    showNeedle: Boolean = true,
    sideBet: Boolean = false,
    spin: Float = 0f,
    silent: Boolean = false,
    onGuessChange: (Float) -> Unit = {},
    onSpinStart: () -> Unit = {},
    onSpinEnd: () -> Unit = {},
) {
    val cover by animateFloatAsState(if (revealed) 0f else 1f, tween(900), label = "cover")
    val betArrows by animateFloatAsState(if (sideBet) 1f else 0f, tween(350), label = "betArrows")
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val sfx = LocalSfx.current
    val scope = rememberCoroutineScope()
    val currentOnGuessChange by rememberUpdatedState(onGuessChange)
    val currentOnSpinStart by rememberUpdatedState(onSpinStart)
    val currentOnSpinEnd by rememberUpdatedState(onSpinEnd)

    var wheelAngle by remember { mutableFloatStateOf(0f) }
    var spinJob by remember { mutableStateOf<Job?>(null) }

    fun turnWheel(angle: Float) {
        if (floor(angle / 20f) != floor(wheelAngle / 20f)) {
            if (sfx?.haptics != false) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            sfx?.tick()
        }
        wheelAngle = angle
    }

    // the needle clicks softly as it passes each notch
    val notch = remember { intArrayOf(-1) }
    fun aim(position: Float) {
        val passed = (position / 6f).toInt()
        if (passed != notch[0]) {
            notch[0] = passed
            sfx?.tick(0.45f)
        }
        currentOnGuessChange(position)
    }

    val firstCover = remember { booleanArrayOf(true) }
    LaunchedEffect(revealed) {
        if (firstCover[0]) firstCover[0] = false else if (!silent) sfx?.play(Sound.SLIDE)
    }

    Canvas(
        // as large as the space it is given allows, in either direction
        modifier
            .widthIn(max = 520.dp)
            .aspectRatio(ASPECT_RATIO)
            .pointerInput(mode) {
                // read at each touch: the device changes size as the page around it does
                fun center() = DialGeometry(size.width.toFloat()).center
                when (mode) {
                    DialMode.LOCKED -> Unit

                    DialMode.AIM -> awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val center = center()
                        aim(positionAt(center, down.position))
                        drag(down.id) { change ->
                            change.consume()
                            aim(positionAt(center, change.position))
                        }
                    }

                    DialMode.SPIN -> awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val center = center()
                        spinJob?.cancel()
                        currentOnSpinStart()
                        var lastAngle = angleAround(center, down.position)
                        var lastTime = down.uptimeMillis
                        var velocity = 0f
                        drag(down.id) { change ->
                            change.consume()
                            val angle = angleAround(center, change.position)
                            var delta = angle - lastAngle
                            if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
                            val elapsed = (change.uptimeMillis - lastTime).coerceAtLeast(1)
                            velocity = velocity * 0.5f + delta / elapsed * 1000f * 0.5f
                            lastAngle = angle
                            lastTime = change.uptimeMillis
                            turnWheel(wheelAngle + delta)
                        }
                        // even a timid push sends the wheel round, so the outcome is never predictable
                        val launch = (if (velocity < 0f) -1f else 1f) * abs(velocity).coerceIn(500f, 3000f)
                        spinJob = scope.launch {
                            AnimationState(wheelAngle, launch)
                                .animateDecay(exponentialDecay(0.6f, 8f)) { turnWheel(value) }
                            currentOnSpinEnd()
                        }
                    }
                }
            }
    ) {
        val g = DialGeometry(size.width)
        drawCasing(g)
        clipRect(bottom = g.center.y) {
            drawWheel(g, wheelAngle + spin, target, measurer)
            drawScreen(g, cover)
            drawBetArrows(g, guess, betArrows * cover)
            // the casing lip casts a shadow on the wheel
            val lip = g.radius * 0.06f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    startY = g.center.y - lip, endY = g.center.y,
                ),
                topLeft = Offset(g.center.x - g.rimRadius, g.center.y - lip),
                size = Size(g.rimRadius * 2, lip),
            )
        }
        drawNeedle(g, guess, showNeedle)
    }
}

private fun DrawScope.drawCasing(g: DialGeometry) {
    val c = g.center
    val r = g.radius

    val ground = Offset(c.x, g.cutY)
    scale(1f, 0.09f, pivot = ground) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                center = ground, radius = r * 1.05f,
            ),
            r * 1.05f, ground,
        )
    }

    clipRect(bottom = g.cutY) {
        drawCircle(
            Brush.verticalGradient(listOf(CasingLight, CasingDark), startY = c.y - r, endY = g.cutY),
            r, c,
        )
        val bevel = r * 0.035f
        drawCircle(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                start = Offset(c.x - r * 0.7f, c.y - r * 0.7f),
                end = Offset(c.x + r * 0.7f, c.y + r * 0.7f),
            ),
            r - bevel / 2, c, style = Stroke(bevel),
        )
        // dark edge along the cut, as if the base had thickness
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                startY = g.cutY - r * 0.07f, endY = g.cutY,
            ),
            topLeft = Offset(c.x - r, g.cutY - r * 0.07f),
            size = Size(r * 2, r * 0.07f),
        )
    }

    for ((x, y, starSize) in Stars) {
        drawCircle(Palette.Cream.copy(alpha = 0.55f), r * starSize, Offset(c.x + x * r, c.y + y * r))
    }
    drawLine(
        Color.White.copy(alpha = 0.28f),
        Offset(c.x - r * 0.985f, c.y), Offset(c.x + r * 0.985f, c.y),
        strokeWidth = r * 0.012f,
    )
}

private fun DrawScope.drawWheel(g: DialGeometry, wheelAngle: Float, target: Float, measurer: TextMeasurer) {
    val c = g.center
    drawCircle(Well, g.rimRadius + g.radius * 0.025f, c)

    // knurled rim, rounded like the edge of a thick disc
    drawCircle(
        Brush.radialGradient(
            0.86f to Color(0xFF8F8878),
            0.935f to Color(0xFFFFFDF5),
            1f to Color(0xFF7F7869),
            center = c, radius = g.rimRadius,
        ),
        g.rimRadius, c,
    )
    for (tick in 0 until 60) {
        val radians = Math.toRadians((wheelAngle + tick * 6f).toDouble())
        val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
        if (direction.y > 0.05f) continue
        val major = tick % 5 == 0
        drawLine(
            Color.Black.copy(alpha = if (major) 0.55f else 0.28f),
            c + direction * (g.faceRadius * 1.035f),
            c + direction * (g.rimRadius * 0.985f),
            strokeWidth = g.radius * if (major) 0.014f else 0.008f,
        )
    }

    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF9EA), Palette.Cream, Color(0xFFDCCFAF)),
            center = c, radius = g.faceRadius,
        ),
        g.faceRadius, c,
    )

    val topLeft = Offset(c.x - g.faceRadius, c.y - g.faceRadius)
    val arcSize = Size(g.faceRadius * 2, g.faceRadius * 2)
    val labelStyle = TextStyle(
        color = Palette.Night,
        fontSize = (g.faceRadius * 0.085f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    for ((offset, points) in Zones) {
        val start = (target + (offset - 0.5f) * Rules.WEDGE).coerceIn(0f, 180f)
        val end = (target + (offset + 0.5f) * Rules.WEDGE).coerceIn(0f, 180f)
        if (end <= start) continue
        drawArc(
            zoneColor(points), 180f + start, end - start,
            useCenter = true, topLeft = topLeft, size = arcSize,
        )
        if (end - start >= Rules.WEDGE * 0.75f) {
            val label = measurer.measure(points.toString(), labelStyle)
            val at = g.pointAt((start + end) / 2f, g.faceRadius * 0.86f)
            drawText(
                label,
                topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f),
            )
        }
    }

    // the face sits lower than the rim: darken it towards the edge
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.12f),
            0.75f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.32f),
            center = c, radius = g.faceRadius,
        ),
        g.faceRadius, c,
    )
}

/** The sliding screen: [cover] 1 = fully closed, 0 = fully open (retracted to the left). */
private fun DrawScope.drawScreen(g: DialGeometry, cover: Float) {
    if (cover <= 0f) return
    val c = g.center
    val sweep = 180f * cover
    val topLeft = Offset(c.x - g.faceRadius, c.y - g.faceRadius)
    val arcSize = Size(g.faceRadius * 2, g.faceRadius * 2)

    if (cover < 1f) {
        drawArc(
            Color.Black.copy(alpha = 0.28f), 180f + sweep, 3.5f,
            useCenter = true, topLeft = topLeft, size = arcSize,
        )
    }
    drawArc(
        Brush.radialGradient(
            listOf(Color(0xFF57BCC8), Palette.Lagoon, Color(0xFF1C5E69)),
            center = c, radius = g.faceRadius,
        ),
        180f, sweep, useCenter = true, topLeft = topLeft, size = arcSize,
    )
    for (fraction in listOf(0.4f, 0.62f, 0.84f)) {
        val r = g.faceRadius * fraction
        val ringTopLeft = Offset(c.x - r, c.y - r)
        val ringSize = Size(r * 2, r * 2)
        val width = g.radius * 0.012f
        // engraved groove: dark line with a light one just inside it
        drawArc(Color.Black.copy(alpha = 0.22f), 180f, sweep, false, ringTopLeft, ringSize, style = Stroke(width))
        drawArc(
            Color.White.copy(alpha = 0.2f), 180f, sweep, false,
            ringTopLeft + Offset(width, width), Size(ringSize.width - width * 2, ringSize.height - width * 2),
            style = Stroke(width * 0.6f),
        )
    }
    // glossy plastic
    drawArc(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
            start = Offset(c.x - g.faceRadius * 0.7f, c.y - g.faceRadius),
            end = Offset(c.x, c.y - g.faceRadius * 0.2f),
        ),
        180f, sweep, useCenter = true, topLeft = topLeft, size = arcSize,
    )
    drawLine(
        Color.White.copy(alpha = 0.55f), c, g.pointAt(sweep, g.faceRadius),
        strokeWidth = g.radius * 0.01f,
    )
    // handle on the leading edge
    val handle = g.pointAt((sweep - 5f).coerceAtLeast(0f), g.faceRadius * 0.9f)
    val handleRadius = g.radius * 0.035f
    drawCircle(Color.Black.copy(alpha = 0.3f), handleRadius * 1.15f, handle + Offset(0f, handleRadius * 0.3f))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFBFEFF5), Palette.Lagoon),
            center = handle - Offset(handleRadius * 0.3f, handleRadius * 0.3f), radius = handleRadius * 1.3f,
        ),
        handleRadius, handle,
    )
}

/** Two arrows curving away from the needle, one towards each end of the spectrum. */
private fun DrawScope.drawBetArrows(g: DialGeometry, guess: Float, alpha: Float) {
    if (alpha <= 0f) return
    val r = g.faceRadius * 0.73f
    val head = g.faceRadius * 0.055f
    val stroke = Stroke(g.radius * 0.03f, cap = StrokeCap.Round)
    val topLeft = Offset(g.center.x - r, g.center.y - r)
    val arcSize = Size(r * 2, r * 2)
    val color = Palette.Cream.copy(alpha = 0.92f * alpha)
    for (dir in listOf(-1f, 1f)) {
        val from = guess + dir * 7f
        val tip = (guess + dir * 34f).coerceIn(5f, 175f)
        // no room for an arrow when the needle is pushed against that edge
        if ((tip - from) * dir < 10f) continue
        drawArc(color, 180f + minOf(from, tip), abs(tip - from), false, topLeft, arcSize, style = stroke)
        val at = g.pointAt(tip, r)
        for (side in listOf(-1f, 1f)) {
            drawLine(color, at, g.pointAt(tip - dir * 4.5f, r + side * head), stroke.width, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawNeedle(g: DialGeometry, guess: Float, showNeedle: Boolean) {
    val c = g.center
    val tip = g.pointAt(guess, g.faceRadius * 0.97f)
    val drop = Offset(g.radius * 0.012f, g.radius * 0.022f)
    val width = g.radius * 0.036f

    if (showNeedle) {
        clipRect(bottom = c.y) {
            drawLine(Color.Black.copy(alpha = 0.3f), c + drop, tip + drop, strokeWidth = width, cap = StrokeCap.Round)
        }
        drawLine(Palette.Needle, c, tip, strokeWidth = width, cap = StrokeCap.Round)
        drawLine(Color(0xFFFF7A88), c, tip, strokeWidth = width * 0.28f, cap = StrokeCap.Round)
    }

    val knob = g.knobRadius
    drawCircle(Color.Black.copy(alpha = 0.4f), knob * 1.06f, c + drop * 2f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFF8D99), Palette.Needle, Color(0xFF7E1021)),
            center = c - Offset(knob * 0.35f, knob * 0.4f), radius = knob * 1.5f,
        ),
        knob, c,
    )
    drawCircle(Color.Black.copy(alpha = 0.2f), knob * 0.62f, c, style = Stroke(knob * 0.07f))
    drawCircle(Color.White.copy(alpha = 0.18f), knob * 0.56f, c, style = Stroke(knob * 0.04f))
}
