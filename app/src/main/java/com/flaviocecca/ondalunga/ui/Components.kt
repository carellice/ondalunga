package com.flaviocecca.ondalunga.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flaviocecca.ondalunga.LocalSfx
import com.flaviocecca.ondalunga.Sound
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------------------

private class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

private val NightSky = Random(11).let { random ->
    List(90) {
        Star(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 1.3f, 0.1f + random.nextFloat() * 0.4f)
    }
}

/** App backdrop: deep-space gradient, a soft glow behind the device, and a fixed scatter of stars. */
fun Modifier.starfield(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1D2E5C), Palette.Night, Color(0xFF090F22))))
    val glow = Offset(size.width / 2f, size.height * 0.4f)
    drawCircle(
        Brush.radialGradient(
            listOf(Palette.Sky.copy(alpha = 0.16f), Color.Transparent),
            center = glow, radius = size.width * 0.95f,
        ),
        size.width * 0.95f, glow,
    )
    for (star in NightSky) {
        drawCircle(
            Palette.Cream.copy(alpha = star.alpha), star.radius.dp.toPx(),
            Offset(star.x * size.width, star.y * size.height),
        )
    }
}

// ---------------------------------------------------------------------------------------
// The two building blocks every control is made of
// ---------------------------------------------------------------------------------------

/** Neutral plastic for secondary controls. */
val Slate = Color(0xFF2C3F74)

/**
 * A block standing proud of the surface: a lit face on top of a darker ledge [depth] tall.
 * [sink] pushes the face down into the ledge (a pressed key). [face] decorates the face only.
 */
@Composable
fun Raised(
    color: Color,
    modifier: Modifier = Modifier,
    depth: Dp = 6.dp,
    sink: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    face: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .background(lerp(color, Color.Black, 0.42f), shape)
        )
        Box(
            Modifier
                .padding(bottom = depth)
                .offset(y = sink)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), color)))
                // light catching the top edge
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)), shape)
                .then(face),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * A slot carved into the surface: dark floor, shadow under the top edge, a lit lip at the bottom.
 * [ring] outlines it (e.g. the focus colour of a text field).
 */
fun Modifier.well(
    shape: Shape,
    ring: Color = Color.Black.copy(alpha = 0.55f),
    floor: Color = Color(0xFF0B1228),
): Modifier = this
    .background(Color.White.copy(alpha = 0.13f), shape)
    .padding(bottom = 1.5.dp)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(lerp(floor, Color.Black, 0.45f), floor)))
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                endY = 9.dp.toPx(),
            )
        )
    }
    .border(1.5.dp, ring, shape)

/** A [Raised] block that behaves like a key: sinks under the finger, clicks, and plays the tap sound. */
@Composable
private fun PushKey(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    depth: Dp = 6.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val sfx = LocalSfx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed || !enabled) depth - 1.dp else 0.dp, label = "sink")
    Raised(
        color = color,
        modifier = if (description != null) modifier.semantics { contentDescription = description } else modifier,
        depth = depth,
        sink = sink,
        shape = shape,
        face = Modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
            sfx?.play(Sound.TAP)
            onClick()
        },
        content = content,
    )
}

// ---------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------

/** The main action button. [caption] adds a small second line. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Sun,
    enabled: Boolean = true,
    contentColor: Color = Palette.Night,
    caption: String? = null,
) {
    val depth = 6.dp
    PushKey(
        onClick = onClick,
        color = if (enabled) color else Color(0xFF34426A),
        modifier = modifier
            .fillMaxWidth()
            .height((if (caption != null) 72.dp else 58.dp) + depth),
        depth = depth,
        enabled = enabled,
    ) {
        val textColor = if (enabled) contentColor else Palette.Cream.copy(alpha = 0.4f)
        Column(
            Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text,
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
            )
            if (caption != null) {
                Text(
                    caption,
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Secondary action: same key, lower and in neutral plastic. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PushKey(onClick, Slate, modifier, depth = 4.dp, shape = RoundedCornerShape(16.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            color = Palette.Cream,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun RoundIconButton(glyph: String, description: String, onClick: () -> Unit) {
    PushKey(
        onClick, Slate,
        Modifier.size(width = 38.dp, height = 41.dp),
        depth = 3.dp, shape = CircleShape, description = description,
    ) {
        Text(glyph, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------

/** Segmented choice: a carved tray with one raised key that slides under the selected option. */
@Composable
fun <T> Toggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val sfx = LocalSfx.current
    val keyHeight = 46.dp
    val keyDepth = 4.dp
    val keyShape = RoundedCornerShape(13.dp)
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .well(RoundedCornerShape(18.dp))
            .padding(5.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, spring(dampingRatio = 0.72f, stiffness = 520f), label = "key")
        Raised(
            Palette.Sun,
            Modifier
                .offset(x = x)
                .size(segment, keyHeight),
            depth = keyDepth,
            shape = keyShape,
        )
        Row(Modifier.height(keyHeight)) {
            for ((value, label) in options) {
                key(value) {
                    val on = value == selected
                    val color by animateColorAsState(
                        if (on) Palette.Night else Palette.Cream.copy(alpha = 0.7f), label = "label",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(keyShape)
                            .selectable(
                                selected = on,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.RadioButton,
                            ) {
                                sfx?.play(Sound.TAP)
                                onSelect(value)
                            }
                            // labels sit on the key's face, which stops short of the ledge
                            .padding(bottom = keyDepth),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Text typed into a carved slot, with its caption above; the rim lights up in [accent] when focused. */
@Composable
fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color = Palette.Sun,
    placeholder: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(if (focused) accent else Color.Black.copy(alpha = 0.55f), label = "ring")
    val style = TextStyle(color = Palette.Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label, Modifier.padding(start = 6.dp), color = accent.copy(alpha = if (focused) 1f else 0.8f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(accent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .well(RoundedCornerShape(16.dp), ring = ring)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = style.copy(color = Palette.Cream.copy(alpha = 0.35f)))
                    }
                    field()
                }
            },
        )
    }
}

/** On/off lever: a raised knob sliding in a carved track. Purely visual; the enclosing row handles the tap. */
@Composable
fun GameSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (checked) 24.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "knob")
    val floor by animateColorAsState(if (checked) Color(0xFF7A5510) else Color(0xFF0B1228), label = "track")
    val knob by animateColorAsState(if (checked) Palette.Sun else Color(0xFF8792B4), label = "knobColor")
    Box(
        modifier
            .size(width = 58.dp, height = 35.5.dp)
            .well(CircleShape, floor = floor)
            .padding(3.dp)
    ) {
        Raised(
            knob,
            Modifier
                .offset(x = x)
                .size(28.dp),
            depth = 3.dp,
            shape = CircleShape,
        )
    }
}

/** 0..1 slider: a carved groove that fills up behind a raised knob. */
@Composable
fun GameSlider(value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val knob = 32.dp
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnDone by rememberUpdatedState(onDone)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    currentOnChange(target.coerceIn(0f, 1f))
                    currentOnDone()
                    true
                }
            }
            .pointerInput(Unit) {
                val knobPx = knob.toPx()
                fun valueAt(x: Float) = ((x - knobPx / 2f) / (size.width - knobPx)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnChange(valueAt(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnChange(valueAt(change.position.x))
                    }
                    currentOnDone()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - knob
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .well(CircleShape)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(knob / 2 + travel * value)
                    .background(Brush.verticalGradient(listOf(Palette.Sun, Color(0xFFC98A1E))))
            )
        }
        Raised(
            Palette.Sun,
            Modifier
                .offset(x = travel * value)
                .size(knob),
            depth = 4.dp,
            shape = CircleShape,
        )
    }
}

/** A carved groove filling up from the left, for downloads and the like. */
@Composable
fun ProgressGroove(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .well(CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Brush.verticalGradient(listOf(Palette.Sun, Color(0xFFC98A1E))))
        )
    }
}

// ---------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------

/**
 * Modal panel in the same raised plastic as the buttons: a title, scrolling [content],
 * and a row of [actions] pinned to the bottom.
 */
@Composable
fun GameDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Raised(Color(0xFF263A72), depth = 9.dp, shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = Palette.Cream, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                    Wave(Modifier.width(72.dp), color = Palette.Sun)
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Text and surfaces
// ---------------------------------------------------------------------------------------

/** Small tracked-out caption used above sections. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.Cream.copy(alpha = 0.6f)) {
    Text(
        text.uppercase(),
        modifier,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Label(
        text,
        modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = color,
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    outline: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.055f), shape)
            .border(1.dp, outline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun Hint(text: String) {
    Panel {
        Text(
            text,
            color = Palette.Cream.copy(alpha = 0.82f),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A short sine wave, the app's little signature under the title; animate [phase] to make it travel. */
@Composable
fun Wave(modifier: Modifier = Modifier, color: Color = Palette.Sky, phase: Float = 0f) {
    Canvas(
        Modifier
            .width(150.dp)
            .then(modifier)
            .height(14.dp)
    ) {
        val path = Path()
        val steps = 60
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = t * size.width
            val y = size.height / 2f - sin(t * 6f * PI.toFloat() + phase) * size.height * 0.4f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
