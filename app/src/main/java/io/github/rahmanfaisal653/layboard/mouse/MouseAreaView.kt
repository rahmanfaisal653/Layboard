package io.github.rahmanfaisal653.layboard.mouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.github.rahmanfaisal653.layboard.R
import io.github.rahmanfaisal653.layboard.hid.HandlerScheduler

/**
 * The Mouse: one finger moves the pointer, two scroll, like a touchpad. It never clicks, not even on
 * a tap; clicking is the job of the Click Buttons. A quick two-finger flick keeps scrolling after the
 * fingers lift. What the fingers mean is [MouseGesture]'s job.
 */
class MouseAreaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Receives whole-pixel pointer deltas for the Host. */
    var onMove: ((dx: Int, dy: Int) -> Unit)? = null

    /** Receives whole scroll units for the Host, as [MouseGesture] does. */
    var onScroll: ((wheel: Int, pan: Int) -> Unit)? = null

    private val gesture = MouseGesture(
        resources.displayMetrics.density,
        HandlerScheduler(Handler(Looper.getMainLooper())),
        onMove = { dx, dy -> onMove?.invoke(dx, dy) },
        onScroll = { wheel, pan -> onScroll?.invoke(wheel, pan) },
    )

    /** Pointer Speed multiplier; see [PointerMotion.speed]. */
    var speed: Float
        get() = gesture.pointerSpeed
        set(value) {
            gesture.pointerSpeed = value
        }

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

    /** Ends any scroll still gliding after the fingers lifted, as when the Input Screen is left. */
    fun stopGlide() = gesture.stopGlide()

    override fun onDetachedFromWindow() {
        gesture.stopGlide()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gesture.cancel()
            return true
        }
        val fingers = when (event.actionMasked) {
            MotionEvent.ACTION_UP -> emptyList()
            // The finger lifting is still in the event that reports it, so it is left out.
            MotionEvent.ACTION_POINTER_UP -> fingersExcept(event, event.actionIndex)
            else -> fingersExcept(event, NONE)
        }
        gesture.update(fingers, event.eventTime)
        return true
    }

    private fun fingersExcept(event: MotionEvent, skipped: Int) =
        (0 until event.pointerCount).filter { it != skipped }.map { event.getX(it) to event.getY(it) }

    private companion object {
        const val NONE = -1
    }
}
