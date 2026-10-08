package com.flaviocecca.ondalunga.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class LogoInk(val face: List<Color>, val side: Color)

private val Warm = LogoInk(listOf(Color(0xFFFFE9A3), Palette.Sun, Palette.Ember), Color(0xFF8E3A12))
private val Cool = LogoInk(listOf(Color(0xFFC9F4F8), Palette.Sky, Palette.Lagoon), Color(0xFF134A56))

private const val LETTERS = 9

/**
 * The title as chunky block letters: they drop in one after another, then keep rolling
 * like a wave travelling through the word. [scale] shrinks it for short screens.
 */
@Composable
fun GameLogo(modifier: Modifier = Modifier, scale: Float = 1f) {
    val phase by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phase",
    )
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1200, easing = LinearEasing)) }

    Column(
        modifier.clearAndSetSemantics { contentDescription = "Onda Lunga" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoWord("ONDA", firstIndex = 0, ink = Warm, phase = phase, intro = intro.value, scale = scale)
        LogoWord(
            "LUNGA", firstIndex = 4, ink = Cool, phase = phase, intro = intro.value, scale = scale,
            modifier = Modifier.offset(y = (-14).dp * scale),
        )
        if (scale > 0.7f) Wave(Modifier.offset(y = (-10).dp), phase = -phase)
    }
}

@Composable
private fun LogoWord(
    word: String,
    firstIndex: Int,
    ink: LogoInk,
    phase: Float,
    intro: Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(fontSize = 62.sp * scale, lineHeight = 66.sp * scale, fontWeight = FontWeight.Black)
    Row(modifier) {
        word.forEachIndexed { i, letter ->
            val index = firstIndex + i
            // each letter starts a little after the previous one and overshoots as it lands
            val t = ((intro - index * 0.6f / LETTERS) / 0.4f).coerceIn(0f, 1f)
            val pop = 1f + 2.7f * (t - 1f) * (t - 1f) * (t - 1f) + 1.7f * (t - 1f) * (t - 1f)
            val swing = phase - index * 0.7f
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .graphicsLayer {
                        translationY = sin(swing) * 5.dp.toPx() * t - (1f - t) * 40.dp.toPx()
                        rotationZ = cos(swing) * 4f * t
                        scaleX = pop
                        scaleY = pop
                        alpha = t
                    }
            ) {
                val text = letter.toString()
                // extruded side of the block, darkest and blurred at the bottom
                Text(
                    text,
                    Modifier.offset(y = 7.dp * scale),
                    style = style.copy(
                        color = ink.side,
                        shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 10f), blurRadius = 18f),
                    ),
                )
                Text(text, Modifier.offset(y = 5.dp * scale), style = style.copy(color = ink.side))
                Text(text, Modifier.offset(y = 2.5.dp * scale), style = style.copy(color = ink.side))
                Text(text, style = style.copy(brush = Brush.verticalGradient(ink.face)))
            }
        }
    }
}
