package com.lumen.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var onItemSelected: ((String) -> Unit)? = null
    var onItemDeleted: ((String) -> Unit)? = null

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var theme: KeyboardTheme? = null
    private var typeface: Typeface? = null
    private var lastItems: List<String> = emptyList()

    init {
        addView(list)
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(newTheme.backgroundColor)
        setItems(lastItems)
    }

    fun applyTypeface(tf: Typeface?) {
        typeface = tf
        setItems(lastItems)
    }

    fun setItems(items: List<String>) {
        lastItems = items
        list.removeAllViews()
        val t = theme
        val tf = typeface

        if (items.isEmpty()) {
            val empty = TextView(context).apply {
                text = context.getString(R.string.clipboard_empty)
                setTextColor(t?.textColor ?: Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(24, 48, 24, 24)
                tf?.let { typeface = it }
            }
            list.addView(empty)
            return
        }

        items.forEach { entry ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 20, 20, 20)
            }
            val label = TextView(context).apply {
                text = entry
                maxLines = 2
                setTextColor(t?.textColor ?: Color.DKGRAY)
                textSize = 14f
                tf?.let { typeface = it }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onItemSelected?.invoke(entry) }
            }
            val delete = TextView(context).apply {
                text = "\u2715"
                setTextColor(t?.textColor ?: Color.DKGRAY)
                setPadding(24, 0, 8, 0)
                setOnClickListener { onItemDeleted?.invoke(entry) }
            }
            row.addView(label)
            row.addView(delete)
            list.addView(row)
        }
    }
}
