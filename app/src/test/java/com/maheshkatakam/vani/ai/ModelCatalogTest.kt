package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun picksPreferredWhenAvailable() {
        val models = listOf("gemini-3.6-flash", "gemini-3.8-flash", "gemini-3.8-flash-tts")
        assertEquals("gemini-3.8-flash", ModelCatalog.pick(models, Provider.GEMINI, ModelKind.LLM))
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
}
