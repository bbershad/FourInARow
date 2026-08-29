package com.bershad.fourinarow

import com.bershad.fourinarow.game.Board
import com.bershad.fourinarow.game.COLS
import com.bershad.fourinarow.game.Cell
import com.bershad.fourinarow.game.Player
import com.bershad.fourinarow.game.ROWS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    /** Build a position from a string of column digits, red moving first by default. */
    private fun board(first: Player = Player.RED, moves: String = ""): Board {
        val b = Board()
        b.reset(first)
        moves.forEach { b.play(it - '0') }
        return b
    }

    @Test
    fun `empty board has no winner and every column playable`() {
        val b = board()
        assertNull(b.winner)
        assertFalse(b.isFull)
        for (c in 0 until COLS) assertTrue(b.canPlay(c))
        assertEquals(Player.RED, b.playerToMove)
    }

    @Test
    fun `turn alternates from whoever goes first`() {
        val b = board(Player.YELLOW)
        assertEquals(Player.YELLOW, b.playerToMove)
        b.play(0)
        assertEquals(Player.RED, b.playerToMove)
        b.play(1)
        assertEquals(Player.YELLOW, b.playerToMove)
    }

    @Test
    fun `discs stack bottom up and report their owner`() {
        val b = board(Player.RED, "00")   // red on the floor of column 0, yellow on top of it
        assertEquals(Player.RED, b.discAt(0, 0))
        assertEquals(Player.YELLOW, b.discAt(0, 1))
        assertNull(b.discAt(0, 2))
        assertEquals(2, b.landingRow(0))
    }

    @Test
    fun `ownership is reported the same way whoever moved first`() {
        val b = board(Player.YELLOW, "00")
        assertEquals(Player.YELLOW, b.discAt(0, 0))
        assertEquals(Player.RED, b.discAt(0, 1))
    }

    @Test
    fun `vertical four wins`() {
        val b = board(Player.RED, "0101010")
        assertEquals(Player.RED, b.winner)
        assertEquals(
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3)),
            b.winningCells().sortedWith(compareBy({ it.col }, { it.row }))
        )
    }

    @Test
    fun `horizontal four wins`() {
        val b = board(Player.RED, "0011223")
        assertEquals(Player.RED, b.winner)
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)),
            b.winningCells().sortedBy { it.col }
        )
    }

    @Test
    fun `rising diagonal wins`() {
        // Red ends on (0,0) (1,1) (2,2) (3,3); columns 4-6 are just somewhere to put
        // red's spare discs without accidentally making a second line.
        val b = board(Player.RED, "0112522343633")
        assertEquals(Player.RED, b.winner)
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 1), Cell(2, 2), Cell(3, 3)),
            b.winningCells().sortedBy { it.col }
        )
    }

    @Test
    fun `falling diagonal wins`() {
        // The same game mirrored left to right: red on (3,3) (4,2) (5,1) (6,0).
        val b = board(Player.RED, "6554144323033")
        assertEquals(Player.RED, b.winner)
        assertEquals(
            listOf(Cell(3, 3), Cell(4, 2), Cell(5, 1), Cell(6, 0)),
            b.winningCells().sortedBy { it.col }
        )
    }

    @Test
    fun `no phantom win where one column's top meets the next column's bottom`() {
        // Those cells are adjacent bits in the bitboard but not adjacent on the board.
        // The sentinel bit above each column is the only thing keeping them apart, so
        // this fills the top of column 0 and the bottom of column 1 with one colour.
        val b = Board()
        b.reset(Player.RED)
        // Red gets rows 0,2,4 of column 0; yellow gets 1,3,5.
        repeat(ROWS) { b.play(0) }
        assertNull(b.winner)
        // Now give red the bottom of column 1 as well - still no line.
        b.play(1)
        assertNull(b.winner)
    }

    @Test
    fun `a full column cannot be played`() {
        val b = board()
        repeat(ROWS) { b.play(3) }
        assertFalse(b.canPlay(3))
        assertEquals(-1, b.landingRow(3))
        assertTrue(b.canPlay(2))
    }

    @Test
    fun `columns outside the board are never playable`() {
        val b = board()
        assertFalse(b.canPlay(-1))
        assertFalse(b.canPlay(COLS))
    }

    @Test
    fun `isWinningMove agrees with actually playing it`() {
        val b = board(Player.RED, "010101")   // red holds three of column 0
        assertTrue(b.isWinningMove(0))
        assertFalse(b.isWinningMove(5))
        b.play(0)
        assertEquals(Player.RED, b.winner)
    }

    @Test
    fun `undo restores the exact previous position`() {
        val b = board()
        val seq = "3342156035241"
        val snapshots = ArrayList<Triple<Long, Long, Int>>()
        seq.forEach {
            snapshots.add(Triple(b.current, b.mask, b.moves))
            b.play(it - '0')
        }
        for (i in seq.indices.reversed()) {
            b.undo()
            assertEquals(snapshots[i], Triple(b.current, b.mask, b.moves))
        }
        assertEquals(0, b.moves)
    }

    @Test
    fun `a board filled with no line is a draw`() {
        // Play random games that decline any move completing a four, restarting whenever
        // the position paints itself into a corner, until one fills all 42 cells.
        val rng = Random(20260828)
        var drawn: Board? = null
        repeat(500) {
            val b = Board()
            b.reset(Player.RED)
            while (!b.isFull) {
                val safe = (0 until COLS).filter { c -> b.canPlay(c) && !b.isWinningMove(c) }
                if (safe.isEmpty()) break
                b.play(safe[rng.nextInt(safe.size)])
            }
            if (b.isFull) {
                drawn = b
                return@repeat
            }
        }
        val d = requireNotNull(drawn) { "no drawn fill found in 500 attempts" }
        assertTrue(d.isFull)
        assertEquals(COLS * ROWS, d.moves)
        assertNull(d.winner)
        assertTrue(d.winningCells().isEmpty())
        for (c in 0 until COLS) assertFalse(d.canPlay(c))
    }

    @Test
    fun `copy is independent of the original`() {
        val b = board(Player.RED, "3344")
        val c = b.copy()
        c.play(5)
        assertEquals(4, b.moves)
        assertEquals(5, c.moves)
        assertNull(b.discAt(5, 0))
    }

    @Test
    fun `reset clears the board and sets who starts`() {
        val b = board(Player.RED, "334455")
        b.reset(Player.YELLOW)
        assertEquals(0, b.moves)
        assertEquals(0L, b.mask)
        assertEquals(Player.YELLOW, b.playerToMove)
        assertNull(b.discAt(3, 0))
    }
}
