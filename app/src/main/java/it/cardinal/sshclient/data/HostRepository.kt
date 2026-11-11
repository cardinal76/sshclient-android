package it.cardinal.sshclient.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HostProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val keyId: String? = null,
) {
    val title: String get() = name.ifBlank { "$user@$host" }
}

/** I server salvati, in `hosts.json`. */
class HostRepository(dir: File) {

    private val file = File(dir, "hosts.json")

    private val _hosts = MutableStateFlow(read())
    val hosts: StateFlow<List<HostProfile>> = _hosts

    @Synchronized
    fun save(host: HostProfile) {
        val current = _hosts.value
        write(if (current.any { it.id == host.id }) current.map { if (it.id == host.id) host else it } else current + host)
    }

    @Synchronized
    fun delete(id: String) = write(_hosts.value.filter { it.id != id })

    private fun read(): List<HostProfile> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            HostProfile(
                it.getString("id"), it.getString("name"), it.getString("host"), it.getInt("port"),
                it.getString("user"), it.optString("keyId").ifEmpty { null },
            )
        }
    }

    private fun write(hosts: List<HostProfile>) {
        val array = JSONArray()
        hosts.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                    .put("port", it.port).put("user", it.user).put("keyId", it.keyId ?: ""),
            )
        }
        file.writeText(array.toString())
        _hosts.value = hosts
    }
}
