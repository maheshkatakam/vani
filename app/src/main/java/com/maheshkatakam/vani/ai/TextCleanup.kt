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

    data class Tagged(val transcript: String, val language: String, val output: String)

    /**
     * Reads Gemini's <transcript>/<language>/<output> reply. Works on partial text while it streams
     * (an unclosed tag runs to the end) and on replies that ignored the format (all of it is the output).
     */
    fun parseTagged(raw: String): Tagged {
        val transcript = between(raw, "transcript")
        val language = between(raw, "language")
        var output = between(raw, "output")
        if (output == null) {
            val lastClose = listOf("</language>", "</transcript>")
                .map { tag -> raw.lastIndexOf(tag).let { if (it < 0) -1 else it + tag.length } }
                .maxOrNull() ?: -1
            output = when {
                transcript == null && language == null -> if (raw.trimStart().startsWith("<")) "" else raw
                lastClose >= 0 && raw.indexOf("<output", lastClose) < 0 && raw.indexOf("<language", lastClose) < 0 ->
                    raw.substring(lastClose)
                else -> ""
            }
        }
        return Tagged(
            transcript = stripPartialTag(transcript.orEmpty()).trim(),
            language = stripPartialTag(language.orEmpty()).trim(),
            output = stripPartialTag(output).trim(),
        )
    }

    private fun between(text: String, tag: String): String? {
        val open = "<$tag>"
        val start = text.indexOf(open)
        if (start < 0) return null
        val from = start + open.length
        val end = text.indexOf("</$tag>", from)
        return if (end < 0) text.substring(from) else text.substring(from, end)
    }

    private val partialTag = Regex("</?[a-zA-Z_]*$")

    /** Drops a tag that's only half streamed in, e.g. "...done.</outp". */
    fun stripPartialTag(text: String): String = text.replace(partialTag, "")

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
