package com.lumen.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide

class GifResultsStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onGifSelected: ((GifResult) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val density = context.resources.displayMetrics.density

    private val queryLabel = TextView(context).apply {
        textSize = 12f
        setPadding(20, 6, 20, 0)
    }
    private val closeButton = TextView(context).apply {
        text = "\u2715"
        textSize = 16f
        setPadding(24, 0, 24, 0)
        gravity = Gravity.CENTER
    }
    private val resultsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(
            resultsRow,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        )
    }

    init {
        orientation = VERTICAL
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        closeButton.setOnClickListener { onClose?.invoke() }
        topRow.addView(closeButton)
        topRow.addView(
            queryLabel,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun applyTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.backgroundColor)
        queryLabel.setTextColor(theme.textColor)
        closeButton.setTextColor(theme.textColor)
    }

    fun applyTypeface(tf: android.graphics.Typeface?) {
        queryLabel.typeface = tf
    }

    fun setQuery(query: String) {
        queryLabel.text = if (query.isBlank()) {
            context.getString(R.string.gif_search_hint)
        } else {
            context.getString(R.string.gif_searching_for, query)
        }
    }

    fun setStatus(message: String) {
        resultsRow.removeAllViews()
        val tv = TextView(context).apply {
            text = message
            setPadding(20, 20, 20, 20)
        }
        resultsRow.addView(tv)
    }

    fun setResults(results: List<GifResult>) {
        resultsRow.removeAllViews()
        if (results.isEmpty()) {
            setStatus(context.getString(R.string.gif_no_results))
            return
        }
        results.forEach { gif ->
            val size = (72 * density).toInt()
            val margin = (6 * density).toInt()
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener { onGifSelected?.invoke(gif) }
            }
            resultsRow.addView(iv)
            Glide.with(context)
                .asGif()
                .load(gif.previewUrl)
                .into(iv)
        }
    }
}
