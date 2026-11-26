package com.maheshkatakam.vani.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshkatakam.vani.BuildConfig
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.ai.Errors
import com.maheshkatakam.vani.ai.ModelCatalog
import com.maheshkatakam.vani.core.AppSettings
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.core.Provider
import com.maheshkatakam.vani.core.Script
import com.maheshkatakam.vani.system.SystemSetup
import kotlinx.coroutines.launch

private enum class Dialog { NONE, STT, LLM, DEFAULT_MODE, LANGUAGE, SCRIPT, ENGLISH, NAME, SPOKEN, AUTOSTOP, MAXLEN }

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val s by Vani.settings.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var modelDialog by remember { mutableStateOf<Pair<Provider, ModelKind>?>(null) }
    var geminiPresetDialog by remember { mutableStateOf(false) }
    var tileMessage by remember { mutableStateOf<String?>(null) }
    val update: ((AppSettings) -> AppSettings) -> Unit = { Vani.settings.update(it) }

    VaniScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item { SectionTitle("AI engine") }
            item {
                val preset = if (s.sttProvider == s.llmProvider) s.sttProvider else null
                Column(Modifier.padding(horizontal = 8.dp)) {
                    EngineOption(
                        selected = preset == Provider.GEMINI,
                        title = "Gemini (recommended)",
                        body = "Listens to your audio directly, so it's the best with Telugu, Hindi and mixed speech. One fast request. Free key.",
                        onClick = { update { it.copy(sttProvider = Provider.GEMINI, llmProvider = Provider.GEMINI) } },
                    )
                    EngineOption(
                        selected = preset == Provider.GROQ,
                        title = "Groq",
                        body = "Whisper large-v3 for speech, then GPT-OSS for writing. Very fast; has a free tier.",
                        onClick = { update { it.copy(sttProvider = Provider.GROQ, llmProvider = Provider.GROQ) } },
                    )
                    EngineOption(
                        selected = preset == Provider.OPENAI,
                        title = "OpenAI",
                        body = "OpenAI transcription, then a GPT model for writing. Paid.",
                        onClick = { update { it.copy(sttProvider = Provider.OPENAI, llmProvider = Provider.OPENAI) } },
                    )
                    EngineOption(
                        selected = preset == null,
                        title = "Mix and match",
                        body = "Pick one provider for speech and another for writing.",
                        onClick = {
                            if (preset != null) {
                                val other = if (preset == Provider.GEMINI) Provider.GROQ else Provider.GEMINI
                                update { it.copy(llmProvider = other) }
                            }
                        },
                    )
                }
            }
            if (s.sttProvider != s.llmProvider) {
                item { SettingRow("Speech-to-text", s.sttProvider.label, onClick = { dialog = Dialog.STT }) }
                item { SettingRow("Writing", s.llmProvider.label, onClick = { dialog = Dialog.LLM }) }
            }
            item { SubHeading("API keys (stored encrypted on this phone)") }
            Provider.entries.forEach { p ->
                item(key = "key_${p.name}") { ApiKeyCard(p) }
            }
            item {
                SwitchRow(
                    title = "Use another provider if one fails",
                    subtitle = "If you've saved more than one key, Vani quietly switches when a provider is down or out of quota.",
                    checked = s.providerFallback,
                    onChange = { v -> update { it.copy(providerFallback = v) } },
                )
            }
            item {
                val used = listOf(s.sttProvider to ModelKind.STT, s.llmProvider to ModelKind.LLM)
                    .distinctBy { (p, k) -> AppSettings.modelKey(p, k) }
                Column {
                    used.forEach { (p, kind) ->
                        val override = s.modelOverride(p, kind)
                        val auto = Vani.models.current(p, kind)
                        val what = when {
                            p == Provider.GEMINI -> "Gemini model"
                            kind == ModelKind.STT -> "${p.label} speech model"
                            else -> "${p.label} writing model"
                        }
                        val preset = if (p == Provider.GEMINI) {
                            ModelCatalog.geminiPresets.firstOrNull { it.model == ModelCatalog.normalizeId(override) }
                        } else {
                            null
                        }
                        SettingRow(
                            title = what,
                            subtitle = when {
                                preset != null && override.isBlank() -> "${preset.title} · $auto"
                                preset != null -> "${preset.title} · ${preset.model}"
                                override.isBlank() -> "Automatic ($auto)"
                                else -> override
                            },
                            onClick = { if (p == Provider.GEMINI) geminiPresetDialog = true else modelDialog = p to kind },
                        )
                    }
                }
            }

            item { SectionTitle("Writing") }
            item {
                val mode = Modes.find(s.defaultModeId, s.customModes)
                SettingRow("Default style", "${mode.emoji} ${mode.name} (apps can override it below)", icon = Icons.Filled.Palette, onClick = { dialog = Dialog.DEFAULT_MODE })
            }
            item {
                SettingRow(
                    "Write in",
                    if (s.outputLanguage == Languages.SAME) "Same language I speak" else s.outputLanguage,
                    onClick = { dialog = Dialog.LANGUAGE },
                )
            }
            item {
                SettingRow(
                    "Indian languages are written in",
                    if (s.script == Script.NATIVE) "Their own script (తెలుగు, हिंदी)" else "Roman letters (Tenglish / Hinglish style)",
                    onClick = { dialog = Dialog.SCRIPT },
                )
            }
            item {
                SettingRow("English spelling", englishLabel(s.englishVariant), onClick = { dialog = Dialog.ENGLISH })
            }
            item {
                SettingRow(
                    "Your name",
                    s.userName.ifBlank { "Used for email sign-offs and spelling" },
                    onClick = { dialog = Dialog.NAME },
                )
            }
            item {
                SettingRow("Languages you speak", s.spokenLanguages.joinToString(", ").ifBlank { "Auto-detect" }, onClick = { dialog = Dialog.SPOKEN })
            }
            item { SettingRow("My styles", "Create your own writing styles", icon = Icons.Filled.Dashboard, trailing = { Chevron() }, onClick = { onOpen(Routes.STYLES) }) }
            item { SettingRow("Style per app", "${s.appRules.size} apps, e.g. Gmail → Email", icon = Icons.Filled.Apps, trailing = { Chevron() }, onClick = { onOpen(Routes.APPS) }) }
            item {
                SettingRow(
                    "Vocabulary & replacements",
                    "${s.vocabulary.size} words · ${s.replacements.size} replacements",
                    icon = Icons.Filled.Spellcheck,
                    trailing = { Chevron() },
                    onClick = { onOpen(Routes.VOCAB) },
                )
            }

            item { SectionTitle("Dictation") }
            item {
                SwitchRow(
                    title = "Insert automatically",
                    subtitle = "Skip the preview and type the result straight into the app (needs the Accessibility helper).",
                    checked = s.autoInsert,
                    onChange = { v -> update { it.copy(autoInsert = v) } },
                )
            }
            item { SettingRow("Stop listening when I pause", autoStopLabel(s.autoStopMs), onClick = { dialog = Dialog.AUTOSTOP }) }
            item { SettingRow("Longest recording", "${s.maxRecordingSec / 60} min", onClick = { dialog = Dialog.MAXLEN }) }
            item {
                SwitchRow(
                    title = "Use on-screen text as context",
                    subtitle = "Sends the visible text (like the chat you're replying to) along with your words so replies fit better. Off by default.",
                    checked = s.screenContext,
                    onChange = { v -> update { it.copy(screenContext = v) } },
                )
            }
            item {
                SwitchRow("Vibrate on start and stop", null, s.haptics) { v -> update { it.copy(haptics = v) } }
            }
            item {
                SwitchRow("Keep history", "Recent dictations stay on this phone only.", s.saveHistory) { v -> update { it.copy(saveHistory = v) } }
            }

            item { SectionTitle("Starting Vani") }
            item { SettingRow("Buttons & Key mapper", "Hold power, volume buttons, and more", icon = Icons.Filled.Keyboard, trailing = { Chevron() }, onClick = { onOpen(Routes.KEYS) }) }
            item {
                SettingRow(
                    "Add Quick Settings tile",
                    tileMessage ?: "Swipe down, tap Vani, speak",
                    onClick = { SystemSetup.requestTile(ctx) { tileMessage = it } },
                )
            }
            item { SettingRow("History", null, icon = Icons.Filled.History, trailing = { Chevron() }, onClick = { onOpen(Routes.HISTORY) }) }

            item { SectionTitle("About") }
            item {
                SettingRow(
                    "Diagnostics",
                    "Timings and errors of recent AI requests, to copy if something's slow or failing",
                    trailing = { Chevron() },
                    onClick = { onOpen(Routes.DIAG) },
                )
            }
            item {
                Text(
                    "Vani ${BuildConfig.VERSION_NAME}. Your voice is sent only to the AI provider you choose, only while you " +
                        "dictate. API keys are encrypted with the Android Keystore. Inspired by VoiceInk for Mac.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    when (dialog) {
        Dialog.STT, Dialog.LLM -> ChoiceDialog(
            title = if (dialog == Dialog.STT) "Speech-to-text provider" else "Writing provider",
            options = Provider.entries.map { it to it.label },
            selected = if (dialog == Dialog.STT) s.sttProvider else s.llmProvider,
            onPick = { p ->
                update { if (dialog == Dialog.STT) it.copy(sttProvider = p) else it.copy(llmProvider = p) }
                dialog = Dialog.NONE
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.DEFAULT_MODE -> ChoiceDialog(
            title = "Default style",
            options = s.allModes.map { it.id to "${it.emoji} ${it.name}" },
            selected = s.defaultModeId,
            onPick = { id -> update { it.copy(defaultModeId = id) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LANGUAGE -> ChoiceDialog(
            title = "Write the result in",
            options = listOf(Languages.SAME to "Same language I speak") + Languages.outputChoices.map { it to it },
            selected = s.outputLanguage,
            onPick = { v -> update { it.copy(outputLanguage = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SCRIPT -> ChoiceDialog(
            title = "Indian languages are written in",
            options = listOf(Script.NATIVE to "Their own script (తెలుగు, हिंदी)", Script.LATIN to "Roman letters (Tenglish / Hinglish)"),
            selected = s.script,
            onPick = { v -> update { it.copy(script = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ENGLISH -> ChoiceDialog(
            title = "English spelling",
            options = listOf("US", "UK", "IN").map { it to englishLabel(it) },
            selected = s.englishVariant,
            onPick = { v -> update { it.copy(englishVariant = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NAME -> TextInputDialog(
            title = "Your name",
            initial = s.userName,
            label = "Name",
            supporting = "Used to sign emails and to spell your name right.",
            onSave = { v -> update { it.copy(userName = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SPOKEN -> MultiChoiceDialog(
            title = "Languages you speak",
            options = Languages.spokenChoices,
            selected = s.spokenLanguages,
            onSave = { v -> update { it.copy(spokenLanguages = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUTOSTOP -> ChoiceDialog(
            title = "Stop listening after a pause of",
            options = listOf(0, 1500, 2500, 4000, 6000).map { it to autoStopLabel(it) },
            selected = s.autoStopMs,
            onPick = { v -> update { it.copy(autoStopMs = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.MAXLEN -> ChoiceDialog(
            title = "Longest recording",
            options = listOf(60, 120, 180, 300).map { it to "${it / 60} min" },
            selected = s.maxRecordingSec,
            onPick = { v -> update { it.copy(maxRecordingSec = v) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NONE -> Unit
    }

    if (geminiPresetDialog) {
        val current = ModelCatalog.normalizeId(s.modelOverride(Provider.GEMINI, ModelKind.LLM))
        val known = ModelCatalog.geminiPresets.any { it.model == current }
        ChoiceDialog(
            title = "Gemini model",
            options = ModelCatalog.geminiPresets.map { it.model to "${it.title}\n${it.subtitle}" } +
                ("custom" to "Custom model id…" + if (!known) "\n$current" else ""),
            selected = if (known) current else "custom",
            onPick = { v ->
                geminiPresetDialog = false
                if (v == "custom") {
                    modelDialog = Provider.GEMINI to ModelKind.LLM
                } else {
                    val key = AppSettings.modelKey(Provider.GEMINI, ModelKind.LLM)
                    update { st -> st.copy(models = if (v.isBlank()) st.models - key else st.models + (key to v)) }
                }
            },
            onDismiss = { geminiPresetDialog = false },
        )
    }

    modelDialog?.let { (p, kind) ->
        TextInputDialog(
            title = "Model",
            initial = s.modelOverride(p, kind),
            label = "Model id (leave empty for automatic)",
            supporting = "Automatic picks the best model your key can use (now: ${Vani.models.current(p, kind)}). " +
                "Only change this if you know the exact model id.",
            onSave = { raw ->
                val v = ModelCatalog.normalizeId(raw)
                val key = AppSettings.modelKey(p, kind)
                update { st -> st.copy(models = if (v.isBlank()) st.models - key else st.models + (key to v)) }
                modelDialog = null
            },
            onDismiss = { modelDialog = null },
        )
    }
}

@Composable
private fun EngineOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(top = 12.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
}

@Composable
private fun ApiKeyCard(p: Provider) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Vani.keys.version.collectAsStateWithLifecycle()
    val saved = remember(version) { Vani.keys.get(p) }
    var value by remember(version) { mutableStateOf(saved) }
    var visible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (saved.isNotBlank()) "Saved" else "Not set",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (saved.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim() },
                singleLine = true,
                placeholder = { Text(p.keyHint) },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { SystemSetup.openUrl(ctx, p.keyUrl) }) { Text("Get a key") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    enabled = value != saved,
                    onClick = {
                        Vani.keys.set(p, value)
                        status = if (value.isBlank()) "Key removed." else "Saved. Tap Test to check it."
                    },
                ) { Text("Save") }
                OutlinedButton(
                    enabled = !testing && value.isNotBlank(),
                    onClick = {
                        if (value != saved) Vani.keys.set(p, value)
                        testing = true
                        status = "Testing…"
                        scope.launch {
                            status = try {
                                Vani.engine.testKey(p, value)
                            } catch (e: Exception) {
                                Errors.describe(e)
                            }
                            testing = false
                        }
                    },
                ) { Text("Test") }
            }
        }
    }
}

private fun englishLabel(v: String) = when (v) {
    "UK" -> "British (colour, organise)"
    "IN" -> "Indian English"
    else -> "American (color, organize)"
}

private fun autoStopLabel(ms: Int) = if (ms <= 0) "Never (tap Done)" else "After %.1f s".format(ms / 1000.0)
