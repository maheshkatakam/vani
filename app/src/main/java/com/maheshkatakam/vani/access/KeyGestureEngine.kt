package com.maheshkatakam.vani.access

import com.maheshkatakam.vani.core.Gesture
import com.maheshkatakam.vani.core.KeyMapping

/**
 * Key Mapper core: turns raw key down/up events into Hold / Double-press / Single-press triggers.
 * Keys with a mapping are fully taken over; a plain press of a volume key is replayed through
 * [passthrough] so volume still works. Pure logic with an injectable scheduler (unit tested).
 */
class KeyGestureEngine(
    private val scheduler: Scheduler,
    private val mappings: () -> List<KeyMapping>,
    private val longPressMs: () -> Long,
    private val doublePressMs: () -> Long,
    private val onTrigger: (KeyMapping, Phase) -> Unit,
    private val passthrough: (Int) -> Unit,
) {
    interface Scheduler {
        fun schedule(token: Any, delayMs: Long, action: () -> Unit)
        fun cancel(token: Any)
    }

    enum class Phase { START, RELEASE }

    private class KeyState {
        val longToken = Any()
        val singleToken = Any()
        var isDown = false
        var longFired: KeyMapping? = null
        var pendingSingle = false
    }

    private val states = HashMap<Int, KeyState>()

    /** Returns true if the event was consumed. */
    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean {
        val maps = mappings().filter { it.keyCode == keyCode }
        if (maps.isEmpty()) return false
        val st = states.getOrPut(keyCode) { KeyState() }
        return if (isDown) onDown(st, maps, repeatCount) else onUp(keyCode, st, maps)
    }

    private fun onDown(st: KeyState, maps: List<KeyMapping>, repeatCount: Int): Boolean {
        if (repeatCount > 0 || st.isDown) return true
        st.isDown = true
        st.longFired = null
        val long = maps.firstOrNull { it.gesture == Gesture.LONG }
        if (long != null) {
            scheduler.schedule(st.longToken, longPressMs()) {
                if (st.isDown) {
                    st.longFired = long
                    if (st.pendingSingle) {
                        scheduler.cancel(st.singleToken)
                        st.pendingSingle = false
                    }
                    onTrigger(long, Phase.START)
                }
            }
        }
        return true
    }

    private fun onUp(keyCode: Int, st: KeyState, maps: List<KeyMapping>): Boolean {
        // The matching down went to the system (mapping added mid-press): let the up through too.
        if (!st.isDown) return false
        st.isDown = false
        scheduler.cancel(st.longToken)

        val fired = st.longFired
        if (fired != null) {
            st.longFired = null
            if (fired.holdToTalk) onTrigger(fired, Phase.RELEASE)
            return true
        }

        val double = maps.firstOrNull { it.gesture == Gesture.DOUBLE }
        val single = maps.firstOrNull { it.gesture == Gesture.SINGLE }
        if (double != null) {
            if (st.pendingSingle) {
                scheduler.cancel(st.singleToken)
                st.pendingSingle = false
                onTrigger(double, Phase.START)
                return true
            }
            st.pendingSingle = true
            scheduler.schedule(st.singleToken, doublePressMs()) {
                st.pendingSingle = false
                if (single != null) onTrigger(single, Phase.START) else passthrough(keyCode)
            }
            return true
        }
        if (single != null) onTrigger(single, Phase.START) else passthrough(keyCode)
        return true
    }
}
