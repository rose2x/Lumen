package com.lumen.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class VoiceInputStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onCancel: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    private val statusLabel = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        textSize = 14f
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        setPadding(20, 0, 20, 0)
        maxLines = 2
    }
    private val cancelButton = TextView(context).apply {
        text = "\u2715"
        gravity = Gravity.CENTER
        textSize = 16f
        setPadding(28, 0, 28, 0)
    }
    private val doneButton = TextView(context).apply {
        text = "\u2713"
        gravity = Gravity.CENTER
        textSize = 18f
        setPadding(28, 0, 28, 0)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        cancelButton.setOnClickListener { onCancel?.invoke() }
        doneButton.setOnClickListener { onDone?.invoke() }
        addView(cancelButton)
        addView(statusLabel)
        addView(doneButton)
        setListening(false, "")
    }

    fun applyTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.backgroundColor)
        statusLabel.setTextColor(theme.textColor)
        cancelButton.setTextColor(theme.textColor)
        doneButton.setTextColor(theme.accentColor)
    }

    fun applyTypeface(tf: android.graphics.Typeface?) {
        statusLabel.typeface = tf
    }

    fun setListening(listening: Boolean, partialText: String) {
        statusLabel.text = when {
            partialText.isNotBlank() -> partialText
            listening -> context.getString(R.string.voice_listening)
            else -> context.getString(R.string.voice_idle)
        }
    }

    fun setError(message: String) {
        statusLabel.text = message
    }
}
