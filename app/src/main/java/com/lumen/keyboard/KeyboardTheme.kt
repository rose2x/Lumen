package com.lumen.keyboard

data class KeyboardTheme(
    val backgroundColor: Int,
    val keyColor: Int,
    val keyPressedColor: Int,
    val specialKeyColor: Int,
    val textColor: Int,
    val accentColor: Int,
    val popupColor: Int
)

enum class ThemeMode { LIGHT, DARK, AMOLED, AUTO }

object Themes {
    fun forMode(mode: ThemeMode, systemIsDark: Boolean, accent: Int): KeyboardTheme {
        val resolved = if (mode == ThemeMode.AUTO) {
            if (systemIsDark) ThemeMode.DARK else ThemeMode.LIGHT
        } else mode

        return when (resolved) {
            ThemeMode.LIGHT -> KeyboardTheme(
                backgroundColor = 0xFFF5F6FA.toInt(),
                keyColor = 0xFFFFFFFF.toInt(),
                keyPressedColor = 0xFFE4E7F5.toInt(),
                specialKeyColor = 0xFFE0E2EC.toInt(),
                textColor = 0xFF1C1D2B.toInt(),
                accentColor = accent,
                popupColor = 0xFFFFFFFF.toInt()
            )
            ThemeMode.DARK -> KeyboardTheme(
                backgroundColor = 0xFF1B1C27.toInt(),
                keyColor = 0xFF272935.toInt(),
                keyPressedColor = 0xFF373A4C.toInt(),
                specialKeyColor = 0xFF2E303E.toInt(),
                textColor = 0xFFF2F2F7.toInt(),
                accentColor = accent,
                popupColor = 0xFF2E303E.toInt()
            )
            ThemeMode.AMOLED -> KeyboardTheme(
                backgroundColor = 0xFF000000.toInt(),
                keyColor = 0xFF141414.toInt(),
                keyPressedColor = 0xFF262626.toInt(),
                specialKeyColor = 0xFF1A1A1A.toInt(),
                textColor = 0xFFF2F2F2.toInt(),
                accentColor = accent,
                popupColor = 0xFF1A1A1A.toInt()
            )
            ThemeMode.AUTO -> forMode(ThemeMode.LIGHT, systemIsDark, accent)
        }
    }
}
