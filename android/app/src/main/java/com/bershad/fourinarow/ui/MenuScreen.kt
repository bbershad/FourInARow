package com.bershad.fourinarow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bershad.fourinarow.Mode
import com.bershad.fourinarow.game.Difficulty

@Composable
fun MenuScreen(onStart: (Mode, Difficulty) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(palette.red, 22.dp)
            Dot(palette.yellow, 22.dp)
            Dot(palette.red, 22.dp)
            Dot(palette.yellow, 22.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Four in a Row",
            style = MaterialTheme.typography.displaySmall,
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "No ads. No tracking. No internet permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textDim,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Two players", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Same phone, taking turns. A coin flip decides who goes first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onStart(Mode.TWO_PLAYER, difficulty) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Play") }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Against the computer", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "You play red. A coin flip still decides who goes first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { level ->
                        val selected = level == difficulty
                        if (selected) {
                            Button(
                                onClick = { difficulty = level },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text(level.label) }
                        } else {
                            OutlinedButton(
                                onClick = { difficulty = level },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textDim),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text(level.label) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    difficulty.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onStart(Mode.VS_COMPUTER, difficulty) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Play ${difficulty.label}") }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Drop a disc into any column. Four in a line - across, up, or diagonally - wins.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Dot(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
