package io.github.rahmanfaisal653.layboard.mouse

/**
 * Turns finger travel on the Mouse, in screen pixels, into whole Host units scaled by a speed: pointer
 * pixels by Pointer Speed, or scroll steps when [unitsPerDp] is the scroll scale. The fraction a move
 * leaves over is carried into the next: at every point of a slide the Host has been sent exactly the
 * whole-unit part of the finger's scaled travel so far.
 */
class PointerMotion(private val density: Float, private val unitsPerDp: Float = BASE_PIXELS_PER_DP) {
    /** Speed multiplier; 1.0 moves the Host [unitsPerDp] units per dp of finger travel. */
    var speed = 1f

    // The slide's scaled travel so far, and how much of it has been sent.
    private var travelX = 0.0
    private var travelY = 0.0
    private var sentX = 0
    private var sentY = 0

    /** Starts a new slide, forgetting the last one's leftover fraction. */
    fun reset() {
        travelX = 0.0
        travelY = 0.0
        sentX = 0
        sentY = 0
    }

    /** Adds a finger move and returns the whole pixels to send now, or null when it adds up to none yet. */
    fun move(deltaX: Float, deltaY: Float): Pair<Int, Int>? {
        val scale = speed.toDouble() * unitsPerDp / density
        travelX += deltaX * scale
        travelY += deltaY * scale
        val moveX = travelX.toInt() - sentX
        val moveY = travelY.toInt() - sentY
        if (moveX == 0 && moveY == 0) return null
        sentX += moveX
        sentY += moveY
        return moveX to moveY
    }

    companion object {
        const val BASE_PIXELS_PER_DP = 2f
    }
}
