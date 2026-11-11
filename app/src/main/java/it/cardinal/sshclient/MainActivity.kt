package it.cardinal.sshclient

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
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
                // La notifica della connessione aperta: da Android 13 va chiesto il permesso.
                // Se viene negato il servizio lavora lo stesso, solo senza notifica visibile.
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(screen is Screen.Terminal) {
                    if (screen is Screen.Terminal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
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
