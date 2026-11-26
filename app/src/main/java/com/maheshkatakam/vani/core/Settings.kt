package com.maheshkatakam.vani.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val sttProvider: Provider = Provider.GEMINI,
    val llmProvider: Provider = Provider.GEMINI,
    /** User-chosen model per provider/kind. Empty = pick automatically. Keys come from [modelKey]. */
    val models: Map<String, String> = emptyMap(),
    val defaultModeId: String = Modes.PROFESSIONAL,
    val outputLanguage: String = Languages.ENGLISH,
    val script: Script = Script.NATIVE,
    val englishVariant: String = "US",
    val userName: String = "",
    val spokenLanguages: List<String> = listOf("English"),
    val customModes: List<Mode> = emptyList(),
    val appRules: List<AppRule> = DefaultAppRules.list,
    val vocabulary: List<String> = emptyList(),
    val replacements: List<Replacement> = emptyList(),
    val autoInsert: Boolean = false,
    val autoStopMs: Int = 2500,
    val maxRecordingSec: Int = 300,
    val haptics: Boolean = true,
    val screenContext: Boolean = false,
    val saveHistory: Boolean = true,
    val providerFallback: Boolean = true,
    val keyMappings: List<KeyMapping> = emptyList(),
    val longPressMs: Int = 450,
    val doublePressMs: Int = 300,
) {
    fun modelOverride(p: Provider, kind: ModelKind): String = models[modelKey(p, kind)].orEmpty()

    val allModes: List<Mode> get() = Modes.all(customModes)

    companion object {
        fun modelKey(p: Provider, kind: ModelKind): String =
            if (p == Provider.GEMINI) "GEMINI" else "${p.name}_${kind.name}"
    }
}

object SettingsJson {
    fun toJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("v", 1)
        put("stt", s.sttProvider.name)
        put("llm", s.llmProvider.name)
        put("models", JSONObject().apply { s.models.forEach { (k, v) -> put(k, v) } })
        put("defaultMode", s.defaultModeId)
        put("outLang", s.outputLanguage)
        put("script", s.script.name)
        put("engVar", s.englishVariant)
        put("name", s.userName)
        put("spoken", JSONArray().apply { s.spokenLanguages.forEach { put(it) } })
        put("customModes", JSONArray().apply {
            s.customModes.forEach { m ->
                put(JSONObject().put("id", m.id).put("name", m.name).put("emoji", m.emoji).put("ins", m.instructions))
            }
        })
        put("appRules", JSONArray().apply {
            s.appRules.forEach { put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("mode", it.modeId)) }
        })
        put("vocab", JSONArray().apply { s.vocabulary.forEach { put(it) } })
        put("repl", JSONArray().apply {
            s.replacements.forEach { put(JSONObject().put("from", it.from).put("to", it.to)) }
        })
        put("autoInsert", s.autoInsert)
        put("autoStopMs", s.autoStopMs)
        put("maxSec", s.maxRecordingSec)
        put("haptics", s.haptics)
        put("screenCtx", s.screenContext)
        put("history", s.saveHistory)
        put("fallback", s.providerFallback)
        put("keys", JSONArray().apply {
            s.keyMappings.forEach { k ->
                put(
                    JSONObject()
                        .put("id", k.id)
                        .put("code", k.keyCode)
                        .put("label", k.keyLabel)
                        .put("gesture", k.gesture.name)
                        .put("action", k.action.name)
                        .put("mode", k.modeId ?: "")
                        .put("htt", k.holdToTalk)
                )
            }
        })
        put("longMs", s.longPressMs)
        put("doubleMs", s.doublePressMs)
    }

    fun fromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sttProvider = Provider.from(o.optString("stt")) ?: d.sttProvider,
            llmProvider = Provider.from(o.optString("llm")) ?: d.llmProvider,
            models = o.optJSONObject("models")?.let { m ->
                buildMap<String, String> {
                    m.keys().forEach { k -> val v = m.optString(k); if (v.isNotBlank()) put(k, v) }
                }
            } ?: d.models,
            defaultModeId = o.optString("defaultMode").ifBlank { d.defaultModeId },
            outputLanguage = o.optString("outLang").ifBlank { d.outputLanguage },
            script = runCatching { Script.valueOf(o.optString("script")) }.getOrDefault(d.script),
            englishVariant = o.optString("engVar").ifBlank { d.englishVariant },
            userName = o.optString("name"),
            spokenLanguages = o.optJSONArray("spoken")?.strings() ?: d.spokenLanguages,
            customModes = o.optJSONArray("customModes")?.objects()?.mapNotNull { m ->
                val id = m.optString("id")
                if (id.isBlank()) null else Mode(id, m.optString("name"), m.optString("emoji"), m.optString("ins"))
            } ?: d.customModes,
            appRules = o.optJSONArray("appRules")?.objects()?.mapNotNull { r ->
                val pkg = r.optString("pkg")
                if (pkg.isBlank()) null else AppRule(pkg, r.optString("label").ifBlank { pkg }, r.optString("mode"))
            } ?: d.appRules,
            vocabulary = o.optJSONArray("vocab")?.strings() ?: d.vocabulary,
            replacements = o.optJSONArray("repl")?.objects()?.mapNotNull { r ->
                val from = r.optString("from")
                if (from.isBlank()) null else Replacement(from, r.optString("to"))
            } ?: d.replacements,
            autoInsert = o.optBoolean("autoInsert", d.autoInsert),
            autoStopMs = o.optInt("autoStopMs", d.autoStopMs),
            maxRecordingSec = o.optInt("maxSec", d.maxRecordingSec),
            haptics = o.optBoolean("haptics", d.haptics),
            screenContext = o.optBoolean("screenCtx", d.screenContext),
            saveHistory = o.optBoolean("history", d.saveHistory),
            providerFallback = o.optBoolean("fallback", d.providerFallback),
            keyMappings = o.optJSONArray("keys")?.objects()?.mapNotNull { k ->
                val gesture = runCatching { Gesture.valueOf(k.optString("gesture")) }.getOrNull() ?: return@mapNotNull null
                KeyMapping(
                    id = k.optString("id").ifBlank { "k${k.optInt("code")}${gesture.name}" },
                    keyCode = k.optInt("code"),
                    keyLabel = k.optString("label"),
                    gesture = gesture,
                    action = runCatching { KeyAction.valueOf(k.optString("action")) }.getOrDefault(KeyAction.DICTATE),
                    modeId = k.optString("mode").ifBlank { null },
                    holdToTalk = k.optBoolean("htt", true),
                )
            } ?: d.keyMappings,
            longPressMs = o.optInt("longMs", d.longPressMs),
            doublePressMs = o.optInt("doubleMs", d.doublePressMs),
        )
    }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Settings live in one JSON blob in SharedPreferences and are mirrored in a StateFlow. */
class SettingsRepo(context: Context) {
    private val prefs = context.getSharedPreferences("vani_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = runCatching {
        prefs.getString("json", null)?.let { SettingsJson.fromJson(JSONObject(it)) }
    }.getOrNull() ?: AppSettings()

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val next = transform(_state.value)
            _state.value = next
            prefs.edit().putString("json", SettingsJson.toJson(next).toString()).apply()
        }
    }
}
