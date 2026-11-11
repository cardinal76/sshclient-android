package it.cardinal.sshclient.ssh

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import kotlin.concurrent.thread

/** Il client contro un server SSH vero (Apache MINA) acceso dentro il test. */
class SshConnectionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: SshServer
    private var authorized: PublicKey? = null

    @Before
    fun startServer() {
        server = SshServer.setUpDefaultServer().apply {
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider()
            setPublickeyAuthenticator { _, key, _ -> KeyUtils.compareKeys(key, authorized) }
            shellFactory = ShellFactory { EchoShell() }
            start()
        }
    }

    @After
    fun stopServer() {
        server.stop(true)
    }

    private fun knownHosts() = KnownHosts(tmp.root.resolve("known_hosts"))

    private fun connect(key: KeyPair, known: KnownHosts, prompt: HostKeyPrompt) =
        SshConnection.open("127.0.0.1", server.port, "marco", key, known, prompt)

    private fun echo(conn: SshConnection, text: String): String {
        conn.write("$text\n".toByteArray())
        val out = ByteArrayOutputStream()
        val buf = ByteArray(256)
        while (!out.toString().contains("\n")) {
            val n = conn.input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString().trim()
    }

    @Test
    fun `chiave generata, salvata e riletta apre una shell`() {
        val key = SshKeys.decode(SshKeys.encode(SshKeys.generateEd25519()))
        authorized = key.public
        assertTrue(SshKeys.publicOpenSsh(key.public, "telefono").startsWith("ssh-ed25519 AAAA"))

        connect(key, knownHosts()) { _, _, _, fp -> fp.startsWith("SHA256:") }.use { conn ->
            assertEquals("eco: ciao", echo(conn, "ciao"))
            conn.resize(120, 40)
            assertEquals("eco: ancora", echo(conn, "ancora"))
        }
    }

    @Test
    fun `chiave non autorizzata viene rifiutata`() {
        authorized = SshKeys.generateEd25519().public
        assertThrows(AuthenticationFailedException::class.java) {
            connect(SshKeys.generateEd25519(), knownHosts()) { _, _, _, _ -> true }
        }
    }

    @Test
    fun `server nuovo chiede conferma una volta sola`() {
        val key = SshKeys.generateEd25519().also { authorized = it.public }
        val known = knownHosts()
        var asked = 0

        assertThrows(HostKeyRejectedException::class.java) {
            connect(key, known) { _, _, _, _ -> asked++; false }
        }
        connect(key, known) { _, _, _, _ -> asked++; true }.close()
        connect(key, known) { _, _, _, _ -> asked++; true }.close()

        assertEquals(2, asked)
    }

    @Test
    fun `chiave del server cambiata blocca la connessione senza chiedere`() {
        val key = SshKeys.generateEd25519().also { authorized = it.public }
        val known = knownHosts()
        known.add("127.0.0.1", server.port, SshKeys.generateEd25519().public)

        assertThrows(HostKeyChangedException::class.java) {
            connect(key, known) { _, _, _, _ -> throw AssertionError("non deve chiedere") }
        }

        known.remove("127.0.0.1", server.port)
        connect(key, known) { _, _, _, _ -> true }.close()
    }

    @Test
    fun `importa chiavi OpenSSH di ogni tipo, anche con passphrase`() {
        val pairs = mapOf(
            "ssh-ed25519" to SshKeys.generateEd25519(),
            "ssh-rsa" to KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair(),
            "ecdsa-sha2-nistp256" to KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair(),
        )
        for ((type, original) in pairs) {
            for (passphrase in listOf(null, "segreta")) {
                val text = openSsh(original, passphrase)
                val imported = SshKeys.import(text, passphrase?.toCharArray())
                assertEquals(type, SshKeys.typeName(imported.public))
                assertEquals(SshKeys.fingerprint(original.public), SshKeys.fingerprint(imported.public))

                authorized = original.public
                connect(imported, knownHosts()) { _, _, _, _ -> true }.use {
                    assertEquals("eco: $type", echo(it, type))
                }
            }
        }
    }

    @Test
    fun `passphrase mancante o sbagliata si distinguono`() {
        val text = openSsh(SshKeys.generateEd25519(), "segreta")
        assertThrows(PassphraseRequiredException::class.java) { SshKeys.import(text, null) }
        assertThrows(InvalidKeyException::class.java) { SshKeys.import(text, "sbagliata".toCharArray()) }
        assertThrows(InvalidKeyException::class.java) { SshKeys.import("non e' una chiave", null) }
    }

    private fun openSsh(pair: KeyPair, passphrase: String?): String {
        val out = ByteArrayOutputStream()
        val options = passphrase?.let {
            OpenSSHKeyEncryptionContext().apply { password = it; cipherName = "AES"; cipherType = "256" }
        }
        OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(pair, "test", options, out)
        return out.toString()
    }

    /** Una «shell» che rimanda indietro ogni riga con `eco: ` davanti. */
    private class EchoShell : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private var exit: ExitCallback? = null

        override fun setInputStream(stream: InputStream) { input = stream }
        override fun setOutputStream(stream: OutputStream) { output = stream }
        override fun setErrorStream(stream: OutputStream) {}
        override fun setExitCallback(callback: ExitCallback) { exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            thread(isDaemon = true) {
                runCatching {
                    input.bufferedReader().forEachLine {
                        output.write("eco: $it\r\n".toByteArray())
                        output.flush()
                    }
                }
                exit?.onExit(0)
            }
        }

        override fun destroy(channel: ChannelSession) {}
    }
}
