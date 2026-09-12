package com.vironix.app

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ExtraKeysBar
 *
 * Phone soft keyboards don't have Ctrl, Esc, Tab, or arrow keys — but real
 * terminal use is full of them (Ctrl+C to cancel, Ctrl+D to log out, Tab to
 * autocomplete, Esc to leave vim's insert mode, arrows for shell history).
 * Termux solves this with a row of tappable extra keys, and this class
 * does the same thing.
 *
 * "Ctrl" here is a STICKY modifier: tapping it arms Ctrl for the *next*
 * key you press (e.g. tap Ctrl, then tap "c" on the real keyboard, to send
 * Ctrl+C) — this is simpler to implement correctly than trying to detect
 * simultaneous touches, and is how Termux's own Ctrl key behaves too.
 */
class ExtraKeysBar(
    private val container: LinearLayout,
    private val onSendBytes: (ByteArray) -> Unit
) {
    private var ctrlArmed = false
    private var ctrlButton: TextView? = null

    fun build() {
        container.removeAllViews()

        addKey("ESC") { onSendBytes(byteArrayOf(0x1B)) }
        ctrlButton = addKey("CTRL") { toggleCtrl() }
        addKey("TAB") { onSendBytes(byteArrayOf(0x09)) }
        addKey("/") { onSendBytes(byteArrayOf('/'.code.toByte())) }
        addKey("-") { onSendBytes(byteArrayOf('-'.code.toByte())) }
        addKey("|") { onSendBytes(byteArrayOf('|'.code.toByte())) }
        addKey("HOME") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte())) }
        addKey("END") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte())) }
        addKey("\u2191") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())) } // up
        addKey("\u2193") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())) } // down
        addKey("\u2190") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())) } // left
        addKey("\u2192") { onSendBytes(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())) } // right
    }

    private fun toggleCtrl() {
        ctrlArmed = !ctrlArmed
        ctrlButton?.setBackgroundColor(if (ctrlArmed) Color.parseColor("#00FF41") else Color.parseColor("#262626"))
        ctrlButton?.setTextColor(if (ctrlArmed) Color.BLACK else Color.parseColor("#00FF41"))
    }

    /**
     * Called by TerminalView's input handling BEFORE sending a normal
     * character, so that if Ctrl is armed, we transform e.g. 'c' -> 0x03
     * (the real Ctrl+C byte) instead of sending a literal "c".
     *
     * Returns true if this consumed the keypress (i.e. Ctrl was armed).
     */
    fun interceptIfCtrlArmed(text: String): Boolean {
        if (!ctrlArmed || text.length != 1) return false
        val ch = text[0].uppercaseChar()
        if (ch in 'A'..'Z') {
            val controlCode = (ch.code - 'A'.code + 1) // Ctrl+A=1, Ctrl+C=3, Ctrl+D=4, etc.
            onSendBytes(byteArrayOf(controlCode.toByte()))
        }
        toggleCtrl() // Ctrl is "used up" after one keypress, same as Termux
        return true
    }

    private fun addKey(label: String, action: () -> Unit): TextView {
        val button = TextView(container.context).apply {
            text = label
            setTextColor(Color.parseColor("#00FF41"))
            setBackgroundColor(Color.parseColor("#262626"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(28, 20, 28, 20)
            isClickable = true
            isFocusable = true
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = 6
        button.layoutParams = params
        button.setOnClickListener { action() }
        container.addView(button)
        return button
    }
}
