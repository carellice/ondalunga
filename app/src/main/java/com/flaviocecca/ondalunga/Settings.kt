package com.flaviocecca.ondalunga

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.flaviocecca.ondalunga.game.RuleSet

data class Settings(
    /** Jingles and button sounds, 0..1. */
    val effectsVolume: Float = 0.8f,
    /** Wheel, needle and sliding screen, 0..1. */
    val deviceVolume: Float = 0.7f,
    val haptics: Boolean = true,
    val rules: RuleSet = RuleSet(),
)

/** Settings as observable state, persisted across launches. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var value by mutableStateOf(load())
        private set

    fun update(transform: (Settings) -> Settings) {
        value = transform(value)
        save(value)
    }

    private fun load(): Settings {
        val defaults = Settings()
        return Settings(
            effectsVolume = prefs.getFloat("effectsVolume", defaults.effectsVolume),
            deviceVolume = prefs.getFloat("deviceVolume", defaults.deviceVolume),
            haptics = prefs.getBoolean("haptics", defaults.haptics),
            rules = RuleSet(
                targetScore = prefs.getInt("targetScore", defaults.rules.targetScore),
                sideBet = prefs.getBoolean("sideBet", defaults.rules.sideBet),
                catchUp = prefs.getBoolean("catchUp", defaults.rules.catchUp),
                cardSwap = prefs.getBoolean("cardSwap", defaults.rules.cardSwap),
                coopCards = prefs.getInt("coopCards", defaults.rules.coopCards),
            ),
        )
    }

    private fun save(s: Settings) = prefs.edit {
        putFloat("effectsVolume", s.effectsVolume)
        putFloat("deviceVolume", s.deviceVolume)
        putBoolean("haptics", s.haptics)
        putInt("targetScore", s.rules.targetScore)
        putBoolean("sideBet", s.rules.sideBet)
        putBoolean("catchUp", s.rules.catchUp)
        putBoolean("cardSwap", s.rules.cardSwap)
        putInt("coopCards", s.rules.coopCards)
    }
}
