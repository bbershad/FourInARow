package com.bershad.fourinarow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bershad.fourinarow.game.Ai
import com.bershad.fourinarow.game.Board
import com.bershad.fourinarow.game.COLS
import com.bershad.fourinarow.game.Cell
import com.bershad.fourinarow.game.Difficulty
import com.bershad.fourinarow.game.Player
import com.bershad.fourinarow.game.ROWS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Mode { TWO_PLAYER, VS_COMPUTER }

/** In a game against the computer the person always plays red. */
val HUMAN: Player = Player.RED
val COMPUTER: Player = Player.YELLOW

data class GameState(
    val started: Boolean = false,
    val mode: Mode = Mode.TWO_PLAYER,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val grid: List<List<Player?>> = emptyGrid(),
    val toMove: Player = Player.RED,
    val starter: Player = Player.RED,
    val winner: Player? = null,
    val draw: Boolean = false,
    val winningCells: Set<Cell> = emptySet(),
    val lastDrop: Cell? = null,
    /** Increments on every disc played, so the board knows when to run the drop animation. */
    val moveIndex: Int = 0,
    val thinking: Boolean = false,
    val canUndo: Boolean = false,
    val scoreRed: Int = 0,
    val scoreYellow: Int = 0,
    val draws: Int = 0,
) {
    val over: Boolean get() = winner != null || draw
    val isHumanTurn: Boolean get() = mode == Mode.TWO_PLAYER || toMove == HUMAN
    /** True while the board should accept taps. */
    val acceptingInput: Boolean get() = started && !over && !thinking && isHumanTurn
}

internal fun emptyGrid(): List<List<Player?>> = List(COLS) { List(ROWS) { null } }

class GameViewModel : ViewModel() {

    private val board = Board()
    private val rng = Random.Default
    private var cpuJob: Job? = null
    private var scored = false

    var state by mutableStateOf(GameState())
        private set

    /** Start a fresh match. Who moves first is decided by a coin flip, every game. */
    fun startGame(mode: Mode, difficulty: Difficulty) {
        cpuJob?.cancel()
        state = state.copy(
            started = true,
            mode = mode,
            difficulty = difficulty,
            scoreRed = 0,
            scoreYellow = 0,
            draws = 0,
        )
        deal()
    }

    /** Another game in the same mode, keeping the running score. Starter is re-flipped. */
    fun newGame() {
        cpuJob?.cancel()
        deal()
    }

    fun backToMenu() {
        cpuJob?.cancel()
        state = GameState(scoreRed = 0, scoreYellow = 0)
    }

    private fun deal() {
        val starter = if (rng.nextBoolean()) Player.RED else Player.YELLOW
        board.reset(starter)
        scored = false
        state = state.copy(
            starter = starter,
            winner = null,
            draw = false,
            winningCells = emptySet(),
            lastDrop = null,
            moveIndex = 0,
            thinking = false,
        )
        publish()
        maybeMoveComputer()
    }

    fun drop(col: Int) {
        if (!state.acceptingInput) return
        if (!board.canPlay(col)) return
        val row = board.landingRow(col)
        board.play(col)
        publish(lastDrop = Cell(col, row))
        maybeMoveComputer()
    }

    /**
     * Take back the last move. Against the computer that means both its reply and the move
     * that prompted it, so control comes back to the person rather than to a board they
     * cannot act on.
     */
    fun undo() {
        if (state.thinking || !state.canUndo) return
        cpuJob?.cancel()
        board.undo()
        if (state.mode == Mode.VS_COMPUTER) {
            while (board.moves > 0 && board.playerToMove != HUMAN) board.undo()
        }
        scored = false
        state = state.copy(winner = null, draw = false, winningCells = emptySet(), thinking = false)
        publish(lastDrop = null)
    }

    private fun maybeMoveComputer() {
        val s = state
        if (s.mode != Mode.VS_COMPUTER || s.over || board.playerToMove != COMPUTER) return
        state = s.copy(thinking = true)
        cpuJob = viewModelScope.launch {
            // A pause even when the answer is instant: a reply with no delay at all reads
            // as a glitch rather than as a move.
            delay(420)
            val col = withContext(Dispatchers.Default) {
                Ai.chooseMove(board, state.difficulty, rng)
            }
            // An undo can land in the gap between the search returning and the move being
            // applied. Playing it then would put a disc on a board the person just cleared.
            if (!isActive) return@launch
            val row = board.landingRow(col)
            board.play(col)
            state = state.copy(thinking = false)
            publish(lastDrop = Cell(col, row))
        }
    }

    private fun publish(lastDrop: Cell? = state.lastDrop) {
        val winner = board.winner
        val draw = winner == null && board.isFull
        var red = state.scoreRed
        var yellow = state.scoreYellow
        var draws = state.draws
        if (!scored && (winner != null || draw)) {
            scored = true
            when (winner) {
                Player.RED -> red++
                Player.YELLOW -> yellow++
                null -> draws++
            }
        }
        val minMoves = if (state.mode == Mode.VS_COMPUTER && state.starter == COMPUTER) 2 else 1
        state = state.copy(
            grid = List(COLS) { c -> List(ROWS) { r -> board.discAt(c, r) } },
            toMove = board.playerToMove,
            winner = winner,
            draw = draw,
            winningCells = board.winningCells().toSet(),
            lastDrop = lastDrop,
            moveIndex = board.moves,
            canUndo = board.moves >= minMoves,
            scoreRed = red,
            scoreYellow = yellow,
            draws = draws,
        )
    }
}
