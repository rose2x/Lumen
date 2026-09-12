package com.lumen.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView

class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var onEmojiSelected: ((String) -> Unit)? = null

    private val grid = GridLayout(context).apply {
        columnCount = 8
    }

    private val emojis = listOf(
        "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔", "🙄", "😴", "😢", "😭", "😡", "🥳", "👍",
        "👎", "👏", "🙏", "💪", "🤝", "👋", "❤️", "🔥", "✨", "🎉", "💯", "✅", "❌", "⭐", "🌟", "☀️",
        "🌧️", "☕", "🍕", "🍔", "🎂", "🎁", "📅", "⏰", "📌", "📎", "💡", "🔑", "📱", "💻", "🎵", "⚽"
    )

    init {
        addView(grid)
        emojis.forEach { emoji ->
            val tv = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
                setOnClickListener { onEmojiSelected?.invoke(emoji) }
            }
            grid.addView(tv)
        }
    }

    fun applyTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.backgroundColor)
    }
}
