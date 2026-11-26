package com.maheshkatakam.vani.access

/** Snapshot of the app and text box the user was in when they started Vani. */
data class TargetInfo(
    val pkg: String,
    val appLabel: String,
    val hasField: Boolean,
    val isPassword: Boolean,
    val text: String,
    val selStart: Int,
    val selEnd: Int,
    val hint: String?,
    val screenText: String?,
) {
    val selectedText: String
        get() {
            val len = text.length
            if (selStart !in 0..len || selEnd !in 0..len || selStart == selEnd) return ""
            return text.substring(minOf(selStart, selEnd), maxOf(selStart, selEnd))
        }

    /** What "polish what I typed" / voice edit works on: the selection, or the whole box. */
    val editableSource: String get() = selectedText.ifEmpty { text }

    val textBeforeCursor: String
        get() {
            val len = text.length
            val cursor = listOf(selStart, selEnd).filter { it in 0..len }.minOrNull() ?: len
            return text.substring(0, cursor)
        }

    val canInsert: Boolean get() = hasField && !isPassword
}
