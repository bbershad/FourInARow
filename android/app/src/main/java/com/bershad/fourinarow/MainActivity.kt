package com.bershad.fourinarow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bershad.fourinarow.ui.FourInARowTheme
import com.bershad.fourinarow.ui.GameScreen
import com.bershad.fourinarow.ui.LocalPalette
import com.bershad.fourinarow.ui.MenuScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FourInARowTheme {
                val vm: GameViewModel = viewModel()
                val state = vm.state

                // System back leaves the game rather than the app, which is what someone
                // mid-match expects. From the menu it falls through and closes as normal.
                BackHandler(enabled = state.started) { vm.backToMenu() }

                Surface(
                    color = LocalPalette.current.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.started) {
                        GameScreen(
                            state = state,
                            onDrop = vm::drop,
                            onUndo = vm::undo,
                            onNewGame = vm::newGame,
                            onBack = vm::backToMenu,
                            modifier = Modifier.systemBarsPadding(),
                        )
                    } else {
                        MenuScreen(
                            onStart = vm::startGame,
                            modifier = Modifier.systemBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}
