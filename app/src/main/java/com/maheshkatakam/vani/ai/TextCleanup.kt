package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Replacement
import org.json.JSONObject

object TextCleanup {
    private val thinkBlocks = Regex("(?s)<(think|thinking|reasoning)>.*?</\\1>")
    private val fence = Regex("^```[a-zA-Z]*\\s*\\n([\\s\\S]*?)\\n?```$")
    private val preamble = Regex("^(?:sure[,!.]?\\s*)?here(?:'s| is| are)[^\\n]{0,80}:\\s*\\n+", RegexOption.IGNORE_CASE)
    private val bold = Regex("\\*\\*(.+?)\\*\\*")

    /** Strips things models sometimes add around the answer. */
    fun clean(raw: String): String {
        var t = raw.replace(thinkBlocks, "").trim()
        fence.find(t)?.let { t = it.groupValues[1].trim() }
        t = t.replace(preamble, "")
        if (t.length >= 2) {
            val first = t.first()
            val last = t.last()
            val wrapped = (first == '"' && last == '"') || (first == '“' && last == '”')
            if (wrapped) {
                val inner = t.substring(1, t.length - 1)
                if (inner.none { it == '"' || it == '“' || it == '”' }) t = inner
            }
        }
        t = t.replace(bold) { it.groupValues[1] }
        return t.trim()
    }

    /** Whole-word, case-insensitive replacements, applied in order. */
    fun applyReplacements(text: String, list: List<Replacement>): String {
        var out = text
        for (r in list) {
            val from = r.from.trim()
            if (from.isEmpty()) continue
            val regex = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(from) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            out = out.replace(regex) { r.to }
        }
        return out
    }

    /** Parses a JSON object even if it's wrapped in a code fence or has stray text around it. */
    fun parseJsonLoose(raw: String): JSONObject? {
        val t = raw.trim()
        runCatching { return JSONObject(t) }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(t.substring(start, end + 1)) }.getOrNull()
    }
}
