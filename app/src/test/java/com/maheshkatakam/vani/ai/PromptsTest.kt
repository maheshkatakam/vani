package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.core.Script
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private fun style(lang: String = Languages.ENGLISH, script: Script = Script.NATIVE, mode: String = Modes.PROFESSIONAL) =
        StyleSpec(Modes.find(mode, emptyList()), lang, script, "US")

    @Test
    fun dictationPromptNeverAnswersTheUser() {
        val p = Prompts.system(Task.DICTATE, style())
        assertTrue(p.contains("never a request to you"))
        assertTrue(p.contains("STYLE: PROFESSIONAL"))
        assertTrue(p.contains("natural American English"))
        assertTrue(p.contains("translate the full meaning"))
    }

    @Test
    fun sameLanguageUsesScriptChoice() {
        val native = Prompts.languageRule(style(Languages.SAME, Script.NATIVE))
        val latin = Prompts.languageRule(style(Languages.SAME, Script.LATIN))
        assertTrue(native.contains("native script"))
        assertTrue(latin.contains("Roman letters"))
    }

    @Test
    fun specificIndianTargetGetsScriptRule() {
        val r = Prompts.languageRule(style("Telugu", Script.LATIN))
        assertTrue(r.startsWith("Write the output in Telugu"))
        assertTrue(r.contains("Roman letters"))
        val es = Prompts.languageRule(style("Spanish"))
        assertFalse(es.contains("script"))
    }

    @Test
    fun polishPromptHasNoSpeechRules() {
        val p = Prompts.system(Task.POLISH, style())
        assertFalse(p.contains("filler"))
        assertTrue(p.contains("<text>"))
    }

    @Test
    fun voiceEditTreatsSpeechAsInstruction() {
        val p = Prompts.system(Task.VOICE_EDIT, style())
        assertTrue(p.contains("editing instruction"))
        val u = Prompts.textUser(Task.VOICE_EDIT, "make it polite", "send the file now", PromptContext())
        assertTrue(u.contains("<existing_text>\nsend the file now\n</existing_text>"))
        assertTrue(u.contains("<spoken_instruction>\nmake it polite\n</spoken_instruction>"))
    }

    @Test
    fun contextBlockOnlyHasWhatWeKnow() {
        val empty = Prompts.textUser(Task.DICTATE, "hello", null, PromptContext())
        assertFalse(empty.contains("<context>"))
        val full = Prompts.textUser(
            Task.DICTATE, "hello", null,
            PromptContext(appLabel = "WhatsApp", userName = "Mahesh", vocabulary = listOf("Cumberlands"), screenText = "Are you coming?"),
        )
        assertTrue(full.contains("App the user is writing in: WhatsApp"))
        assertTrue(full.contains("Vocabulary (exact spellings): Cumberlands"))
        assertTrue(full.contains("Are you coming?"))
        assertTrue(full.contains("<transcript>\nhello\n</transcript>"))
    }

    @Test
    fun audioPromptAsksForJsonFields() {
        val u = Prompts.audioUser(Task.DICTATE, null, PromptContext())
        assertTrue(u.contains("\"transcript\""))
        assertTrue(u.contains("\"language\""))
        assertTrue(u.contains("\"output\""))
    }

    @Test
    fun allBuiltInStylesHaveInstructions() {
        Modes.builtIns.forEach { assertTrue(it.name, it.instructions.length > 60) }
    }
}
