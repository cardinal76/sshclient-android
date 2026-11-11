package it.cardinal.sshclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import it.cardinal.sshclient.ui.HostEditScreen
import it.cardinal.sshclient.ui.HostsScreen
import it.cardinal.sshclient.ui.KeysScreen
import it.cardinal.sshclient.ui.TerminalScreen

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val screen = model.screen
                BackHandler(enabled = screen != Screen.Hosts) { model.back() }
                when (screen) {
                    Screen.Hosts -> HostsScreen(model)
                    Screen.Keys -> KeysScreen(model)
                    is Screen.EditHost -> HostEditScreen(model, screen.host)
                    is Screen.Terminal -> TerminalScreen(screen.session, onClose = model::back)
                }
            }
        }
    }
}
