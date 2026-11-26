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

    @Test
    fun parsesFullTaggedReply() {
        val t = TextCleanup.parseTagged(
            "<transcript>\nనేను పది నిమిషాలు లేట్\n</transcript>\n<language>Telugu</language>\n<output>\nI'll be 10 minutes late.\n</output>"
        )
        assertEquals("నేను పది నిమిషాలు లేట్", t.transcript)
        assertEquals("Telugu", t.language)
        assertEquals("I'll be 10 minutes late.", t.output)
    }

    @Test
    fun parsesWhileStreaming() {
        assertEquals("", TextCleanup.parseTagged("<transcript>\nhello th").output)
        assertEquals("hello th", TextCleanup.parseTagged("<transcript>\nhello th").transcript)
        assertEquals("", TextCleanup.parseTagged("<transcript>hi</transcript>\n<langu").output)
        assertEquals("", TextCleanup.parseTagged("<transcript>hi</transcript><language>English</language>\n<outp").output)
        assertEquals("Hello wor", TextCleanup.parseTagged("<transcript>hi</transcript><output>\nHello wor").output)
        assertEquals("Hello world.", TextCleanup.parseTagged("<output>\nHello world.\n</outp").output)
    }

    @Test
    fun untaggedReplyIsAllOutput() {
        assertEquals("Hello there.", TextCleanup.parseTagged("Hello there.").output)
        assertEquals("Final text", TextCleanup.parseTagged("<transcript>x</transcript><language>English</language>Final text").output)
    }
}
