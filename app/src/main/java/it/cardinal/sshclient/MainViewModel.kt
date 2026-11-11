package it.cardinal.sshclient

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import it.cardinal.sshclient.data.HostProfile

sealed interface Screen {
    data object Hosts : Screen
    data object Keys : Screen
    data class EditHost(val host: HostProfile) : Screen
    data class Terminal(val session: TerminalSession) : Screen
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SshClientApp
    val keys = app.keys
    val hosts = app.hosts
    val knownHosts = app.knownHosts

    // Se l'activity rinasce con una connessione ancora aperta, si riparte dal terminale.
    var screen: Screen by mutableStateOf(app.session?.let { Screen.Terminal(it) } ?: Screen.Hosts)
        private set

    fun open(target: Screen) {
        screen = target
    }

    fun connect(host: HostProfile) {
        screen = Screen.Terminal(app.openSession(host) ?: return)
    }

    /** Torna alla lista dei server, chiudendo la connessione se ce n'e' una. */
    fun back() {
        if (screen is Screen.Terminal) app.closeSession()
        screen = Screen.Hosts
    }
}
