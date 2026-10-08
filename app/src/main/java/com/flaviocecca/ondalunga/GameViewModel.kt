package com.flaviocecca.ondalunga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flaviocecca.ondalunga.game.Engine
import com.flaviocecca.ondalunga.game.GameState
import com.flaviocecca.ondalunga.game.Mode
import com.flaviocecca.ondalunga.game.Phase
import com.flaviocecca.ondalunga.game.Side
import com.flaviocecca.ondalunga.update.Updater

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = Engine()

    val settings = SettingsStore(application)
    val updater = Updater(application, viewModelScope)

    /** `null` while on the home screen. */
    var state by mutableStateOf<GameState?>(null)
        private set

    private inline fun update(block: (GameState) -> GameState) {
        state = state?.let(block)
    }

    fun start(mode: Mode, teamNames: List<String>) {
        state = engine.newGame(mode, teamNames, settings.value.rules)
    }

    fun quit() {
        state = null
    }

    fun psychicReady() = update { it.copy(phase = Phase.SPIN) }

    fun spinStarted() = update { if (it.phase == Phase.SPIN) it.copy(spun = false) else it }

    fun spinEnded() = update { if (it.phase == Phase.SPIN) engine.spin(it) else it }

    fun openScreen() = update { it.copy(phase = Phase.PSYCHIC) }

    fun swapCard() = update(engine::swapCard)

    fun setClue(clue: String) = update { it.copy(clue = clue) }

    fun hideTarget() = update { it.copy(phase = Phase.GUESS) }

    fun setGuess(guess: Float) = update { it.copy(guess = guess) }

    fun confirmGuess() = update {
        if (it.mode == Mode.TEAMS && it.rules.sideBet) it.copy(phase = Phase.LEFT_RIGHT) else engine.resolve(it)
    }

    fun chooseSide(side: Side) = update { engine.resolve(it.copy(sideGuess = side)) }

    fun next() = update(engine::nextRound)
}
