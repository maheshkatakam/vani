package com.maheshkatakam.vani.access

import com.maheshkatakam.vani.core.Gesture
import com.maheshkatakam.vani.core.KeyMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VOL_DOWN = 25
private const val VOL_UP = 24

class KeyGestureEngineTest {
    private class FakeScheduler : KeyGestureEngine.Scheduler {
        var now = 0L
        private val tasks = mutableListOf<Triple<Any, Long, () -> Unit>>()
        override fun schedule(token: Any, delayMs: Long, action: () -> Unit) {
            tasks += Triple(token, now + delayMs, action)
        }
        override fun cancel(token: Any) {
            tasks.removeAll { it.first === token }
        }
        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { it.second <= target }.minByOrNull { it.second } ?: break
                tasks.remove(next)
                now = next.second
                next.third()
            }
            now = target
        }
    }

    private val scheduler = FakeScheduler()
    private val triggers = mutableListOf<String>()
    private val passed = mutableListOf<Int>()
    private var mappings = listOf<KeyMapping>()

    private val engine = KeyGestureEngine(
        scheduler = scheduler,
        mappings = { mappings },
        longPressMs = { 450 },
        doublePressMs = { 300 },
        onTrigger = { m, phase -> triggers += "${m.gesture}:${phase}" },
        passthrough = { passed += it },
    )

    private fun press(code: Int, holdMs: Long) {
        assertTrue(engine.onKey(code, true, 0))
        scheduler.advance(holdMs)
        engine.onKey(code, false, 0)
    }

    @Test
    fun unmappedKeysAreNotTouched() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG))
        assertFalse(engine.onKey(VOL_UP, true, 0))
        assertFalse(engine.onKey(VOL_UP, false, 0))
    }

    @Test
    fun holdTriggersAndReleaseStops() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG, holdToTalk = true))
        press(VOL_DOWN, 2000)
        assertEquals(listOf("LONG:START", "LONG:RELEASE"), triggers)
        assertTrue(passed.isEmpty())
    }

    @Test
    fun shortPressStillChangesVolume() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG))
        press(VOL_DOWN, 100)
        assertTrue(triggers.isEmpty())
        assertEquals(listOf(VOL_DOWN), passed)
    }

    @Test
    fun repeatsWhileHeldAreSwallowed() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG))
        assertTrue(engine.onKey(VOL_DOWN, true, 0))
        assertTrue(engine.onKey(VOL_DOWN, true, 3))
        scheduler.advance(600)
        engine.onKey(VOL_DOWN, false, 0)
        assertEquals(listOf("LONG:START", "LONG:RELEASE"), triggers)
    }

    @Test
    fun doublePressTriggers() {
        mappings = listOf(KeyMapping("d", VOL_UP, "Volume up", Gesture.DOUBLE))
        press(VOL_UP, 60)
        scheduler.advance(120)
        press(VOL_UP, 60)
        scheduler.advance(500)
        assertEquals(listOf("DOUBLE:START"), triggers)
        assertTrue(passed.isEmpty())
    }

    @Test
    fun singlePressWithDoubleMappingPassesThroughAfterWindow() {
        mappings = listOf(KeyMapping("d", VOL_UP, "Volume up", Gesture.DOUBLE))
        press(VOL_UP, 60)
        assertTrue(passed.isEmpty())
        scheduler.advance(400)
        assertEquals(listOf(VOL_UP), passed)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun strayUpIsLetThrough() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG))
        assertFalse(engine.onKey(VOL_DOWN, false, 0))
    }

    @Test
    fun holdWithoutHoldToTalkHasNoRelease() {
        mappings = listOf(KeyMapping("a", VOL_DOWN, "Volume down", Gesture.LONG, holdToTalk = false))
        press(VOL_DOWN, 1000)
        assertEquals(listOf("LONG:START"), triggers)
    }
}
