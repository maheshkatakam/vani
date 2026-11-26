package com.maheshkatakam.vani.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSpliceTest {
    @Test
    fun emptyFieldGetsWholeText() {
        val p = TextSplice.plan("", -1, -1, "Hello there.", ReplaceMode.INSERT)
        assertEquals("Hello there.", p.newText)
        assertEquals(12, p.cursor)
        assertTrue(p.replacesAll)
    }

    @Test
    fun insertAtEndAddsSpace() {
        val p = TextSplice.plan("Hi Ravi,", 8, 8, "I'll be late.", ReplaceMode.INSERT)
        assertEquals("Hi Ravi, I'll be late.", p.newText)
        assertEquals(p.newText.length, p.cursor)
        assertEquals(" I'll be late.", p.segment)
    }

    @Test
    fun insertInMiddleAddsSpacesBothSides() {
        val p = TextSplice.plan("Hello world", 5, 5, "big", ReplaceMode.INSERT)
        assertEquals("Hello big world", p.newText)
        assertEquals(9, p.cursor)
    }

    @Test
    fun noSpaceAfterNewlineOrBeforePunctuation() {
        assertEquals("Line one\nNext", TextSplice.plan("Line one\n", 9, 9, "Next", ReplaceMode.INSERT).newText)
        assertEquals("Thanks.", TextSplice.plan(".", 0, 0, "Thanks", ReplaceMode.INSERT).newText)
    }

    @Test
    fun unknownCursorAppends() {
        val p = TextSplice.plan("Draft", -1, -1, "more", ReplaceMode.INSERT)
        assertEquals("Draft more", p.newText)
    }

    @Test
    fun selectionIsReplaced() {
        val p = TextSplice.plan("pls send it asap", 0, 16, "Please send it as soon as possible.", ReplaceMode.SELECTION_OR_ALL)
        assertEquals("Please send it as soon as possible.", p.newText)
    }

    @Test
    fun partialSelectionReplacedKeepsRest() {
        val p = TextSplice.plan("Hi Ravi. pls send it", 9, 20, "Please send it.", ReplaceMode.SELECTION_OR_ALL)
        assertEquals("Hi Ravi. Please send it.", p.newText)
        assertEquals(false, p.replacesAll)
    }

    @Test
    fun noSelectionReplacesAllInPolishMode() {
        val p = TextSplice.plan("rough text", 3, 3, "Polished text.", ReplaceMode.SELECTION_OR_ALL)
        assertEquals("Polished text.", p.newText)
        assertTrue(p.replacesAll)
    }

    @Test
    fun reversedSelectionIsHandled() {
        val p = TextSplice.plan("abc def", 7, 4, "xyz", ReplaceMode.INSERT)
        assertEquals("abc xyz", p.newText)
    }
}
