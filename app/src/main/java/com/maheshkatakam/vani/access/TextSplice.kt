package com.maheshkatakam.vani.access

enum class ReplaceMode {
    /** Insert at the cursor (replacing a selection if there is one). */
    INSERT,

    /** Replace the selection, or everything if nothing is selected. */
    SELECTION_OR_ALL,

    /** Replace everything. */
    ALL,
}

/** Works out the final text of a field after inserting, with sensible spacing. Pure logic, unit tested. */
object TextSplice {
    data class Plan(val newText: String, val cursor: Int, val segment: String, val replacesAll: Boolean)

    fun plan(current: String, selStart: Int, selEnd: Int, insert: String, mode: ReplaceMode): Plan {
        val len = current.length
        val hasSelection = selStart in 0..len && selEnd in 0..len && selStart != selEnd
        if (mode == ReplaceMode.ALL || current.isEmpty() || (mode == ReplaceMode.SELECTION_OR_ALL && !hasSelection)) {
            return Plan(insert, insert.length, insert, replacesAll = true)
        }
        var s = if (selStart in 0..len) selStart else len
        var e = if (selEnd in 0..len) selEnd else s
        if (s > e) {
            val t = s
            s = e
            e = t
        }
        val before = current.substring(0, s)
        val after = current.substring(e)
        var segment = insert
        if (mode == ReplaceMode.INSERT && segment.isNotEmpty()) {
            if (before.isNotEmpty() && !before.last().isWhitespace() && needsSpaceBefore(before.last(), segment.first())) {
                segment = " $segment"
            }
            if (after.isNotEmpty() && !after.first().isWhitespace() && !segment.last().isWhitespace() && needsSpaceAfter(after.first())) {
                segment = "$segment "
            }
        }
        return Plan(before + segment + after, before.length + segment.length, segment, replacesAll = false)
    }

    private fun needsSpaceBefore(prev: Char, next: Char): Boolean =
        prev !in OPENERS && (next.isLetterOrDigit() || next in "(\"“'‘")

    private fun needsSpaceAfter(next: Char): Boolean = next.isLetterOrDigit() || next in "(\"“"

    private const val OPENERS = "([{\"'“‘/@#\n"
}
