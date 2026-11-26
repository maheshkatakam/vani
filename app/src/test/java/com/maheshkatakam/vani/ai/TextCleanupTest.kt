package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Replacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextCleanupTest {
    @Test
    fun stripsThinkingAndPreamble() {
        assertEquals("Hello Ravi.", TextCleanup.clean("<think>plan</think>\nHere is the rewritten text:\n\nHello Ravi."))
    }

    @Test
    fun stripsFenceAndQuotes() {
        assertEquals("Hi there", TextCleanup.clean("```\nHi there\n```"))
        assertEquals("Hi there", TextCleanup.clean("\"Hi there\""))
        assertEquals("He said \"hi\" twice", TextCleanup.clean("He said \"hi\" twice"))
    }

    @Test
    fun removesMarkdownBold() {
        assertEquals("Please review the deck.", TextCleanup.clean("Please **review** the deck."))
    }

    @Test
    fun keepsNormalText() {
        val text = "Hi Priya,\n\nThe report is ready.\n\nBest regards,\nMahesh"
        assertEquals(text, TextCleanup.clean(text))
    }

    @Test
    fun replacementsAreWholeWordAndCaseInsensitive() {
        val out = TextCleanup.applyReplacements(
            "Gonna send it. Gonnaa is not a word. my email is below",
            listOf(Replacement("gonna", "going to"), Replacement("my email", "mahesh@example.com")),
        )
        assertEquals("going to send it. Gonnaa is not a word. mahesh@example.com is below", out)
    }

    @Test
    fun replacementWithDollarIsLiteral() {
        assertEquals("Costs \$5", TextCleanup.applyReplacements("Costs five", listOf(Replacement("five", "\$5"))))
    }

    @Test
    fun parsesLooseJson() {
        assertEquals("ok", TextCleanup.parseJsonLoose("```json\n{\"output\":\"ok\"}\n```")?.optString("output"))
        assertNotNull(TextCleanup.parseJsonLoose("{\"a\":1}"))
        assertNull(TextCleanup.parseJsonLoose("no json here"))
    }
}
