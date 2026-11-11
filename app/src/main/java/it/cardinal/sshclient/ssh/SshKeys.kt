package it.cardinal.sshclient.ssh

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** La chiave e' cifrata e non e' stata data la passphrase. */
class PassphraseRequiredException : Exception("La chiave e' protetta da passphrase")

/** Il testo non e' una chiave privata leggibile, o la passphrase e' sbagliata. */
class InvalidKeyException(cause: Throwable) :
    Exception("Chiave non leggibile o passphrase sbagliata", cause)

/**
 * Generazione, importazione e serializzazione delle chiavi SSH.
 * Nessuna dipendenza da Android: si prova con i test JVM.
 */
object SshKeys {

    private const val BC = "BC"

    /**
     * Android ha un provider "BC" ridotto, senza Ed25519 ne' Curve25519.
     * Lo si sostituisce con BouncyCastle intero prima di usare sshj, che
     * cerca il provider per nome.
     */
    fun installBouncyCastle() {
        val current = Security.getProvider(BC)
        if (current == null || current.javaClass != BouncyCastleProvider::class.java) {
            Security.removeProvider(BC)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    init {
        installBouncyCastle()
    }

    fun generateEd25519(): KeyPair =
        KeyPairGenerator.getInstance("Ed25519", BC).generateKeyPair()

    /**
     * Legge una chiave privata in formato OpenSSH, PEM (PKCS#1/PKCS#8) o PuTTY.
     * La chiave pubblica si ricava dalla privata.
     */
    fun import(text: String, passphrase: CharArray?): KeyPair {
        var asked = false
        val finder = object : PasswordFinder {
            override fun reqPassword(resource: Resource<*>?): CharArray {
                asked = true
                return passphrase?.clone() ?: CharArray(0)
            }

            override fun shouldRetry(resource: Resource<*>?) = false
        }
        try {
            val provider = SSHClient(DefaultConfig()).loadKeys(text.trim() + "\n", null, finder)
            val pair = KeyPair(provider.public, provider.private)
            // Ripassa dalla serializzazione: una chiave che non si riesce a
            // rileggere va rifiutata adesso, non alla prima connessione.
            return decode(encode(pair))
        } catch (e: Exception) {
            if (asked && passphrase == null) throw PassphraseRequiredException()
            throw InvalidKeyException(e)
        }
    }

    /** Formato dell'archivio: algoritmo, PKCS#8 e X.509 in base64, uno per riga. */
    fun encode(pair: KeyPair): ByteArray {
        val b64 = Base64.getEncoder()
        return listOf(
            pair.private.algorithm,
            b64.encodeToString(pair.private.encoded),
            b64.encodeToString(pair.public.encoded),
        ).joinToString("\n").toByteArray(Charsets.US_ASCII)
    }

    fun decode(bytes: ByteArray): KeyPair {
        val (algorithm, priv, pub) = String(bytes, Charsets.US_ASCII).split("\n")
        val b64 = Base64.getDecoder()
        val factory = KeyFactory.getInstance(algorithm, BC)
        return KeyPair(
            factory.generatePublic(X509EncodedKeySpec(b64.decode(pub))),
            factory.generatePrivate(PKCS8EncodedKeySpec(b64.decode(priv))),
        )
    }

    /** Nome SSH del tipo di chiave, per esempio `ssh-ed25519`. */
    fun typeName(key: PublicKey): String = KeyType.fromKey(key).toString()

    private fun blob(key: PublicKey): ByteArray =
        Buffer.PlainBuffer().putPublicKey(key).compactData

    fun blobBase64(key: PublicKey): String = Base64.getEncoder().encodeToString(blob(key))

    /** La riga da mettere in `~/.ssh/authorized_keys` sul server. */
    fun publicOpenSsh(key: PublicKey, comment: String): String =
        "${typeName(key)} ${blobBase64(key)} ${comment.replace(Regex("\\s+"), "-")}".trim()

    /** Impronta come la stampa `ssh-keygen -l`: `SHA256:...`. */
    fun fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob(key))
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
