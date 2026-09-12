package com.vironix.app

import android.content.Context
import android.content.SharedPreferences

/**
 * TerminalPreferences
 *
 * Stores user-adjustable terminal settings (font size, color scheme) using
 * Android's SharedPreferences — a simple built-in key/value store backed
 * by a small XML file in the app's private storage. This is the standard,
 * lightweight way to persist small settings on Android (no database needed).
 */
class TerminalPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vironix_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_CURSOR_STYLE = "cursor_style"
        private const val KEY_BELL_ENABLED = "bell_enabled"

        const val DEFAULT_FONT_SIZE = 14f
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 28f
    }

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)).apply()

    var colorScheme: ColorScheme
        get() = ColorScheme.byId(prefs.getString(KEY_COLOR_SCHEME, ColorScheme.CLASSIC_GREEN.id) ?: ColorScheme.CLASSIC_GREEN.id)
        set(value) = prefs.edit().putString(KEY_COLOR_SCHEME, value.id).apply()

    var cursorStyle: CursorStyle
        get() = CursorStyle.valueOf(prefs.getString(KEY_CURSOR_STYLE, CursorStyle.BLOCK.name) ?: CursorStyle.BLOCK.name)
        set(value) = prefs.edit().putString(KEY_CURSOR_STYLE, value.name).apply()

    var bellEnabled: Boolean
        get() = prefs.getBoolean(KEY_BELL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BELL_ENABLED, value).apply()
}

enum class CursorStyle { BLOCK, UNDERLINE, BAR }

/**
 * ColorScheme
 *
 * A named set of default foreground/background colors for the terminal —
 * this only changes the DEFAULT colors (what text looks like before any
 * app sets its own ANSI colors); apps that explicitly request colors via
 * escape codes (like `ls --color`) are unaffected, exactly like real
 * terminal color schemes work.
 */
data class ColorScheme(
    val id: String,
    val displayName: String,
    val background: Int,
    val foreground: Int
) {
    companion object {
        val CLASSIC_GREEN = ColorScheme("classic_green", "Classic Green", 0xFF0D0D0D.toInt(), 0xFF00FF41.toInt())
        val SOLARIZED_DARK = ColorScheme("solarized_dark", "Solarized Dark", 0xFF002B36.toInt(), 0xFF839496.toInt())
        val MONOKAI = ColorScheme("monokai", "Monokai", 0xFF272822.toInt(), 0xFFF8F8F2.toInt())
        val DRACULA = ColorScheme("dracula", "Dracula", 0xFF282A36.toInt(), 0xFFF8F8F2.toInt())
        val LIGHT = ColorScheme("light", "Light", 0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt())

        val all = listOf(CLASSIC_GREEN, SOLARIZED_DARK, MONOKAI, DRACULA, LIGHT)

        fun byId(id: String): ColorScheme = all.firstOrNull { it.id == id } ?: CLASSIC_GREEN
    }
}
