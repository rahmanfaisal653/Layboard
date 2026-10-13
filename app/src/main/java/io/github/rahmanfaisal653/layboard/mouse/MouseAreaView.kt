package io.github.rahmanfaisal653.layboard.mouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.github.rahmanfaisal653.layboard.R

/**
 * The Mouse: turns finger movement into pointer movement. It never clicks and never scrolls;
 * clicking is the job of the Click Buttons.
 */
class MouseAreaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Receives whole-pixel pointer deltas for the Host. */
    var onMove: ((dx: Int, dy: Int) -> Unit)? = null

    private val motion = PointerMotion(resources.displayMetrics.density)

    /** Pointer Speed multiplier; see [PointerMotion.speed]. */
    var speed: Float
        get() = motion.speed
        set(value) {
            motion.speed = value
        }

    private var activePointer = INVALID_POINTER
    private var lastX = 0f
    private var lastY = 0f

    private val hintText = context.getString(R.string.mouse_hint)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            resources.displayMetrics,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val baseline = height / 2f - (hintPaint.ascent() + hintPaint.descent()) / 2f
        canvas.drawText(hintText, width / 2f, baseline, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                motion.reset()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index >= 0) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    motion.move(x - lastX, y - lastY)?.let { (dx, dy) -> onMove?.invoke(dx, dy) }
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) activePointer = INVALID_POINTER
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activePointer = INVALID_POINTER
        }
        return true
    }

    private companion object {
        const val INVALID_POINTER = -1
    }
}
