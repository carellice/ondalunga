package com.flaviocecca.ondalunga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flaviocecca.ondalunga.LocalSfx
import com.flaviocecca.ondalunga.Settings
import com.flaviocecca.ondalunga.SettingsStore
import com.flaviocecca.ondalunga.Sound
import com.flaviocecca.ondalunga.game.RuleSet
import com.flaviocecca.ondalunga.update.UpdateState
import com.flaviocecca.ondalunga.update.Updater
import java.util.Locale
import kotlin.math.roundToInt

/** Full-screen sheet shown over the home screen or a running game. */
@Composable
fun SettingsScreen(store: SettingsStore, updater: Updater, inGame: Boolean, onClose: () -> Unit) {
    val settings = store.value
    val rules = settings.rules
    val sfx = LocalSfx.current
    // look for a new version once per session, the first time the settings are opened
    LaunchedEffect(Unit) { if (updater.state == UpdateState.Idle) updater.check() }
    fun setRules(transform: (RuleSet) -> RuleSet) = store.update { it.copy(rules = transform(it.rules)) }

    Column(
        Modifier
            .fillMaxSize()
            .starfield()
            .pointerInput(Unit) {}
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Impostazioni", fontSize = 30.sp, fontWeight = FontWeight.Black)
            RoundIconButton("✕", "Chiudi le impostazioni", onClose)
        }

        Label("Audio")
        Card {
            VolumeSlider(
                title = "Effetti e musichette",
                value = settings.effectsVolume,
                onChange = { v -> store.update { it.copy(effectsVolume = v) } },
                onDone = { sfx?.play(Sound.GOOD) },
            )
            VolumeSlider(
                title = "Ruota, lancetta e schermo",
                value = settings.deviceVolume,
                onChange = { v -> store.update { it.copy(deviceVolume = v) } },
                onDone = { sfx?.play(Sound.SLIDE) },
            )
            SwitchRow(
                title = "Vibrazione",
                description = "Piccoli scatti mentre giri la ruota.",
                checked = settings.haptics,
                onChange = { on -> store.update { it.copy(haptics = on) } },
            )
        }

        Label("Regole")
        if (inGame) Hint("Le modifiche alle regole valgono dalla prossima partita.")
        Card {
            SwitchRow(
                title = "Scommessa sinistra/destra",
                description = "Gli avversari provano a indovinare da che parte sta il bersaglio: +1 se ci prendono.",
                checked = rules.sideBet,
                onChange = { on -> setRules { it.copy(sideBet = on) } },
            )
            SwitchRow(
                title = "Turno extra in rimonta",
                description = "Chi fa centro perfetto ed è ancora in svantaggio gioca subito di nuovo.",
                checked = rules.catchUp,
                onChange = { on -> setRules { it.copy(catchUp = on) } },
            )
            SwitchRow(
                title = "Cambio carta",
                description = "Il Sensitivo può scartare la carta una volta per turno.",
                checked = rules.cardSwap,
                onChange = { on -> setRules { it.copy(cardSwap = on) } },
            )
        }

        Label("Aggiornamenti")
        UpdateCard(updater)

        GhostButton(
            "Ripristina le impostazioni iniziali",
            onClick = { store.update { Settings() } },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        ChunkyButton("Fatto", onClick = onClose)
    }
}

@Composable
private fun UpdateCard(updater: Updater) {
    val state = updater.state
    Card {
        SettingTitle(
            "Versione installata: ${updater.installedVersion}",
            when (state) {
                UpdateState.Idle -> "Controlla se è uscita una versione più recente."
                UpdateState.Checking -> "Controllo in corso…"
                UpdateState.UpToDate -> "Hai già l'ultima versione."
                is UpdateState.Available -> "È disponibile la versione ${state.release.version}."
                is UpdateState.Downloading -> "Scarico la versione ${state.release.version}: ${(state.progress * 100).roundToInt()}%"
                is UpdateState.Ready -> "La versione ${state.release.version} è pronta: conferma l'installazione nella schermata di Android."
                is UpdateState.Failed ->
                    if (state.release == null) "Controllo non riuscito. Sei connesso a internet?"
                    else "Download non riuscito. Controlla la connessione e riprova."
            },
        )
        when (state) {
            is UpdateState.Available -> ChunkyButton(
                "Scarica e installa",
                onClick = { updater.download(state.release) },
                caption = megabytes(state.release.bytes),
            )
            is UpdateState.Downloading -> ProgressGroove(state.progress)
            is UpdateState.Ready -> ChunkyButton("Installa", onClick = { updater.install(state.file) })
            is UpdateState.Failed -> {
                val release = state.release
                if (release != null) {
                    ChunkyButton("Riprova il download", onClick = { updater.download(release) })
                } else {
                    GhostButton("Riprova", onClick = updater::check)
                }
            }
            UpdateState.Checking -> Unit
            UpdateState.Idle, UpdateState.UpToDate -> GhostButton("Controlla aggiornamenti", onClick = updater::check)
        }
    }
}

private fun megabytes(bytes: Long): String? =
    if (bytes > 0) String.format(Locale.ITALY, "%.1f MB", bytes / 1_000_000.0) else null

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.055f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SettingTitle(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (description != null) {
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.Cream.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val sfx = LocalSfx.current
    // the whole row is the switch, not just the little thumb
    Row(
        Modifier.toggleable(value = checked, role = Role.Switch) {
            sfx?.play(Sound.TAP)
            onChange(it)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingTitle(title, description, Modifier.weight(1f))
        GameSwitch(checked)
    }
}

@Composable
private fun VolumeSlider(title: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (value <= 0f) "muto" else "${(value * 100).roundToInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Sun,
            )
        }
        GameSlider(value, onChange, onDone)
    }
}
