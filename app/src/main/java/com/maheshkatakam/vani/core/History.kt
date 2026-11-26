package com.maheshkatakam.vani.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HistoryItem(
    val id: Long,
    val time: Long,
    val app: String,
    val mode: String,
    val language: String,
    val transcript: String,
    val output: String,
)

/** Recent dictations, kept on the phone only (a JSON file in the app's private storage). */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val io = Executors.newSingleThreadExecutor()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun add(item: HistoryItem) = mutate { list ->
        val unique = if (list.any { it.id == item.id }) item.copy(id = (list.maxOf { it.id }) + 1) else item
        (listOf(unique) + list).take(MAX)
    }
    fun delete(id: Long) = mutate { list -> list.filterNot { it.id == id } }
    fun clear() = mutate { emptyList() }

    @Synchronized
    private fun mutate(f: (List<HistoryItem>) -> List<HistoryItem>) {
        val next = f(_items.value)
        _items.value = next
        val json = JSONArray().apply {
            next.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("time", it.time).put("app", it.app).put("mode", it.mode)
                        .put("lang", it.language).put("transcript", it.transcript).put("output", it.output)
                )
            }
        }.toString()
        io.execute { runCatching { file.writeText(json) } }
    }

    private fun load(): List<HistoryItem> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryItem(
                id = o.optLong("id"), time = o.optLong("time"), app = o.optString("app"),
                mode = o.optString("mode"), language = o.optString("lang"),
                transcript = o.optString("transcript"), output = o.optString("output"),
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX = 300
    }
}
