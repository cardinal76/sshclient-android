package it.cardinal.sshclient.data

import it.cardinal.sshclient.ssh.SshKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPair
import java.util.UUID

/** Quello che di una chiave si puo' mostrare: la parte privata resta nel file cifrato. */
data class StoredKey(
    val id: String,
    val name: String,
    val type: String,
    val fingerprint: String,
    val publicOpenSsh: String,
)

/**
 * Le chiavi SSH sul telefono. L'indice (`keys.json`) tiene solo dati
 * pubblici; ogni chiave privata sta in `keys/<id>.bin`, cifrata da
 * [KeystoreCipher].
 */
class KeyRepository(dir: File) {

    private val index = File(dir, "keys.json")
    private val blobs = File(dir, "keys").apply { mkdirs() }

    private val _keys = MutableStateFlow(read())
    val keys: StateFlow<List<StoredKey>> = _keys

    fun generate(name: String): StoredKey = store(name, SshKeys.generateEd25519())

    /** Lancia `PassphraseRequiredException` o `InvalidKeyException`. */
    fun import(name: String, text: String, passphrase: CharArray?): StoredKey =
        store(name, SshKeys.import(text, passphrase))

    @Synchronized
    private fun store(name: String, pair: KeyPair): StoredKey {
        val key = StoredKey(
            id = UUID.randomUUID().toString(),
            name = name,
            type = SshKeys.typeName(pair.public),
            fingerprint = SshKeys.fingerprint(pair.public),
            publicOpenSsh = SshKeys.publicOpenSsh(pair.public, name),
        )
        File(blobs, "${key.id}.bin").writeBytes(KeystoreCipher.encrypt(SshKeys.encode(pair)))
        write(_keys.value + key)
        return key
    }

    fun load(id: String): KeyPair =
        SshKeys.decode(KeystoreCipher.decrypt(File(blobs, "$id.bin").readBytes()))

    @Synchronized
    fun delete(id: String) {
        File(blobs, "$id.bin").delete()
        write(_keys.value.filter { it.id != id })
    }

    private fun read(): List<StoredKey> {
        if (!index.exists()) return emptyList()
        val array = JSONArray(index.readText())
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            StoredKey(
                it.getString("id"), it.getString("name"), it.getString("type"),
                it.getString("fingerprint"), it.getString("public"),
            )
        }
    }

    private fun write(keys: List<StoredKey>) {
        val array = JSONArray()
        keys.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("fingerprint", it.fingerprint).put("public", it.publicOpenSsh),
            )
        }
        index.writeText(array.toString())
        _keys.value = keys
    }
}
