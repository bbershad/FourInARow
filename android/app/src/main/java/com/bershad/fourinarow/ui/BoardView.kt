package com.bershad.fourinarow.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.bershad.fourinarow.game.COLS
import com.bershad.fourinarow.game.Cell
import com.bershad.fourinarow.game.Player
import com.bershad.fourinarow.game.ROWS
import kotlin.math.min

/**
 * The board.
 *
 * Everything is one Canvas: the blue slab, the holes punched through it, the discs, the
 * disc currently falling, and the ring around a winning line. Drawing it rather than
 * composing forty-two views is what keeps the drop animation smooth on a cheap phone.
 *
 * The canvas is seven columns wide and SEVEN rows tall - six rows of board plus a strip
 * above it (the chute) where a falling disc starts and where the disc waiting to be played
 * hovers over the column being pressed.
 */
@Composable
fun BoardView(
    grid: List<List<Player?>>,
    toMove: Player,
    winningCells: Set<Cell>,
    lastDrop: Cell?,
    moveIndex: Int,
    enabled: Boolean,
    onColumnTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var pressedCol by remember { mutableIntStateOf(-1) }

    // Runs 0 -> 1 each time a disc is played. Squared on use, so the disc accelerates
    // downward instead of gliding at a constant speed.
    val fall = remember { Animatable(1f) }
    LaunchedEffect(moveIndex) {
        if (moveIndex > 0 && lastDrop != null) {
            fall.snapTo(0f)
            fall.animateTo(1f, tween(durationMillis = 260, easing = LinearEasing))
        } else {
            fall.snapTo(1f)
        }
    }

    // The winning four pulse so the end of the game is unmissable without a dialog.
    val pulse by rememberInfiniteTransition(label = "win").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "winPulse",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(COLS.toFloat() / (ROWS + 1))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val cell = size.width / COLS.toFloat()
                        pressedCol = (offset.x / cell).toInt().coerceIn(0, COLS - 1)
                        tryAwaitRelease()
                        pressedCol = -1
                    },
                    onTap = { offset ->
                        val cell = size.width / COLS.toFloat()
                        onColumnTapped((offset.x / cell).toInt().coerceIn(0, COLS - 1))
                    },
                )
            },
    ) {
        val cell = size.width / COLS
        val chute = cell
        val radius = cell * 0.40f
        val boardTop = chute

        fun centreOf(col: Int, row: Int) = Offset(
            x = cell * (col + 0.5f),
            y = boardTop + cell * (ROWS - row - 0.5f),
        )

        // The slab, with a slightly darker edge so it reads as a physical thing.
        val corner = CornerRadius(cell * 0.28f, cell * 0.28f)
        drawRoundRect(
            color = palette.boardEdge,
            topLeft = Offset(0f, boardTop),
            size = Size(size.width, size.height - boardTop),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = palette.board,
            topLeft = Offset(cell * 0.045f, boardTop + cell * 0.045f),
            size = Size(size.width - cell * 0.09f, size.height - boardTop - cell * 0.11f),
            cornerRadius = corner,
        )

        // The column under the finger lifts slightly, so a tap is confirmed before it lands.
        if (pressedCol in 0 until COLS) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(cell * pressedCol, boardTop),
                size = Size(cell, size.height - boardTop),
                cornerRadius = CornerRadius(cell * 0.18f, cell * 0.18f),
            )
        }

        // Holes and settled discs. The disc that is currently falling is skipped here and
        // drawn afterwards at its animated position.
        val falling = if (fall.value < 1f) lastDrop else null
        for (col in 0 until COLS) {
            for (row in 0 until ROWS) {
                val centre = centreOf(col, row)
                val occupant = grid[col][row]
                if (occupant == null || (falling != null && falling.col == col && falling.row == row)) {
                    drawCircle(palette.hole, radius, centre)
                    drawCircle(palette.boardEdge.copy(alpha = 0.45f), radius, centre, style = Stroke(cell * 0.035f))
                } else {
                    drawDisc(occupant, centre, radius, palette)
                    if (Cell(col, row) in winningCells) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f + 0.55f * pulse),
                            radius = radius * (1.02f + 0.06f * pulse),
                            center = centre,
                            style = Stroke(cell * 0.075f),
                        )
                    }
                }
            }
        }

        // The disc in flight, drawn over the slab so it appears to drop in front of it.
        if (falling != null) {
            val target = centreOf(falling.col, falling.row)
            val start = chute * 0.5f
            val t = fall.value * fall.value
            val y = start + (target.y - start) * t
            drawDisc(grid[falling.col][falling.row] ?: toMove, Offset(target.x, y), radius, palette)
        }

        // The disc waiting to be played, hovering over the column being pressed.
        if (enabled && pressedCol in 0 until COLS && falling == null) {
            drawDisc(
                toMove,
                Offset(cell * (pressedCol + 0.5f), chute * 0.5f),
                radius * 0.86f,
                palette,
                alpha = 0.65f,
            )
        }
    }
}

private fun DrawScope.drawDisc(
    player: Player,
    centre: Offset,
    radius: Float,
    palette: Palette,
    alpha: Float = 1f,
) {
    val face = if (player == Player.RED) palette.red else palette.yellow
    val rim = if (player == Player.RED) palette.redRim else palette.yellowRim
    drawCircle(rim.copy(alpha = alpha), radius, centre)
    drawCircle(face.copy(alpha = alpha), radius * 0.87f, centre)
    // A highlight up and to the left, so the discs read as rounded rather than flat.
    drawCircle(
        color = Color.White.copy(alpha = 0.18f * alpha),
        radius = radius * 0.42f,
        center = Offset(centre.x - radius * 0.28f, centre.y - radius * 0.30f),
    )
    drawCircle(rim.copy(alpha = alpha * 0.9f), radius, centre, style = Stroke(min(radius * 0.10f, 6f)))
}
