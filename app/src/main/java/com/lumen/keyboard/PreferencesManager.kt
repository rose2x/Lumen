package com.lumen.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager

enum class OneHandedSide { OFF, LEFT, RIGHT }

/**
 * Reads/writes the same SharedPreferences file used by SettingsActivity's
 * PreferenceFragmentCompat, so changes made in Settings are reflected
 * immediately the next time the keyboard is shown.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    // --- Feedback ---
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    var keyPopupEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUP, true)
        set(value) = prefs.edit().putBoolean(KEY_POPUP, value).apply()

    /** One of SoundFeedback.STYLE_* values. */
    var soundStyle: String
        get() = prefs.getString(KEY_SOUND_STYLE, SoundFeedback.STYLE_STANDARD) ?: SoundFeedback.STYLE_STANDARD
        set(value) = prefs.edit().putString(KEY_SOUND_STYLE, value).apply()

    // --- Typing ---
    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOCORRECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOCORRECT, value).apply()

    var swipeTypingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE, value).apply()

    var numberRowEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW, false)
        set(value) = prefs.edit().putBoolean(KEY_NUMBER_ROW, value).apply()

    // --- Appearance ---
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Stored as a "#RRGGBB" hex string so it works cleanly with a ListPreference. */
    var accentColorHex: String
        get() = prefs.getString(KEY_ACCENT, DEFAULT_ACCENT_HEX) ?: DEFAULT_ACCENT_HEX
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    val accentColor: Int
        get() = try { Color.parseColor(accentColorHex) } catch (e: IllegalArgumentException) { DEFAULT_ACCENT }

    /** 80-130, percentage of the default keyboard height. */
    var keyboardHeightPercent: Int
        get() = prefs.getInt(KEY_HEIGHT, 100)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    var oneHandedSide: OneHandedSide
        get() = try {
            OneHandedSide.valueOf(prefs.getString(KEY_ONE_HANDED, "OFF") ?: "OFF")
        } catch (e: IllegalArgumentException) {
            OneHandedSide.OFF
        }
        set(value) = prefs.edit().putString(KEY_ONE_HANDED, value.name).apply()

    var splitKeyboardEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLIT, false)
        set(value) = prefs.edit().putBoolean(KEY_SPLIT, value).apply()

    /** One of [FontManager.SYSTEM_FONT_ID], a built-in id, or [FontManager.CUSTOM_FONT_ID]. */
    var fontId: String
        get() = prefs.getString(KEY_FONT, FontManager.SYSTEM_FONT_ID) ?: FontManager.SYSTEM_FONT_ID
        set(value) = prefs.edit().putString(KEY_FONT, value).apply()

    // --- Clipboard ---
    var clipboardHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_ENABLED, value).apply()

    // --- GIFs ---
    /** Optional; the user supplies their own free Tenor API key. Empty = GIF search disabled. */
    var tenorApiKey: String
        get() = prefs.getString(KEY_TENOR_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TENOR_KEY, value).apply()

    companion object {
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_POPUP = "key_popup_enabled"
        private const val KEY_SOUND_STYLE = "sound_style"
        private const val KEY_SUGGESTIONS = "suggestions_enabled"
        private const val KEY_AUTOCORRECT = "autocorrect_enabled"
        private const val KEY_SWIPE = "swipe_typing_enabled"
        private const val KEY_NUMBER_ROW = "number_row_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent_color_hex"
        private const val KEY_HEIGHT = "keyboard_height_percent"
        private const val KEY_ONE_HANDED = "one_handed_side"
        private const val KEY_SPLIT = "split_keyboard_enabled"
        private const val KEY_FONT = "font_id"
        private const val KEY_CLIPBOARD_ENABLED = "clipboard_history_enabled"
        private const val KEY_TENOR_KEY = "tenor_api_key"

        const val DEFAULT_ACCENT_HEX = "#4C6FFF"
        val DEFAULT_ACCENT: Int = Color.parseColor(DEFAULT_ACCENT_HEX)
    }
}
