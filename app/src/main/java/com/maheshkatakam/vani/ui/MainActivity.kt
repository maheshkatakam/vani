package com.maheshkatakam.vani.ui

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.dictation.DictationActivity
import com.maheshkatakam.vani.system.SystemSetup
import com.maheshkatakam.vani.ui.theme.VaniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VaniTheme { VaniNav() } }
    }
}

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STYLES = "styles"
    const val APPS = "apps"
    const val VOCAB = "vocab"
    const val KEYS = "keys"
    const val HISTORY = "history"
}

@Composable
fun VaniNav() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val open: (String) -> Unit = { nav.navigate(it) }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onOpen = open) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back, onOpen = open) }
        composable(Routes.STYLES) { StylesScreen(onBack = back) }
        composable(Routes.APPS) { AppRulesScreen(onBack = back) }
        composable(Routes.VOCAB) { VocabularyScreen(onBack = back) }
        composable(Routes.KEYS) { KeyMapperScreen(onBack = back) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = back) }
    }
}

fun startDictation(ctx: Context, action: String = DictationActivity.ACTION_DICTATE) {
    ctx.startActivity(DictationActivity.intent(ctx, action).putExtra(DictationActivity.EXTRA_NO_TARGET, true))
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val keysVersion by Vani.keys.version.collectAsStateWithLifecycle()
    val settings by Vani.settings.state.collectAsStateWithLifecycle()
    val history by Vani.history.items.collectAsStateWithLifecycle()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val hasMic = remember(tick) { SystemSetup.hasMic(ctx) }
    val hasKey = remember(tick, keysVersion, settings.sttProvider, settings.llmProvider) {
        Vani.keys.has(settings.sttProvider) && Vani.keys.has(settings.llmProvider)
    }
    val isAssistant = remember(tick) { SystemSetup.isAssistant(ctx) }
    val power = remember(tick) { SystemSetup.powerButtonOpensAssistant(ctx) }
    val access = remember(tick) { SystemSetup.isAccessibilityOn(ctx) }

    VaniScaffold(
        title = "Vani",
        actions = {
            IconButton(onClick = { onOpen(Routes.HISTORY) }) { Icon(Icons.Filled.History, contentDescription = "History") }
            IconButton(onClick = { onOpen(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Hold power. Speak. Send.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Talk in English, Telugu, Hindi or any language, even mixed. Vani writes it up " +
                                "professionally and types it right where your cursor is.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { startDictation(ctx) }) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Try it now")
                            }
                            OutlinedButton(onClick = { startDictation(ctx, DictationActivity.ACTION_POLISH_CLIPBOARD) }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Polish clipboard")
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        SetupStep(
                            done = hasMic,
                            icon = Icons.Filled.Mic,
                            title = "Microphone",
                            body = "So Vani can hear you. It only listens while the Vani sheet is open.",
                            action = "Allow",
                            onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                        SetupStep(
                            done = hasKey,
                            icon = Icons.Filled.Key,
                            title = "AI key (${settings.llmProvider.label})",
                            body = "Paste a free Gemini key from aistudio.google.com (or OpenAI / Groq) in Settings.",
                            action = "Add key",
                            onAction = { onOpen(Routes.SETTINGS) },
                        )
                        SetupStep(
                            done = isAssistant,
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Make Vani your digital assistant",
                            body = "Settings → Apps → Default apps → Digital assistant app → Default digital assistant app → Vani.",
                            action = "Open",
                            onAction = { SystemSetup.openAssistantSettings(ctx) },
                        )
                        SetupStep(
                            done = power == true,
                            unknown = power == null,
                            icon = Icons.Filled.PowerSettingsNew,
                            title = "Hold power to open Vani",
                            body = "Settings → System → Gestures → Press & hold power button → Digital assistant. " +
                                "(Double-tap stays on camera or wallet.)",
                            action = "Open",
                            onAction = { SystemSetup.openPowerButtonSettings(ctx) },
                        )
                        SetupStep(
                            done = access,
                            icon = Icons.Filled.Accessibility,
                            title = "Type into any app + Key mapper",
                            body = "Turn on Vani in Accessibility so it can insert text where your cursor is and react to " +
                                "the buttons you map. If the switch is greyed out: tap App info → ⋮ (top right) → " +
                                "Allow restricted settings, then try again.",
                            action = "Turn on",
                            onAction = { SystemSetup.openAccessibilitySettings(ctx) },
                            secondary = "App info",
                            onSecondary = { SystemSetup.openAppInfo(ctx) },
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SettingRow(
                            title = "Default style",
                            subtitle = Modes.find(settings.defaultModeId, settings.customModes).let { "${it.emoji} ${it.name} · Gmail uses Email, WhatsApp uses Message" },
                            onClick = { onOpen(Routes.SETTINGS) },
                        )
                        SettingRow(
                            title = "Write in",
                            subtitle = if (settings.outputLanguage == Languages.SAME) "Same language I speak" else settings.outputLanguage,
                            onClick = { onOpen(Routes.SETTINGS) },
                        )
                        SettingRow(
                            title = "Buttons & shortcuts",
                            subtitle = if (settings.keyMappings.isEmpty()) "Map a volume button, add a Quick Settings tile" else "${settings.keyMappings.size} button mapping(s)",
                            onClick = { onOpen(Routes.KEYS) },
                        )
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                                Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onOpen(Routes.HISTORY) }) { Text("See all") }
                            }
                            history.take(4).forEachIndexed { i, item ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Vani.copy(item.output)
                                            Vani.toast("Copied", long = false)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(item.output, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOf(item.app, item.mode, DateUtils.getRelativeTimeSpanString(item.time).toString())
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    done: Boolean,
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    unknown: Boolean = false,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val statusIcon = when {
            done -> Icons.Filled.CheckCircle
            unknown -> Icons.AutoMirrored.Outlined.HelpOutline
            else -> Icons.Outlined.Circle
        }
        Icon(
            statusIcon,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(action) }
                    if (secondary != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) { Text(secondary) }
                    }
                }
            }
        }
    }
}
