package com.maheshkatakam.vani.core

/** AI providers Vani can talk to. Each one needs its own API key. */
enum class Provider(val label: String, val keyUrl: String, val keyHint: String) {
    GEMINI("Gemini", "https://aistudio.google.com/apikey", "AIza…"),
    OPENAI("OpenAI", "https://platform.openai.com/api-keys", "sk-…"),
    GROQ("Groq", "https://console.groq.com/keys", "gsk_…");

    companion object {
        fun from(name: String?): Provider? = entries.firstOrNull { it.name == name }
    }
}

/** STT = speech-to-text model, LLM = rewriting model. Gemini uses one model for both. */
enum class ModelKind { STT, LLM }

/** How regional (non-Latin) languages are written in the output. */
enum class Script { NATIVE, LATIN }

/** A writing style ("mode"), like VoiceInk's prompts. */
data class Mode(
    val id: String,
    val name: String,
    val emoji: String,
    val instructions: String,
    val builtIn: Boolean = false,
)

/** Use a specific style automatically when dictating into a given app. */
data class AppRule(val pkg: String, val label: String, val modeId: String)

/** Text replacement applied after every result, e.g. "gonna" -> "going to". */
data class Replacement(val from: String, val to: String)

enum class Gesture(val label: String) {
    LONG("Hold"),
    DOUBLE("Double-press"),
    SINGLE("Single press"),
}

enum class KeyAction(val label: String) {
    DICTATE("Dictate"),
    POLISH_TYPED("Polish what I typed"),
}

/** One Key Mapper entry: a hardware button + gesture that starts Vani. */
data class KeyMapping(
    val id: String,
    val keyCode: Int,
    val keyLabel: String,
    val gesture: Gesture,
    val action: KeyAction = KeyAction.DICTATE,
    val modeId: String? = null,
    val holdToTalk: Boolean = true,
)
