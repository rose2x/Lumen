package com.lumen.keyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Reads/writes the same SharedPreferences file used by SettingsActivity's
 * PreferenceFragmentCompat, so changes made in Settings are reflected
 * immediately the next time the keyboard is shown.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    var keyPopupEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUP, true)
        set(value) = prefs.edit().putBoolean(KEY_POPUP, value).apply()

    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    companion object {
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_POPUP = "key_popup_enabled"
        private const val KEY_SUGGESTIONS = "suggestions_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
        const val DEFAULT_ACCENT: Int = 0xFF4C6FFF.toInt()
    }
}
