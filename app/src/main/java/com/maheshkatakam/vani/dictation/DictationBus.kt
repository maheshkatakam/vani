package com.maheshkatakam.vani.dictation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lets the key mapper talk to the open dictation sheet (e.g. "button released, stop listening"). */
object DictationBus {
    enum class Command { FINISH, CANCEL }

    /** For hold-to-talk: false while the mapped button is held down. */
    val holdReleased = MutableStateFlow(true)

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 4)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun send(command: Command) {
        _commands.tryEmit(command)
    }
}
