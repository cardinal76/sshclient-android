package it.cardinal.sshclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.cardinal.sshclient.MainViewModel
import it.cardinal.sshclient.Screen
import it.cardinal.sshclient.data.HostProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(model: MainViewModel) {
    val hosts by model.hosts.hosts.collectAsState()
    val keys by model.keys.keys.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server") },
                actions = { TextButton(onClick = { model.open(Screen.Keys) }) { Text("Chiavi") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { model.open(Screen.EditHost(HostProfile())) }) {
                Text("Nuovo server")
            }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (keys.isEmpty()) "Per cominciare crea o importa una chiave in «Chiavi», poi aggiungi un server."
                    else "Nessun server salvato.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(hosts, key = { it.id }) { host ->
                val keyName = keys.firstOrNull { it.id == host.keyId }?.name
                Column {
                    ListItem(
                        modifier = Modifier.clickable(enabled = keyName != null) { model.connect(host) },
                        headlineContent = { Text(host.title) },
                        supportingContent = {
                            Text(
                                "${host.user}@${host.host}:${host.port} · " +
                                    (keyName?.let { "chiave «$it»" } ?: "nessuna chiave: scegline una in Modifica"),
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { model.open(Screen.EditHost(host)) }) { Text("Modifica") }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
