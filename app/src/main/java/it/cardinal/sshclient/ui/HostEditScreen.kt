package it.cardinal.sshclient.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.cardinal.sshclient.MainViewModel
import it.cardinal.sshclient.data.HostProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditScreen(model: MainViewModel, original: HostProfile) {
    val context = LocalContext.current
    val keys by model.keys.keys.collectAsState()
    val hosts by model.hosts.hosts.collectAsState()
    val exists = hosts.any { it.id == original.id }

    var name by remember { mutableStateOf(original.name) }
    var host by remember { mutableStateOf(original.host) }
    var port by remember { mutableStateOf(original.port.toString()) }
    var user by remember { mutableStateOf(original.user) }
    var keyId by remember { mutableStateOf(original.keyId ?: keys.singleOrNull()?.id) }

    val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val valid = host.isNotBlank() && user.isNotBlank() && portNumber != null && keys.any { it.id == keyId }
    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (exists) "Modifica server" else "Nuovo server") },
                navigationIcon = { TextButton(onClick = model::back) { Text("Annulla") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome (facoltativo)") }, singleLine = true)
            OutlinedTextField(
                host, { host = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Indirizzo") },
                singleLine = true, keyboardOptions = plain.copy(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Porta") },
                singleLine = true, isError = portNumber == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                user, { user = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Utente") },
                singleLine = true, keyboardOptions = plain,
            )

            Text("Chiave privata", style = MaterialTheme.typography.titleSmall)
            if (keys.isEmpty()) {
                Text(
                    "Non ci sono chiavi: creane o importane una dalla schermata «Chiavi».",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            keys.forEach { key ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = key.id == keyId) { keyId = key.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = key.id == keyId, onClick = null, modifier = Modifier.padding(8.dp))
                    Text("${key.name} (${key.type})")
                }
            }

            Button(
                onClick = {
                    model.hosts.save(HostProfile(original.id, name.trim(), host, portNumber!!, user, keyId))
                    model.back()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva") }

            if (exists) {
                OutlinedButton(
                    onClick = {
                        model.knownHosts.remove(original.host, original.port)
                        Toast.makeText(context, "Alla prossima connessione verra' chiesta di nuovo la conferma", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Dimentica la chiave del server") }
                TextButton(
                    onClick = {
                        model.hosts.delete(original.id)
                        model.knownHosts.remove(original.host, original.port)
                        model.back()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Elimina server", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
