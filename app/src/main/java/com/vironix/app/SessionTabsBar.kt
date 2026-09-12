package com.vironix.app

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * SessionTabsBar
 *
 * Renders one tappable "chip" per open session (tab), plus a "+" button
 * to start a new one — the same idea as Termux's session list, just shown
 * as a horizontal strip instead of a drawer/dialog for simplicity.
 *
 * Tapping a chip switches to that session. Tapping the ACTIVE tab's chip
 * (which shows a "×") closes it, so closing is always one tap away for
 * the tab you're currently looking at.
 */
class SessionTabsBar(
    private val container: LinearLayout,
    private val onSwitchTo: (sessionId: Int) -> Unit,
    private val onCloseSession: (sessionId: Int) -> Unit,
    private val onNewSession: () -> Unit
) {

    /** Redraws the whole tab strip from scratch — cheap enough since tab counts are always small. */
    fun render(sessions: List<SessionManager.SessionEntry>, activeSessionId: Int) {
        container.removeAllViews()

        sessions.forEachIndexed { index, entry ->
            addTabChip(entry, index + 1, isActive = entry.id == activeSessionId)
        }

        addNewSessionButton()
    }

    private fun addTabChip(entry: SessionManager.SessionEntry, position: Int, isActive: Boolean) {
        val label = if (isActive) "${entry.title} $position  \u00D7" else "${entry.title} $position"
        val chip = TextView(container.context).apply {
            text = label
            setTextColor(if (isActive) Color.BLACK else Color.parseColor("#00FF41"))
            setBackgroundColor(if (isActive) Color.parseColor("#00FF41") else Color.parseColor("#262626"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            isClickable = true
            isFocusable = true
            alpha = if (entry.isAlive) 1.0f else 0.5f // dim tabs whose shell has exited
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.marginEnd = 6
        chip.layoutParams = params

        chip.setOnClickListener {
            if (isActive) {
                onCloseSession(entry.id) // tapping the "×" on the active tab closes it
            } else {
                onSwitchTo(entry.id)
            }
        }
        container.addView(chip)
    }

    private fun addNewSessionButton() {
        val button = TextView(container.context).apply {
            text = "+"
            setTextColor(Color.parseColor("#00FF41"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(30, 16, 30, 16)
            isClickable = true
            isFocusable = true
        }
        button.setOnClickListener { onNewSession() }
        container.addView(button)
    }
}
