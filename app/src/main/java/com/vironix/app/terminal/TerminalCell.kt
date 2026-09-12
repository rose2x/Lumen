package com.vironix.app.terminal

/**
 * TerminalCell
 *
 * A real terminal isn't just "lines of text" — every single character
 * position on screen has its OWN color, boldness, etc. This is what makes
 * `ls --color`, colored prompts, `htop`, `vim` syntax highlighting, and
 * progress bars in `apt`/`apk` look right.
 *
 * Colors can come from two systems, matching real terminal behavior:
 *   - Palette colors (0-255): fgColor/bgColor hold an index into the
 *     16-color or 256-color ANSI palette (see TerminalColors.resolve).
 *   - True color (24-bit RGB): fgTrueColor/bgTrueColor hold a packed ARGB
 *     int directly, used when an app sends "\x1b[38;2;r;g;bm" — the modern
 *     "true color" escape sequence that most current terminals (including
 *     Termux) support, letting apps like neovim render exact RGB colors
 *     instead of being limited to the 256-color palette.
 *
 * One TerminalCell = one character cell on the grid.
 */
data class TerminalCell(
    var char: Char = ' ',
    var fgColor: Int = TerminalColors.DEFAULT_FG,
    var bgColor: Int = TerminalColors.DEFAULT_BG,
    var fgTrueColor: Int = 0, // 0 means "not set, use fgColor palette index instead"
    var bgTrueColor: Int = 0,
    var bold: Boolean = false,
    var underline: Boolean = false,
    var reverse: Boolean = false
) {
    fun reset() {
        char = ' '
        fgColor = TerminalColors.DEFAULT_FG
        bgColor = TerminalColors.DEFAULT_BG
        fgTrueColor = 0
        bgTrueColor = 0
        bold = false
        underline = false
        reverse = false
    }

    fun copyStyleFrom(other: TerminalCell) {
        fgColor = other.fgColor
        bgColor = other.bgColor
        fgTrueColor = other.fgTrueColor
        bgTrueColor = other.bgTrueColor
        bold = other.bold
        underline = other.underline
        reverse = other.reverse
    }

    /** Resolves this cell's actual foreground draw color, preferring true color if set. */
    fun resolvedFg(): Int =
        if (fgTrueColor != 0) fgTrueColor
        else if (fgColor == TerminalColors.DEFAULT_FG) TerminalColors.themeForeground
        else TerminalColors.resolve(fgColor)

    /** Resolves this cell's actual background draw color, preferring true color if set. */
    fun resolvedBg(): Int =
        if (bgTrueColor != 0) bgTrueColor
        else if (bgColor == TerminalColors.DEFAULT_BG) TerminalColors.themeBackground
        else TerminalColors.resolve(bgColor)
}

/**
 * TerminalColors
 *
 * The classic 16-color ANSI palette (colors 0-7 normal, 8-15 bright),
 * plus the 256-color extended palette used by many terminal apps.
 * These are the exact color numbers the ANSI standard defines — not
 * something we invented — so any app printing "\x1b[31m" (red) etc.
 * looks the way it's supposed to.
 */
object TerminalColors {
    const val DEFAULT_FG = 7  // light gray, standard terminal default palette index
    const val DEFAULT_BG = 0  // black, standard terminal default palette index

    // The ACTUAL colors drawn for "default" (unstyled) text/background.
    // Overridden by whichever ColorScheme the user picks in Settings —
    // see TerminalView.applyColorScheme(). Defaults to the classic
    // green-on-black look until a preference is loaded.
    var themeForeground: Int = 0xFF00FF41.toInt()
    var themeBackground: Int = 0xFF0D0D0D.toInt()

    // Standard 16-color palette (0-7 normal, 8-15 bright variants)
    private val ansi16 = intArrayOf(
        0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
        0xFF0000EE.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
        0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
        0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt()
    )

    /** Resolves any ANSI color number (0-255) to an actual ARGB int for drawing. */
    fun resolve(colorIndex: Int): Int {
        return when {
            colorIndex < 16 -> ansi16[colorIndex]
            colorIndex < 232 -> {
                // 216-color cube: 16 + 36*r + 6*g + b, each component 0-5
                val i = colorIndex - 16
                val r = i / 36
                val g = (i % 36) / 6
                val b = i % 6
                val toByte = { v: Int -> if (v == 0) 0 else 55 + v * 40 }
                (0xFF shl 24) or (toByte(r) shl 16) or (toByte(g) shl 8) or toByte(b)
            }
            else -> {
                // Grayscale ramp: 232-255
                val gray = 8 + (colorIndex - 232) * 10
                (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }
    }
}
