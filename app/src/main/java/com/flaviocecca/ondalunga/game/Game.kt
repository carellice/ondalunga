package com.flaviocecca.ondalunga.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * [DUEL] is one player against one: the psychic gives the clue to the opponent, who aims
 * the needle and takes the points. Scoring is otherwise the same race as [TEAMS].
 */
enum class Mode { TEAMS, DUEL, COOP }

enum class Phase { PASS, SPIN, PSYCHIC, GUESS, LEFT_RIGHT, REVEAL, GAME_OVER }

enum class Side { LEFT, RIGHT }

/** House rules, chosen in the settings before a game starts. */
data class RuleSet(
    val targetScore: Int = 10,
    /** Opponents bet on which side of the needle the target lies. */
    val sideBet: Boolean = true,
    /** A bullseye while still behind earns another turn. */
    val catchUp: Boolean = true,
    /** The psychic may trade the card once per round. */
    val cardSwap: Boolean = true,
    val coopCards: Int = 7,
)

/**
 * [bonus] is an extra turn in team play (bullseye while still behind)
 * and an extra card in cooperative play (bullseye).
 */
data class RoundResult(
    val zone: Int,
    val points: Int,
    val opponentScored: Boolean,
    val bonus: Boolean,
)

/** Dial positions ([target], [guess]) are degrees along the half circle: 0 = far left, 180 = far right. */
data class GameState(
    val mode: Mode,
    val teamNames: List<String>,
    val rules: RuleSet,
    val scores: List<Int>,
    val activeTeam: Int,
    val round: Int,
    val cardsLeft: Int,
    val phase: Phase,
    val spectrum: Spectrum,
    val spareSpectrum: Spectrum?,
    val target: Float,
    val spun: Boolean = false,
    val guess: Float = 90f,
    val clue: String = "",
    val sideGuess: Side? = null,
    val result: RoundResult? = null,
    val finished: Boolean = false,
) {
    val opponent: Int get() = 1 - activeTeam

    /** Two sides racing to the target score, as opposed to everyone playing together. */
    val versus: Boolean get() = mode != Mode.COOP

    /** Whose hands the phone is in while the target is visible. In a duel that is the rival of whoever scores. */
    val psychicSide: Int get() = if (mode == Mode.DUEL) opponent else activeTeam
    val leader: Int get() = scores.indices.maxBy { scores[it] }
}

object Rules {
    const val WEDGE = 8f
    const val TARGET_MIN = 12f
    const val TARGET_MAX = 168f

    /** Value of the wedge the needle landed on: 4 (centre), 3, 2 or 0 (miss). */
    fun zone(target: Float, guess: Float): Int {
        val distance = abs(target - guess)
        return when {
            distance <= WEDGE * 0.5f -> 4
            distance <= WEDGE * 1.5f -> 3
            distance <= WEDGE * 2.5f -> 2
            else -> 0
        }
    }

    fun sideOfTarget(target: Float, guess: Float): Side? = when {
        target < guess -> Side.LEFT
        target > guess -> Side.RIGHT
        else -> null
    }
}

class Engine(
    private val random: Random = Random.Default,
    private val cards: List<Spectrum> = Spectra.all,
) {
    private val deck = ArrayDeque<Spectrum>()

    private fun draw(): Spectrum {
        if (deck.isEmpty()) deck.addAll(cards.shuffled(random))
        return deck.removeFirst()
    }

    private fun randomTarget(): Float =
        Rules.TARGET_MIN + random.nextFloat() * (Rules.TARGET_MAX - Rules.TARGET_MIN)

    fun newGame(mode: Mode, teamNames: List<String>, rules: RuleSet = RuleSet()): GameState {
        val first = if (mode != Mode.COOP) random.nextInt(2) else 0
        return GameState(
            mode = mode,
            teamNames = teamNames,
            rules = rules,
            // the team that plays second starts one point ahead
            scores = if (mode != Mode.COOP) List(2) { if (it == first) 0 else 1 } else listOf(0),
            activeTeam = first,
            round = 1,
            cardsLeft = if (mode == Mode.COOP) rules.coopCards else 0,
            phase = Phase.PASS,
            spectrum = draw(),
            spareSpectrum = if (rules.cardSwap) draw() else null,
            target = randomTarget(),
        )
    }

    /** The wheel came to rest after a spin: wherever it was, the target is now somewhere new. */
    fun spin(s: GameState): GameState = s.copy(target = randomTarget(), spun = true)

    /** The psychic may trade the card once per round. */
    fun swapCard(s: GameState): GameState {
        val spare = s.spareSpectrum ?: return s
        return s.copy(spectrum = spare, spareSpectrum = null)
    }

    fun resolve(s: GameState): GameState {
        val zone = Rules.zone(s.target, s.guess)
        return when (s.mode) {
            Mode.TEAMS, Mode.DUEL -> {
                val opponentScored = zone != 4 && s.sideGuess != null &&
                    s.sideGuess == Rules.sideOfTarget(s.target, s.guess)
                val scores = s.scores.toMutableList()
                scores[s.activeTeam] += zone
                if (opponentScored) scores[s.opponent] += 1
                val bonus = s.rules.catchUp && zone == 4 && scores[s.activeTeam] < scores[s.opponent]
                s.copy(
                    phase = Phase.REVEAL,
                    scores = scores,
                    result = RoundResult(zone, zone, opponentScored, bonus),
                    // a tie at or past the target score keeps the game going
                    finished = scores.max() >= s.rules.targetScore && scores[0] != scores[1],
                )
            }

            Mode.COOP -> {
                val bullseye = zone == 4
                val points = if (bullseye) 3 else zone
                val cardsLeft = s.cardsLeft - 1 + if (bullseye) 1 else 0
                s.copy(
                    phase = Phase.REVEAL,
                    scores = listOf(s.scores[0] + points),
                    cardsLeft = cardsLeft,
                    result = RoundResult(zone, points, opponentScored = false, bonus = bullseye),
                    finished = cardsLeft == 0,
                )
            }
        }
    }

    fun nextRound(s: GameState): GameState {
        if (s.finished) return s.copy(phase = Phase.GAME_OVER)
        val keepsTurn = s.mode == Mode.COOP || s.result?.bonus == true
        return s.copy(
            activeTeam = if (keepsTurn) s.activeTeam else s.opponent,
            round = s.round + 1,
            phase = Phase.PASS,
            spectrum = draw(),
            spareSpectrum = if (s.rules.cardSwap) draw() else null,
            // wheel and needle stay where the last round left them, like the physical device
            spun = false,
            clue = "",
            sideGuess = null,
            result = null,
        )
    }
}
