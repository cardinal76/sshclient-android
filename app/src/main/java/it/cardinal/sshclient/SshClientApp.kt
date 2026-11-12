package it.cardinal.sshclient

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import it.cardinal.sshclient.data.HostProfile
import it.cardinal.sshclient.data.HostRepository
import it.cardinal.sshclient.data.KeyRepository
import it.cardinal.sshclient.ssh.KnownHosts
import java.io.File

/**
 * Tiene gli archivi e la sessione aperta. BouncyCastle non si carica qui:
 * costa, e lo installa `SshKeys` al primo uso, che avviene sempre fuori dal
 * thread dell'interfaccia.
 */
class SshClientApp : Application() {

    val keys by lazy { KeyRepository(filesDir) }
    val hosts by lazy { HostRepository(filesDir) }
    val knownHosts by lazy { KnownHosts(File(filesDir, "known_hosts")) }

    private val main = Handler(Looper.getMainLooper())

    /**
     * La sessione sta nell'applicazione e non nella schermata, cosi'
     * sopravvive quando Android distrugge l'activity sullo sfondo;
     * [SessionService] tiene vivo il processo finche' e' collegata.
     */
    var session: TerminalSession? by mutableStateOf(null)
        private set

    fun openSession(host: HostProfile): TerminalSession? {
        val keyId = host.keyId ?: return null
        session?.close()
        val opened = TerminalSession(
            host, { keys.load(keyId) }, knownHosts,
            // Arriva dal thread di rete: il servizio si ferma dal thread principale.
            onEnded = { main.post { SessionService.stopIfIdle() } },
        )
        session = opened
        ContextCompat.startForegroundService(this, Intent(this, SessionService::class.java))
        return opened
    }

    fun closeSession() {
        session?.close()
        session = null
        SessionService.stopIfIdle()
    }
}
