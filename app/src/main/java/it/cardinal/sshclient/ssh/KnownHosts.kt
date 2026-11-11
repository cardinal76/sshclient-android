package it.cardinal.sshclient.ssh

import java.io.File
import java.security.PublicKey

/**
 * Le chiavi dei server gia' visti, una per riga: `[host]:porta tipo base64`.
 * La prima connessione chiede conferma, dalla seconda la chiave deve
 * coincidere.
 */
class KnownHosts(private val file: File) {

    enum class Status { KNOWN, UNKNOWN, CHANGED }

    private fun id(host: String, port: Int) = "[${host.lowercase()}]:$port"

    private fun lines(): List<List<String>> =
        if (file.exists()) file.readLines().map { it.split(" ") }.filter { it.size == 3 } else emptyList()

    @Synchronized
    fun keyType(host: String, port: Int): String? =
        lines().firstOrNull { it[0] == id(host, port) }?.get(1)

    @Synchronized
    fun check(host: String, port: Int, key: PublicKey): Status {
        val known = lines().filter { it[0] == id(host, port) }
        return when {
            known.isEmpty() -> Status.UNKNOWN
            known.any { it[1] == SshKeys.typeName(key) && it[2] == SshKeys.blobBase64(key) } -> Status.KNOWN
            else -> Status.CHANGED
        }
    }

    @Synchronized
    fun add(host: String, port: Int, key: PublicKey) {
        val kept = lines().filter { it[0] != id(host, port) }
        write(kept + listOf(listOf(id(host, port), SshKeys.typeName(key), SshKeys.blobBase64(key))))
    }

    @Synchronized
    fun remove(host: String, port: Int) {
        write(lines().filter { it[0] != id(host, port) })
    }

    private fun write(lines: List<List<String>>) {
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("") { it.joinToString(" ") + "\n" })
    }
}
