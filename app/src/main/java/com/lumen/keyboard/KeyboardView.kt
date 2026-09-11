package com.lumen.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.lumen.keyboard.model.KeyDef
import com.lumen.keyboard.model.KeyRow
import com.lumen.keyboard.model.KeyType
import com.lumen.keyboard.model.KeyboardLayout
import com.lumen.keyboard.model.Layouts

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnKeyboardActionListener {
        fun onKey(key: KeyDef, outputChar: String)
    }

    var listener: OnKeyboardActionListener? = null
    var showPopupPreview: Boolean = true

    private var layout: KeyboardLayout = Layouts.qwerty
    private var theme: KeyboardTheme = Themes.forMode(ThemeMode.LIGHT, false, PreferencesManager.DEFAULT_ACCENT)

    private var capsOn = false
    private var capsLocked = false

    private data class KeyBounds(val key: KeyDef, val rect: RectF)
    private var keyBounds: List<KeyBounds> = emptyList()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private var pressedKey: KeyBounds? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private var longPressTriggered = false

    private var popupWindow: PopupWindow? = null
    private var popupItems: List<TextView> = emptyList()
    private var popupSelectedIndex = 0

    fun setKeyboardLayout(newLayout: KeyboardLayout) {
        layout = newLayout
        requestLayout()
        invalidate()
    }

    fun setTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    fun setCaps(on: Boolean, locked: Boolean) {
        capsOn = on
        capsLocked = locked
        invalidate()
    }

    fun setEnterLabel(label: String) {
        layout = KeyboardLayout(
            layout.rows.map { row ->
                KeyRow(row.keys.map { k -> if (k.type == KeyType.ENTER) k.copy(label = label) else k })
            }
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds(w.toFloat(), h.toFloat())
    }

    private fun computeKeyBounds(w: Float, h: Float) {
        val rows = layout.rows
        if (rows.isEmpty() || w <= 0f || h <= 0f) {
            keyBounds = emptyList()
            return
        }
        val rowHeight = h / rows.size
        val maxRowWeight = rows.maxOf { row -> row.keys.sumOf { it.weight.toDouble() }.toFloat() }
        val bounds = mutableListOf<KeyBounds>()

        rows.forEachIndexed { rowIndex, row ->
            val rowWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val rowWidth = w * (rowWeight / maxRowWeight)
            val startX = (w - rowWidth) / 2f
            val unit = if (rowWeight > 0f) rowWidth / rowWeight else 0f
            var x = startX
            val top = rowIndex * rowHeight
            row.keys.forEach { key ->
                val keyWidth = unit * key.weight
                bounds.add(KeyBounds(key, RectF(x + 4, top + 4, x + keyWidth - 4, top + rowHeight - 4)))
                x += keyWidth
            }
        }
        keyBounds = bounds
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)
        keyBounds.forEach { kb -> drawKey(canvas, kb) }
    }

    private fun drawKey(canvas: Canvas, kb: KeyBounds) {
        val isSpecial = kb.key.type != KeyType.CHARACTER && kb.key.type != KeyType.SPACE
        val isPressed = kb == pressedKey

        keyPaint.color = when {
            isPressed && isSpecial -> theme.accentColor
            isPressed -> theme.keyPressedColor
            isSpecial -> theme.specialKeyColor
            else -> theme.keyColor
        }
        canvas.drawRoundRect(kb.rect, 18f, 18f, keyPaint)

        when (kb.key.type) {
            KeyType.BACKSPACE -> drawBackspaceIcon(canvas, kb.rect)
            KeyType.SHIFT -> drawShiftIcon(canvas, kb.rect)
            KeyType.EMOJI -> drawText(canvas, kb.rect, "\uD83D\uDE0A", small = false)
            KeyType.SPACE -> {}
            else -> {
                val useCaps = (capsOn || capsLocked) &&
                    kb.key.type == KeyType.CHARACTER &&
                    kb.key.label.length == 1 &&
                    kb.key.label[0].isLetter()
                val label = if (useCaps) kb.key.capsLabel else kb.key.label
                drawText(canvas, kb.rect, label, small = kb.key.type != KeyType.CHARACTER)
            }
        }
    }

    private fun drawText(canvas: Canvas, rect: RectF, text: String, small: Boolean) {
        textPaint.color = theme.textColor
        textPaint.textSize = if (small) rect.height() * 0.32f else rect.height() * 0.42f
        val fm = textPaint.fontMetrics
        val y = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, rect.centerX(), y, textPaint)
    }

    private fun drawBackspaceIcon(canvas: Canvas, rect: RectF) {
        iconPaint.color = theme.textColor
        iconPaint.style = Paint.Style.STROKE
        val cx = rect.centerX(); val cy = rect.centerY()
        val w = rect.width() * 0.26f; val h = rect.height() * 0.18f
        val path = Path().apply {
            moveTo(cx - w, cy)
            lineTo(cx - w * 0.4f, cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - w * 0.4f, cy + h)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawLine(cx - w * 0.05f, cy - h * 0.55f, cx + w * 0.55f, cy + h * 0.55f, iconPaint)
        canvas.drawLine(cx + w * 0.55f, cy - h * 0.55f, cx - w * 0.05f, cy + h * 0.55f, iconPaint)
    }

    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val on = capsOn || capsLocked
        iconPaint.color = if (on) theme.accentColor else theme.textColor
        iconPaint.style = if (capsLocked) Paint.Style.FILL else Paint.Style.STROKE
        val cx = rect.centerX(); val cy = rect.centerY()
        val s = rect.height() * 0.2f
        val path = Path().apply {
            moveTo(cx - s, cy + s * 0.3f)
            lineTo(cx, cy - s)
            lineTo(cx + s, cy + s * 0.3f)
            lineTo(cx + s * 0.5f, cy + s * 0.3f)
            lineTo(cx + s * 0.5f, cy + s)
            lineTo(cx - s * 0.5f, cy + s)
            lineTo(cx - s * 0.5f, cy + s * 0.3f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        iconPaint.style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp()
            MotionEvent.ACTION_CANCEL -> cancelPress()
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): KeyBounds? =
        keyBounds.firstOrNull { it.rect.contains(x, y) }

    private fun handleDown(event: MotionEvent) {
        val kb = findKeyAt(event.x, event.y) ?: return
        pressedKey = kb
        longPressTriggered = false
        invalidate()

        if (kb.key.type == KeyType.BACKSPACE) {
            fireKey(kb.key)
            val r = object : Runnable {
                override fun run() {
                    fireKey(kb.key)
                    handler.postDelayed(this, 60)
                }
            }
            repeatRunnable = r
            handler.postDelayed(r, 450)
        } else if (kb.key.longPress.isNotEmpty() && showPopupPreview) {
            val r = Runnable {
                longPressTriggered = true
                showLongPressPopup(kb)
            }
            longPressRunnable = r
            handler.postDelayed(r, 350)
        }
    }

    private fun handleMove(event: MotionEvent) {
        if (longPressTriggered && popupWindow?.isShowing == true) {
            updatePopupSelection(event.rawX)
        }
    }

    private fun handleUp() {
        val kb = pressedKey
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }

        if (longPressTriggered) {
            val chars = kb?.key?.longPress ?: emptyList()
            if (kb != null && chars.isNotEmpty() && popupSelectedIndex in chars.indices) {
                listener?.onKey(kb.key, chars[popupSelectedIndex])
            }
            dismissPopup()
        } else if (kb != null && kb.key.type != KeyType.BACKSPACE) {
            fireKey(kb.key)
        }
        cancelPress()
    }

    private fun cancelPress() {
        pressedKey = null
        longPressTriggered = false
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }
        dismissPopup()
        invalidate()
    }

    private fun fireKey(key: KeyDef) {
        val output = when (key.type) {
            KeyType.CHARACTER -> if (capsOn || capsLocked) key.capsLabel else key.label
            KeyType.SPACE -> " "
            else -> key.label
        }
        listener?.onKey(key, output)
    }

    private fun showLongPressPopup(kb: KeyBounds) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.popupColor)
            setPadding(12, 12, 12, 12)
        }
        popupItems = kb.key.longPress.map { alt ->
            TextView(context).apply {
                text = alt
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(theme.textColor)
                setPadding(28, 14, 28, 14)
            }
        }
        popupItems.forEach { container.addView(it) }
        popupSelectedIndex = 0
        highlightPopupItem(0)

        val pw = PopupWindow(container, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pw.isOutsideTouchable = false
        popupWindow = pw

        val loc = IntArray(2)
        getLocationInWindow(loc)
        pw.showAtLocation(
            this,
            Gravity.NO_GRAVITY,
            (loc[0] + kb.rect.centerX() - popupItems.size * 30).toInt(),
            (loc[1] + kb.rect.top - 110).toInt()
        )
    }

    private fun updatePopupSelection(rawX: Float) {
        if (popupItems.isEmpty()) return
        val loc = IntArray(2)
        var newIndex = popupSelectedIndex
        for ((i, tv) in popupItems.withIndex()) {
            tv.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + tv.width) {
                newIndex = i
                break
            }
        }
        if (newIndex != popupSelectedIndex) highlightPopupItem(newIndex)
    }

    private fun highlightPopupItem(index: Int) {
        popupSelectedIndex = index
        popupItems.forEachIndexed { i, tv ->
            tv.setBackgroundColor(if (i == index) theme.accentColor else Color.TRANSPARENT)
        }
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
        popupItems = emptyList()
    }
}
