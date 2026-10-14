package io.github.rahmanfaisal653.layboard.mouse

import io.github.rahmanfaisal653.layboard.hid.HidReports
import io.github.rahmanfaisal653.layboard.hid.Scheduler
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Turns the fingers on the Mouse into pointer moves and scrolls, like a touchpad: one finger moves the
 * pointer, two or more scroll by the travel of their middle. The mode follows the number of fingers
 * live, and every change of that number starts again from where the fingers are now, so a finger
 * landing or lifting never makes the pointer jump or scrolls by itself. Nothing here ever clicks.
 *
 * Scrolling is natural, as on a phone: fingers up scroll the content up, which is a negative wheel,
 * and fingers left scroll it left, which is a positive pan.
 *
 * After a quick two-finger flick the scroll glides on in the same direction, slowing to a stop, like a
 * laptop touchpad. Touching the Mouse again, [cancel] or [stopGlide] ends it at once. Time comes only
 * from the timestamps given to [update] and from [scheduler], so tests drive it with a fake clock.
 */
class MouseGesture(
    private val density: Float,
    /** Paces the glide's steps. */
    private val scheduler: Scheduler,
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

    // Where the middle of the fingers has been while scrolling, newest last, to tell how fast they went.
    private class Sample(val timeMs: Long, val x: Float, val y: Float)

    private val samples = mutableListOf<Sample>()

    // How fast the fingers were going, in pixels per ms, when they began to lift at [flickTimeMs]; [flicked] while that was quick enough to glide.
    private var flickX = 0f
    private var flickY = 0f
    private var flickTimeMs = 0L
    private var flicked = false

    // Each glide has its own number, so the steps a stopped glide left scheduled find out they are stale.
    private var glideId = 0
    private var glideX = 0f
    private var glideY = 0f

    /**
     * Feeds the positions, in screen pixels, of every finger on the Mouse now; empty once the last one
     * lifts. [timeMs] is when they were there, on any clock that only runs forward.
     */
    fun update(fingers: List<Pair<Float, Float>>, timeMs: Long) {
        stopGlide()
        if (fingers.isEmpty()) {
            if (fingerCount >= 2) noteFlick(timeMs)
            if (flicked && timeMs - flickTimeMs <= LIFT_TOGETHER_MS) startGlide()
            flicked = false
            fingerCount = 0
            samples.clear()
            return
        }
        val x = fingers.sumOf { it.first.toDouble() }.toFloat() / fingers.size
        val y = fingers.sumOf { it.second.toDouble() }.toFloat() / fingers.size
        if (fingers.size != fingerCount) {
            // A flick is only over when the last finger lifts; fingers on again or more of them cancel it.
            if (fingerCount >= 2 && fingers.size < fingerCount) noteFlick(timeMs) else flicked = false
            fingerCount = fingers.size
            pointer.reset()
            scroll.reset()
            samples.clear()
        } else if (fingerCount == 1) {
            pointer.move(x - lastX, y - lastY)?.let { (dx, dy) ->
                flicked = false
                onMove(dx, dy)
            }
        } else {
            scroll.move(x - lastX, y - lastY)?.let { (dx, dy) -> onScroll(dy, -dx) }
        }
        if (fingerCount >= 2) {
            samples += Sample(timeMs, x, y)
            // Only the last moments say how fast the fingers are going.
            samples.removeAll { timeMs - it.timeMs > VELOCITY_WINDOW_MS }
        }
        lastX = x
        lastY = y
    }

    /** The touch was taken away, not lifted: nothing glides. */
    fun cancel() {
        stopGlide()
        flicked = false
        fingerCount = 0
        samples.clear()
    }

    /** Ends a glide at once; nothing more is sent for it. */
    fun stopGlide() {
        glideId++
    }

    // Fingers scrolling at [timeMs] start to lift: remembers how fast they were going, if they were quick.
    private fun noteFlick(timeMs: Long) {
        flicked = false
        val last = samples.lastOrNull() ?: return
        if (timeMs - last.timeMs > PAUSE_MS) return
        val first = samples.first { last.timeMs - it.timeMs <= VELOCITY_WINDOW_MS }
        val span = last.timeMs - first.timeMs
        if (span <= 0) return
        flickX = (last.x - first.x) / span
        flickY = (last.y - first.y) / span
        flickTimeMs = timeMs
        flicked = speedInDp(flickX, flickY) >= MIN_FLICK_DP_PER_MS
    }

    private fun speedInDp(pxPerMsX: Float, pxPerMsY: Float) = hypot(pxPerMsX, pxPerMsY) / density

    private fun startGlide() {
        val speed = speedInDp(flickX, flickY)
        val scale = if (speed > MAX_FLICK_DP_PER_MS) MAX_FLICK_DP_PER_MS / speed else 1f
        glideX = flickX * scale
        glideY = flickY * scale
        scroll.reset()
        val id = ++glideId
        scheduler.postDelayed(STEP_MS) { glideStep(id) }
    }

    private fun glideStep(id: Int) {
        if (id != glideId) return
        scroll.move(glideX * STEP_MS, glideY * STEP_MS)?.let { (dx, dy) -> onScroll(dy, -dx) }
        val slowing = exp(-STEP_MS / SLOWING_TIME_MS).toFloat()
        glideX *= slowing
        glideY *= slowing
        if (speedInDp(glideX, glideY) < STOP_DP_PER_MS) return
        scheduler.postDelayed(STEP_MS) { glideStep(id) }
    }

    companion object {
        /** Scroll units per dp of two-finger travel, a tenth of a step; fixed until Scroll Speed arrives. */
        const val SCROLL_UNITS_PER_DP = 0.1f * HidReports.SCROLL_UNITS_PER_STEP

        /** The glide sends a step this often. */
        private const val STEP_MS = 16L

        /** The glide's speed falls to a third each this long. */
        private const val SLOWING_TIME_MS = 325.0

        /** Slower than this, in dp per ms, the glide has come to a stop. */
        private const val STOP_DP_PER_MS = 0.05f

        /** Fingers must lift at least this fast, in dp per ms, to glide: slower moves are steady scrolling. */
        private const val MIN_FLICK_DP_PER_MS = 0.4f

        /** The glide never starts faster than this, in dp per ms, however hard the flick. */
        private const val MAX_FLICK_DP_PER_MS = 6f

        /** How far back from the lift, in ms, the fingers' speed is measured. */
        private const val VELOCITY_WINDOW_MS = 100L

        /** Fingers that stopped this long, in ms, before lifting were paused, not flicked. */
        private const val PAUSE_MS = 80L

        /** The last finger must lift within this many ms of the first one lifting for the two to count as one lift. */
        private const val LIFT_TOGETHER_MS = 120L
    }
}
