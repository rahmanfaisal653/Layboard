package io.github.rahmanfaisal653.layboard.mouse

import io.github.rahmanfaisal653.layboard.hid.HidReports

/**
 * Turns the fingers on the Mouse into pointer moves and scrolls, like a touchpad: one finger moves the
 * pointer, two or more scroll by the travel of their middle. The mode follows the number of fingers
 * live, and every change of that number starts again from where the fingers are now, so a finger
 * landing or lifting never makes the pointer jump or scrolls by itself. Nothing here ever clicks.
 *
 * Scrolling is natural, as on a phone: fingers up scroll the content up, which is a negative wheel,
 * and fingers left scroll it left, which is a positive pan.
 */
class MouseGesture(
    density: Float,
    /** Receives whole-pixel pointer deltas for the Host. */
    private val onMove: (dx: Int, dy: Int) -> Unit,
    /** Receives whole scroll units for the Host, [HidReports.SCROLL_UNITS_PER_STEP] to a wheel or pan step. */
    private val onScroll: (wheel: Int, pan: Int) -> Unit,
) {
    private val pointer = PointerMotion(density)
    private val scroll = PointerMotion(density, SCROLL_UNITS_PER_DP)

    /** Pointer Speed multiplier; see [PointerMotion.speed]. */
    var pointerSpeed: Float
        get() = pointer.speed
        set(value) {
            pointer.speed = value
        }

    private var fingerCount = 0
    private var lastX = 0f
    private var lastY = 0f

    /** Feeds the positions, in screen pixels, of every finger on the Mouse now; empty once the last one lifts. */
    fun update(fingers: List<Pair<Float, Float>>) {
        if (fingers.isEmpty()) {
            fingerCount = 0
            return
        }
        val x = fingers.sumOf { it.first.toDouble() }.toFloat() / fingers.size
        val y = fingers.sumOf { it.second.toDouble() }.toFloat() / fingers.size
        if (fingers.size != fingerCount) {
            fingerCount = fingers.size
            pointer.reset()
            scroll.reset()
        } else if (fingerCount == 1) {
            pointer.move(x - lastX, y - lastY)?.let { (dx, dy) -> onMove(dx, dy) }
        } else {
            scroll.move(x - lastX, y - lastY)?.let { (dx, dy) -> onScroll(dy, -dx) }
        }
        lastX = x
        lastY = y
    }

    companion object {
        /** Scroll units per dp of two-finger travel, a tenth of a step; fixed until Scroll Speed arrives. */
        const val SCROLL_UNITS_PER_DP = 0.1f * HidReports.SCROLL_UNITS_PER_STEP
    }
}
