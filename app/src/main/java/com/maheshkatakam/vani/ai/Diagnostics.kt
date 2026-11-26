package com.maheshkatakam.vani.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short log of recent AI requests (provider, model, size, time taken, result), shown in
 * Settings → Diagnostics so problems like "it's taking forever" can be pinned down.
 * Never contains your API key, audio or text.
 */
object Diagnostics {
    data class Entry(
        val time: Long,
        val provider: String,
        val model: String,
        val what: String,
        val bytes: Int,
        val firstByteMs: Long?,
        val totalMs: Long,
        val result: String,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun log(entry: Entry) {
        _entries.update { (listOf(entry) + it).take(40) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun format(e: Entry): String {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(e.time))
        val size = if (e.bytes >= 1024) "${e.bytes / 1024} KB" else "${e.bytes} B"
        val first = e.firstByteMs?.let { "first reply ${"%.1f".format(it / 1000.0)} s, " }.orEmpty()
        return "$t ${e.provider} ${e.model} · ${e.what} · sent $size · ${first}total ${"%.1f".format(e.totalMs / 1000.0)} s · ${e.result}"
    }

    fun asText(): String = entries.value.joinToString("\n") { format(it) }
}
