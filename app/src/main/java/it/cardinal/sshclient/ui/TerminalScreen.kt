package it.cardinal.sshclient.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cardinal.sshclient.SessionState
import it.cardinal.sshclient.TerminalSession
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey

private const val MOD_ALT = 2
private const val MOD_CTRL = 4

private val barColor = Color(0xFF1E1E1E)
private val keyColor = Color(0xFFE0E0E0)
private val activeColor = Color(0xFF4CD964)

@Composable
fun TerminalScreen(session: TerminalSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val state = session.state

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Terminal(
                terminalEmulator = session.emulator,
                modifier = Modifier.fillMaxSize(),
                initialFontSize = 12.sp,
                keyboardEnabled = state == SessionState.Connected,
                modifierManager = session.modifiers,
                onPasteRequest = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString()
                        ?.let(session.emulator::pasteText)
                },
            )
            if (state == SessionState.Connecting) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = activeColor)
            }
        }
        ExtraKeys(session)
    }

    when (state) {
        is SessionState.ConfirmHost -> AlertDialog(
            onDismissRequest = { state.answer(false) },
            title = { Text("Server mai visto") },
            text = {
                Column {
                    Text("${session.host.host}:${session.host.port} si presenta con questa chiave (${state.keyType}):")
                    Text(state.fingerprint, Modifier.padding(vertical = 8.dp), fontFamily = FontFamily.Monospace)
                    Text("Confrontala con quella del server prima di accettare: da qui in poi il telefono si fidera' solo di questa.")
                }
            },
            confirmButton = { TextButton(onClick = { state.answer(true) }) { Text("Accetta") } },
            dismissButton = { TextButton(onClick = { state.answer(false) }) { Text("Rifiuta") } },
        )
        is SessionState.Closed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(if (state.error == null) "Connessione chiusa" else "Connessione non riuscita") },
            text = { Text(state.error ?: "La sessione su ${session.host.title} e' terminata.") },
            confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } },
        )
        else -> {}
    }
}

/** I tasti che la tastiera del telefono non ha. */
@Composable
private fun ExtraKeys(session: TerminalSession) {
    val modifiers = session.modifiers
    val emulator = session.emulator

    fun mask() = (if (modifiers.ctrl) MOD_CTRL else 0) or (if (modifiers.alt) MOD_ALT else 0)
    fun key(code: Int) {
        emulator.dispatchKey(mask(), code)
        modifiers.clearTransients()
    }
    fun char(ch: Char) {
        emulator.dispatchCharacter(mask(), ch)
        modifiers.clearTransients()
    }

    Row(Modifier.fillMaxWidth().background(barColor).horizontalScroll(rememberScrollState())) {
        ExtraKey("ESC") { key(VTermKey.ESCAPE) }
        ExtraKey("TAB") { key(VTermKey.TAB) }
        ExtraKey("CTRL", active = modifiers.ctrl) { modifiers.ctrl = !modifiers.ctrl }
        ExtraKey("ALT", active = modifiers.alt) { modifiers.alt = !modifiers.alt }
        ExtraKey("←") { key(VTermKey.LEFT) }
        ExtraKey("↓") { key(VTermKey.DOWN) }
        ExtraKey("↑") { key(VTermKey.UP) }
        ExtraKey("→") { key(VTermKey.RIGHT) }
        "/-|~".forEach { ch -> ExtraKey(ch.toString()) { char(ch) } }
        ExtraKey("HOME") { key(VTermKey.HOME) }
        ExtraKey("END") { key(VTermKey.END) }
        ExtraKey("PGUP") { key(VTermKey.PAGEUP) }
        ExtraKey("PGDN") { key(VTermKey.PAGEDOWN) }
    }
}

@Composable
private fun ExtraKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        color = if (active) activeColor else keyColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
    )
}
