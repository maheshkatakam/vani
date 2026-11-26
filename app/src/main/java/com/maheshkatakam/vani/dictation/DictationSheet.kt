package com.maheshkatakam.vani.dictation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maheshkatakam.vani.ai.Task
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.Mode

data class SheetActions(
    val onDone: () -> Unit,
    val onCancel: () -> Unit,
    val onPrimary: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    val onAgain: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onAllowMic: () -> Unit,
    val onSelectMode: (String) -> Unit,
    val onSelectLanguage: (String) -> Unit,
    val onPolishTyped: () -> Unit,
    val onToggleVoiceEdit: () -> Unit,
    val onEdit: (String) -> Unit,
)

@Composable
fun DictationSheet(state: SheetState, modes: List<Mode>, actions: SheetActions) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val outside = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = outside, indication = null) {
                // Tapping outside closes the sheet, but never mid-dictation.
                if (state.phase != Phase.RECORDING && state.phase != Phase.PROCESSING) actions.onCancel()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn()) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(10.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .animateContentSize()
                ) {
                    Header(state, modes, actions)
                    Spacer(Modifier.height(14.dp))
                    when (state.phase) {
                        Phase.PERMISSION -> PermissionContent(actions)
                        Phase.RECORDING -> RecordingContent(state, modes, actions)
                        Phase.PROCESSING -> ProcessingContent(state, modes)
                        Phase.RESULT -> ResultContent(state, modes, actions)
                        Phase.ERROR -> ErrorContent(state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: SheetState, modes: List<Mode>, actions: SheetActions) {
    val mode = modes.firstOrNull { it.id == state.modeId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusOrb(state.phase)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title(state), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val parts = buildList {
                state.appLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                mode?.let { add("${it.emoji} ${it.name}") }
                add("→ " + Languages.label(state.outputLanguage, state.detectedLanguage))
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = actions.onCancel) { Icon(Icons.Filled.Close, contentDescription = "Close") }
    }
}

private fun title(state: SheetState): String = when (state.phase) {
    Phase.PERMISSION -> "Microphone needed"
    Phase.RECORDING -> if (state.voiceEdit && state.hasTypedText) "Say how to change it…" else "Listening…"
    Phase.PROCESSING -> if (state.task == Task.POLISH) "Polishing your text…" else "Writing it up…"
    Phase.RESULT -> "Ready"
    Phase.ERROR -> "That didn't work"
}

@Composable
private fun StatusOrb(phase: Phase) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    val animated = phase == Phase.RECORDING || phase == Phase.PROCESSING
    val bg = if (phase == Phase.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
    val fg = if (phase == Phase.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
    val icon: ImageVector = when (phase) {
        Phase.PERMISSION, Phase.RECORDING -> Icons.Filled.Mic
        Phase.PROCESSING -> Icons.Filled.AutoAwesome
        Phase.RESULT -> Icons.Filled.Check
        Phase.ERROR -> Icons.Filled.PriorityHigh
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                val s = if (animated) pulse else 1f
                scaleX = s
                scaleY = s
            }
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionContent(actions: SheetActions) {
    Text(
        "Vani only listens while this sheet is open. Allow the microphone to start dictating.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = actions.onCancel) { Text("Not now") }
        Spacer(Modifier.weight(1f))
        Button(onClick = actions.onAllowMic) { Text("Allow microphone") }
    }
}

@Composable
private fun RecordingContent(state: SheetState, modes: List<Mode>, actions: SheetActions) {
    Waveform(
        state.levels,
        Modifier
            .fillMaxWidth()
            .height(64.dp)
    )
    Spacer(Modifier.height(8.dp))
    val hint = when {
        state.holdToTalk -> "Release the button when you're done"
        state.autoStopMs > 0 -> "I'll stop when you pause, or tap Done"
        else -> "Tap Done when you've finished"
    }
    Text(
        "${formatElapsed(state.elapsedMs)}  ·  $hint",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.hasTypedText) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = actions.onPolishTyped,
                label = { Text("Polish what I typed") },
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.voiceEdit,
                onClick = actions.onToggleVoiceEdit,
                label = { Text("Voice edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (state.voiceEdit) {
            Text(
                "Say how to change your text, like “make it more polite” or “translate it to Hindi”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    StyleChips(state, modes, actions, enabled = true)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = actions.onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Spacer(Modifier.width(12.dp))
        Button(onClick = actions.onDone, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Done")
        }
    }
}

@Composable
private fun ProcessingContent(state: SheetState, modes: List<Mode>) {
    val mode = modes.firstOrNull { it.id == state.modeId }
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    val target = state.appLabel?.let { " for $it" }.orEmpty()
    Text(
        "${mode?.emoji.orEmpty()} ${mode?.name ?: "Professional"} style$target, in ${Languages.label(state.outputLanguage)}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ResultContent(state: SheetState, modes: List<Mode>, actions: SheetActions) {
    Box {
        OutlinedTextField(
            value = state.output,
            onValueChange = actions.onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 300.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            enabled = !state.busy,
        )
        if (state.busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }

    val original = if (state.task == Task.POLISH) state.sourceText else state.transcript
    if (original.isNotBlank() && original.trim() != state.output.trim()) {
        var show by remember { mutableStateOf(false) }
        val label = when {
            show -> "Hide original"
            state.task == Task.POLISH -> "Show original"
            state.detectedLanguage.isNotBlank() -> "What you said (${state.detectedLanguage})"
            else -> "What you said"
        }
        TextButton(onClick = { show = !show }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Icon(if (show) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visible = show) {
            Text(
                original,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    } else {
        Spacer(Modifier.height(8.dp))
    }

    StyleChips(state, modes, actions, enabled = !state.busy)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
        IconButton(onClick = actions.onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
        if (state.kind == LaunchKind.DICTATE) {
            IconButton(onClick = actions.onAgain) { Icon(Icons.Filled.Mic, contentDescription = "Dictate again") }
        }
        Spacer(Modifier.weight(1f))
        val (label, icon) = primaryAction(state)
        Button(onClick = actions.onPrimary, enabled = !state.busy && state.output.isNotBlank()) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

private fun primaryAction(state: SheetState): Pair<String, ImageVector> = when {
    state.returnsResult && !state.readOnly -> "Replace" to Icons.Filled.Check
    state.canInsert && state.task == Task.DICTATE -> "Insert" to Icons.AutoMirrored.Filled.Send
    state.canInsert -> "Replace" to Icons.Filled.Check
    else -> "Copy" to Icons.Outlined.ContentCopy
}

@Composable
private fun ErrorContent(state: SheetState, actions: SheetActions) {
    Text(state.error ?: "Something went wrong.", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = actions.onOpenSettings) { Text("Settings") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = actions.onCancel) { Text("Close") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = actions.onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Try again")
        }
    }
}

@Composable
private fun StyleChips(state: SheetState, modes: List<Mode>, actions: SheetActions, enabled: Boolean) {
    var showLanguages by remember { mutableStateOf(false) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(modes, key = { it.id }) { m ->
            FilterChip(
                selected = m.id == state.modeId,
                onClick = { actions.onSelectMode(m.id) },
                enabled = enabled,
                label = { Text("${m.emoji} ${m.name}") },
            )
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.outputLanguage == Languages.ENGLISH,
                onClick = { actions.onSelectLanguage(Languages.ENGLISH) },
                enabled = enabled,
                label = { Text("English") },
                leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        item {
            FilterChip(
                selected = state.outputLanguage == Languages.SAME,
                onClick = { actions.onSelectLanguage(Languages.SAME) },
                enabled = enabled,
                label = { Text(Languages.label(Languages.SAME, state.detectedLanguage)) },
            )
        }
        item {
            val other = state.outputLanguage.takeIf { it != Languages.ENGLISH && it != Languages.SAME }
            FilterChip(
                selected = other != null,
                onClick = { showLanguages = true },
                enabled = enabled,
                label = { Text(other ?: "Other…") },
            )
        }
    }
    if (showLanguages) {
        LanguageDialog(
            onDismiss = { showLanguages = false },
            onPick = {
                showLanguages = false
                actions.onSelectLanguage(it)
            },
        )
    }
}

@Composable
fun LanguageDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Write it in") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(Languages.outputChoices) { lang ->
                    Text(
                        lang,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(lang) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val n = levels.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barW = slot * 0.55f
        levels.forEachIndexed { i, lv ->
            val h = (0.06f + 0.94f * lv) * size.height
            val x = i * slot + (slot - barW) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * lv),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
