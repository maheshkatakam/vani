package com.maheshkatakam.vani.dictation

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.access.ReplaceMode
import com.maheshkatakam.vani.access.TargetInfo
import com.maheshkatakam.vani.access.VaniAccessibilityService
import com.maheshkatakam.vani.ai.Errors
import com.maheshkatakam.vani.ai.PromptContext
import com.maheshkatakam.vani.ai.StyleSpec
import com.maheshkatakam.vani.ai.Task
import com.maheshkatakam.vani.ai.TextCleanup
import com.maheshkatakam.vani.audio.VoiceRecorder
import com.maheshkatakam.vani.core.HistoryItem
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.Modes
import com.maheshkatakam.vani.system.Haptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { PERMISSION, RECORDING, PROCESSING, RESULT, ERROR }

enum class LaunchKind { DICTATE, POLISH_TYPED, POLISH_TEXT, POLISH_CLIPBOARD }

data class LaunchRequest(
    val kind: LaunchKind,
    val modeId: String? = null,
    val holdToTalk: Boolean = false,
    val target: TargetInfo? = null,
    val sourcePackage: String? = null,
    val text: String? = null,
    val readOnly: Boolean = false,
    val returnsResult: Boolean = false,
)

const val BAR_COUNT = 36
private val EMPTY_LEVELS = List(BAR_COUNT) { 0f }

data class SheetState(
    val phase: Phase = Phase.RECORDING,
    val kind: LaunchKind = LaunchKind.DICTATE,
    val task: Task = Task.DICTATE,
    val levels: List<Float> = EMPTY_LEVELS,
    val elapsedMs: Long = 0,
    val target: TargetInfo? = null,
    val appLabel: String? = null,
    val modeId: String = Modes.PROFESSIONAL,
    val outputLanguage: String = Languages.ENGLISH,
    val voiceEdit: Boolean = false,
    val holdToTalk: Boolean = false,
    val autoStopMs: Int = 2500,
    val sourceText: String = "",
    val transcript: String = "",
    val detectedLanguage: String = "",
    val output: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val canInsert: Boolean = false,
    val returnsResult: Boolean = false,
    val readOnly: Boolean = false,
) {
    val hasTypedText: Boolean get() = target?.editableSource?.isNotBlank() == true
}

sealed interface SheetEvent {
    data object Close : SheetEvent
    data object RequestMic : SheetEvent
    data class Share(val text: String) : SheetEvent
    data class ReturnResult(val text: String) : SheetEvent
}

class DictationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val settings get() = Vani.settings.current

    private val _state = MutableStateFlow(SheetState())
    val state: StateFlow<SheetState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SheetEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SheetEvent> = _events.asSharedFlow()

    var started = false
        private set

    private var recorder: VoiceRecorder? = null
    private var workJob: Job? = null
    private var lastAudio: ByteArray? = null
    private var autoInsertDone = false
    private var holdJob: Job? = null

    /** One history entry per dictation; switching style or language updates it. */
    private var historyId: Long? = null

    init {
        viewModelScope.launch {
            DictationBus.commands.collect { command ->
                when (command) {
                    DictationBus.Command.FINISH -> finishRecording()
                    DictationBus.Command.CANCEL -> cancel()
                }
            }
        }
    }

    fun start(req: LaunchRequest, hasMic: Boolean) {
        if (started) return
        started = true
        val s = settings
        val target = req.target
        val pkg = target?.pkg ?: req.sourcePackage?.takeIf { it != app.packageName }
        val ruleMode = pkg?.let { p -> s.appRules.firstOrNull { it.pkg == p }?.modeId }
        val modeId = Modes.find(req.modeId?.takeIf { it.isNotBlank() } ?: ruleMode ?: s.defaultModeId, s.customModes).id
        _state.value = SheetState(
            phase = if (req.kind == LaunchKind.DICTATE) Phase.RECORDING else Phase.PROCESSING,
            kind = req.kind,
            target = target,
            appLabel = target?.appLabel ?: pkg?.let { Vani.appLabel(it) },
            modeId = modeId,
            outputLanguage = s.outputLanguage,
            holdToTalk = req.holdToTalk,
            autoStopMs = s.autoStopMs,
            canInsert = VaniAccessibilityService.instance != null && target?.canInsert == true && !req.returnsResult,
            returnsResult = req.returnsResult,
            readOnly = req.readOnly,
        )
        when (req.kind) {
            LaunchKind.DICTATE -> {
                if (hasMic) beginRecording() else _state.update { it.copy(phase = Phase.PERMISSION) }
                if (req.holdToTalk) watchHoldRelease()
            }
            LaunchKind.POLISH_TYPED -> polishTyped()
            LaunchKind.POLISH_TEXT -> polishText(req.text.orEmpty())
            LaunchKind.POLISH_CLIPBOARD -> Unit // the activity reads the clipboard once it has focus
        }
    }

    fun onMicPermission(granted: Boolean) {
        if (granted) beginRecording() else fail("Vani needs microphone access to hear you.")
    }

    fun requestMic() {
        _events.tryEmit(SheetEvent.RequestMic)
    }

    /** Pressing the trigger again (power button, mapped key, tile) while the sheet is open. */
    fun onRetrigger(hold: Boolean = false) {
        when (state.value.phase) {
            Phase.RECORDING -> finishRecording()
            Phase.RESULT, Phase.ERROR -> {
                _state.update { it.copy(holdToTalk = hold) }
                beginRecording()
                if (hold) watchHoldRelease()
            }
            else -> Unit
        }
    }

    /** Hold-to-talk: stop listening as soon as the mapped button is released. */
    private fun watchHoldRelease() {
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            DictationBus.holdReleased.first { it }
            finishRecording()
        }
    }

    fun beginRecording() {
        workJob?.cancel()
        recorder?.cancel()
        val s = settings
        val rec = VoiceRecorder()
        recorder = rec
        lastAudio = null
        historyId = null
        _state.update {
            it.copy(
                phase = Phase.RECORDING, task = Task.DICTATE, error = null, levels = EMPTY_LEVELS,
                elapsedMs = 0, output = "", transcript = "", busy = false,
            )
        }
        Haptics.start(app)
        workJob = viewModelScope.launch {
            val levelJob = launch {
                rec.level.collect { lv -> _state.update { st -> st.copy(levels = st.levels.drop(1) + lv) } }
            }
            val startedAt = SystemClock.elapsedRealtime()
            val timerJob = launch {
                while (true) {
                    _state.update { it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt) }
                    delay(250)
                }
            }
            val result: VoiceRecorder.Result? = try {
                rec.record(
                    autoStopMs = if (state.value.holdToTalk) 0 else s.autoStopMs,
                    // Capped at 5 minutes so the audio fits in a single Gemini request.
                    maxMs = s.maxRecordingSec.coerceIn(30, 300) * 1000L,
                    noSpeechTimeoutMs = 12_000,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Couldn't use the microphone: ${e.message ?: "unknown error"}")
                null
            } finally {
                levelJob.cancel()
                timerJob.cancel()
                if (recorder === rec) recorder = null
            }
            if (result == null) return@launch
            Haptics.stop(app)
            if (!result.hasSpeech) {
                fail("I didn't hear anything. Try again a little closer to the mic.")
                return@launch
            }
            lastAudio = result.wav
            runAudio(result.wav)
        }
    }

    fun finishRecording() {
        recorder?.stop()
    }

    fun cancel() {
        recorder?.cancel()
        workJob?.cancel()
        _events.tryEmit(SheetEvent.Close)
    }

    fun retry() {
        val audio = lastAudio
        if (audio != null && state.value.task != Task.POLISH) {
            workJob?.cancel()
            workJob = viewModelScope.launch { runAudio(audio) }
        } else if (state.value.task == Task.POLISH && state.value.sourceText.isNotBlank()) {
            polishText(state.value.sourceText)
        } else {
            beginRecording()
        }
    }

    private suspend fun runAudio(wav: ByteArray) {
        val st = state.value
        val voiceEdit = st.voiceEdit && st.hasTypedText
        val task = if (voiceEdit) Task.VOICE_EDIT else Task.DICTATE
        _state.update { it.copy(phase = Phase.PROCESSING, task = task, error = null) }
        try {
            val styleSpec = style()
            val promptCtx = promptContext(task)
            val cfg = settings
            // Base64 + JSON for a few MB of audio is real work: keep it off the main thread.
            val out = withContext(Dispatchers.Default) {
                Vani.engine.processAudio(
                    wav = wav,
                    task = task,
                    style = styleSpec,
                    ctx = promptCtx,
                    existing = if (voiceEdit) st.target?.editableSource else null,
                    s = cfg,
                )
            }
            if (out.output.isBlank()) {
                fail(
                    if (out.transcript.isBlank()) "I couldn't make out any words. Try again?"
                    else "The AI returned an empty result. Try again."
                )
                return
            }
            showResult(out.transcript, out.language, out.output, firstResult = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(Errors.describe(e))
        }
    }

    fun polishTyped() {
        val src = state.value.target?.editableSource.orEmpty()
        if (src.isBlank()) {
            fail("There's no typed text to polish. Put the cursor in a text box that has text, then try again.")
            return
        }
        recorder?.cancel()
        polishText(src)
    }

    fun polishText(text: String) {
        workJob?.cancel()
        if (text.isBlank()) {
            fail("There's no text to polish.")
            return
        }
        historyId = null
        _state.update { it.copy(phase = Phase.PROCESSING, task = Task.POLISH, sourceText = text, transcript = "", error = null) }
        workJob = viewModelScope.launch {
            try {
                val styleSpec = style()
                val promptCtx = promptContext(Task.POLISH)
                val cfg = settings
                val out = withContext(Dispatchers.Default) { Vani.engine.processText(Task.POLISH, text, null, styleSpec, promptCtx, cfg) }
                if (out.isBlank()) fail("The AI returned an empty result. Try again.")
                else showResult("", "", out, firstResult = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(Errors.describe(e))
            }
        }
    }

    /** Re-writes the same words in another style or language; no new recording needed. */
    fun regenerate(modeId: String? = null, outputLanguage: String? = null) {
        val st = state.value
        val next = st.copy(modeId = modeId ?: st.modeId, outputLanguage = outputLanguage ?: st.outputLanguage)
        if (st.phase != Phase.RESULT) {
            _state.value = next
            return
        }
        val input = if (st.task == Task.POLISH) st.sourceText else st.transcript
        if (input.isBlank()) {
            // No transcript to reuse (rare): redo from the audio if we have it.
            _state.value = next
            lastAudio?.let { audio ->
                workJob?.cancel()
                workJob = viewModelScope.launch { runAudio(audio) }
            }
            return
        }
        _state.value = next.copy(busy = true)
        workJob?.cancel()
        workJob = viewModelScope.launch {
            try {
                val styleSpec = style()
                val promptCtx = promptContext(st.task)
                val cfg = settings
                val out = withContext(Dispatchers.Default) {
                    Vani.engine.processText(
                        task = st.task,
                        input = input,
                        existing = if (st.task == Task.VOICE_EDIT) st.target?.editableSource else null,
                        style = styleSpec,
                        ctx = promptCtx,
                        s = cfg,
                    )
                }
                if (out.isBlank()) {
                    _state.update { it.copy(busy = false) }
                    Vani.toast("Got an empty result. Try again.", long = false)
                } else {
                    showResult(st.transcript, st.detectedLanguage, out, firstResult = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                Vani.toast(Errors.describe(e))
            }
        }
    }

    fun selectMode(id: String) {
        if (id == state.value.modeId) return
        regenerate(modeId = id)
    }

    fun selectLanguage(lang: String) {
        if (lang == state.value.outputLanguage) return
        regenerate(outputLanguage = lang)
    }

    fun toggleVoiceEdit() {
        _state.update { it.copy(voiceEdit = !it.voiceEdit) }
    }

    fun editOutput(text: String) {
        _state.update { it.copy(output = text) }
    }

    fun onClipboardText(text: String?) {
        if (text.isNullOrBlank()) fail("Your clipboard is empty. Copy some text first.") else polishText(text)
    }

    private fun showResult(transcript: String, language: String, rawOutput: String, firstResult: Boolean) {
        val s = settings
        val output = TextCleanup.applyReplacements(rawOutput, s.replacements)
        _state.update {
            it.copy(
                phase = Phase.RESULT,
                transcript = transcript,
                detectedLanguage = language.ifBlank { it.detectedLanguage },
                output = output,
                busy = false,
                error = null,
            )
        }
        Haptics.success(app)
        val st = state.value
        if (s.saveHistory) {
            val id = historyId ?: System.currentTimeMillis().also { historyId = it }
            Vani.history.upsert(
                HistoryItem(
                    id = id,
                    time = System.currentTimeMillis(),
                    app = st.appLabel.orEmpty(),
                    mode = Modes.find(st.modeId, s.customModes).name,
                    language = Languages.label(st.outputLanguage, st.detectedLanguage),
                    transcript = if (st.task == Task.POLISH) st.sourceText else transcript,
                    output = output,
                )
            )
        }
        if (firstResult && s.autoInsert && st.canInsert && !autoInsertDone) {
            autoInsertDone = true
            primary()
        }
    }

    /** The big button: Insert / Replace / Copy, depending on where Vani was opened from. */
    fun primary() {
        val st = state.value
        val text = st.output.trim()
        if (text.isEmpty()) return
        when {
            st.returnsResult && !st.readOnly -> _events.tryEmit(SheetEvent.ReturnResult(text))
            st.canInsert && VaniAccessibilityService.instance != null -> {
                val mode = if (st.task == Task.DICTATE) ReplaceMode.INSERT else ReplaceMode.SELECTION_OR_ALL
                VaniAccessibilityService.instance?.insert(text, mode) { ok ->
                    if (!ok) {
                        Vani.copy(text)
                        Vani.toast("Couldn't type it into the app, so it's copied. Long-press the text box and tap Paste.")
                    }
                }
                _events.tryEmit(SheetEvent.Close)
            }
            else -> {
                Vani.copy(text)
                val hint = if (VaniAccessibilityService.instance == null && st.kind == LaunchKind.DICTATE) {
                    "Copied. Turn on Vani in Accessibility to have it typed in for you."
                } else {
                    "Copied to clipboard."
                }
                if (Build.VERSION.SDK_INT < 33 || st.kind == LaunchKind.DICTATE) Vani.toast(hint, long = false)
                _events.tryEmit(SheetEvent.Close)
            }
        }
    }

    fun copy() {
        val text = state.value.output.trim()
        if (text.isEmpty()) return
        Vani.copy(text)
        if (Build.VERSION.SDK_INT < 33) Vani.toast("Copied", long = false)
    }

    fun share() {
        val text = state.value.output.trim()
        if (text.isNotEmpty()) _events.tryEmit(SheetEvent.Share(text))
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = Phase.ERROR, error = message, busy = false) }
        Haptics.error(app)
    }

    private fun style(): StyleSpec {
        val s = settings
        val st = state.value
        return StyleSpec(Modes.find(st.modeId, s.customModes), st.outputLanguage, s.script, s.englishVariant)
    }

    private fun promptContext(task: Task): PromptContext {
        val s = settings
        val st = state.value
        val t = st.target
        return PromptContext(
            appLabel = st.appLabel,
            fieldHint = t?.hint,
            userName = s.userName,
            spokenLanguages = s.spokenLanguages,
            vocabulary = s.vocabulary,
            textBeforeCursor = if (task == Task.DICTATE) t?.textBeforeCursor else null,
            screenText = if (s.screenContext) t?.screenText else null,
        )
    }

    override fun onCleared() {
        recorder?.cancel()
        super.onCleared()
    }
}
