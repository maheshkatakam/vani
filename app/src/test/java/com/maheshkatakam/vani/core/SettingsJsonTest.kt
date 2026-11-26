package com.maheshkatakam.vani.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun roundTrip() {
        val s = AppSettings(
            sttProvider = Provider.GROQ,
            llmProvider = Provider.GEMINI,
            models = mapOf("GEMINI" to "gemini-3.6-flash"),
            defaultModeId = Modes.EMAIL,
            outputLanguage = Languages.SAME,
            script = Script.LATIN,
            englishVariant = "IN",
            userName = "Mahesh Katakam",
            spokenLanguages = listOf("English", "Telugu"),
            customModes = listOf(Mode("custom_1", "LinkedIn", "📣", "Write a LinkedIn post.")),
            appRules = listOf(AppRule("com.whatsapp", "WhatsApp", Modes.MESSAGE)),
            vocabulary = listOf("Cumberlands"),
            replacements = listOf(Replacement("gonna", "going to")),
            autoInsert = true,
            autoStopMs = 4000,
            maxRecordingSec = 600,
            haptics = false,
            screenContext = true,
            saveHistory = false,
            providerFallback = false,
            keyMappings = listOf(KeyMapping("k1", 25, "Volume down", Gesture.LONG, KeyAction.DICTATE, null, true)),
            longPressMs = 500,
            doublePressMs = 250,
        )
        val back = SettingsJson.fromJson(JSONObject(SettingsJson.toJson(s).toString()))
        assertEquals(s, back)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val back = SettingsJson.fromJson(JSONObject("{\"name\":\"M\"}"))
        assertEquals(AppSettings(userName = "M"), back)
    }
}
