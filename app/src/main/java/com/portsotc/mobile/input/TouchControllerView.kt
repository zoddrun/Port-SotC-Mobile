package com.portsotc.mobile.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
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
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
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
        val fillColor = alphaColor(displayedAlpha * if (pressed) 0.34f else 0.16f, 0xE8, 0xE1, 0xD2)
        val edgeColor = alphaColor(displayedAlpha * if (pressed) 1f else 0.72f, 0xE8, 0xE1, 0xD2)
        fillPaint.color = fillColor
        strokePaint.color = edgeColor
        strokePaint.strokeWidth = dp(if (pressed) 2.5f else 2f)

        when (control.kind) {
            TouchControlKind.BUTTON -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
                textPaint.color = alphaColor(displayedAlpha, 0xE8, 0xE1, 0xD2)
                textPaint.textSize = radius * 0.62f
                val metrics = textPaint.fontMetrics
                val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(control.label, centerX, baseline, textPaint)
            }

            TouchControlKind.STICK -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
                strokePaint.color = alphaColor(displayedAlpha * 0.35f, 0xE8, 0xE1, 0xD2)
                canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, strokePaint)
                canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, strokePaint)

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
                val knobRadius = radius * 0.4f
                fillPaint.color = alphaColor(displayedAlpha * 0.9f, 0xC9, 0xB2, 0x7A)
                canvas.drawCircle(knobX, knobY, knobRadius, fillPaint)
                strokePaint.color = alphaColor(displayedAlpha, 0xE8, 0xE1, 0xD2)
                canvas.drawCircle(knobX, knobY, knobRadius, strokePaint)
            }

            TouchControlKind.DPAD -> {
                canvas.drawCircle(centerX, centerY, radius, fillPaint)
                canvas.drawCircle(centerX, centerY, radius, strokePaint)
                val directions = dpadDirections(control)
                val inner = radius * 0.18f
                val outer = radius * 0.8f
                val halfWidth = radius * 0.22f
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
                    if (control.kind == TouchControlKind.BUTTON && !isInside(control, pointer.x, pointer.y, 1.7f)) {
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

    private fun addPointer(id: Int, x: Float, y: Float) {
        val control = controlAt(x, y) ?: return
        pointers[id] = PointerState(control.id, x, y)
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
                        val radius = (control.radius * minDimension * globalScale).coerceAtLeast(dp(18f))
                        var dx = (pointer.x - control.centerX * width) / radius
                        var dy = (pointer.y - control.centerY * height) / radius
                        val magnitude = hypot(dx, dy)
                        if (magnitude > 1f) {
                            dx /= magnitude
                            dy /= magnitude
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
        pointers.values.firstOrNull { it.controlId == controlId }

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
