package com.maheshkatakam.vani.access

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetInfoTest {
    private fun t(text: String, s: Int, e: Int) = TargetInfo("p", "App", true, false, text, s, e, null, null)

    @Test
    fun editableSourceIsSelectionOrAll() {
        assertEquals("world", t("hello world", 6, 11).editableSource)
        assertEquals("hello world", t("hello world", 11, 11).editableSource)
        assertEquals("hello world", t("hello world", -1, -1).editableSource)
    }

    @Test
    fun textBeforeCursor() {
        assertEquals("hello ", t("hello world", 6, 6).textBeforeCursor)
        assertEquals("hello world", t("hello world", -1, -1).textBeforeCursor)
    }
}
