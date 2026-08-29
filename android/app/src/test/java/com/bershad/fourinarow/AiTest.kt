package com.bershad.fourinarow

import com.bershad.fourinarow.game.Ai
import com.bershad.fourinarow.game.Board
import com.bershad.fourinarow.game.COLS
import com.bershad.fourinarow.game.Difficulty
import com.bershad.fourinarow.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiTest {

    private fun board(moves: String, first: Player = Player.RED): Board {
        val b = Board()
        b.reset(first)
        moves.forEach { b.play(it - '0') }
        return b
    }

    /** Play one game and return the winner, or null for a draw. */
    private fun playGame(first: Difficulty, second: Difficulty, seed: Int): Player? {
        val rng = Random(seed)
        val b = Board()
        b.reset(Player.RED)
        while (!b.isFull) {
            val level = if (b.playerToMove == Player.RED) first else second
            val col = Ai.chooseMove(b, level, rng)
            assertTrue("AI chose an unplayable column $col", b.canPlay(col))
            b.play(col)
            b.winner?.let { return it }
        }
        return null
    }

    @Test
    fun `every level takes a win it can see`() {
        // Red holds the bottom three of column 0 and is on move.
        val b = board("010101")
        assertEquals(Player.RED, b.playerToMove)
        for (level in Difficulty.entries) {
            repeat(20) { seed ->
                assertEquals(
                    "$level walked past its own win",
                    0, Ai.chooseMove(b, level, Random(seed))
                )
            }
        }
    }

    @Test
    fun `medium and hard block an immediate loss`() {
        // Red threatens (0,3); yellow is on move and has nothing of its own.
        val b = board("01010")
        assertEquals(Player.YELLOW, b.playerToMove)
        for (level in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            repeat(20) { seed ->
                assertEquals(
                    "$level failed to block",
                    0, Ai.chooseMove(b, level, Random(seed))
                )
            }
        }
    }

    @Test
    fun `easy blocks sometimes but not always`() {
        val b = board("01010")
        var blocked = 0
        val trials = 400
        repeat(trials) { seed -> if (Ai.chooseMove(b, Difficulty.EASY, Random(seed)) == 0) blocked++ }
        val rate = blocked.toDouble() / trials
        // Easy is meant to feel like a beginner: it sees some threats and misses others.
        // If this ever reads 0.0 or 1.0 the level has silently become useless or perfect.
        assertTrue("easy blocked $rate of the time, expected roughly half", rate in 0.30..0.75)
    }

    @Test
    fun `hard opens in the middle`() {
        val b = Board()
        b.reset(Player.RED)
        repeat(5) { seed ->
            val col = Ai.chooseMove(b, Difficulty.HARD, Random(seed))
            assertTrue("hard opened on column $col", col in 2..4)
        }
    }

    @Test
    fun `hard never plays an illegal column in a full game`() {
        repeat(10) { seed -> playGame(Difficulty.HARD, Difficulty.HARD, seed) }
    }

    @Test
    fun `hard beats easy almost every time`() {
        var hardWins = 0
        val games = 12
        repeat(games) { i ->
            // Alternate who moves first so the result is not just a first-move advantage.
            val winner =
                if (i % 2 == 0) playGame(Difficulty.HARD, Difficulty.EASY, i)
                else playGame(Difficulty.EASY, Difficulty.HARD, i)
            val hardIs = if (i % 2 == 0) Player.RED else Player.YELLOW
            if (winner == hardIs) hardWins++
        }
        assertTrue("hard won only $hardWins of $games against easy", hardWins >= games - 1)
    }

    @Test
    fun `hard beats medium more often than not`() {
        var hardWins = 0
        var mediumWins = 0
        val games = 8
        repeat(games) { i ->
            val winner =
                if (i % 2 == 0) playGame(Difficulty.HARD, Difficulty.MEDIUM, 100 + i)
                else playGame(Difficulty.MEDIUM, Difficulty.HARD, 100 + i)
            val hardIs = if (i % 2 == 0) Player.RED else Player.YELLOW
            when (winner) {
                hardIs -> hardWins++
                null -> {}
                else -> mediumWins++
            }
        }
        assertTrue(
            "hard $hardWins, medium $mediumWins - the levels are not separated",
            hardWins > mediumWins
        )
    }

    @Test
    fun `hard answers fast enough to feel instant`() {
        // A move is made while the user watches, so the worst case matters more than the
        // average. Measured on the build VM; a phone is in the same ballpark.
        var worstMs = 0L
        var worstMove = -1
        repeat(3) { seed ->
            val rng = Random(seed)
            val b = Board()
            b.reset(Player.RED)
            while (!b.isFull && b.winner == null) {
                val t0 = System.nanoTime()
                val col = Ai.chooseMove(b, Difficulty.HARD, rng)
                val ms = (System.nanoTime() - t0) / 1_000_000
                if (ms > worstMs) { worstMs = ms; worstMove = b.moves }
                b.play(col)
            }
        }
        println("hard: worst move took ${worstMs}ms (at ply $worstMove)")
        assertTrue("hard took ${worstMs}ms on one move", worstMs < 2000)
    }

    @Test
    fun `the empty board is scored dead level`() {
        val b = Board()
        b.reset(Player.RED)
        assertEquals(0, Ai.evaluate(b))
    }

    @Test
    fun `chooseMove leaves the caller's board untouched`() {
        val b = board("3344")
        val before = Triple(b.current, b.mask, b.moves)
        Ai.chooseMove(b, Difficulty.HARD, Random(1))
        assertEquals(before, Triple(b.current, b.mask, b.moves))
    }

    @Test
    fun `every level stays inside the board on random positions`() {
        val rng = Random(7)
        repeat(200) {
            val b = Board()
            b.reset(if (rng.nextBoolean()) Player.RED else Player.YELLOW)
            repeat(rng.nextInt(0, 20)) {
                if (b.winner == null && !b.isFull) {
                    val legal = (0 until COLS).filter { c -> b.canPlay(c) }
                    b.play(legal[rng.nextInt(legal.size)])
                }
            }
            if (b.winner != null || b.isFull) return@repeat
            for (level in Difficulty.entries) {
                val col = Ai.chooseMove(b, level, rng)
                assertTrue("$level chose column $col", b.canPlay(col))
            }
        }
    }
}
