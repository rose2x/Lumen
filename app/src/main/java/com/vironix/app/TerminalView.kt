package com.vironix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.vironix.app.terminal.TerminalCell
import com.vironix.app.terminal.TerminalColors
import com.vironix.app.terminal.TerminalEmulator
import kotlin.math.max
/**
 * TerminalView
 *
 * Renders a TerminalEmulator's character grid to the screen and forwards
 * keyboard input back to the shell. This class deliberately knows NOTHING
 * about ANSI escape codes or terminal state — all of that logic lives in
 * TerminalEmulator. TerminalView's only job is:
 *   1. Draw whatever the grid currently contains (colors, bold, cursor)
 *   2. Turn Android key events / IME text into raw bytes for the shell
 *
 * This split (emulator = logic, view = pixels) mirrors how real terminal
 * emulators like Termux's are structured, and makes each half easy to
 * reason about on its own.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onKeyTyped: ((ByteArray) -> Unit)? = null

    /** The emulator currently being displayed. Swappable — see attachEmulator(). */
    var emulator: TerminalEmulator = TerminalEmulator(24, 80)
        private set

    /**
     * Points this view at a different session's emulator — used when the
     * user switches tabs. The old emulator keeps running in the background
     * (SessionManager keeps pumping its output into it); we just stop
     * listening to it and start listening to the new one instead.
     */
    fun attachEmulator(newEmulator: TerminalEmulator) {
        emulator = newEmulator
        emulator.onDirty = { post { invalidate() } }
        // The newly-attached session might already have a different size
        // than this view's pixel dimensions (e.g. it was created before a
        // rotation) — resize it to match immediately so nothing looks cut off.
        if (charWidth > 0 && charHeight > 0 && width > 0 && height > 0) {
            val cols = max(1, (width / charWidth).toInt())
            val rows = max(1, (height / charHeight).toInt())
            if (cols != emulator.cols || rows != emulator.rows) {
                emulator.resize(rows, cols)
            }
        }
        invalidate()
    }

    private val textPaint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = 32f
        isAntiAlias = true
    }
    private val cellBackgroundPaint = Paint()
    private val cursorPaint = Paint().apply { color = Color.parseColor("#AAFFAA") }

    private var charWidth = 0f
    private var charHeight = 0f
    private var charAscent = 0f
    private var cursorStyle: CursorStyle = CursorStyle.BLOCK

    /**
     * Applies user preferences (font size, color scheme, cursor style) from
     * TerminalPreferences. Call this once when the screen is created/resumed
     * — see TerminalActivity — so settings changes take effect on next open.
     */
    fun applySettings(prefs: TerminalPreferences) {
        textPaint.textSize = spToPx(prefs.fontSize)
        TerminalColors.themeForeground = prefs.colorScheme.foreground
        TerminalColors.themeBackground = prefs.colorScheme.background
        cursorStyle = prefs.cursorStyle
        measureCharSize()
        // Re-run the size calculation since font size changed how many
        // rows/cols fit in the same pixel area.
        if (width > 0 && height > 0) onSizeChanged(width, height, width, height)
        invalidate()
    }

    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

    // NOTE: intentionally recomputed each onDraw (not cached as a `val`) —
    // the theme background can change at runtime via Settings, and this is
    // a single cheap field read per frame, not per cell.

    // Blinking cursor, like a real terminal.
    private var cursorVisible = true
    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            blinkHandler.postDelayed(this, 530)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        measureCharSize()
        emulator.onDirty = { post { invalidate() } }
    }

    private fun measureCharSize() {
        charWidth = textPaint.measureText("X")
        val metrics = textPaint.fontMetrics
        charHeight = metrics.descent - metrics.ascent
        charAscent = -metrics.ascent
    }

    fun columns(): Int = emulator.cols
    fun rows(): Int = emulator.rows

    /** Feeds raw shell output bytes into the emulator for interpretation and display. */
    fun feed(data: ByteArray, len: Int) {
        emulator.feed(data, len)
    }

    /**
     * Sends raw bytes directly to the shell — used for mouse reports, which
     * bypass the IME entirely (they're synthetic escape sequences, not
     * something the user "typed").
     */
    var onMouseEvent: ((ByteArray) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only intercept taps as "mouse events" if the running app (vim,
        // tmux, etc.) actually asked for mouse reporting via "ESC[?1000h"
        // or similar. Otherwise, taps should just open the keyboard as normal —
        // handled by the click listener set up in TerminalActivity.
        if (emulator.mouseMode == TerminalEmulator.MouseMode.OFF) {
            return super.onTouchEvent(event)
        }

        val col = (event.x / charWidth).toInt().coerceIn(0, emulator.cols - 1)
        val row = (event.y / charHeight).toInt().coerceIn(0, emulator.rows - 1)

        val pressed = event.action == MotionEvent.ACTION_DOWN
        val released = event.action == MotionEvent.ACTION_UP
        if (!pressed && !released) return true // ignore MOVE for v1 (no drag-select yet)

        onMouseEvent?.invoke(buildMouseReport(row, col, isPress = pressed))
        return true
    }

    /**
     * Builds an xterm-style mouse report escape sequence. Two encodings
     * exist; we use the modern SGR encoding (ESC[<btn;col;rowM/m) when the
     * app requested it (mode 1006), since it supports terminals wider than
     * 223 columns, which the old encoding can't represent.
     */
    private fun buildMouseReport(row: Int, col: Int, isPress: Boolean): ByteArray {
        val button = 0 // left button
        val finalChar = if (isPress) 'M' else 'm'
        return if (emulator.mouseSgrMode) {
            "\u001b[<$button;${col + 1};${row + 1}$finalChar".toByteArray(Charsets.US_ASCII)
        } else {
            // Legacy X10/normal encoding: fixed 3 bytes after "ESC[M", each
            // offset by 32 ("space") per the original xterm mouse protocol.
            val b = (32 + button + if (isPress) 0 else 3).toByte()
            val cx = (32 + col + 1).toByte()
            val cy = (32 + row + 1).toByte()
            byteArrayOf(0x1B, '['.code.toByte(), 'M'.code.toByte(), b, cx, cy)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Fill the whole view with the theme background first, so switching
        // to a light color scheme doesn't leave black gaps in unpainted
        // margins/padding around the character grid.
        canvas.drawColor(TerminalColors.themeBackground)

        // Then paint each cell individually so reverse-video / colored
        // backgrounds (e.g. selection highlights, colored prompts) render correctly,
        // rather than relying on one flat background color for the whole screen.
        val grid = emulator.grid
        for (row in grid.indices) {
            var y = row * charHeight
            for (col in grid[row].indices) {
                val cell = grid[row][col]
                drawCell(canvas, cell, row, col, y)
            }
        }

        if (cursorVisible) drawCursor(canvas, grid)
    }

    /** Draws the cursor in whichever shape the user picked in Settings (block/underline/bar). */
    private fun drawCursor(canvas: Canvas, grid: Array<Array<TerminalCell>>) {
        val cx = emulator.cursorCol * charWidth
        val cy = emulator.cursorRow * charHeight

        when (cursorStyle) {
            CursorStyle.BLOCK -> {
                canvas.drawRect(cx, cy, cx + charWidth, cy + charHeight, cursorPaint)
                val cell = grid.getOrNull(emulator.cursorRow)?.getOrNull(emulator.cursorCol)
                if (cell != null && cell.char != ' ') {
                    textPaint.color = Color.BLACK
                    canvas.drawText(cell.char.toString(), cx, cy + charAscent, textPaint)
                }
            }
            CursorStyle.UNDERLINE -> {
                val thickness = charHeight * 0.12f
                canvas.drawRect(cx, cy + charHeight - thickness, cx + charWidth, cy + charHeight, cursorPaint)
            }
            CursorStyle.BAR -> {
                val thickness = charWidth * 0.15f
                canvas.drawRect(cx, cy, cx + thickness, cy + charHeight, cursorPaint)
            }
        }
    }

    private fun drawCell(canvas: Canvas, cell: TerminalCell, row: Int, col: Int, y: Float) {
        val x = col * charWidth
        val fg = if (cell.reverse) cell.resolvedBg() else cell.resolvedFg()
        val bg = if (cell.reverse) cell.resolvedFg() else cell.resolvedBg()

        // Only paint a background rect when it differs from the default black —
        // avoids overdrawing every single cell every frame for a mostly-blank screen.
        if (bg != TerminalColors.themeBackground) {
            cellBackgroundPaint.color = bg
            canvas.drawRect(x, y, x + charWidth, y + charHeight, cellBackgroundPaint)
        }

        if (cell.char != ' ') {
            textPaint.color = fg
            textPaint.isFakeBoldText = cell.bold
            textPaint.isUnderlineText = cell.underline
            canvas.drawText(cell.char.toString(), x, y + charAscent, textPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        measureCharSize()
        val newCols = max(1, (w / charWidth).toInt())
        val newRows = max(1, (h / charHeight).toInt())
        if (newCols != emulator.cols || newRows != emulator.rows) {
            emulator.resize(newRows, newCols)
            onSizeChangedListener?.invoke(newRows, newCols)
        }
    }

    /** Notifies the owning Activity so it can tell the native PTY about the new size (TIOCSWINSZ). */
    var onSizeChangedListener: ((rows: Int, cols: Int) -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        blinkHandler.post(blinkRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkHandler.removeCallbacks(blinkRunnable)
    }

    // --- Keyboard input handling ---

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                onKeyTyped?.invoke(text.toString().toByteArray(Charsets.UTF_8))
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> onKeyTyped?.invoke(byteArrayOf('\n'.code.toByte()))
                        KeyEvent.KEYCODE_DEL -> onKeyTyped?.invoke(byteArrayOf(0x7F.toByte()))
                        KeyEvent.KEYCODE_DPAD_UP -> onKeyTyped?.invoke(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()))
                        KeyEvent.KEYCODE_DPAD_DOWN -> onKeyTyped?.invoke(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()))
                        KeyEvent.KEYCODE_DPAD_RIGHT -> onKeyTyped?.invoke(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()))
                        KeyEvent.KEYCODE_DPAD_LEFT -> onKeyTyped?.invoke(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()))
                        KeyEvent.KEYCODE_TAB -> onKeyTyped?.invoke(byteArrayOf(0x09))
                        else -> {
                            val ch = event.unicodeChar
                            if (ch != 0) onKeyTyped?.invoke(String(Character.toChars(ch)).toByteArray())
                        }
                    }
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { onKeyTyped?.invoke(byteArrayOf(0x7F.toByte())) }
                return true
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true
}
