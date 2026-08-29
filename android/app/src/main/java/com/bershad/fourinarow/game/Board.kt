package com.bershad.fourinarow.game

/**
 * The board, as a bitboard.
 *
 * Seven columns of six, but stored SEVEN bits per column, bottom row first. The extra
 * bit at the top of each column is a permanently-empty sentinel: it stops a vertical
 * shift from wrapping the top of one column into the bottom of the next, which is what
 * makes the whole win check four shift-and-mask pairs instead of a nested loop.
 *
 *      col 0        col 1
 *      bit 6  <- sentinel, never set
 *      bit 5  row 5 (top)
 *      ...
 *      bit 0  row 0 (bottom)          bit 7 .. bit 13
 *
 * Two longs describe any position:
 *   [current] the stones of the player whose turn it is
 *   [mask]    every stone on the board, either colour
 * so the other player's stones are simply `current xor mask`. Playing a move flips the
 * perspective (`current = current xor mask`), which is why the search can be a plain
 * negamax with no "who am I" bookkeeping.
 */
const val COLS = 7
const val ROWS = 6

private const val H1 = ROWS + 1          // 7 bits per column, top one is the sentinel
private const val SIZE = COLS * H1       // 49 bits, comfortably inside a Long

internal fun bottomMask(col: Int): Long = 1L shl (col * H1)
internal fun topMask(col: Int): Long = 1L shl (col * H1 + ROWS - 1)
internal fun columnMask(col: Int): Long = ((1L shl ROWS) - 1) shl (col * H1)
internal fun cellBit(col: Int, row: Int): Long = 1L shl (col * H1 + row)

/** True if [pos] contains four in a row in any direction. */
internal fun alignment(pos: Long): Boolean {
    // vertical (step 1), horizontal (step H1), and the two diagonals (H1-1, H1+1).
    var m = pos and (pos shr 1)
    if (m and (m shr 2) != 0L) return true
    m = pos and (pos shr H1)
    if (m and (m shr 2 * H1) != 0L) return true
    m = pos and (pos shr (H1 - 1))
    if (m and (m shr 2 * (H1 - 1)) != 0L) return true
    m = pos and (pos shr (H1 + 1))
    if (m and (m shr 2 * (H1 + 1)) != 0L) return true
    return false
}

/** Every four-in-a-row window on the board: 24 horizontal, 21 vertical, 24 diagonal. */
internal val WINDOWS: LongArray = buildList {
    val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
    for ((dc, dr) in dirs.map { it[0] to it[1] }) {
        for (c in 0 until COLS) for (r in 0 until ROWS) {
            val lastC = c + 3 * dc
            val lastR = r + 3 * dr
            if (lastC !in 0 until COLS || lastR !in 0 until ROWS) continue
            var m = 0L
            for (i in 0..3) m = m or cellBit(c + i * dc, r + i * dr)
            add(m)
        }
    }
}.toLongArray()

enum class Player { RED, YELLOW;
    val other: Player get() = if (this == RED) YELLOW else RED
}

data class Cell(val col: Int, val row: Int)

class Board {
    var current: Long = 0L
        private set
    var mask: Long = 0L
        private set
    var moves: Int = 0
        private set

    private val history = IntArray(COLS * ROWS)

    /** Which colour moves first. Set once per game; everything else derives from it. */
    var firstPlayer: Player = Player.RED
        private set

    fun reset(first: Player) {
        current = 0L
        mask = 0L
        moves = 0
        firstPlayer = first
    }

    val playerToMove: Player
        get() = if (moves % 2 == 0) firstPlayer else firstPlayer.other

    fun canPlay(col: Int): Boolean = col in 0 until COLS && (mask and topMask(col)) == 0L

    val isFull: Boolean get() = moves == COLS * ROWS

    /** True if dropping into [col] wins the game for the player to move. */
    fun isWinningMove(col: Int): Boolean {
        if (!canPlay(col)) return false
        val after = current or ((mask + bottomMask(col)) and columnMask(col))
        return alignment(after)
    }

    fun play(col: Int) {
        require(canPlay(col)) { "column $col is full or out of range" }
        history[moves] = col
        current = current xor mask
        mask = mask or (mask + bottomMask(col))
        moves++
    }

    fun undo() {
        require(moves > 0) { "nothing to undo" }
        moves--
        val col = history[moves]
        val top = java.lang.Long.highestOneBit(mask and columnMask(col))
        mask = mask xor top
        current = current xor mask
    }

    /** The row a disc would land in if dropped into [col], or -1 if the column is full. */
    fun landingRow(col: Int): Int {
        if (!canPlay(col)) return -1
        for (r in 0 until ROWS) if (mask and cellBit(col, r) == 0L) return r
        return -1
    }

    /** The stones belonging to [firstPlayer], whoever is on move right now. */
    private val firstPlayerStones: Long
        get() = if (moves % 2 == 0) current else current xor mask

    fun discAt(col: Int, row: Int): Player? {
        val bit = cellBit(col, row)
        if (mask and bit == 0L) return null
        return if (firstPlayerStones and bit != 0L) firstPlayer else firstPlayer.other
    }

    /** True if the player who moved LAST has just made four in a row. */
    val lastMoveWon: Boolean
        get() = moves > 0 && alignment(current xor mask)

    val winner: Player? get() = if (lastMoveWon) playerToMove.other else null

    /**
     * The four cells of the winning line, for the UI to highlight. If several lines
     * complete on the same move, the first one found is returned.
     */
    fun winningCells(): List<Cell> {
        if (!lastMoveWon) return emptyList()
        val stones = current xor mask
        for (w in WINDOWS) {
            if (stones and w == w) {
                val cells = ArrayList<Cell>(4)
                for (c in 0 until COLS) for (r in 0 until ROWS) {
                    if (w and cellBit(c, r) != 0L) cells.add(Cell(c, r))
                }
                return cells
            }
        }
        return emptyList()
    }

    fun copy(): Board {
        val b = Board()
        b.current = current
        b.mask = mask
        b.moves = moves
        b.firstPlayer = firstPlayer
        history.copyInto(b.history)
        return b
    }
}
