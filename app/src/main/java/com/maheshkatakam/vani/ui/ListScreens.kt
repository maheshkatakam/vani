package com.maheshkatakam.vani.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.core.AppRule
import com.maheshkatakam.vani.core.HistoryItem
import com.maheshkatakam.vani.core.Mode
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.core.Replacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Styles

@Composable
fun StylesScreen(onBack: () -> Unit) {
    val s by Vani.settings.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Mode?>(null) }
    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Mode?>(null) }

    VaniScaffold(
        title = "Styles",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New style") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "A style tells Vani how to write. Tap one to see its instructions. Your own styles appear as chips on the Vani sheet too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (s.customModes.isNotEmpty()) {
                item { SectionTitle("My styles") }
                items(s.customModes, key = { it.id }) { m ->
                    SettingRow(
                        title = "${m.emoji} ${m.name}",
                        subtitle = m.instructions,
                        trailing = {
                            Row {
                                IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                                IconButton(onClick = {
                                    Vani.settings.update { st -> st.copy(customModes = st.customModes.filterNot { it.id == m.id }) }
                                }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                            }
                        },
                        onClick = { editing = m },
                    )
                }
            }
            item { SectionTitle("Built-in") }
            items(Modes.builtIns, key = { it.id }) { m ->
                SettingRow(title = "${m.emoji} ${m.name}", subtitle = m.instructions.take(90) + "…", onClick = { viewing = m })
            }
        }
    }

    if (creating || editing != null) {
        StyleEditDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, emoji, instructions ->
                val current = editing
                Vani.settings.update { st ->
                    if (current != null) {
                        st.copy(customModes = st.customModes.map {
                            if (it.id == current.id) it.copy(name = name, emoji = emoji, instructions = instructions) else it
                        })
                    } else {
                        st.copy(customModes = st.customModes + Mode("custom_${System.currentTimeMillis()}", name, emoji, instructions))
                    }
                }
                creating = false
                editing = null
            },
        )
    }

    viewing?.let { m ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    viewing = null
                    editing = null
                    Vani.settings.update { st ->
                        st.copy(customModes = st.customModes + m.copy(id = "custom_${System.currentTimeMillis()}", name = "${m.name} (mine)", builtIn = false))
                    }
                }) { Text("Make a copy to edit") }
            },
            title = { Text("${m.emoji} ${m.name}") },
            text = { Text(m.instructions, style = MaterialTheme.typography.bodyMedium) },
        )
    }
}

@Composable
private fun StyleEditDialog(initial: Mode?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "✨") }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && instructions.isNotBlank(),
                onClick = { onSave(name.trim(), emoji.trim().ifBlank { "✨" }, instructions.trim()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (initial == null) "New style" else "Edit style") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(90.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    instructions,
                    { instructions = it },
                    label = { Text("How should Vani write?") },
                    placeholder = { Text("e.g. Write a LinkedIn post: a hook in the first line, short paragraphs, 3 hashtags at the end.") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
            }
        },
    )
}

// ------------------------------------------------------------------ Per-app styles

@Composable
fun AppRulesScreen(onBack: () -> Unit) {
    val s by Vani.settings.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf<AppRule?>(null) }

    VaniScaffold(
        title = "Style per app",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add app") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "When you start Vani inside one of these apps, it uses that app's style automatically. You can still switch styles on the sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(s.appRules, key = { it.pkg }) { rule ->
                val mode = Modes.find(rule.modeId, s.customModes)
                SettingRow(
                    title = rule.label,
                    subtitle = "${mode.emoji} ${mode.name}",
                    trailing = {
                        IconButton(onClick = {
                            Vani.settings.update { st -> st.copy(appRules = st.appRules.filterNot { it.pkg == rule.pkg }) }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    },
                    onClick = { styling = rule },
                )
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            onDismiss = { picking = false },
            onPick = { pkg, label ->
                picking = false
                styling = AppRule(pkg, label, s.defaultModeId)
            },
        )
    }
    styling?.let { rule ->
        ChoiceDialog(
            title = "Style for ${rule.label}",
            options = s.allModes.map { it.id to "${it.emoji} ${it.name}" },
            selected = rule.modeId,
            onPick = { id ->
                Vani.settings.update { st ->
                    val others = st.appRules.filterNot { it.pkg == rule.pkg }
                    st.copy(appRules = (others + rule.copy(modeId = id)).sortedBy { it.label.lowercase() })
                }
                styling = null
            },
            onDismiss = { styling = null },
        )
    }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedBy { it.second.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Pick an app") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Text("Loading apps…")
                } else {
                    val filtered = list.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(pkg, label) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------------ Vocabulary & replacements

@Composable
fun VocabularyScreen(onBack: () -> Unit) {
    val s by Vani.settings.state.collectAsStateWithLifecycle()
    var word by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    VaniScaffold(title = "Vocabulary", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SectionTitle("Words & names") }
            item {
                Text(
                    "Names, brands and terms Vani should always spell exactly like this (e.g. your name, your university, product names).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(word, { word = it }, label = { Text("Add a word") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = word.isNotBlank(),
                        onClick = {
                            val w = word.trim()
                            Vani.settings.update { st -> if (w in st.vocabulary) st else st.copy(vocabulary = st.vocabulary + w) }
                            word = ""
                        },
                    ) { Text("Add") }
                }
            }
            items(s.vocabulary, key = { "w_$it" }) { w ->
                SettingRow(
                    title = w,
                    trailing = {
                        IconButton(onClick = { Vani.settings.update { st -> st.copy(vocabulary = st.vocabulary - w) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    },
                )
            }

            item { SectionTitle("Replacements") }
            item {
                Text(
                    "Applied to every result, whole words only. Handy for abbreviations, e.g. \"my email\" → your address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(from, { from = it }, label = { Text("Replace") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(to, { to = it }, label = { Text("With") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        enabled = from.isNotBlank(),
                        onClick = {
                            val r = Replacement(from.trim(), to.trim())
                            Vani.settings.update { st -> st.copy(replacements = st.replacements.filterNot { it.from.equals(r.from, true) } + r) }
                            from = ""
                            to = ""
                        },
                    ) { Text("Add replacement") }
                }
            }
            items(s.replacements, key = { "r_${it.from}" }) { r ->
                SettingRow(
                    title = "${r.from}  →  ${r.to.ifBlank { "(remove)" }}",
                    trailing = {
                        IconButton(onClick = { Vani.settings.update { st -> st.copy(replacements = st.replacements - r) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ History

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val items by Vani.history.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<HistoryItem?>(null) }

    VaniScaffold(
        title = "History",
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all") }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Text(
                "Nothing yet. Your dictations will show up here (they stay on this phone).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth().clickable { open = item }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.output, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOf(item.app, item.mode, item.language, DateUtils.getRelativeTimeSpanString(item.time).toString())
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    Vani.copy(item.output)
                                    Vani.toast("Copied", long = false)
                                }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            message = "This deletes all saved dictations from this phone. It can't be undone.",
            confirm = "Clear",
            onConfirm = {
                Vani.history.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    open?.let { item ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = {
                TextButton(onClick = {
                    Vani.copy(item.output)
                    Vani.toast("Copied", long = false)
                    open = null
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Vani.history.delete(item.id)
                    open = null
                }) { Text("Delete") }
            },
            title = { Text(listOf(item.app, item.mode).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Dictation" }) },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    LazyColumn {
                        item { Text(item.output, style = MaterialTheme.typography.bodyLarge) }
                        if (item.transcript.isNotBlank()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text("Original", style = MaterialTheme.typography.labelLarge)
                                Text(item.transcript, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
        )
    }
}
