package it.cardinal.sshclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.cardinal.sshclient.MainViewModel
import it.cardinal.sshclient.data.StoredKey
import it.cardinal.sshclient.ssh.PassphraseRequiredException

private sealed interface KeyDialog {
    data object Generate : KeyDialog
    data class Import(val text: String) : KeyDialog
    data class Delete(val key: StoredKey) : KeyDialog
}

/** Una chiave privata sta in pochi kilobyte: oltre questa misura il file scelto e' un altro. */
private const val MAX_KEY_FILE_BYTES = 64 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(model: MainViewModel) {
    val context = LocalContext.current
    val keys by model.keys.keys.collectAsState()
    val hosts by model.hosts.hosts.collectAsState()
    var dialog by remember { mutableStateOf<KeyDialog?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Si legge un byte oltre il limite solo per accorgersi di un file troppo grande.
                val buffer = ByteArray(MAX_KEY_FILE_BYTES + 1)
                var size = 0
                while (size < buffer.size) {
                    val read = input.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    size += read
                }
                buffer.copyOf(size)
            }
        }.getOrNull()
        if (bytes == null || bytes.size > MAX_KEY_FILE_BYTES) {
            Toast.makeText(context, "Il file non sembra una chiave privata", Toast.LENGTH_LONG).show()
        } else {
            dialog = KeyDialog.Import(String(bytes))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chiavi") },
                navigationIcon = { TextButton(onClick = model::back) { Text("Indietro") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { dialog = KeyDialog.Generate }, modifier = Modifier.weight(1f)) { Text("Genera") }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Importa da file")
                }
            }
        },
    ) { padding ->
        if (keys.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nessuna chiave. «Genera» ne crea una nuova (Ed25519) che non lascia mai il telefono; " +
                        "«Importa da file» ne legge una esistente.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(keys, key = { it.id }) { key ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(key.name, style = MaterialTheme.typography.titleMedium)
                    Text(key.type, style = MaterialTheme.typography.bodySmall)
                    Text(key.fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Chiave pubblica SSH", key.publicOpenSsh))
                            Toast.makeText(context, "Chiave pubblica copiata: va in ~/.ssh/authorized_keys sul server", Toast.LENGTH_LONG).show()
                        }) { Text("Copia chiave pubblica") }
                        TextButton(onClick = { dialog = KeyDialog.Delete(key) }) {
                            Text("Elimina", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    when (val current = dialog) {
        null -> {}
        is KeyDialog.Delete -> {
            val used = hosts.filter { it.keyId == current.key.id }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Eliminare «${current.key.name}»?") },
                text = {
                    Text(
                        "La chiave privata viene cancellata dal telefono e non si puo' recuperare." +
                            if (used.isEmpty()) "" else " La usano: ${used.joinToString { it.title }}.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { model.keys.delete(current.key.id); dialog = null }) {
                        Text("Elimina", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } },
            )
        }
        KeyDialog.Generate, is KeyDialog.Import -> {
            val importing = current as? KeyDialog.Import
            var name by remember(current) { mutableStateOf("") }
            var passphrase by remember(current) { mutableStateOf("") }
            var askPassphrase by remember(current) { mutableStateOf(false) }
            var error by remember(current) { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(if (importing == null) "Nuova chiave Ed25519" else "Importa chiave") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                        if (askPassphrase) {
                            OutlinedTextField(
                                passphrase, { passphrase = it }, label = { Text("Passphrase della chiave") },
                                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            try {
                                if (importing == null) {
                                    model.keys.generate(name.trim())
                                } else {
                                    model.keys.import(
                                        name.trim(), importing.text,
                                        passphrase.takeIf { askPassphrase }?.toCharArray(),
                                    )
                                }
                                dialog = null
                            } catch (e: PassphraseRequiredException) {
                                askPassphrase = true
                                error = null
                            } catch (e: Exception) {
                                error = e.message
                            }
                        },
                    ) { Text(if (importing == null) "Genera" else "Importa") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annulla") } },
            )
        }
    }
}
