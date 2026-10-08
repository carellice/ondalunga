package com.flaviocecca.ondalunga.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flaviocecca.ondalunga.GameViewModel
import com.flaviocecca.ondalunga.LocalSfx
import com.flaviocecca.ondalunga.SettingsStore
import com.flaviocecca.ondalunga.Sfx
import com.flaviocecca.ondalunga.Sound
import com.flaviocecca.ondalunga.game.GameState
import com.flaviocecca.ondalunga.game.Mode
import com.flaviocecca.ondalunga.game.Phase
import com.flaviocecca.ondalunga.game.RuleSet
import com.flaviocecca.ondalunga.game.Side
import com.flaviocecca.ondalunga.game.Spectrum
import kotlinx.coroutines.delay

internal val TeamColors = listOf(Palette.Sun, Palette.Sky)

// the two ends of the spectrum card; the left/right bet reuses them
internal val SpectrumLeft = Color(0xFF329AA7)
internal val SpectrumRight = Color(0xFFE9763C)

private val TitleBrush = Brush.horizontalGradient(listOf(Palette.Sun, Palette.Ember))

@Composable
fun OndaLungaApp(vm: GameViewModel = viewModel()) {
    val context = LocalContext.current.applicationContext
    val sfx = remember { Sfx(context) { vm.settings.value } }
    DisposableEffect(Unit) { onDispose { sfx.release() } }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // a text field left focused underneath would keep its cursor and keyboard on top of the sheet
    LaunchedEffect(settingsOpen) { if (settingsOpen) focusManager.clearFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .starfield()
    ) {
        CompositionLocalProvider(LocalContentColor provides Palette.Cream, LocalSfx provides sfx) {
            AnimatedContent(
                targetState = vm.state == null,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "screen",
            ) { home ->
                val state = vm.state
                if (home || state == null) {
                    HomeScreen(vm.settings, onStart = vm::start, onOpenSettings = { settingsOpen = true })
                } else {
                    GameScreen(state, vm, onOpenSettings = { settingsOpen = true })
                }
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
            ) {
                BackHandler { settingsOpen = false }
                SettingsScreen(vm.settings, vm.updater, inGame = vm.state != null, onClose = { settingsOpen = false })
            }
        }
    }
}

/**
 * Full-height scrolling page. Children are spread from top to bottom, so the last group
 * (the actions) sits at the bottom edge on tall screens and the page scrolls on short ones.
 */
@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun HomeScreen(settings: SettingsStore, onStart: (Mode, List<String>) -> Unit, onOpenSettings: () -> Unit) {
    val rules = settings.value.rules
    fun setRules(transform: (RuleSet) -> RuleSet) = settings.update { it.copy(rules = transform(it.rules)) }
    var mode by rememberSaveable { mutableStateOf(Mode.TEAMS) }
    var teamA by rememberSaveable { mutableStateOf("Squadra Sole") }
    var teamB by rememberSaveable { mutableStateOf("Squadra Luna") }
    var playerA by rememberSaveable { mutableStateOf("Sole") }
    var playerB by rememberSaveable { mutableStateOf("Luna") }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var needle by remember { mutableFloatStateOf(64f) }

    if (showRules) RulesDialog(onDismiss = { showRules = false })

    ScreenColumn {
        Group {
            GameLogo(Modifier.padding(top = 4.dp))
            Dial(
                target = 118f,
                guess = needle,
                revealed = true,
                modifier = Modifier.offset(y = (-14).dp),
                mode = DialMode.AIM,
                onGuessChange = { needle = it },
            )
        }

        Group(Modifier.padding(bottom = 12.dp)) {
            Toggle(
                options = listOf(Mode.TEAMS to "A squadre", Mode.DUEL to "1 contro 1", Mode.COOP to "Cooperativa"),
                selected = mode,
                onSelect = { mode = it },
            )
            if (mode == Mode.TEAMS) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        GameTextField(teamA, { teamA = it.take(20) }, "Prima squadra", TeamColors[0])
                    }
                    Box(Modifier.weight(1f)) {
                        GameTextField(teamB, { teamB = it.take(20) }, "Seconda squadra", TeamColors[1])
                    }
                }
                GameLength(
                    label = "Punti per vincere",
                    options = listOf(5, 10, 15, 20),
                    selected = rules.targetScore,
                    onSelect = { n -> setRules { it.copy(targetScore = n) } },
                )
            } else if (mode == Mode.DUEL) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        GameTextField(playerA, { playerA = it.take(20) }, "Giocatore 1", TeamColors[0])
                    }
                    Box(Modifier.weight(1f)) {
                        GameTextField(playerB, { playerB = it.take(20) }, "Giocatore 2", TeamColors[1])
                    }
                }
                GameLength(
                    label = "Punti per vincere",
                    options = listOf(5, 10, 15, 20),
                    selected = rules.targetScore,
                    onSelect = { n -> setRules { it.copy(targetScore = n) } },
                )
            } else {
                Hint("Tutti insieme, una carta per turno: fate più punti possibile. Ogni centro perfetto regala una carta in più.")
                GameLength(
                    label = "Carte da giocare",
                    options = listOf(5, 7, 9),
                    selected = rules.coopCards,
                    onSelect = { n -> setRules { it.copy(coopCards = n) } },
                )
            }
        }

        Group {
            ChunkyButton("Gioca", onClick = {
                val names = when (mode) {
                    Mode.TEAMS -> listOf(teamA.trim().ifEmpty { "Squadra 1" }, teamB.trim().ifEmpty { "Squadra 2" })
                    Mode.DUEL -> listOf(playerA.trim().ifEmpty { "Giocatore 1" }, playerB.trim().ifEmpty { "Giocatore 2" })
                    Mode.COOP -> listOf("Tutti")
                }
                onStart(mode, names)
            })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Come si gioca", onClick = { showRules = true })
                GhostButton("Impostazioni", onClick = onOpenSettings)
            }
        }
    }
}

/** How long the game about to start lasts; the choice is remembered for next time. */
@Composable
private fun GameLength(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label, Modifier.padding(start = 6.dp))
        Toggle(options = options.map { it to "$it" }, selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun GameScreen(s: GameState, vm: GameViewModel, onOpenSettings: () -> Unit) {
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }
    if (confirmQuit) {
        GameDialog(
            title = "Abbandonare la partita?",
            onDismiss = { confirmQuit = false },
            actions = {
                ChunkyButton("Continua", { confirmQuit = false }, Modifier.weight(1f), color = Slate, contentColor = Palette.Cream)
                ChunkyButton("Abbandona", vm::quit, Modifier.weight(1f), color = Palette.Needle, contentColor = Palette.Cream)
            },
        ) {
            Text("Il punteggio di questa partita andrà perso.", fontSize = 15.sp, lineHeight = 21.sp, color = Palette.Cream.copy(alpha = 0.9f))
        }
    }

    ScreenColumn {
        if (s.phase == Phase.GAME_OVER) {
            Group {
                TopBar(s, onSettings = onOpenSettings, onQuit = vm::quit)
                Scoreboard(s)
            }
            GameOverSummary(s)
            Group {
                ChunkyButton("Rivincita", onClick = { vm.start(s.mode, s.teamNames) })
                GhostButton("Torna al menu", onClick = vm::quit)
            }
        } else {
            Group {
                TopBar(s, onSettings = onOpenSettings, onQuit = { confirmQuit = true })
                Scoreboard(s)
                PhaseTitle(s)
                SpectrumCard(s.spectrum)
                Dial(
                    target = s.target,
                    guess = s.guess,
                    revealed = s.phase == Phase.PSYCHIC || s.phase == Phase.REVEAL,
                    mode = when (s.phase) {
                        Phase.SPIN -> DialMode.SPIN
                        Phase.GUESS -> DialMode.AIM
                        else -> DialMode.LOCKED
                    },
                    showNeedle = s.phase != Phase.PSYCHIC,
                    sideBet = s.phase == Phase.LEFT_RIGHT,
                    onGuessChange = vm::setGuess,
                    onSpinStart = vm::spinStarted,
                    onSpinEnd = vm::spinEnded,
                )
            }
            Group(Modifier.padding(top = 10.dp)) {
                if (s.clue.isNotBlank() && s.phase in listOf(Phase.GUESS, Phase.LEFT_RIGHT, Phase.REVEAL)) {
                    ClueBubble(s.clue.trim())
                }
                AnimatedContent(
                    targetState = s.phase,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 90)) + slideInVertically(tween(260, delayMillis = 90)) { it / 5 }) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "controls",
                ) { phase ->
                    Group { PhaseControls(phase, s, vm) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(s: GameState, onSettings: () -> Unit, onQuit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ONDA LUNGA",
            style = TextStyle(brush = TitleBrush, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Turno ${s.round}", Palette.Cream)
            RoundIconButton("⚙", "Impostazioni", onSettings)
            RoundIconButton("✕", "Abbandona la partita", onQuit)
        }
    }
}

@Composable
private fun Scoreboard(s: GameState) {
    if (s.mode == Mode.COOP) {
        Row(
            Modifier
                .fillMaxWidth()
                .scoreCard(Palette.Sun, active = false)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("Punti", color = Palette.Sun)
                ScoreNumber(s.scores[0])
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Carte rimaste")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(s.cardsLeft.coerceAtMost(10)) {
                        Box(
                            Modifier
                                .size(width = 13.dp, height = 19.dp)
                                .background(Palette.Sun, RoundedCornerShape(3.dp))
                        )
                    }
                    if (s.cardsLeft == 0) Text("nessuna", color = Palette.Cream.copy(alpha = 0.6f))
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        s.teamNames.forEachIndexed { team, name ->
            TeamScore(
                name = name,
                score = s.scores[team],
                targetScore = s.rules.targetScore,
                color = TeamColors[team],
                active = if (s.phase == Phase.GAME_OVER) team == s.leader else team == s.activeTeam,
            )
        }
    }
}

private fun Modifier.scoreCard(color: Color, active: Boolean): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return this
        .shadow(if (active) 14.dp else 0.dp, shape, ambientColor = color, spotColor = color)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF283B70), Color(0xFF17254A))))
        .border(if (active) 2.dp else 1.dp, if (active) color else Color.White.copy(alpha = 0.09f), shape)
}

@Composable
private fun ScoreNumber(score: Int) {
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        label = "score",
    ) { value ->
        Text("$value", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RowScope.TeamScore(name: String, score: Int, targetScore: Int, color: Color, active: Boolean) {
    val progress by animateFloatAsState((score.toFloat() / targetScore).coerceIn(0f, 1f), label = "progress")
    Row(
        Modifier
            .weight(1f)
            .alpha(if (active) 1f else 0.72f)
            .scoreCard(color, active)
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(name, color = color)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(color, CircleShape)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            ScoreNumber(score)
            Text(
                "/$targetScore",
                Modifier.padding(start = 2.dp, bottom = 7.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Cream.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun PhaseTitle(s: GameState) {
    val teams = s.versus
    AnimatedContent(
        targetState = s.phase,
        transitionSpec = { fadeIn(tween(250, delayMillis = 80)) togetherWith fadeOut(tween(100)) },
        label = "title",
    ) { phase ->
        val team = when (phase) {
            Phase.LEFT_RIGHT -> s.opponent
            Phase.PASS, Phase.SPIN, Phase.PSYCHIC -> s.psychicSide
            else -> s.activeTeam
        }
        val color = if (teams) TeamColors[team] else Palette.Sun
        val teamName = if (teams) s.teamNames[team] else "Tutti insieme"
        val (chip, title) = when (phase) {
            Phase.PASS -> teamName to "Telefono al Sensitivo"
            Phase.SPIN -> "Sensitivo" to "Gira la ruota!"
            Phase.PSYCHIC -> "Sensitivo" to "Solo per i tuoi occhi"
            Phase.GUESS -> teamName to "Dov'è il bersaglio?"
            Phase.LEFT_RIGHT -> teamName to "La vostra scommessa"
            else -> "Risultato" to "Ecco il bersaglio"
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Chip(chip, color)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun SpectrumCard(spectrum: Spectrum) {
    val shape = RoundedCornerShape(22.dp)
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .height(IntrinsicSize.Min),
        ) {
            SpectrumHalf(
                text = spectrum.left,
                colors = listOf(Color(0xFF49AEBB), SpectrumLeft, Color(0xFF226F7B)),
                textColor = Palette.Cream,
                alignment = Alignment.Start,
                modifier = Modifier.padding(end = 14.dp),
            )
            SpectrumHalf(
                text = spectrum.right,
                colors = listOf(Color(0xFFF7935B), SpectrumRight, Color(0xFFCB5A24)),
                textColor = Palette.Night,
                alignment = Alignment.End,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        Canvas(
            Modifier
                .size(30.dp)
                .background(Palette.Night, CircleShape)
                .border(2.dp, Palette.Cream.copy(alpha = 0.7f), CircleShape),
        ) {
            val half = 6.5.dp.toPx()
            val head = 3.5.dp.toPx()
            val stroke = 2.dp.toPx()
            fun line(from: Offset, to: Offset) =
                drawLine(Palette.Cream, from, to, stroke, StrokeCap.Round)
            line(center.copy(x = center.x - half), center.copy(x = center.x + half))
            for (dir in listOf(-1f, 1f)) {
                val tip = center.copy(x = center.x + dir * half)
                line(tip, Offset(tip.x - dir * head, tip.y - head))
                line(tip, Offset(tip.x - dir * head, tip.y + head))
            }
        }
    }
}

@Composable
private fun RowScope.SpectrumHalf(
    text: String,
    colors: List<Color>,
    textColor: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(Brush.verticalGradient(colors))
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(modifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = alignment,
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClueBubble(clue: String) {
    Panel(outline = Palette.Sun.copy(alpha = 0.55f)) {
        Label("Indizio", color = Palette.Sun)
        Text("«$clue»", fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhaseControls(phase: Phase, s: GameState, vm: GameViewModel) {
    val duel = s.mode == Mode.DUEL
    val guesser = s.teamNames[s.activeTeam]
    val psychic = s.teamNames[s.psychicSide]
    val accent = when {
        !s.versus -> Palette.Sun
        phase == Phase.PASS || phase == Phase.SPIN || phase == Phase.PSYCHIC -> TeamColors[s.psychicSide]
        else -> TeamColors[s.activeTeam]
    }
    when (phase) {
        Phase.PASS -> {
            Hint(
                if (duel) "In questo turno $psychic fa il Sensitivo e dà l'indizio, $guesser indovina e prende i punti. $guesser: occhi lontani dallo schermo!"
                else "Scegliete chi fa il Sensitivo in questo turno e passategli il telefono. Tutti gli altri: occhi lontani dallo schermo!"
            )
            ChunkyButton(if (duel) "Sono $psychic" else "Sono il Sensitivo", onClick = vm::psychicReady, color = accent)
        }

        Phase.SPIN -> {
            Hint("Dai una bella spinta alla ruota con il dito. Lo schermo è chiuso: nessuno sa dove si fermerà il bersaglio.")
            ChunkyButton("Apri lo schermo", onClick = vm::openScreen, color = accent, enabled = s.spun)
        }

        Phase.PSYCHIC -> {
            GameTextField(s.clue, { vm.setClue(it.take(60)) }, "Indizio", accent, placeholder = "Dillo a voce, o scrivilo qui")
            ChunkyButton("Chiudi lo schermo", onClick = vm::hideTarget, color = accent)
            if (s.spareSpectrum != null) {
                GhostButton("Cambia carta (una volta sola)", onClick = vm::swapCard)
            }
        }

        Phase.GUESS -> {
            Hint(
                if (duel) "$guesser, trascina la lancetta dove pensi che sia il bersaglio. $psychic non può dire altro!"
                else "Discutete e trascinate la lancetta. Il Sensitivo non può dire altro!"
            )
            ChunkyButton(if (duel) "Confermo" else "Confermiamo", onClick = vm::confirmGuess, color = accent)
        }

        Phase.LEFT_RIGHT -> {
            SideBetBrief(s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChunkyButton(
                    "Più a sinistra", { vm.chooseSide(Side.LEFT) }, Modifier.weight(1f),
                    color = SpectrumLeft, contentColor = Palette.Cream, caption = "verso ${s.spectrum.left}",
                )
                ChunkyButton(
                    "Più a destra", { vm.chooseSide(Side.RIGHT) }, Modifier.weight(1f),
                    color = SpectrumRight, caption = "verso ${s.spectrum.right}",
                )
            }
        }

        Phase.REVEAL -> {
            RevealResult(s)
            ChunkyButton(if (s.finished) "Vedi il risultato" else "Prossimo turno", onClick = vm::next)
        }

        Phase.GAME_OVER -> Unit
    }
}

/** Tells the opponents what their bet is about: who aimed, what to decide, what is at stake. */
@Composable
private fun SideBetBrief(s: GameState) {
    val rivals = s.teamNames[s.activeTeam]
    Panel(outline = TeamColors[s.opponent].copy(alpha = 0.55f)) {
        Label("In palio: +1 punto", color = TeamColors[s.opponent])
        Text(
            "$rivals ha fermato la lancetta. Da che parte ha sbagliato?",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Dite se il bersaglio vero sta più a sinistra o più a destra della lancetta. " +
                "Se indovinate il lato prendete 1 punto, a meno che $rivals non faccia centro perfetto.",
            color = Palette.Cream.copy(alpha = 0.82f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RevealResult(s: GameState) {
    val result = s.result ?: return
    val teams = s.versus
    val badgeColor = when (result.zone) {
        4 -> Palette.Sky
        3 -> Palette.Ember
        2 -> Palette.Sun
        else -> Color(0xFF5A6788)
    }
    // the badge pops in once the screen has slid open
    val pop = remember { Animatable(0f) }
    val sfx = LocalSfx.current
    LaunchedEffect(Unit) {
        delay(750)
        sfx?.play(
            when (result.zone) {
                4 -> Sound.BULLSEYE
                0 -> Sound.MISS
                else -> Sound.GOOD
            }
        )
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    Panel(outline = badgeColor.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = pop.value.coerceIn(0f, 1f)
                    }
                    .size(68.dp)
                    .background(badgeColor, CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${result.points}", color = Palette.Night, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (result.zone) {
                        4 -> "Centro perfetto!"
                        0 -> "Fuori bersaglio"
                        else -> "Bel colpo!"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                val detail = when {
                    !teams -> if (result.bonus) "Avete guadagnato una carta in più!" else null
                    s.mode == Mode.DUEL -> if (result.points > 0) "Punti a ${s.teamNames[s.activeTeam]}." else null
                    !s.rules.sideBet -> null
                    result.zone == 4 -> "${s.teamNames[s.opponent]} non prende punti."
                    result.opponentScored -> "${s.teamNames[s.opponent]} indovina il lato: +1"
                    else -> "${s.teamNames[s.opponent]} sbaglia lato: niente punto."
                }
                if (detail != null) {
                    Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = Palette.Cream.copy(alpha = 0.8f))
                }
                if (teams && result.bonus && !s.finished) {
                    Text(
                        if (s.mode == Mode.DUEL) "Ancora in svantaggio: ${s.teamNames[s.activeTeam]} indovina di nuovo!"
                        else "Ancora in svantaggio: ${s.teamNames[s.activeTeam]} gioca di nuovo!",
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TeamColors[s.activeTeam],
                    )
                }
            }
        }
    }
}

private fun coopVerdict(score: Int): String = when {
    score <= 5 -> "Frequenze disturbate… serve una risintonizzata."
    score <= 9 -> "Si sente un po' di fruscio, ma ci siete."
    score <= 13 -> "Buona ricezione!"
    score <= 17 -> "Sintonia quasi perfetta."
    else -> "Stessa identica lunghezza d'onda!"
}

@Composable
private fun GameOverSummary(s: GameState) {
    val teams = s.versus
    val sfx = LocalSfx.current
    LaunchedEffect(Unit) { sfx?.play(Sound.WIN) }
    Group(Modifier.padding(vertical = 24.dp)) {
        Chip("Partita finita", if (teams) TeamColors[s.leader] else Palette.Sun)
        Text(
            if (teams) "Vince\n${s.teamNames[s.leader]}!" else "${s.scores[0]} punti",
            style = TextStyle(
                brush = TitleBrush,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            ),
        )
        Wave(Modifier.padding(bottom = 6.dp))
        Hint(
            if (teams) "${s.scores[s.leader]} a ${s.scores[1 - s.leader]} dopo ${s.round} turni."
            else coopVerdict(s.scores[0])
        )
    }
}
