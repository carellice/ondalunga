package com.flaviocecca.ondalunga.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {
    private val engine = Engine(Random(7))

    private fun teams(scores: List<Int>, target: Float, guess: Float, side: Side?, rules: RuleSet = RuleSet()) =
        engine.newGame(Mode.TEAMS, listOf("A", "B"), rules).copy(
            activeTeam = 0, scores = scores, target = target, guess = guess, sideGuess = side,
        )

    @Test
    fun zonesFollowDistanceFromTarget() {
        assertEquals(4, Rules.zone(90f, 90f))
        assertEquals(4, Rules.zone(90f, 86f))
        assertEquals(3, Rules.zone(90f, 85f))
        assertEquals(3, Rules.zone(90f, 102f))
        assertEquals(2, Rules.zone(90f, 103f))
        assertEquals(2, Rules.zone(90f, 70f))
        assertEquals(0, Rules.zone(90f, 69f))
    }

    @Test
    fun newTeamGameGivesSecondTeamOnePoint() {
        val s = engine.newGame(Mode.TEAMS, listOf("A", "B"))
        assertEquals(0, s.scores[s.activeTeam])
        assertEquals(1, s.scores[s.opponent])
        assertTrue(s.target in Rules.TARGET_MIN..Rules.TARGET_MAX)
    }

    @Test
    fun opponentScoresOnCorrectSide() {
        val s = engine.resolve(teams(listOf(0, 1), target = 60f, guess = 70f, side = Side.LEFT))
        assertEquals(listOf(3, 2), s.scores)
        assertTrue(s.result!!.opponentScored)
        assertEquals(1, engine.nextRound(s).activeTeam)
    }

    @Test
    fun opponentGetsNothingOnWrongSideOrBullseye() {
        val wrong = engine.resolve(teams(listOf(0, 1), target = 60f, guess = 70f, side = Side.RIGHT))
        assertEquals(listOf(3, 1), wrong.scores)
        val bullseye = engine.resolve(teams(listOf(0, 1), target = 60f, guess = 62f, side = Side.LEFT))
        assertEquals(listOf(4, 1), bullseye.scores)
        assertFalse(bullseye.result!!.opponentScored)
    }

    @Test
    fun bullseyeWhileBehindGrantsAnotherTurn() {
        val s = engine.resolve(teams(listOf(0, 6), target = 60f, guess = 60f, side = Side.LEFT))
        assertTrue(s.result!!.bonus)
        val next = engine.nextRound(s)
        assertEquals(0, next.activeTeam)
        assertEquals(Phase.PASS, next.phase)
        assertNull(next.result)
    }

    @Test
    fun gameEndsAtTargetScoreButNotOnTie() {
        val win = engine.resolve(teams(listOf(7, 5), target = 60f, guess = 68f, side = Side.RIGHT))
        assertEquals(listOf(10, 5), win.scores)
        assertTrue(win.finished)
        assertEquals(Phase.GAME_OVER, engine.nextRound(win).phase)
        assertEquals(0, win.leader)

        val tie = engine.resolve(teams(listOf(8, 9), target = 60f, guess = 75f, side = Side.LEFT))
        assertEquals(listOf(10, 10), tie.scores)
        assertFalse(tie.finished)
    }

    @Test
    fun coopBullseyeScoresThreeAndAddsCard() {
        val start = engine.newGame(Mode.COOP, listOf("Tutti"))
        assertEquals(7, start.cardsLeft)
        val bullseye = engine.resolve(start.copy(target = 100f, guess = 100f))
        assertEquals(listOf(3), bullseye.scores)
        assertEquals(7, bullseye.cardsLeft)
        val miss = engine.resolve(bullseye.copy(target = 100f, guess = 20f))
        assertEquals(7 - 1, miss.cardsLeft)
        val last = engine.resolve(miss.copy(cardsLeft = 1, target = 100f, guess = 110f))
        assertEquals(listOf(6), last.scores)
        assertTrue(last.finished)
    }

    @Test
    fun spinningMovesTheTargetAndIsResetEachRound() {
        val s = engine.newGame(Mode.COOP, listOf("Tutti"))
        assertFalse(s.spun)
        val spun = engine.spin(s)
        assertTrue(spun.spun)
        assertTrue(spun.target != s.target)
        assertTrue(spun.target in Rules.TARGET_MIN..Rules.TARGET_MAX)
        assertFalse(engine.nextRound(engine.resolve(spun)).spun)
    }

    @Test
    fun spinsCoverTheWholeDial() {
        var s = engine.newGame(Mode.COOP, listOf("Tutti"))
        val targets = List(300) { s = engine.spin(s); s.target }
        assertTrue(targets.min() < 30f)
        assertTrue(targets.max() > 150f)
        assertTrue(targets.count { it in 80f..100f } < 80)
    }

    @Test
    fun houseRulesCanBeSwitchedOff() {
        val rules = RuleSet(targetScore = 5, catchUp = false, cardSwap = false, coopCards = 5)
        val behind = engine.resolve(teams(listOf(0, 4), target = 60f, guess = 60f, side = Side.LEFT, rules = rules))
        assertFalse(behind.result!!.bonus)
        assertEquals(1, engine.nextRound(behind).activeTeam)
        assertNull(behind.spareSpectrum)
        assertNull(engine.nextRound(behind).spareSpectrum)
        val win = engine.resolve(teams(listOf(2, 0), target = 60f, guess = 68f, side = null, rules = rules))
        assertEquals(listOf(5, 0), win.scores)
        assertTrue(win.finished)
        assertEquals(5, engine.newGame(Mode.COOP, listOf("Tutti"), rules).cardsLeft)
    }

    @Test
    fun cardCanBeSwappedOnlyOnce() {
        val s = engine.newGame(Mode.COOP, listOf("Tutti"))
        val swapped = engine.swapCard(s)
        assertEquals(s.spareSpectrum, swapped.spectrum)
        assertEquals(swapped, engine.swapCard(swapped))
    }
}
