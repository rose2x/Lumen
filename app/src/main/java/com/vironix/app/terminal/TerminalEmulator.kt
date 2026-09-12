package com.vironix.app.terminal

import kotlin.math.max
import kotlin.math.min

/**
 * TerminalEmulator
 *
 * This is the "brain" of a real terminal — completely separate from any
 * Android drawing code (see TerminalView, which just renders whatever
 * this class's grid currently looks like).
 *
 * WHAT A TERMINAL EMULATOR ACTUALLY DOES (for beginners):
 * When you run a shell, the shell doesn't know it's "on a phone screen."
 * It just writes bytes to its output like it would to any old-school
 * physical terminal — plain characters PLUS special "escape sequences"
 * that mean things like "move cursor to row 5", "make the next text red",
 * "clear the screen", "switch to a separate blank screen". These sequences
 * all start with the ESC byte (0x1B) followed by `[` and then some
 * numbers/letters — this is the classic ANSI/VT100/xterm standard every
 * terminal (including Termux's) implements.
 *
 * This class handles:
 *   - A 2D grid of TerminalCell (one per character position), with a
 *     SEPARATE alternate-screen grid for full-screen apps (see below)
 *   - Palette (16/256) AND true-color (24-bit RGB) SGR codes
 *   - Cursor movement, scrolling, scroll regions, screen/line clearing
 *   - Mouse reporting mode toggles (so apps like `vim` or `tmux` that want
 *     mouse input can request it, and TerminalView knows to forward taps
 *     as mouse events instead of just moving the text cursor)
 *
 * This is not the complete xterm spec (hundreds of obscure sequences exist)
 * but it covers what real-world shells, vim, nano, htop, tmux, and
 * package managers actually rely on.
 */
class TerminalEmulator(var rows: Int, var cols: Int) {

    // --- Primary vs alternate screen buffer ---
    //
    // WHY THIS EXISTS: when you run `vim` or `htop`, they don't want to
    // scroll your normal shell history — they take over the WHOLE screen,
    // and when you quit, your previous shell scrollback reappears exactly
    // as it was. Real terminals do this with a second, separate grid
    // called the "alternate screen buffer". Apps switch to it with
    // "\x1b[?1049h" and back with "\x1b[?1049l".
    private var primaryGrid: Array<Array<TerminalCell>> = Array(rows) { Array(cols) { TerminalCell() } }
    private var alternateGrid: Array<Array<TerminalCell>> = Array(rows) { Array(cols) { TerminalCell() } }
    private var usingAlternateScreen = false
    private var savedPrimaryCursorRow = 0
    private var savedPrimaryCursorCol = 0

    val grid: Array<Array<TerminalCell>>
        get() = if (usingAlternateScreen) alternateGrid else primaryGrid

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    // Scroll region (set via "ESC[<top>;<bottom>r") — used by full-screen
    // apps like `less`/`vim` to scroll only part of the screen (e.g.
    // keeping a status bar fixed while content above it scrolls).
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    /** Which mouse-reporting mode (if any) the running app has requested. */
    enum class MouseMode { OFF, NORMAL, BUTTON_MOTION, ANY_MOTION }
    var mouseMode = MouseMode.OFF
        private set
    var mouseSgrMode = false // whether to encode mouse reports in the newer SGR format
        private set

    // Current "pen" state — what color/style newly-typed characters get.
    // Updated by SGR (Select Graphic Rendition) escape sequences like \x1b[1;31m
    private val pen = TerminalCell()

    // Parser state machine
    private enum class ParserState { NORMAL, ESCAPE, CSI, OSC }
    private var state = ParserState.NORMAL
    private val paramBuffer = StringBuilder()

    // Fires whenever the grid changes, so the UI knows to redraw.
    var onDirty: (() -> Unit)? = null

    /** Feeds one chunk of raw bytes from the shell's output into the emulator. */
    fun feed(data: ByteArray, len: Int) {
        var i = 0
        while (i < len) {
            processByte(data[i])
            i++
        }
        onDirty?.invoke()
    }

    private fun processByte(byte: Byte) {
        val b = byte.toInt() and 0xFF
        when (state) {
            ParserState.NORMAL -> handleNormalByte(b)
            ParserState.ESCAPE -> handleEscapeByte(b)
            ParserState.CSI -> handleCsiByte(b)
            ParserState.OSC -> handleOscByte(b)
        }
    }

    private fun handleNormalByte(b: Int) {
        when (b) {
            0x1B -> state = ParserState.ESCAPE // ESC — start of an escape sequence
            '\n'.code -> lineFeed()
            '\r'.code -> cursorCol = 0
            0x08 -> cursorCol = max(0, cursorCol - 1) // backspace
            0x09 -> cursorCol = min(cols - 1, ((cursorCol / 8) + 1) * 8) // tab, 8-column stops
            0x07 -> { /* bell — ignored visually */ }
            else -> if (b >= 0x20) writeChar(b.toChar())
        }
    }

    private fun handleEscapeByte(b: Int) {
        when (b.toChar()) {
            '[' -> { state = ParserState.CSI; paramBuffer.clear() }
            ']' -> { state = ParserState.OSC; paramBuffer.clear() }
            '7' -> { // save cursor position (DECSC)
                savedPrimaryCursorRow = cursorRow
                savedPrimaryCursorCol = cursorCol
                state = ParserState.NORMAL
            }
            '8' -> { // restore cursor position (DECRC)
                cursorRow = savedPrimaryCursorRow.coerceIn(0, rows - 1)
                cursorCol = savedPrimaryCursorCol.coerceIn(0, cols - 1)
                state = ParserState.NORMAL
            }
            'D', 'E', 'M', 'c' -> state = ParserState.NORMAL // other simple sequences, no-op for v1
            else -> state = ParserState.NORMAL
        }
    }

    /** CSI = "Control Sequence Introducer" — the ESC[ ... sequences that do the real work. */
    private fun handleCsiByte(b: Int) {
        val c = b.toChar()
        if (c.isDigit() || c == ';' || c == '?') {
            paramBuffer.append(c)
            return
        }

        val raw = paramBuffer.toString()
        val isPrivateMode = raw.startsWith("?") // "?" prefix marks DEC private modes (alt screen, mouse, etc.)
        val params = raw.removePrefix("?").split(';').mapNotNull { it.toIntOrNull() }

        when (c) {
            'A' -> cursorRow = max(scrollTop, cursorRow - (params.getOrElse(0) { 1 }))
            'B' -> cursorRow = min(scrollBottom, cursorRow + (params.getOrElse(0) { 1 }))
            'C' -> cursorCol = min(cols - 1, cursorCol + (params.getOrElse(0) { 1 }))
            'D' -> cursorCol = max(0, cursorCol - (params.getOrElse(0) { 1 }))
            'H', 'f' -> { // cursor position
                cursorRow = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                cursorCol = ((params.getOrElse(1) { 1 }) - 1).coerceIn(0, cols - 1)
            }
            'J' -> eraseInDisplay(params.getOrElse(0) { 0 })
            'K' -> eraseInLine(params.getOrElse(0) { 0 })
            'm' -> applySgr(params)
            'r' -> { // DECSTBM: set scroll region top;bottom
                scrollTop = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                scrollBottom = ((params.getOrElse(1) { rows }) - 1).coerceIn(0, rows - 1)
                cursorRow = scrollTop
                cursorCol = 0
            }
            'h' -> handleModeChange(params, isPrivateMode, enable = true)
            'l' -> handleModeChange(params, isPrivateMode, enable = false)
            else -> { /* unsupported sequence: ignored rather than crashing */ }
        }
        state = ParserState.NORMAL
    }

    /**
     * Handles "ESC[?<n>h" (enable) / "ESC[?<n>l" (disable) DEC private modes,
     * and the equivalent non-private modes. This is how apps switch to the
     * alternate screen buffer and turn mouse reporting on/off.
     */
    private fun handleModeChange(params: List<Int>, isPrivateMode: Boolean, enable: Boolean) {
        if (!isPrivateMode) return
        for (mode in params) {
            when (mode) {
                1049, 47, 1047 -> setAlternateScreen(enable) // alt screen buffer (vim, htop, less, tmux)
                1000 -> mouseMode = if (enable) MouseMode.NORMAL else MouseMode.OFF
                1002 -> mouseMode = if (enable) MouseMode.BUTTON_MOTION else MouseMode.OFF
                1003 -> mouseMode = if (enable) MouseMode.ANY_MOTION else MouseMode.OFF
                1006 -> mouseSgrMode = enable // SGR-encoded mouse reports (modern, supports coords > 223)
                25 -> { /* cursor visibility — TerminalView always shows a blinking cursor for v1 */ }
            }
        }
    }

    private fun setAlternateScreen(enable: Boolean) {
        if (enable == usingAlternateScreen) return
        if (enable) {
            savedPrimaryCursorRow = cursorRow
            savedPrimaryCursorCol = cursorCol
            alternateGrid = Array(rows) { Array(cols) { TerminalCell() } } // always starts blank
            usingAlternateScreen = true
            cursorRow = 0
            cursorCol = 0
        } else {
            usingAlternateScreen = false
            cursorRow = savedPrimaryCursorRow.coerceIn(0, rows - 1)
            cursorCol = savedPrimaryCursorCol.coerceIn(0, cols - 1)
        }
    }

    /** OSC = "Operating System Command" — e.g. setting the terminal title. We just skip these. */
    private fun handleOscByte(b: Int) {
        if (b == 0x07 || b == 0x1B) state = ParserState.NORMAL // terminated by BEL or ESC
    }

    private fun writeChar(ch: Char) {
        if (cursorCol >= cols) {
            lineFeed()
            cursorCol = 0
        }
        val cell = grid[cursorRow][cursorCol]
        cell.char = ch
        cell.copyStyleFrom(pen)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    /** Shifts rows within the current scroll region up by one (respects DECSTBM regions). */
    private fun scrollUp() {
        val g = grid
        for (r in scrollTop until scrollBottom) {
            g[r] = g[r + 1]
        }
        g[scrollBottom] = Array(cols) { TerminalCell() }
        if (usingAlternateScreen) alternateGrid = g else primaryGrid = g
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) grid[r].forEach { it.reset() }
            }
            1 -> {
                eraseInLine(1)
                for (r in 0 until cursorRow) grid[r].forEach { it.reset() }
            }
            2, 3 -> {
                for (r in 0 until rows) grid[r].forEach { it.reset() }
            }
        }
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) grid[cursorRow][c].reset()
            1 -> for (c in 0..cursorCol) grid[cursorRow][c].reset()
            2 -> for (c in 0 until cols) grid[cursorRow][c].reset()
        }
    }

    /**
     * SGR = "Select Graphic Rendition" — the color/bold/underline codes.
     * Handles three color systems, same as a real terminal:
     *   \x1b[31m           -> basic 16-color palette
     *   \x1b[38;5;208m     -> 256-color palette (code 5 = "palette index follows")
     *   \x1b[38;2;255;0;0m -> true color / 24-bit RGB (code 2 = "r;g;b follows")
     */
    private fun applySgr(params: List<Int>) {
        if (params.isEmpty()) { pen.reset(); return }
        var i = 0
        while (i < params.size) {
            val p = params[i]
            when {
                p == 0 -> pen.reset()
                p == 1 -> pen.bold = true
                p == 4 -> pen.underline = true
                p == 7 -> pen.reverse = true
                p == 22 -> pen.bold = false
                p == 24 -> pen.underline = false
                p == 27 -> pen.reverse = false
                p in 30..37 -> { pen.fgColor = p - 30; pen.fgTrueColor = 0 }
                p == 38 && params.getOrNull(i + 1) == 5 -> { // 256-color fg
                    pen.fgColor = params.getOrElse(i + 2) { pen.fgColor }
                    pen.fgTrueColor = 0
                    i += 2
                }
                p == 38 && params.getOrNull(i + 1) == 2 -> { // true-color fg
                    val r = params.getOrElse(i + 2) { 0 }
                    val g = params.getOrElse(i + 3) { 0 }
                    val bl = params.getOrElse(i + 4) { 0 }
                    pen.fgTrueColor = packArgb(r, g, bl)
                    i += 4
                }
                p == 39 -> { pen.fgColor = TerminalColors.DEFAULT_FG; pen.fgTrueColor = 0 }
                p in 40..47 -> { pen.bgColor = p - 40; pen.bgTrueColor = 0 }
                p == 48 && params.getOrNull(i + 1) == 5 -> { // 256-color bg
                    pen.bgColor = params.getOrElse(i + 2) { pen.bgColor }
                    pen.bgTrueColor = 0
                    i += 2
                }
                p == 48 && params.getOrNull(i + 1) == 2 -> { // true-color bg
                    val r = params.getOrElse(i + 2) { 0 }
                    val g = params.getOrElse(i + 3) { 0 }
                    val bl = params.getOrElse(i + 4) { 0 }
                    pen.bgTrueColor = packArgb(r, g, bl)
                    i += 4
                }
                p == 49 -> { pen.bgColor = TerminalColors.DEFAULT_BG; pen.bgTrueColor = 0 }
                p in 90..97 -> { pen.fgColor = p - 90 + 8; pen.fgTrueColor = 0 }
                p in 100..107 -> { pen.bgColor = p - 100 + 8; pen.bgTrueColor = 0 }
            }
            i++
        }
    }

    /** Packs 0-255 R/G/B components into an ARGB int Android's Canvas/Paint expects. */
    private fun packArgb(r: Int, g: Int, b: Int): Int {
        // A packed value of exactly 0 would be ambiguous with "not set" (see
        // TerminalCell.fgTrueColor), so pure black true-color is nudged to
        // 0xFF000001 — visually identical, but distinguishable internally.
        val packed = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        return if (packed == 0) 0xFF000001.toInt() else packed
    }

    /**
     * Resizes the grid (e.g. on screen rotation). Existing content is
     * preserved where it still fits; new cells default to blank. Both
     * the primary and alternate screens are resized, since either could
     * become active later.
     */
    fun resize(newRows: Int, newCols: Int) {
        primaryGrid = resizedCopy(primaryGrid, newRows, newCols)
        alternateGrid = resizedCopy(alternateGrid, newRows, newCols)
        rows = newRows
        cols = newCols
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    private fun resizedCopy(old: Array<Array<TerminalCell>>, newRows: Int, newCols: Int): Array<Array<TerminalCell>> {
        return Array(newRows) { r ->
            Array(newCols) { c ->
                if (r < old.size && c < old[r].size) old[r][c] else TerminalCell()
            }
        }
    }
}
