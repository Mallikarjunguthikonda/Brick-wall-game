package com.example.ui.game

import android.media.AudioManager
import android.media.ToneGenerator

object SoundPlayer {
    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playBrickHit(hp: Int) {
        val tone = when (hp) {
            1 -> ToneGenerator.TONE_PROP_BEEP
            2 -> ToneGenerator.TONE_CDMA_PIP
            else -> ToneGenerator.TONE_CDMA_HIGH_L
        }
        playTone(tone, 40)
    }

    fun playPaddleHit() {
        playTone(ToneGenerator.TONE_PROP_ACK, 45)
    }

    fun playPowerUp() {
        playTone(ToneGenerator.TONE_CDMA_CONFIRM, 120)
    }

    fun playLoseLife() {
        playTone(ToneGenerator.TONE_SUP_ERROR, 220)
    }

    fun playLevelComplete() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 350)
    }

    private fun playTone(toneType: Int, duration: Int) {
        try {
            toneGen?.startTone(toneType, duration)
        } catch (e: Exception) {
            // Fallback silently if audio is busy
        }
    }
}
