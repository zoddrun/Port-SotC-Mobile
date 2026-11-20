package com.portsotc.mobile.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Multi-touch PlayStation 2 virtual controller.
 *
 * The view owns no game state: it translates pointers into [PadState] calls.
 * It supports a direct-play mode and an edit mode used by the touch layout
 * editor to move and resize individual controls.
 *
 * Each pointer is tracked by its Android pointer id, so releasing one finger
 * never cancels a control that another finger is holding.
 *
 * Scope: this is the launcher/diagnostics overlay. Gameplay input and the
 * in-game sigil overlay are owned by the recompiled runner (D18, patch 0005);
 * changes here never alter what a game session receives.
 */
class TouchControllerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onControlSelected(control: TouchControl)
        fun onControlMoved(control: TouchControl)
    }

    var layout: TouchLayout = TouchLayout.defaults()
        set(value) {
            field = value
            invalidate()
        }

    var padState: PadState? = null

    var editMode: Boolean = false
        set(value) {
            field = value
            pointers.clear()
            dragging = false
            invalidate()
        }

    var controlsVisible: Boolean = true
        set(value) {
            field = value
            if (!value) pointers.clear()
            invalidate()
        }

    var globalAlpha: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var globalScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.6f, 1.6f)
            invalidate()
        }

    /** Multiplier applied to the right (camera) stick. */
    var lookSensitivity: Float = 1f
        set(value) {
            field = value.coerceIn(0.4f, 2f)
        }

    var invertLookX: Boolean = false
    var invertLookY: Boolean = false

    /** Radial deadzone applied to both virtual sticks. */
    var stickDeadzone: Float = 0.08f
        set(value) {
            field = value.coerceIn(0f, 0.3f)
        }

    var listener: Listener? = null

    val selectedControl: TouchControl?
        get() = selectedId?.let { layout.byId(it) }

    private data class PointerState(val controlId: String, var x: Float, var y: Float)

    private val pointers = HashMap<Int, PointerState>()
    private var selectedId: String? = null
    private var dragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#C9B27A")
    }
    private val arrowPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val minDimension = min(width, height).toFloat()
        if (!controlsVisible && !editMode) return
        for (control in layout.controls) {
            if (control.hidden) continue
            drawControl(canvas, control, minDimension)
        }
    }

    private fun drawControl(canvas: Canvas, control: TouchControl, minDimension: Float) {
        val centerX = control.centerX * width
        val centerY = control.centerY * height
        val radius = (control.radius * minDimension * globalScale).coerceAtLeast(dp(18f))
        val displayedAlpha = (control.alpha * globalAlpha).coerceIn(0f, 1f)
        if (displayedAlpha <= 0.01f) return

        val pressed = isPressed(control)
        val fillColor = if (pressed) {
            alphaColor(displayedAlpha * 0.22f, 0xC9, 0xB2, 0x7A)
        } else {
            alphaColor(displayedAlpha * 0.42f, 0x0B, 0x0F, 0x13)
        }
        val edgeColor = if (pressed) {
            alphaColor(displayedAlpha, 0xC9, 0xB2, 0x7A)
        } else {
            alphaColor(displayedAlpha * 0.62f, 0xE8, 0xE1, 0xD2)
        }
        fillPaint.color = fillColor
        strokePaint.color = edgeColor
        strokePaint.strokeWidth = dp(if (pressed) 2.2f else 1.4f)

        when (control.kind) {
            TouchControlKind.BUTTON -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)

                // Carved stone tablet: a thin inner rim plus a cyan sigil glow
                // only while the control is actually pressed.
                strokePaint.color = alphaColor(displayedAlpha * 0.30f, 0xC9, 0xB2, 0x7A)
                strokePaint.strokeWidth = dp(1f)
                canvas.drawCircle(centerX, centerY, radius * 0.80f, strokePaint)
                if (pressed) {
                    strokePaint.color = alphaColor(displayedAlpha * 0.75f, 0x38, 0xBD, 0xF8)
                    strokePaint.strokeWidth = dp(1.5f)
                    canvas.drawCircle(centerX, centerY, radius * 1.06f, strokePaint)
                }

                val symbolColor = if (pressed) {
                    alphaColor(displayedAlpha, 0x38, 0xBD, 0xF8)
                } else {
                    alphaColor(displayedAlpha * 0.88f, 0xE8, 0xE1, 0xD2)
                }
                drawPsSymbol(canvas, control.label, centerX, centerY, radius, symbolColor, displayedAlpha)
            }

            TouchControlKind.STICK -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
                strokePaint.color = alphaColor(displayedAlpha * 0.22f, 0xE8, 0xE1, 0xD2)
                strokePaint.strokeWidth = dp(1f)
                canvas.drawCircle(centerX, centerY, radius * 0.66f, strokePaint)

                var knobX = centerX
                var knobY = centerY
                val state = pointerFor(control.id)
                if (state != null) {
                    val magnitude = hypot(state.x - centerX, state.y - centerY)
                    if (magnitude > 0.001f) {
                        val limit = radius * 0.62f
                        val factor = min(magnitude, limit) / magnitude
                        knobX = centerX + (state.x - centerX) * factor
                        knobY = centerY + (state.y - centerY) * factor
                    }
                }
                val knobRadius = radius * 0.36f
                fillPaint.color = alphaColor(
                    displayedAlpha * if (pressed) 0.85f else 0.55f,
                    0xC9,
                    0xB2,
                    0x7A
                )
                canvas.drawCircle(knobX, knobY, knobRadius, fillPaint)
                strokePaint.color = alphaColor(displayedAlpha * 0.8f, 0xE8, 0xE1, 0xD2)
                strokePaint.strokeWidth = dp(1.2f)
                canvas.drawCircle(knobX, knobY, knobRadius, strokePaint)
            }

            TouchControlKind.DPAD -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
                val directions = dpadDirections(control)
                val inner = radius * 0.18f
                val outer = radius * 0.78f
                val halfWidth = radius * 0.2f
                drawArrow(canvas, centerX, centerY, 0f, -1f, inner, outer, halfWidth, directions.up, edgeColor)
                drawArrow(canvas, centerX, centerY, 1f, 0f, inner, outer, halfWidth, directions.right, edgeColor)
                drawArrow(canvas, centerX, centerY, 0f, 1f, inner, outer, halfWidth, directions.down, edgeColor)
                drawArrow(canvas, centerX, centerY, -1f, 0f, inner, outer, halfWidth, directions.left, edgeColor)
            }
        }

        if (editMode && selectedId == control.id) {
            canvas.drawCircle(centerX, centerY, radius + dp(6f), accentPaint)
        }
    }

    /**
     * Dispatches to the correct PS geometric symbol renderer based on the control label.
     * Falls back to drawing the label text for unrecognized labels.
     */
    private fun drawPsSymbol(
        canvas: Canvas,
        label: String,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        alpha: Float
    ) {
        val symPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeCap = Paint.Cap.ROUND
        }
        when (label) {
            "△", "T" -> {
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(2f)
                drawTriangle(canvas, cx, cy, radius, symPaint)
            }
            "○", "O" -> {
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(2f)
                drawCircleSymbol(canvas, cx, cy, radius, symPaint)
            }
            "✕", "X" -> {
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(2.2f)
                drawCross(canvas, cx, cy, radius, symPaint)
            }
            "□", "S" -> {
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(2f)
                drawSquare(canvas, cx, cy, radius, symPaint)
            }
            "R1", "L1" -> {
                // Double ring with label text
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(1.5f)
                canvas.drawCircle(cx, cy, radius * 0.60f, symPaint)
                canvas.drawCircle(cx, cy, radius * 0.40f, symPaint)
                textPaint.color = color
                textPaint.textSize = radius * 0.36f
                val metrics = textPaint.fontMetrics
                val baseline = cy - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(label, cx, baseline, textPaint)
            }
            "L2", "R2" -> {
                // Pill shape with label text
                symPaint.style = Paint.Style.STROKE
                symPaint.strokeWidth = dp(1.5f)
                val pillW = radius * 0.60f
                val pillH = radius * 0.36f
                val rx = pillW * 0.5f
                canvas.drawRoundRect(
                    cx - pillW * 0.5f, cy - pillH * 0.5f,
                    cx + pillW * 0.5f, cy + pillH * 0.5f,
                    rx, rx, symPaint
                )
                textPaint.color = color
                textPaint.textSize = radius * 0.36f
                val metrics = textPaint.fontMetrics
                val baseline = cy - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(label, cx, baseline, textPaint)
            }
            else -> {
                // Fallback: draw label text
                textPaint.color = color
                textPaint.textSize = radius * 0.6f
                val metrics = textPaint.fontMetrics
                val baseline = cy - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(label, cx, baseline, textPaint)
            }
        }
    }

    private fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path()
        val h = r * 0.80f
        path.moveTo(cx, cy - h)                        // top apex
        path.lineTo(cx - h * 0.87f, cy + h * 0.5f)    // bottom-left
        path.lineTo(cx + h * 0.87f, cy + h * 0.5f)    // bottom-right
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCircleSymbol(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        canvas.drawCircle(cx, cy, r * 0.32f, paint)
    }

    private fun drawCross(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val arm = r * 0.32f
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, paint)
        canvas.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, paint)
    }

    private fun drawSquare(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val half = r * 0.26f
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
    }

    private fun drawArrow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        directionX: Float,
        directionY: Float,
        inner: Float,
        outer: Float,
        halfWidth: Float,
        pressed: Boolean,
        color: Int
    ) {
        val tipX = centerX + directionX * outer
        val tipY = centerY + directionY * outer
        val baseX = centerX + directionX * inner
        val baseY = centerY + directionY * inner
        val perpendicularX = -directionY
        val perpendicularY = directionX
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(
            baseX + perpendicularX * halfWidth,
            baseY + perpendicularY * halfWidth
        )
        arrowPath.lineTo(
            baseX - perpendicularX * halfWidth,
            baseY - perpendicularY * halfWidth
        )
        arrowPath.close()
        fillPaint.color = if (pressed) {
            Color.argb(230, 0xC9, 0xB2, 0x7A)
        } else {
            color
        }
        canvas.drawPath(arrowPath, fillPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) {
            return handleEditTouch(event)
        }
        if (!controlsVisible) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> addPointer(event.getPointerId(0), event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                addPointer(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index)
                    val pointer = pointers[id] ?: continue
                    pointer.x = event.getX(index)
                    pointer.y = event.getY(index)
                    val control = layout.byId(pointer.controlId) ?: continue
                    if (control.kind == TouchControlKind.BUTTON &&
                        !isInside(control, pointer.x, pointer.y, 1.7f)
                    ) {
                        pointers.remove(id)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                pointers.remove(event.getPointerId(index))
            }
            MotionEvent.ACTION_CANCEL -> pointers.clear()
        }
        syncPadState()
        invalidate()
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetInput()
    }

    override fun onDetachedFromWindow() {
        resetInput()
        super.onDetachedFromWindow()
    }

    /** Releases every virtual button. Called on pause, focus loss, and teardown. */
    fun resetInput() {
        pointers.clear()
        dragging = false
        syncPadState()
        invalidate()
    }

    private fun addPointer(id: Int, x: Float, y: Float) {
        val control = controlAt(x, y) ?: return
        pointers[id] = PointerState(control.id, x, y)
        if (control.kind == TouchControlKind.BUTTON) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val control = controlAt(event.x, event.y) ?: return false
                selectedId = control.id
                dragging = true
                dragOffsetX = event.x - control.centerX * width
                dragOffsetY = event.y - control.centerY * height
                listener?.onControlSelected(control)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val control = selectedControl ?: return false
                control.centerX = ((event.x - dragOffsetX) / width).coerceIn(0.02f, 0.98f)
                control.centerY = ((event.y - dragOffsetY) / height).coerceIn(0.02f, 0.98f)
                listener?.onControlMoved(control)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return dragging
    }

    private fun syncPadState() {
        val pad = padState ?: return
        for (control in layout.controls) {
            when (control.kind) {
                TouchControlKind.BUTTON -> {
                    val pressed = pointers.values.any { it.controlId == control.id }
                    pad.setTouchButton(control.buttonMask, pressed)
                }
                TouchControlKind.STICK -> {
                    val pointer = pointerFor(control.id)
                    if (pointer == null) {
                        pad.setTouchStick(control.stickLeft, 0f, 0f)
                    } else {
                        val minDimension = min(width, height).toFloat()
                        val radius = (control.radius * minDimension * globalScale)
                            .coerceAtLeast(dp(18f))
                        var dx = (pointer.x - control.centerX * width) / radius
                        var dy = (pointer.y - control.centerY * height) / radius
                        val magnitude = hypot(dx, dy)
                        if (magnitude > 1f) {
                            dx /= magnitude
                            dy /= magnitude
                        }
                        val shaped = applyDeadzone(dx, dy)
                        dx = shaped.first
                        dy = shaped.second
                        if (!control.stickLeft) {
                            dx = (dx * lookSensitivity).coerceIn(-1f, 1f)
                            dy = (dy * lookSensitivity).coerceIn(-1f, 1f)
                            if (invertLookX) dx = -dx
                            if (invertLookY) dy = -dy
                        }
                        pad.setTouchStick(control.stickLeft, dx, dy)
                    }
                }
                TouchControlKind.DPAD -> {
                    val directions = dpadDirections(control)
                    pad.setTouchDpad(
                        up = directions.up,
                        right = directions.right,
                        down = directions.down,
                        left = directions.left
                    )
                }
            }
        }
    }

    /** Radial deadzone with rescaling, so small drift does not move the stick. */
    private fun applyDeadzone(x: Float, y: Float): Pair<Float, Float> {
        val magnitude = hypot(x, y)
        if (magnitude <= stickDeadzone) return 0f to 0f
        val scaled = ((magnitude - stickDeadzone) / (1f - stickDeadzone)).coerceIn(0f, 1f)
        return (x / magnitude * scaled) to (y / magnitude * scaled)
    }

    private fun isPressed(control: TouchControl): Boolean = when (control.kind) {
        TouchControlKind.BUTTON -> pointers.values.any { it.controlId == control.id }
        TouchControlKind.STICK -> pointerFor(control.id) != null
        TouchControlKind.DPAD -> {
            val directions = dpadDirections(control)
            directions.up || directions.down || directions.right || directions.left
        }
    }

    private fun dpadDirections(control: TouchControl): DpadDirections {
        val pointer = pointerFor(control.id) ?: return DpadDirections(false, false, false, false)
        val minDimension = min(width, height).toFloat()
        val radius = (control.radius * minDimension * globalScale).coerceAtLeast(dp(18f))
        val dx = (pointer.x - control.centerX * width) / radius
        val dy = (pointer.y - control.centerY * height) / radius
        if (hypot(dx, dy) < 0.3f) return DpadDirections(false, false, false, false)
        return DpadDirections(
            up = dy < -0.35f,
            down = dy > 0.35f,
            right = dx > 0.35f,
            left = dx < -0.35f
        )
    }

    private fun pointerFor(controlId: String): PointerState? =
        pointers.values.lastOrNull { it.controlId == controlId }

    /**
     * Finds the control under a point. When hit areas overlap, the control
     * whose center is nearest in normalized terms wins, so a tap on the middle
     * of one button never activates a neighbouring one.
     */
    private fun controlAt(x: Float, y: Float): TouchControl? {
        val minDimension = min(width, height).toFloat()
        var best: TouchControl? = null
        var bestDistance = Float.MAX_VALUE
        for (control in layout.controls) {
            if (control.hidden) continue
            val factor = if (control.kind == TouchControlKind.BUTTON) 1.15f else 1f
            val radius =
                (control.radius * minDimension * globalScale).coerceAtLeast(dp(18f)) * factor
            val dx = x - control.centerX * width
            val dy = y - control.centerY * height
            val distance = hypot(dx, dy)
            if (distance > radius) continue
            val normalized = distance / radius
            if (normalized < bestDistance) {
                best = control
                bestDistance = normalized
            }
        }
        return best
    }

    private fun isInside(control: TouchControl, x: Float, y: Float, factor: Float): Boolean {
        val minDimension = min(width, height).toFloat()
        val radius = (control.radius * minDimension * globalScale).coerceAtLeast(dp(18f)) * factor
        val dx = x - control.centerX * width
        val dy = y - control.centerY * height
        return dx * dx + dy * dy <= radius * radius
    }

    private fun alphaColor(alpha: Float, red: Int, green: Int, blue: Int): Int =
        Color.argb((alpha * 255f).toInt().coerceIn(0, 255), red, green, blue)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class DpadDirections(
        val up: Boolean,
        val down: Boolean,
        val right: Boolean,
        val left: Boolean
    )
}
