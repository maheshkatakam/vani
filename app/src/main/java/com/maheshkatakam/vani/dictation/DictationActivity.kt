package com.maheshkatakam.vani.dictation

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.maheshkatakam.vani.Vani
import com.maheshkatakam.vani.access.VaniAccessibilityService
import com.maheshkatakam.vani.system.SystemSetup
import com.maheshkatakam.vani.ui.MainActivity
import com.maheshkatakam.vani.ui.theme.VaniTheme
import kotlinx.coroutines.launch

/** Shared bottom-sheet host for dictation and for "Polish with Vani" from the text-selection menu. */
abstract class SheetActivity : ComponentActivity() {
    protected val vm: DictationViewModel by viewModels()

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onMicPermission(it)
    }

    abstract fun buildRequest(intent: Intent): LaunchRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!vm.started) vm.start(buildRequest(intent), SystemSetup.hasMic(this))
        setContent {
            VaniTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val modes = Vani.settings.current.allModes
                DictationSheet(
                    state = state,
                    modes = modes,
                    actions = SheetActions(
                        onDone = vm::finishRecording,
                        onCancel = vm::cancel,
                        onPrimary = vm::primary,
                        onCopy = vm::copy,
                        onShare = vm::share,
                        onAgain = vm::beginRecording,
                        onRetry = vm::retry,
                        onOpenSettings = {
                            val ctx = this@SheetActivity
                            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            ctx.finish()
                        },
                        onAllowMic = vm::requestMic,
                        onSelectMode = vm::selectMode,
                        onSelectLanguage = vm::selectLanguage,
                        onPolishTyped = vm::polishTyped,
                        onToggleVoiceEdit = vm::toggleVoiceEdit,
                        onEdit = vm::editOutput,
                    ),
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vm.events.collect { handle(it) }
            }
        }
    }

    private fun handle(event: SheetEvent) {
        when (event) {
            SheetEvent.Close -> finish()
            SheetEvent.RequestMic -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            is SheetEvent.Share -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, event.text)
                startActivity(Intent.createChooser(send, null))
            }
            is SheetEvent.ReturnResult -> onReturnResult(event.text)
        }
    }

    protected open fun onReturnResult(text: String) {
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Volume keys finish a dictation, so you can use Vani without looking at the screen.
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volume && vm.state.value.phase == Phase.RECORDING) {
            vm.finishRecording()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        // Screen turned off or user left mid-dictation: stop listening and process what we have.
        if (!isChangingConfigurations && vm.state.value.phase == Phase.RECORDING) vm.finishRecording()
    }
}

/**
 * Opens on "hold power" (as the digital assistant), from the Quick Settings tile, launcher
 * shortcuts and the Key Mapper.
 */
class DictationActivity : SheetActivity() {

    companion object {
        const val ACTION_DICTATE = "com.maheshkatakam.vani.action.DICTATE"
        const val ACTION_POLISH_TYPED = "com.maheshkatakam.vani.action.POLISH_TYPED"
        const val ACTION_POLISH_CLIPBOARD = "com.maheshkatakam.vani.action.POLISH_CLIPBOARD"
        const val EXTRA_MODE = "com.maheshkatakam.vani.extra.MODE"
        const val EXTRA_HOLD = "com.maheshkatakam.vani.extra.HOLD_TO_TALK"
        const val EXTRA_NO_TARGET = "com.maheshkatakam.vani.extra.NO_TARGET"

        fun intent(ctx: Context, action: String = ACTION_DICTATE, modeId: String? = null, hold: Boolean = false): Intent =
            Intent(ctx, DictationActivity::class.java)
                .setAction(action)
                .putExtra(EXTRA_MODE, modeId)
                .putExtra(EXTRA_HOLD, hold)
    }

    private var clipboardPending = false

    override fun buildRequest(intent: Intent): LaunchRequest {
        val s = Vani.settings.current
        val target = if (intent.getBooleanExtra(EXTRA_NO_TARGET, false)) {
            null
        } else {
            runCatching { VaniAccessibilityService.instance?.captureTarget(includeScreen = s.screenContext) }.getOrNull()
        }
        val kind = when (intent.action) {
            ACTION_POLISH_TYPED -> LaunchKind.POLISH_TYPED
            ACTION_POLISH_CLIPBOARD -> LaunchKind.POLISH_CLIPBOARD
            else -> LaunchKind.DICTATE
        }
        clipboardPending = kind == LaunchKind.POLISH_CLIPBOARD
        return LaunchRequest(
            kind = kind,
            modeId = intent.getStringExtra(EXTRA_MODE),
            holdToTalk = intent.getBooleanExtra(EXTRA_HOLD, false),
            target = target,
            sourcePackage = intent.getStringExtra(Intent.EXTRA_ASSIST_PACKAGE),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.onRetrigger(hold = intent.getBooleanExtra(EXTRA_HOLD, false))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android only lets the app in front read the clipboard, and only once its window has focus.
        if (hasFocus && clipboardPending) {
            clipboardPending = false
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            vm.onClipboardText(text)
        }
    }
}

/** "Polish with Vani" in the text-selection menu of any app. Works without the accessibility helper. */
class PolishTextActivity : SheetActivity() {
    private var readOnly = false

    override fun buildRequest(intent: Intent): LaunchRequest {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        return LaunchRequest(
            kind = LaunchKind.POLISH_TEXT,
            text = text,
            readOnly = readOnly,
            returnsResult = true,
            sourcePackage = callingPackage,
        )
    }

    override fun onReturnResult(text: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}
