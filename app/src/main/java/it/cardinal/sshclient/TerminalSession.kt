package it.cardinal.sshclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.cardinal.sshclient.data.HostProfile
import it.cardinal.sshclient.ssh.KnownHosts
import it.cardinal.sshclient.ssh.SshConnection
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.security.KeyPair
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.concurrent.thread

sealed interface SessionState {
    data object Connecting : SessionState
    data class ConfirmHost(val keyType: String, val fingerprint: String, val answer: (Boolean) -> Unit) : SessionState
    data object Connected : SessionState
    data class Closed(val error: String?) : SessionState
}

/** Ctrl e Alt della barra dei tasti: valgono per il tasto successivo, poi si spengono. */
class StickyModifiers : ModifierManager {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)

    override fun isCtrlActive() = ctrl
    override fun isAltActive() = alt
    override fun isShiftActive() = false
    override fun clearTransients() {
        ctrl = false
        alt = false
    }
}

/**
 * Una connessione aperta e il suo terminale. La rete sta su thread propri:
 * uno legge dal server e disegna, uno (in coda) scrive i tasti.
 */
class TerminalSession(
    val host: HostProfile,
    private val loadKey: () -> KeyPair,
    private val knownHosts: KnownHosts,
    private val onEnded: () -> Unit = {},
) {
    var state: SessionState by mutableStateOf(SessionState.Connecting)
        private set

    val modifiers = StickyModifiers()

    private val writer = Executors.newSingleThreadExecutor()

    @Volatile
    private var connection: SshConnection? = null

    @Volatile
    private var closed = false

    @Volatile
    private var disconnecting = false

    /** Falso da quando la connessione e' finita, per qualunque motivo. */
    val isActive: Boolean get() = !closed && state !is SessionState.Closed

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        onKeyboardInput = { bytes -> send { it.write(bytes) } },
        onResize = { size -> send { it.resize(size.columns, size.rows) } },
    )

    init {
        thread(name = "ssh-${host.host}", isDaemon = true) { run() }
    }

    private fun send(action: (SshConnection) -> Unit) {
        val conn = connection ?: return
        if (closed) return
        writer.execute { runCatching { action(conn) } }
    }

    private fun run() {
        var error: String? = null
        try {
            val conn = SshConnection.open(
                host = host.host,
                port = host.port,
                user = host.user,
                key = loadKey(),
                knownHosts = knownHosts,
                prompt = { _, _, type, fingerprint ->
                    val answer = CompletableFuture<Boolean>()
                    state = SessionState.ConfirmHost(type, fingerprint) {
                        state = SessionState.Connecting
                        answer.complete(it)
                    }
                    answer.get()
                },
                columns = emulator.dimensions.columns,
                rows = emulator.dimensions.rows,
            )
            connection = conn
            if (closed) return
            state = SessionState.Connected
            // Il terminale puo' aver cambiato misura mentre ci si collegava.
            send { it.resize(emulator.dimensions.columns, emulator.dimensions.rows) }
            val buffer = ByteArray(8192)
            while (true) {
                val read = conn.input.read(buffer)
                if (read < 0) break
                emulator.writeInput(buffer, 0, read)
            }
        } catch (e: Exception) {
            if (!closed && !disconnecting) error = e.message ?: e.javaClass.simpleName
        } finally {
            connection?.close()
            if (!closed) state = SessionState.Closed(error)
            onEnded()
        }
    }

    /**
     * Chiude la connessione ma lascia il terminale sullo schermo, con quello
     * che c'era scritto: e' il «Disconnetti» della notifica.
     */
    fun disconnect() {
        if (closed || !isActive) return
        disconnecting = true
        (state as? SessionState.ConfirmHost)?.answer?.invoke(false)
        val conn = connection
        thread(isDaemon = true) { conn?.close() }
    }

    /** Chiude tutto, terminale compreso: si chiama uscendo dalla schermata. */
    fun close() {
        if (closed) return
        closed = true
        (state as? SessionState.ConfirmHost)?.answer?.invoke(false)
        writer.shutdownNow()
        val conn = connection
        thread(isDaemon = true) {
            conn?.close()
            emulator.close()
        }
    }
}
