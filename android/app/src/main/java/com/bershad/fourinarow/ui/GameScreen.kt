package com.bershad.fourinarow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bershad.fourinarow.COMPUTER
import com.bershad.fourinarow.GameState
import com.bershad.fourinarow.HUMAN
import com.bershad.fourinarow.Mode
import com.bershad.fourinarow.game.Player

@Composable
fun GameScreen(
    state: GameState,
    onDrop: (Int) -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current

    // One tick of feedback per disc, including the computer's, so a move that happens
    // while you are not looking at the screen still registers.
    LaunchedEffect(state.moveIndex) {
        if (state.moveIndex > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the menu", tint = palette.textDim)
            }
            Text(
                text = when (state.mode) {
                    Mode.TWO_PLAYER -> "Two players"
                    Mode.VS_COMPUTER -> "Computer - ${state.difficulty.label}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            // Balances the back button so the title sits centred.
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(4.dp))
        ScoreStrip(state)

        Spacer(Modifier.height(12.dp))
        StatusLine(state)

        // The board and its controls float as one block in whatever room is left, so a tall
        // phone does not leave a screenful of dead space under them.
        Spacer(Modifier.weight(1f))
        BoardView(
            grid = state.grid,
            toMove = state.toMove,
            winningCells = state.winningCells,
            lastDrop = state.lastDrop,
            moveIndex = state.moveIndex,
            enabled = state.acceptingInput,
            onColumnTapped = onDrop,
            modifier = Modifier.widthIn(max = 520.dp),
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = state.canUndo && !state.thinking,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Filled.Undo, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Undo")
            }
            Button(
                onClick = onNewGame,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (state.over) "Play again" else "New game")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "${nameOf(state.starter, state)} started this game (coin flip).",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ScoreStrip(state: GameState) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(palette.red, 14.dp)
        Text(
            "${nameOf(Player.RED, state)}  ${state.scoreRed}",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text,
        )
        Text("|", color = palette.textDim)
        Dot(palette.yellow, 14.dp)
        Text(
            "${nameOf(Player.YELLOW, state)}  ${state.scoreYellow}",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text,
        )
        if (state.draws > 0) {
            Text("|", color = palette.textDim)
            Text(
                "Draws ${state.draws}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
            )
        }
    }
}

@Composable
private fun StatusLine(state: GameState) {
    val palette = LocalPalette.current
    val (text, colour) = when {
        state.winner != null -> {
            val w = state.winner
            val label = when {
                state.mode == Mode.VS_COMPUTER && w == HUMAN -> "You win!"
                state.mode == Mode.VS_COMPUTER -> "Computer wins"
                else -> "${nameOf(w, state)} wins!"
            }
            label to (if (w == Player.RED) palette.red else palette.yellow)
        }
        state.draw -> "Draw - the board is full" to palette.textDim
        state.thinking -> "Thinking..." to palette.textDim
        else -> {
            val label = if (state.mode == Mode.VS_COMPUTER && state.toMove == HUMAN) {
                "Your turn"
            } else {
                "${nameOf(state.toMove, state)} to play"
            }
            label to (if (state.toMove == Player.RED) palette.red else palette.yellow)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!state.over && !state.thinking) {
            Dot(if (state.toMove == Player.RED) palette.red else palette.yellow, 18.dp)
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = colour)
    }
}

private fun nameOf(player: Player, state: GameState): String = when {
    state.mode == Mode.TWO_PLAYER && player == Player.RED -> "Red"
    state.mode == Mode.TWO_PLAYER -> "Yellow"
    player == HUMAN -> "You"
    player == COMPUTER -> "Computer"
    else -> "Yellow"
}
