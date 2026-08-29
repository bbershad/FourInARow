package com.bershad.fourinarow.game

import kotlin.random.Random

enum class Difficulty(val label: String, val blurb: String) {
    EASY("Easy", "Takes the win in front of it, misses most traps."),
    MEDIUM("Medium", "Looks four moves ahead. A fair fight."),
    HARD("Hard", "Looks ten moves ahead. Expect to lose.")
}

/**
 * The computer opponent.
 *
 * All three levels share one negamax search with alpha-beta pruning; what separates them
 * is how deep it looks and how much it is allowed to overlook.
 *
 *  - EASY   no search at all. It takes a win it can see this move, blocks a loss only
 *           about half the time, and is otherwise random. Beatable by a child, which is
 *           the point.
 *  - MEDIUM depth 4 - its move, your reply, its move, your reply. Enough to set up and
 *           to defend, not enough to see a two-move trap coming.
 *  - HARD   depth 10 with a transposition table. It sees forced wins and forced losses
 *           long before they arrive.
 *
 * Scores are always from the point of view of the player to move, so the search never has
 * to track which colour it is playing.
 */
object Ai {

    const val WIN = 1_000_000
    private const val INF = 10_000_000

    /** Centre-out. Strong moves are usually central, and trying them first prunes hardest. */
    private val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)

    private val CENTRE_MASK = columnMask(3)

    private fun depthFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 0
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 10
    }

    /**
     * Pick a column for the player to move on [board]. The board is not modified.
     * [rng] is a parameter rather than a global so a test can replay an exact game.
     */
    fun chooseMove(board: Board, difficulty: Difficulty, rng: Random = Random.Default): Int {
        val b = board.copy()
        val legal = (0 until COLS).filter { b.canPlay(it) }
        require(legal.isNotEmpty()) { "no legal move: the board is full" }

        // Every level takes a win it can see right now. An opponent that walks past its own
        // four-in-a-row does not read as "easy", it reads as broken.
        ORDER.firstOrNull { b.canPlay(it) && b.isWinningMove(it) }?.let { return it }

        if (difficulty == Difficulty.EASY) return easyMove(b, legal, rng)

        val tt = if (difficulty == Difficulty.HARD) TranspositionTable() else null
        val depth = depthFor(difficulty)

        var best = Int.MIN_VALUE
        val bestCols = ArrayList<Int>(COLS)
        for (col in ORDER) {
            if (!b.canPlay(col)) continue
            b.play(col)
            val score = -negamax(b, depth - 1, -INF, INF, tt)
            b.undo()
            if (score > best) {
                best = score
                bestCols.clear()
                bestCols.add(col)
            } else if (score == best) {
                bestCols.add(col)
            }
        }
        // Tie-break at random so it does not play the identical game every time.
        return bestCols[rng.nextInt(bestCols.size)]
    }

    /**
     * Easy has already been offered the win above. Here it blocks a loss only half the
     * time and is otherwise random: a plausible beginner rather than a bot that is either
     * perfect or nonsensical.
     */
    private fun easyMove(b: Board, legal: List<Int>, rng: Random): Int {
        val threats = legal.filter { opponentWouldWinAt(b, it) }
        if (threats.isNotEmpty() && rng.nextFloat() < 0.5f) {
            return threats[rng.nextInt(threats.size)]
        }
        return legal[rng.nextInt(legal.size)]
    }

    /** True if the opponent, moving next, would win by dropping into [col]. */
    internal fun opponentWouldWinAt(b: Board, col: Int): Boolean {
        val row = b.landingRow(col)
        if (row < 0) return false
        val theirs = b.current xor b.mask
        return alignment(theirs or cellBit(col, row))
    }

    private fun negamax(b: Board, depth: Int, alpha0: Int, beta0: Int, tt: TranspositionTable?): Int {
        if (b.isFull) return 0

        // A win available right now ends the line. Subtracting the move count makes a win
        // in three plies score higher than the same win in seven, so it finishes games
        // instead of shuffling around in a position it has already won.
        for (col in ORDER) {
            if (b.canPlay(col) && b.isWinningMove(col)) return WIN - b.moves
        }
        if (depth <= 0) return evaluate(b)

        var alpha = alpha0
        var beta = beta0

        // position + mask is a unique key for a Connect Four position: adding the two sets
        // a marker bit just above the top stone of every column, so no two positions collide.
        val key = b.current + b.mask
        val cached = tt?.get(key, depth)
        if (cached != null) {
            when (cached.flag) {
                TT_EXACT -> return cached.score
                TT_LOWER -> if (cached.score > alpha) alpha = cached.score
                TT_UPPER -> if (cached.score < beta) beta = cached.score
            }
            if (alpha >= beta) return cached.score
        }

        var best = -INF
        for (col in ORDER) {
            if (!b.canPlay(col)) continue
            b.play(col)
            val score = -negamax(b, depth - 1, -beta, -alpha, tt)
            b.undo()
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }

        val flag = when {
            best <= alpha0 -> TT_UPPER
            best >= beta0 -> TT_LOWER
            else -> TT_EXACT
        }
        tt?.put(key, depth, best, flag)
        return best
    }

    /**
     * Positional score for the player to move, used when the search runs out of depth.
     *
     * Each of the 69 four-cell windows is worth something only while one side still has it
     * to itself: three of ours with a gap is a real threat, two is a start, a window both
     * sides have touched is dead and scores nothing. Centre stones are worth a little extra
     * because more winning lines run through the middle column than through any other.
     */
    internal fun evaluate(b: Board): Int {
        val mine = b.current
        val theirs = b.current xor b.mask
        var score = 0
        for (w in WINDOWS) {
            val m = java.lang.Long.bitCount(mine and w)
            val t = java.lang.Long.bitCount(theirs and w)
            if (m > 0 && t > 0) continue
            if (m > 0) score += WINDOW_VALUE[m] else if (t > 0) score -= WINDOW_VALUE[t]
        }
        score += 3 * (java.lang.Long.bitCount(mine and CENTRE_MASK) -
                java.lang.Long.bitCount(theirs and CENTRE_MASK))
        return score
    }

    private val WINDOW_VALUE = intArrayOf(0, 1, 10, 50, WIN)

    // ---- transposition table -------------------------------------------------------
    // The same position is reached constantly by different move orders, so remembering
    // what a position was worth is most of what makes depth 10 fast enough to run between
    // taps. Open addressing, newest wins: a slot lost to a collision costs a re-search,
    // never a wrong answer, because the full key is stored and checked on read.

    private const val TT_EXACT = 0
    private const val TT_LOWER = 1
    private const val TT_UPPER = 2

    private class Entry(val score: Int, val flag: Int)

    private class TranspositionTable(bits: Int = 18) {
        private val size = 1 shl bits
        private val indexMask = size - 1
        private val keys = LongArray(size)
        private val scores = IntArray(size)
        private val depths = ByteArray(size)
        private val flags = ByteArray(size)

        // Long.hashCode folds the high word into the low one, which leaves the useful bits
        // clumped for a table this size. Multiply by a 64-bit odd constant first to spread
        // them, then take the high bits.
        private fun slot(key: Long): Int =
            (((key * -7046029254386353131L) ushr 40).toInt()) and indexMask

        fun get(key: Long, depth: Int): Entry? {
            val i = slot(key)
            if (keys[i] != key || depths[i] < depth) return null
            return Entry(scores[i], flags[i].toInt())
        }

        fun put(key: Long, depth: Int, score: Int, flag: Int) {
            val i = slot(key)
            keys[i] = key
            scores[i] = score
            depths[i] = depth.toByte()
            flags[i] = flag.toByte()
        }
    }
}
