package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun picksPreferredWhenAvailable() {
        val models = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.8-flash-tts")
        assertEquals("gemini-3.6-flash", ModelCatalog.pick(models, Provider.GEMINI, ModelKind.LLM))
        assertEquals("gemini-3.8-flash", ModelCatalog.pick(listOf("gemini-3.8-flash", "gemini-3.8-flash-tts"), Provider.GEMINI, ModelKind.LLM))
    }

    @Test
    fun fallsBackToNewestFlash() {
        val models = listOf("gemini-4.0-flash", "gemini-4.0-flash-lite", "gemini-4.0-flash-live", "gemini-4.0-pro")
        assertEquals("gemini-4.0-flash", ModelCatalog.pick(models, Provider.GEMINI, ModelKind.LLM))
    }

    @Test
    fun groqAndOpenAi() {
        assertEquals("whisper-large-v3", ModelCatalog.pick(listOf("whisper-large-v3-turbo", "whisper-large-v3"), Provider.GROQ, ModelKind.STT))
        assertEquals("openai/gpt-oss-120b", ModelCatalog.pick(listOf("llama-3.1-8b-instant", "openai/gpt-oss-120b"), Provider.GROQ, ModelKind.LLM))
        assertEquals("gpt-transcribe", ModelCatalog.pick(listOf("gpt-transcribe", "gpt-6-luna"), Provider.OPENAI, ModelKind.STT))
        assertEquals("gpt-9-nova", ModelCatalog.pick(listOf("gpt-9-nova", "gpt-9-nova-realtime"), Provider.OPENAI, ModelKind.LLM))
    }

    @Test
    fun normalizesTypedModelNames() {
        assertEquals("gemini-3.8-flash", ModelCatalog.normalizeId("Gemini 3.8 Flash"))
        assertEquals("gemini-3.8-flash", ModelCatalog.normalizeId(" models/gemini-3.8-flash "))
        assertEquals("openai/gpt-oss-120b", ModelCatalog.normalizeId("openai/gpt-oss-120b"))
        assertEquals("", ModelCatalog.normalizeId("   "))
    }

    @Test
    fun fastestGeminiIsFlashLite() {
        assertEquals("gemini-3.5-flash-lite", ModelCatalog.pickFastestGemini(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")))
        assertEquals("gemini-4.0-flash-lite", ModelCatalog.pickFastestGemini(listOf("gemini-4.0-flash", "gemini-4.0-flash-lite", "gemini-4.0-flash-lite-tts")))
    }

    @Test
    fun thinkingLevelsUseApiSpellingAndFastestFirst() {
        assertEquals(listOf("MINIMAL", "LOW", null), GeminiApi.thinkingCandidates("gemini-3.6-flash"))
        assertEquals(listOf("LOW", null), GeminiApi.thinkingCandidates("gemini-3.8-flash"))
        assertEquals(listOf("MINIMAL", "LOW", null), GeminiApi.thinkingCandidates("gemini-3.5-flash-lite"))
        assertEquals(listOf<String?>(null), GeminiApi.thinkingCandidates("gemini-2.5-flash"))
    }
}
