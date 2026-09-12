package com.lumen.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The strip above the keyboard. While the user is typing it shows word
 * suggestions; when idle it shows shortcut icons for Clipboard, Voice
 * typing, and GIF search, similar to Gboard's toolbar.
 */
class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onSuggestionClick: ((String) -> Unit)? = null
    var onClipboardClick: (() -> Unit)? = null
    var onMicClick: (() -> Unit)? = null
    var onGifClick: (() -> Unit)? = null

    private var theme: KeyboardTheme? = null
    private var typeface: Typeface? = null
    private var lastWords: List<String> = emptyList()

    init {
        orientation = HORIZONTAL
        setSuggestions(emptyList())
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(newTheme.backgroundColor)
        setSuggestions(lastWords)
    }

    fun applyTypeface(tf: Typeface?) {
        typeface = tf
        setSuggestions(lastWords)
    }

    fun setSuggestions(words: List<String>) {
        lastWords = words
        removeAllViews()
        val t = theme

        if (words.isEmpty()) {
            addView(iconView("\uD83D\uDCCB") { onClipboardClick?.invoke() }) // clipboard
            addView(iconView("\uD83C\uDFA4") { onMicClick?.invoke() })       // microphone
            addView(iconView("GIF") { onGifClick?.invoke() })
            return
        }

        words.take(3).forEach { word ->
            addView(centerLabel(word, t).apply {
                isClickable = true
                setOnClickListener { onSuggestionClick?.invoke(word) }
            })
        }
    }

    private fun centerLabel(text: String, t: KeyboardTheme?): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(t?.textColor ?: Color.DKGRAY)
        textSize = 15f
        typeface?.let { this.typeface = it }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
    }

    private fun iconView(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(theme?.textColor ?: Color.DKGRAY)
        textSize = 13f
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        isClickable = true
        setOnClickListener { onClick() }
    }
}
