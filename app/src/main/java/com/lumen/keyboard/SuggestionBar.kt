package com.lumen.keyboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onSuggestionClick: ((String) -> Unit)? = null
    private var theme: KeyboardTheme? = null
    private var lastWords: List<String> = emptyList()

    init {
        orientation = HORIZONTAL
        setSuggestions(emptyList())
    }

    fun applyTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.backgroundColor)
        setSuggestions(lastWords)
    }

    fun setSuggestions(words: List<String>) {
        lastWords = words
        removeAllViews()
        val t = theme
        val display = if (words.isEmpty()) listOf("", "", "") else words.take(3)
        display.forEach { word ->
            val tv = TextView(context).apply {
                text = word
                gravity = Gravity.CENTER
                setTextColor(t?.textColor ?: Color.DKGRAY)
                textSize = 15f
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                isClickable = word.isNotEmpty()
                if (word.isNotEmpty()) {
                    setOnClickListener { onSuggestionClick?.invoke(word) }
                }
            }
            addView(tv)
        }
    }
}
