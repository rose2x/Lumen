package com.lumen.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
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
import kotlin.math.hypot

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnKeyboardActionListener {
        fun onKey(key: KeyDef, outputChar: String)
        /** Called once, on release, after a continuous drag across 2+ letter keys. */
        fun onSwipeGesture(letters: List<Char>) {}
        /** Called repeatedly while dragging on the space bar. +1 = one step right, -1 = one step left. */
        fun onCursorMove(steps: Int) {}
        /** Called when dragging left on Backspace crosses the word-delete threshold. */
        fun onDeleteWord() {}
        /** Called after holding Space (without dragging) for the long-press duration. */
        fun onSpaceLongPress() {}
    }

    var listener: OnKeyboardActionListener? = null
    var showPopupPreview: Boolean = true
    var swipeTypingEnabled: Boolean = true
    var onOneHandedExit: (() -> Unit)? = null

    private var layout: KeyboardLayout = Layouts.qwerty
    /** Whether the current [layout] is a letters layout (swipe-typing only applies there). */
    private var swipeEligible: Boolean = true
    private var theme: KeyboardTheme = Themes.forMode(ThemeMode.LIGHT, false, PreferencesManager.DEFAULT_ACCENT)
    private var oneHandedSide: OneHandedSide = OneHandedSide.OFF
    private var splitEnabled: Boolean = false
    private var backgroundBitmap: Bitmap? = null

    private var capsOn = false
    private var capsLocked = false

    private data class KeyBounds(val key: KeyDef, val rect: RectF)
    private var keyBounds: List<KeyBounds> = emptyList()
    private var restoreButtonRect: RectF? = null

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val swipeTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapSrcRect = Rect()
    private val bitmapDstRect = Rect()

    private var pressedKey: KeyBounds? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private var longPressTriggered = false
    private var downX = 0f
    private var downY = 0f

    private var popupWindow: PopupWindow? = null
    private var popupItems: List<TextView> = emptyList()
    private var popupSelectedIndex = 0

    // --- Swipe / gesture typing state ---
    private var swipePath: MutableList<KeyBounds>? = null
    private var isSwiping = false

    // --- Space-drag cursor move state ---
    private var spaceDragActive = false
    private var spaceDragMoved = false
    private var spaceDragLastStepX = 0f

    // --- Backspace drag-to-delete-word state ---
    private var backspaceDragActive = false
    private var backspaceWordModeTriggered = false
    private var backspaceDragLastStepX = 0f

    fun setKeyboardLayout(newLayout: KeyboardLayout, swipeEligible: Boolean = true) {
        layout = newLayout
        this.swipeEligible = swipeEligible
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

    fun setOneHandedSide(side: OneHandedSide) {
        oneHandedSide = side
        computeKeyBounds(width.toFloat(), height.toFloat())
        invalidate()
    }

    fun setSplitEnabled(enabled: Boolean) {
        splitEnabled = enabled
        computeKeyBounds(width.toFloat(), height.toFloat())
        invalidate()
    }

    fun setTypeface(typeface: Typeface?) {
        textPaint.typeface = typeface ?: Typeface.DEFAULT
        invalidate()
    }

    fun setBackgroundImage(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
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
            restoreButtonRect = null
            return
        }

        val rowHeight = h / rows.size
        val maxRowWeight = rows.maxOf { row -> row.keys.sumOf { it.weight.toDouble() }.toFloat() }
        val bounds = mutableListOf<KeyBounds>()

        if (splitEnabled) {
            restoreButtonRect = null
            val gap = w * SPLIT_GAP_FRACTION
            val halfWidth = (w - gap) / 2f
            rows.forEachIndexed { rowIndex, row ->
                val top = rowIndex * rowHeight
                val hasSpace = row.keys.any { it.type == KeyType.SPACE }
                if (hasSpace) {
                    bounds += layoutRow(row, 0f, w, top, rowHeight, maxRowWeight)
                } else {
                    val mid = (row.keys.size + 1) / 2
                    val leftKeys = row.keys.subList(0, mid)
                    val rightKeys = row.keys.subList(mid, row.keys.size)
                    val leftWeight = leftKeys.sumOf { it.weight.toDouble() }.toFloat()
                    val rightWeight = rightKeys.sumOf { it.weight.toDouble() }.toFloat()
                    val refWeight = maxOf(maxOf(leftWeight, rightWeight), 0.001f)
                    bounds += layoutRow(KeyRow(leftKeys), 0f, halfWidth, top, rowHeight, refWeight)
                    bounds += layoutRow(KeyRow(rightKeys), halfWidth + gap, w, top, rowHeight, refWeight)
                }
            }
        } else {
            val effectiveWidth = if (oneHandedSide == OneHandedSide.OFF) w else w * ONE_HANDED_FRACTION
            val anchorOffset = if (oneHandedSide == OneHandedSide.RIGHT) w - effectiveWidth else 0f

            restoreButtonRect = when (oneHandedSide) {
                OneHandedSide.LEFT -> RectF(effectiveWidth + 16f, h / 2f - 44f, effectiveWidth + 96f, h / 2f + 44f)
                OneHandedSide.RIGHT -> RectF(16f, h / 2f - 44f, 96f, h / 2f + 44f)
                OneHandedSide.OFF -> null
            }

            rows.forEachIndexed { rowIndex, row ->
                val top = rowIndex * rowHeight
                bounds += layoutRow(row, anchorOffset, anchorOffset + effectiveWidth, top, rowHeight, maxRowWeight)
            }
        }
        keyBounds = bounds
    }

    /** Lays out one row's keys, by weight, within [xStart]..[xEnd], scored against [refWeight]. */
    private fun layoutRow(row: KeyRow, xStart: Float, xEnd: Float, top: Float, rowHeight: Float, refWeight: Float): List<KeyBounds> {
        val rowWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
        val available = xEnd - xStart
        val rowWidth = if (refWeight > 0f) available * (rowWeight / refWeight) else available
        val startX = xStart + (available - rowWidth) / 2f
        val unit = if (rowWeight > 0f) rowWidth / rowWeight else 0f

        var x = startX
        val bounds = mutableListOf<KeyBounds>()
        row.keys.forEach { key ->
            val keyWidth = unit * key.weight
            bounds.add(KeyBounds(key, RectF(x + 4, top + 4, x + keyWidth - 4, top + rowHeight - 4)))
            x += keyWidth
        }
        return bounds
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        keyBounds.forEach { kb -> drawKey(canvas, kb) }
        drawSwipeTrail(canvas)
        drawRestoreButton(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val bmp = backgroundBitmap
        if (bmp == null) {
            canvas.drawColor(theme.backgroundColor)
            return
        }
        bitmapSrcRect.set(0, 0, bmp.width, bmp.height)
        bitmapDstRect.set(0, 0, width, height)
        canvas.drawBitmap(bmp, bitmapSrcRect, bitmapDstRect, null)
        scrimPaint.color = theme.backgroundColor
        scrimPaint.alpha = 140
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
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

    private fun drawSwipeTrail(canvas: Canvas) {
        val path = swipePath ?: return
        if (!isSwiping || path.size < 2) return
        swipeTrailPaint.color = theme.accentColor
        val trail = Path()
        path.forEachIndexed { i, kb ->
            if (i == 0) trail.moveTo(kb.rect.centerX(), kb.rect.centerY())
            else trail.lineTo(kb.rect.centerX(), kb.rect.centerY())
        }
        canvas.drawPath(trail, swipeTrailPaint)
    }

    private fun drawRestoreButton(canvas: Canvas) {
        val r = restoreButtonRect ?: return
        keyPaint.color = theme.specialKeyColor
        canvas.drawRoundRect(r, 16f, 16f, keyPaint)
        drawText(canvas, r, "\u21C6", small = true)
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

    private fun isLetterKey(kb: KeyBounds) =
        kb.key.type == KeyType.CHARACTER && kb.key.label.length == 1 && kb.key.label[0].isLetter()

    private fun handleDown(event: MotionEvent) {
        val restoreRect = restoreButtonRect
        if (restoreRect != null && restoreRect.contains(event.x, event.y)) {
            onOneHandedExit?.invoke()
            return
        }

        val kb = findKeyAt(event.x, event.y) ?: return
        pressedKey = kb
        longPressTriggered = false
        downX = event.x
        downY = event.y
        invalidate()

        spaceDragActive = kb.key.type == KeyType.SPACE
        spaceDragMoved = false
        spaceDragLastStepX = event.x

        backspaceDragActive = kb.key.type == KeyType.BACKSPACE
        backspaceWordModeTriggered = false
        backspaceDragLastStepX = event.x

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
        } else if (kb.key.type == KeyType.SPACE) {
            val r = Runnable {
                longPressTriggered = true
                listener?.onSpaceLongPress()
            }
            longPressRunnable = r
            handler.postDelayed(r, 350)
        }

        swipePath = if (swipeEligible && swipeTypingEnabled && isLetterKey(kb)) mutableListOf(kb) else null
        isSwiping = false
    }

    private fun handleMove(event: MotionEvent) {
        if (longPressTriggered && popupWindow?.isShowing == true) {
            updatePopupSelection(event.rawX)
            return
        }

        if (spaceDragActive) {
            val movedDist = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
            if (movedDist > SWIPE_START_THRESHOLD_PX) {
                longPressRunnable?.let { handler.removeCallbacks(it) }
            }
            while (event.x - spaceDragLastStepX >= CURSOR_STEP_PX) {
                listener?.onCursorMove(1)
                spaceDragLastStepX += CURSOR_STEP_PX
                spaceDragMoved = true
            }
            while (event.x - spaceDragLastStepX <= -CURSOR_STEP_PX) {
                listener?.onCursorMove(-1)
                spaceDragLastStepX -= CURSOR_STEP_PX
                spaceDragMoved = true
            }
        }

        if (backspaceDragActive) {
            val dragLeft = downX - event.x
            if (!backspaceWordModeTriggered && dragLeft > WORD_DELETE_TRIGGER_PX) {
                backspaceWordModeTriggered = true
                repeatRunnable?.let { handler.removeCallbacks(it) }
                listener?.onDeleteWord()
                backspaceDragLastStepX = event.x
            } else if (backspaceWordModeTriggered) {
                while (backspaceDragLastStepX - event.x >= WORD_DELETE_STEP_PX) {
                    listener?.onDeleteWord()
                    backspaceDragLastStepX -= WORD_DELETE_STEP_PX
                }
            }
        }

        val path = swipePath
        if (path != null) {
            val moved = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
            if (moved > SWIPE_START_THRESHOLD_PX) {
                longPressRunnable?.let { handler.removeCallbacks(it) }
            }
            val kb = findKeyAt(event.x, event.y)
            if (kb != null && isLetterKey(kb) && kb.key != path.last().key) {
                path.add(kb)
                pressedKey = kb
                if (path.size >= 2) isSwiping = true
                invalidate()
            }
        }
    }

    private fun handleUp() {
        val kb = pressedKey
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }

        if (isSwiping) {
            val letters = swipePath.orEmpty().mapNotNull { it.key.label.firstOrNull() }
            if (letters.size >= 2) listener?.onSwipeGesture(letters)
        } else if (longPressTriggered) {
            val chars = kb?.key?.longPress ?: emptyList()
            if (kb != null && chars.isNotEmpty() && popupSelectedIndex in chars.indices) {
                listener?.onKey(kb.key, chars[popupSelectedIndex])
            }
            dismissPopup()
        } else if (kb != null && kb.key.type == KeyType.SPACE && spaceDragMoved) {
            // Consumed as a cursor-move gesture -- don't also insert a space.
        } else if (kb != null && kb.key.type != KeyType.BACKSPACE) {
            fireKey(kb.key)
        }
        cancelPress()
    }

    private fun cancelPress() {
        pressedKey = null
        longPressTriggered = false
        swipePath = null
        isSwiping = false
        spaceDragActive = false
        spaceDragMoved = false
        backspaceDragActive = false
        backspaceWordModeTriggered = false
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

    companion object {
        private const val ONE_HANDED_FRACTION = 0.74f
        private const val SPLIT_GAP_FRACTION = 0.14f
        private const val SWIPE_START_THRESHOLD_PX = 24f
        private const val CURSOR_STEP_PX = 24f
        private const val WORD_DELETE_TRIGGER_PX = 50f
        private const val WORD_DELETE_STEP_PX = 45f
    }
}
