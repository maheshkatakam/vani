package com.maheshkatakam.vani.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.core.KeyAction
import com.maheshkatakam.vani.core.KeyMapping
import com.maheshkatakam.vani.dictation.DictationActivity
import com.maheshkatakam.vani.dictation.DictationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional helper that makes Vani feel built in:
 *  - types the result straight into the text box you were writing in;
 *  - lets Vani see what's already typed (for "polish what I typed" and voice edits);
 *  - powers the Key Mapper (start Vani by holding / double-pressing a button).
 * It reads nothing until you start a dictation, and keeps nothing.
 */
class VaniAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: VaniAccessibilityService? = null
            private set

        private val BLOCKED_CAPTURE_KEYS = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ENDCALL,
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var keyEngine: KeyGestureEngine
    private var captureCallback: ((Int) -> Unit)? = null

    private var targetPkg: String? = null
    private var targetNode: AccessibilityNodeInfo? = null
    private var pending: PendingInsert? = null

    private class PendingInsert(
        val text: String,
        val mode: ReplaceMode,
        val startedAt: Long,
        val onDone: (Boolean) -> Unit,
    )

    private val insertRunnable = Runnable { tryInsert() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        keyEngine = KeyGestureEngine(
            scheduler = object : KeyGestureEngine.Scheduler {
                override fun schedule(token: Any, delayMs: Long, action: () -> Unit) {
                    handler.postAtTime({ action() }, token, SystemClock.uptimeMillis() + delayMs)
                }

                override fun cancel(token: Any) {
                    handler.removeCallbacksAndMessages(token)
                }
            },
            mappings = { Vani.settings.current.keyMappings },
            longPressMs = { Vani.settings.current.longPressMs.toLong() },
            doublePressMs = { Vani.settings.current.doublePressMs.toLong() },
            onTrigger = ::onKeyTrigger,
            passthrough = ::passthroughKey,
        )
        scope.launch {
            Vani.settings.state.map { it.keyMappings.isNotEmpty() }.distinctUntilChanged().collect { refreshKeyFilter() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Key mapper

    /** Only ask Android for key events when there is a mapping (or we're recording a new one). */
    private fun refreshKeyFilter() {
        val info = serviceInfo ?: return
        val want = Vani.settings.current.keyMappings.isNotEmpty() || captureCallback != null
        val flags = if (want) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        if (flags != info.flags) {
            info.flags = flags
            serviceInfo = info
        }
    }

    /** The next button pressed is reported to [onKey] instead of being handled (for "Add mapping"). */
    fun captureNextKey(onKey: (Int) -> Unit) {
        captureCallback = onKey
        refreshKeyFilter()
    }

    fun cancelCapture() {
        captureCallback = null
        refreshKeyFilter()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val capture = captureCallback
        if (capture != null) {
            if (event.keyCode in BLOCKED_CAPTURE_KEYS) return false
            if (event.action == KeyEvent.ACTION_UP) {
                captureCallback = null
                refreshKeyFilter()
                capture(event.keyCode)
            }
            return true
        }
        if (isInCall()) return false
        return keyEngine.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount)
    }

    private fun isInCall(): Boolean {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        return am.mode == AudioManager.MODE_IN_CALL ||
            am.mode == AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == AudioManager.MODE_RINGTONE
    }

    private fun onKeyTrigger(mapping: KeyMapping, phase: KeyGestureEngine.Phase) {
        when (phase) {
            KeyGestureEngine.Phase.START -> {
                val hold = mapping.holdToTalk && mapping.gesture == com.maheshkatakam.vani.core.Gesture.LONG &&
                    mapping.action == KeyAction.DICTATE
                if (hold) DictationBus.holdReleased.value = false
                val action = if (mapping.action == KeyAction.POLISH_TYPED) {
                    DictationActivity.ACTION_POLISH_TYPED
                } else {
                    DictationActivity.ACTION_DICTATE
                }
                val intent = DictationActivity.intent(this, action, mapping.modeId, hold)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(intent) }
            }
            KeyGestureEngine.Phase.RELEASE -> DictationBus.holdReleased.value = true
        }
    }

    /** Replays a plain press of a key we intercepted, so volume buttons keep working. */
    private fun passthroughKey(keyCode: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> am.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
            )
            KeyEvent.KEYCODE_VOLUME_DOWN -> am.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
            )
            KeyEvent.KEYCODE_VOLUME_MUTE -> am.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
            )
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            -> {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- Reading the target field

    /**
     * Called when the dictation sheet opens, before its window appears: finds the app on screen
     * and the text box that has the cursor.
     */
    fun captureTarget(includeScreen: Boolean): TargetInfo? {
        val root = topAppRoot() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        targetPkg = pkg
        targetNode = field
        val screen = if (includeScreen) runCatching { collectScreenText(root, field) }.getOrNull() else null
        return TargetInfo(
            pkg = pkg,
            appLabel = Vani.appLabel(pkg),
            hasField = field != null,
            isPassword = field?.isPassword == true,
            text = field?.let { currentText(it) }.orEmpty(),
            selStart = field?.textSelectionStart ?: -1,
            selEnd = field?.textSelectionEnd ?: -1,
            hint = field?.let { hintOf(it) },
            screenText = screen,
        )
    }

    private fun topAppRoot(): AccessibilityNodeInfo? {
        val appWindows = runCatching { windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedWith(
                compareByDescending<AccessibilityWindowInfo> { it.isFocused }
                    .thenByDescending { it.isActive }
                    .thenByDescending { it.layer }
            )
        val top = appWindows.firstOrNull()
        if (top != null) {
            val root = top.root ?: return null
            // If Vani itself is in front (e.g. "Try it" from the app), there is no outside target.
            return if (root.packageName?.toString() == packageName) null else root
        }
        return rootInActiveWindow?.takeIf { it.packageName?.toString() != packageName }
    }

    private fun currentText(node: AccessibilityNodeInfo): String {
        if (node.isShowingHintText) return ""
        val text = node.text?.toString().orEmpty()
        val hint = node.hintText?.toString()
        return if (hint != null && text == hint) "" else text
    }

    private fun hintOf(node: AccessibilityNodeInfo): String? =
        node.hintText?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }

    private fun collectScreenText(root: AccessibilityNodeInfo, exclude: AccessibilityNodeInfo?): String {
        val out = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            if (node.isPassword) continue
            if (exclude != null && node == exclude) continue
            if (node.isVisibleToUser) {
                val t = (node.text ?: node.contentDescription)?.toString()?.trim()
                if (!t.isNullOrEmpty() && t.length > 1 && out.lastOrNull() != t) out += t
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
        }
        return out.joinToString("\n").takeLast(1500)
    }

    // ---------------------------------------------------------------- Typing the result

    /**
     * Types [text] into the captured text box once its app is back in front (the sheet has to close
     * first). [onDone] gets false if that didn't work, so the caller can fall back to the clipboard.
     */
    fun insert(text: String, mode: ReplaceMode, onDone: (Boolean) -> Unit) {
        pending = PendingInsert(text, mode, SystemClock.uptimeMillis(), onDone)
        handler.removeCallbacks(insertRunnable)
        handler.postDelayed(insertRunnable, 180)
    }

    private fun tryInsert() {
        val p = pending ?: return
        val pkg = targetPkg
        if (pkg == null) {
            pending = null
            p.onDone(false)
            return
        }
        val active = rootInActiveWindow
        if (active == null || active.packageName?.toString() != pkg) {
            if (SystemClock.uptimeMillis() - p.startedAt < 2000) {
                handler.postDelayed(insertRunnable, 120)
            } else {
                // The user moved to another app; never type into the wrong place.
                pending = null
                p.onDone(false)
            }
            return
        }
        pending = null
        val node = active.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: targetNode?.takeIf { it.refresh() && it.isEditable }
        if (node == null || node.isPassword) {
            p.onDone(false)
            return
        }
        performInsert(node, p.text, p.mode, p.onDone)
    }

    private fun performInsert(node: AccessibilityNodeInfo, text: String, mode: ReplaceMode, onDone: (Boolean) -> Unit) {
        val current = currentText(node)
        val plan = TextSplice.plan(current, node.textSelectionStart, node.textSelectionEnd, text, mode)
        val canPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        if (!plan.replacesAll && canPaste) {
            // Paste respects the cursor, rich text and undo in most apps.
            Vani.copy(plan.segment)
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                handler.postDelayed({
                    val changed = node.refresh() && currentText(node) != current
                    onDone(changed || setText(node, plan))
                }, 300)
                return
            }
        }
        onDone(setText(node, plan))
    }

    private fun setText(node: AccessibilityNodeInfo, plan: TextSplice.Plan): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, plan.newText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            val sel = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        }
        return ok
    }
}
