package com.flaviocecca.ondalunga.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flaviocecca.ondalunga.game.Spectrum
import kotlinx.coroutines.delay

private class DemoStep(val title: String, val text: String)

private val DemoSteps = listOf(
    DemoStep(
        "Il Sensitivo gira la ruota",
        "A schermo chiuso: nessuno sa dove si ferma il bersaglio. Gli altri non guardano il telefono.",
    ),
    DemoStep(
        "Guarda il bersaglio in segreto",
        "Apre lo schermo e vede in che punto, tra i due estremi della carta, è finito il bersaglio.",
    ),
    DemoStep(
        "Dà un solo indizio",
        "Qualcosa che stia proprio in quel punto tra «Freddo» e «Caldo». Poi richiude lo schermo e resta in silenzio.",
    ),
    DemoStep(
        "La squadra muove la lancetta",
        "Discute sull'indizio e la ferma dove pensa che sia il bersaglio.",
    ),
    DemoStep(
        "Gli avversari scommettono",
        "Il bersaglio sta più a sinistra o più a destra della lancetta? Se indovinano prendono 1 punto.",
    ),
    DemoStep(
        "Si apre lo schermo",
        "4 punti al centro, 3 e 2 nelle fasce accanto. Poi tocca all'altra squadra: vince chi arriva prima al traguardo.",
    ),
)

private val DemoCard = Spectrum("Freddo", "Caldo")
private const val DEMO_CLUE = "Caffè appena fatto"
private const val DEMO_TARGET = 136f
private const val DEMO_GUESS = 126f

/** What sits under the demo device at each moment of the round. */
private enum class DemoExtra { NONE, CLUE, BET, BET_CHOSEN, RESULT }

/**
 * "Come si gioca": one example round, full screen. The reader moves through it one step at a
 * time, back and forth; each step plays its own bit of the round when it comes up.
 */
@Composable
internal fun TutorialScreen(onClose: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var showNeedle by remember { mutableStateOf(true) }
    var extra by remember { mutableStateOf(DemoExtra.NONE) }
    val needle = remember { Animatable(90f) }
    val spin = remember { Animatable(0f) }
    val last = DemoSteps.lastIndex

    // every step first puts the device in its starting pose, so it reads the same from either side
    LaunchedEffect(step) {
        revealed = step == 1 || step == 2
        showNeedle = step != 1 && step != 2
        extra = when (step) {
            2, 3 -> DemoExtra.CLUE
            4 -> DemoExtra.BET
            else -> DemoExtra.NONE
        }
        needle.snapTo(if (step >= 4) DEMO_GUESS else 90f)
        when (step) {
            0 -> {
                delay(600)
                spin.animateTo(spin.value + 600f, tween(2200, easing = FastOutSlowInEasing))
            }

            2 -> {
                delay(2400)
                revealed = false
                delay(1300)
                showNeedle = true
            }

            3 -> {
                delay(700)
                needle.animateTo(58f, tween(1000))
                needle.animateTo(152f, tween(1300))
                needle.animateTo(DEMO_GUESS, tween(1000))
            }

            4 -> {
                delay(2200)
                extra = DemoExtra.BET_CHOSEN
            }

            5 -> {
                delay(500)
                revealed = true
                delay(1100)
                extra = DemoExtra.RESULT
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .starfield()
            .pointerInput(Unit) {}
    ) {
        ScreenColumn {
            Group {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Come si gioca", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    RoundIconButton("✕", "Chiudi la guida", onClose)
                }
                SpectrumCard(DemoCard)
                Dial(
                    target = DEMO_TARGET,
                    guess = needle.value,
                    revealed = revealed,
                    showNeedle = showNeedle,
                    sideBet = extra == DemoExtra.BET || extra == DemoExtra.BET_CHOSEN,
                    spin = spin.value,
                    silent = true,
                )
                AnimatedContent(
                    targetState = extra,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 66.dp),
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    contentAlignment = Alignment.Center,
                    contentKey = { if (it == DemoExtra.BET_CHOSEN) DemoExtra.BET else it },
                    label = "demoExtra",
                ) { shown ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (shown) {
                            DemoExtra.NONE -> Unit
                            DemoExtra.CLUE -> DemoClue()
                            DemoExtra.BET, DemoExtra.BET_CHOSEN -> DemoBet(chosen = shown == DemoExtra.BET_CHOSEN)
                            DemoExtra.RESULT -> DemoResult()
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .heightIn(min = 170.dp),
                transitionSpec = { fadeIn(tween(250, delayMillis = 80)) togetherWith fadeOut(tween(120)) },
                contentAlignment = Alignment.Center,
                label = "demoStep",
            ) { index ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Chip("Passo ${index + 1} di ${DemoSteps.size}", Palette.Sun)
                    Text(
                        DemoSteps[index].title,
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        DemoSteps[index].text,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = Palette.Cream.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                    if (index == last) {
                        Text(
                            "In più: chi fa centro ed è ancora in svantaggio gioca di nuovo, e chi inizia per secondo parte con 1 punto.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Palette.Cream.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Group {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DemoSteps.indices.forEach { index ->
                        val width by animateDpAsState(if (index == step) 22.dp else 7.dp, label = "dot")
                        Box(
                            Modifier
                                .size(width = width, height = 7.dp)
                                .background(
                                    if (index == step) Palette.Sun else Palette.Cream.copy(alpha = if (index < step) 0.55f else 0.2f),
                                    CircleShape,
                                )
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChunkyButton(
                        "Indietro", { step-- }, Modifier.weight(1f),
                        color = Slate, enabled = step > 0, contentColor = Palette.Cream,
                    )
                    ChunkyButton(
                        if (step == last) "Ho capito" else "Avanti",
                        { if (step == last) onClose() else step++ },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoClue() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.055f), shape)
            .border(1.dp, Palette.Sun.copy(alpha = 0.55f), shape)
            .heightIn(min = 60.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label("Indizio", color = Palette.Sun)
        Text("«$DEMO_CLUE»", fontSize = 19.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DemoBet(chosen: Boolean) {
    val dim by animateFloatAsState(if (chosen) 0.35f else 1f, label = "dim")
    val sink by animateDpAsState(if (chosen) 3.dp else 0.dp, label = "sink")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Raised(SpectrumLeft, Modifier.weight(1f).height(52.dp).alpha(dim), depth = 4.dp, shape = RoundedCornerShape(16.dp)) {
            Text("Più a sinistra", color = Palette.Cream, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Raised(SpectrumRight, Modifier.weight(1f).height(52.dp), depth = 4.dp, sink = sink, shape = RoundedCornerShape(16.dp)) {
            Text("Più a destra", color = Palette.Night, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DemoResult() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .background(Palette.Ember, CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+3", color = Palette.Night, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text("Bel colpo: 3 punti!", fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(
                "Il bersaglio era più a destra: +1 agli avversari.",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Palette.Cream.copy(alpha = 0.8f),
            )
        }
    }
}
