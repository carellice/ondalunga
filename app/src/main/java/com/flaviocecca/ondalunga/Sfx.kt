package com.flaviocecca.ondalunga

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf

/** [device] sounds come from the gadget itself and follow their own volume slider. */
enum class Sound(@RawRes val res: Int, val device: Boolean = false) {
    TAP(R.raw.tap),
    TICK(R.raw.tick, device = true),
    SLIDE(R.raw.slide, device = true),
    GOOD(R.raw.good),
    BULLSEYE(R.raw.bullseye),
    MISS(R.raw.miss),
    WIN(R.raw.win),
}

class Sfx(context: Context, private val settings: () -> Settings) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = Sound.entries.associateWith { pool.load(context, it.res, 1) }
    private var lastTick = 0L

    val haptics: Boolean get() = settings().haptics

    fun play(sound: Sound, gain: Float = 1f) {
        val s = settings()
        val volume = (if (sound.device) s.deviceVolume else s.effectsVolume) * gain
        if (volume > 0f) pool.play(ids.getValue(sound), volume, volume, 1, 0, 1f)
    }

    /** Ratchet click, rate-limited so a fast spin purrs instead of piling up. */
    fun tick(gain: Float = 1f) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 30) return
        lastTick = now
        play(Sound.TICK, gain)
    }

    fun release() = pool.release()
}

val LocalSfx = staticCompositionLocalOf<Sfx?> { null }
