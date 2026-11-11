package it.cardinal.sshclient.ssh

import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import java.io.Closeable
import java.io.InputStream
import java.security.KeyPair
import java.security.PublicKey

/** Chiede all'utente se fidarsi di un server mai visto. Chiamata fuori dal thread principale: puo' bloccare. */
fun interface HostKeyPrompt {
    fun acceptNewHost(host: String, port: Int, keyType: String, fingerprint: String): Boolean
}

/** La chiave del server non e' quella salvata: possibile intercettazione. */
class HostKeyChangedException(val fingerprint: String) :
    Exception("La chiave del server e' cambiata (ora $fingerprint)")

class HostKeyRejectedException : Exception("Chiave del server non accettata")

class AuthenticationFailedException(cause: Throwable) :
    Exception("Il server ha rifiutato la chiave", cause)

/** Una shell interattiva su SSH, autenticata con chiave privata. */
class SshConnection private constructor(
    private val client: SSHClient,
    private val session: Session,
    private val shell: Session.Shell,
) : Closeable {

    /** Quello che il server scrive (stdout e stderr insieme, come in un terminale). */
    val input: InputStream get() = shell.inputStream

    val isOpen: Boolean get() = shell.isOpen

    fun write(bytes: ByteArray) {
        shell.outputStream.write(bytes)
        shell.outputStream.flush()
    }

    fun resize(columns: Int, rows: Int) {
        shell.changeWindowDimensions(columns, rows, 0, 0)
    }

    override fun close() {
        runCatching { session.close() }
        runCatching { client.disconnect() }
    }

    companion object {
        fun open(
            host: String,
            port: Int,
            user: String,
            key: KeyPair,
            knownHosts: KnownHosts,
            prompt: HostKeyPrompt,
            columns: Int = 80,
            rows: Int = 24,
            connectTimeoutMs: Int = 15_000,
        ): SshConnection {
            SshKeys.installBouncyCastle()
            val config = DefaultConfig().apply { keepAliveProvider = KeepAliveProvider.KEEP_ALIVE }
            val client = SSHClient(config)
            var refusal: Exception? = null
            client.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, p: Int, serverKey: PublicKey): Boolean =
                    when (knownHosts.check(host, port, serverKey)) {
                        KnownHosts.Status.KNOWN -> true
                        KnownHosts.Status.CHANGED -> {
                            refusal = HostKeyChangedException(SshKeys.fingerprint(serverKey))
                            false
                        }
                        KnownHosts.Status.UNKNOWN -> {
                            val ok = prompt.acceptNewHost(
                                host, port, SshKeys.typeName(serverKey), SshKeys.fingerprint(serverKey),
                            )
                            if (ok) knownHosts.add(host, port, serverKey) else refusal = HostKeyRejectedException()
                            ok
                        }
                    }

                // Chiede al server lo stesso tipo di chiave gia' salvato, cosi'
                // un server con piu' chiavi non sembra «cambiato».
                override fun findExistingAlgorithms(hostname: String, p: Int): List<String> =
                    listOfNotNull(knownHosts.keyType(host, port))
            })
            client.connectTimeout = connectTimeoutMs
            try {
                try {
                    client.connect(host, port)
                } catch (e: Exception) {
                    throw refusal ?: e
                }
                client.connection.keepAlive.keepAliveInterval = 30
                try {
                    client.authPublickey(user, KeyPairWrapper(key))
                } catch (e: UserAuthException) {
                    throw AuthenticationFailedException(e)
                }
                val session = client.startSession()
                session.allocatePTY("xterm-256color", columns, rows, 0, 0, emptyMap())
                return SshConnection(client, session, session.startShell())
            } catch (e: Exception) {
                runCatching { client.disconnect() }
                throw e
            }
        }
    }
}
