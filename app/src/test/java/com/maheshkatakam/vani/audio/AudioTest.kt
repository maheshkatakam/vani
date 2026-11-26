package com.maheshkatakam.vani.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioTest {
    @Test
    fun wavHeaderIsValid() {
        val pcm = ByteArray(3200) { 1 }
        val wav = Wav.encode(pcm, 0, pcm.size, 16000)
        assertEquals(44 + 3200, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, bb.getInt(24))
        assertEquals(3200, bb.getInt(40))
    }

    @Test
    fun speechRangeTrimsSilenceWithPadding() {
        val r = Wav.speechRange(totalBytes = 640 * 200, firstSpeechFrame = 50, lastSpeechFrame = 100, bytesPerFrame = 640, padBeforeFrames = 15, padAfterFrames = 25)
        assertEquals(35 * 640, r.first)
        assertEquals(126 * 640 - 1, r.last)
        val none = Wav.speechRange(1000, -1, -1, 640, 15, 25)
        assertEquals(0, none.first)
        assertEquals(999, none.last)
    }

    @Test
    fun vadDetectsSpeechAndPause() {
        val vad = VoiceActivityDetector(frameMs = 20)
        repeat(50) { vad.process(60.0) } // quiet room
        assertFalse(vad.speechStarted)
        repeat(40) { vad.process(2500.0) } // talking
        assertTrue(vad.speechStarted)
        repeat(100) { vad.process(60.0) } // 2 s pause
        assertTrue(vad.msSinceLastSpeech() >= 1900)
        assertTrue(vad.hasMeaningfulAudio())
    }

    @Test
    fun silenceIsNotSpeech() {
        val vad = VoiceActivityDetector()
        repeat(300) { vad.process(40.0 + (it % 5)) }
        assertFalse(vad.speechStarted)
        assertFalse(vad.hasMeaningfulAudio())
    }

    @Test
    fun levelMapping() {
        assertEquals(0f, VoiceActivityDetector.level(0.0), 0.0001f)
        assertTrue(VoiceActivityDetector.level(32767.0) == 1f)
        assertTrue(VoiceActivityDetector.level(3000.0) in 0.5f..1f)
    }
}
