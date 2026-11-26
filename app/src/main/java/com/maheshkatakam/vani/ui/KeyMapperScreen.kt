package com.maheshkatakam.vani.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.access.VaniAccessibilityService
import com.maheshkatakam.vani.core.Gesture
import com.maheshkatakam.vani.core.KeyAction
import com.maheshkatakam.vani.core.KeyMapping
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.system.SystemSetup
import kotlin.math.roundToInt

fun keyName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume down"
    KeyEvent.KEYCODE_VOLUME_UP -> "Volume up"
    KeyEvent.KEYCODE_HEADSETHOOK -> "Headset button"
    KeyEvent.KEYCODE_CAMERA -> "Camera button"
    else -> KeyEvent.keyCodeToString(code)
        .removePrefix("KEYCODE_")
        .lowercase()
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
}

private fun describe(m: KeyMapping): String {
    val mode = m.modeId?.let { Modes.find(it, Vani.settings.current.customModes).name } ?: "smart style"
    val action = if (m.action == KeyAction.POLISH_TYPED) "polish what I typed" else "dictate ($mode)"
    val hold = if (m.gesture == Gesture.LONG && m.holdToTalk && m.action == KeyAction.DICTATE) ", release to finish" else ""
    return "${m.gesture.label} → $action$hold"
}

@Composable
fun KeyMapperScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by Vani.settings.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val accessOn = remember(tick) { SystemSetup.isAccessibilityOn(ctx) }
    val isAssistant = remember(tick) { SystemSetup.isAssistant(ctx) }
    var adding by remember { mutableStateOf(false) }
    var tileMessage by remember { mutableStateOf<String?>(null) }

    VaniScaffold(
        title = "Buttons & Key mapper",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Map a button") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                            Text("  Power button", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Android doesn't let apps remap the power button directly, so Vani uses Pixel's own " +
                                "shortcut: make Vani the digital assistant, then set Press & hold power button → " +
                                "Digital assistant. Holding power then opens Vani from anywhere, even mid-sentence in another app. " +
                                "Hold it again to finish. (Pixel's double-tap power only offers camera or wallet, so that stays as it is.)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isAssistant) "✓ Vani is your digital assistant" else "Vani isn't your digital assistant yet",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { SystemSetup.openAssistantSettings(ctx) }) { Text("Digital assistant") }
                            TextButton(onClick = { SystemSetup.openPowerButtonSettings(ctx) }) { Text("Power button") }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TouchApp, contentDescription = null)
                            Text("  Other buttons", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Map a volume button (or a headset / Bluetooth button) to Vani. Hold Volume down to talk and let go " +
                                "to finish, like a walkie-talkie. A normal short press still changes the volume.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!accessOn) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Needs Vani's Accessibility helper. If the switch is greyed out, open App info → ⋮ → Allow restricted settings first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { SystemSetup.openAccessibilitySettings(ctx) }) { Text("Turn on") }
                                TextButton(onClick = { SystemSetup.openAppInfo(ctx) }) { Text("App info") }
                            }
                        }
                    }
                }
            }

            if (s.keyMappings.isEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            Vani.settings.update {
                                it.copy(
                                    keyMappings = it.keyMappings + KeyMapping(
                                        id = "k${System.currentTimeMillis()}",
                                        keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                                        keyLabel = keyName(KeyEvent.KEYCODE_VOLUME_DOWN),
                                        gesture = Gesture.LONG,
                                        holdToTalk = true,
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Quick add: hold Volume down to talk") }
                }
            }

            items(s.keyMappings, key = { it.id }) { m ->
                Card {
                    SettingRow(
                        title = m.keyLabel.ifBlank { keyName(m.keyCode) },
                        subtitle = describe(m),
                        trailing = {
                            IconButton(onClick = {
                                Vani.settings.update { st -> st.copy(keyMappings = st.keyMappings.filterNot { it.id == m.id }) }
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                        },
                    )
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Timing", style = MaterialTheme.typography.titleSmall)
                        Text("Hold for ${s.longPressMs} ms", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = s.longPressMs.toFloat(),
                            onValueChange = { v -> Vani.settings.update { it.copy(longPressMs = (v / 50).roundToInt() * 50) } },
                            valueRange = 250f..1200f,
                        )
                        Text("Double-press within ${s.doublePressMs} ms", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = s.doublePressMs.toFloat(),
                            onValueChange = { v -> Vani.settings.update { it.copy(doublePressMs = (v / 25).roundToInt() * 25) } },
                            valueRange = 150f..600f,
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "More ways to start Vani",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        SettingRow(
                            "Quick Settings tile",
                            tileMessage ?: "Tap to add it: swipe down, tap Vani, speak",
                            onClick = { SystemSetup.requestTile(ctx) { tileMessage = it } },
                        )
                        SettingRow("Home screen", "Long-press the Vani icon for Dictate and Polish clipboard shortcuts")
                        SettingRow("Any app's text menu", "Select text → ⋮ → Polish with Vani, rewrites it in place")
                    }
                }
            }
        }
    }

    if (adding) {
        AddMappingDialog(onDismiss = { adding = false }, onSave = { mapping ->
            Vani.settings.update { st ->
                // One gesture per button: replace an existing mapping for the same key + gesture.
                st.copy(keyMappings = st.keyMappings.filterNot { it.keyCode == mapping.keyCode && it.gesture == mapping.gesture } + mapping)
            }
            adding = false
        })
    }
}

@Composable
private fun AddMappingDialog(onDismiss: () -> Unit, onSave: (KeyMapping) -> Unit) {
    val s = Vani.settings.current
    var keyCode by remember { mutableIntStateOf(KeyEvent.KEYCODE_VOLUME_DOWN) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var gesture by remember { mutableStateOf(Gesture.LONG) }
    var action by remember { mutableStateOf(KeyAction.DICTATE) }
    var modeId by remember { mutableStateOf<String?>(null) }
    var holdToTalk by remember { mutableStateOf(true) }
    var pickingMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { VaniAccessibilityService.instance?.cancelCapture() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    KeyMapping(
                        id = "k${System.currentTimeMillis()}",
                        keyCode = keyCode,
                        keyLabel = keyName(keyCode),
                        gesture = gesture,
                        action = action,
                        modeId = modeId,
                        holdToTalk = holdToTalk,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Map a button") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Button", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && !capturing,
                        onClick = { keyCode = KeyEvent.KEYCODE_VOLUME_DOWN },
                        label = { Text("Volume down") },
                    )
                    FilterChip(
                        selected = keyCode == KeyEvent.KEYCODE_VOLUME_UP && !capturing,
                        onClick = { keyCode = KeyEvent.KEYCODE_VOLUME_UP },
                        label = { Text("Volume up") },
                    )
                }
                val isPreset = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                FilterChip(
                    selected = capturing || !isPreset,
                    onClick = {
                        val svc = VaniAccessibilityService.instance
                        if (svc == null) {
                            captureError = "Turn on Vani's Accessibility helper first."
                        } else {
                            captureError = null
                            capturing = true
                            svc.captureNextKey { code ->
                                keyCode = code
                                capturing = false
                            }
                        }
                    },
                    label = {
                        Text(
                            when {
                                capturing -> "Press the button now…"
                                !isPreset -> keyName(keyCode)
                                else -> "Other: press a button…"
                            }
                        )
                    },
                )
                captureError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Text("Gesture", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gesture.entries.forEach { g ->
                        FilterChip(selected = gesture == g, onClick = { gesture = g }, label = { Text(g.label) })
                    }
                }
                if (gesture == Gesture.SINGLE && isPreset) {
                    Text(
                        "A single press will no longer change the volume. Hold or Double-press keep normal volume presses working.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text("What it does", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyAction.entries.forEach { a ->
                        FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a.label) })
                    }
                }
                if (action == KeyAction.DICTATE) {
                    val modeLabel = modeId?.let { id -> Modes.find(id, s.customModes).let { "${it.emoji} ${it.name}" } }
                        ?: "Smart (per app, else default)"
                    SettingRow("Style", modeLabel, onClick = { pickingMode = true })
                }
                if (gesture == Gesture.LONG && action == KeyAction.DICTATE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Hold to talk", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Keep holding while you speak; let go to finish.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = holdToTalk, onCheckedChange = { holdToTalk = it })
                    }
                }
            }
        },
    )

    if (pickingMode) {
        ChoiceDialog(
            title = "Style",
            options = listOf<Pair<String?, String>>(null to "Smart (per app, else default)") +
                s.allModes.map { it.id to "${it.emoji} ${it.name}" },
            selected = modeId,
            onPick = {
                modeId = it
                pickingMode = false
            },
            onDismiss = { pickingMode = false },
        )
    }
}
