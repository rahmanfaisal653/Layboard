package io.github.rahmanfaisal653.layboard.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PointerMotionTest {
    private val motion = PointerMotion(density = 1f)

    /** Feeds [deltas] as one finger slide and returns the total pointer movement sent. */
    private fun slide(deltas: List<Pair<Float, Float>>): Pair<Int, Int> {
        motion.reset()
        var sentX = 0
        var sentY = 0
        deltas.forEach { (dx, dy) ->
            motion.move(dx, dy)?.let { (x, y) ->
                sentX += x
                sentY += y
            }
        }
        return sentX to sentY
    }

    @Test
    fun theMovementSentAddsUpToTheScaledFingerTravelIncludingCarriedFractions() {
        // At speed 1 and density 1 each finger pixel is BASE_PIXELS_PER_DP Host pixels; quarters stay exact.
        val deltas = listOf(0.25f to -0.25f, 0.125f to 0.5f, 1.5f to -2.75f, 0.125f to 0.25f, 3f to 0f)

        val sent = slide(deltas)

        val scale = PointerMotion.BASE_PIXELS_PER_DP
        assertEquals((deltas.sumOf { it.first.toDouble() } * scale).toInt(), sent.first)
        assertEquals((deltas.sumOf { it.second.toDouble() } * scale).toInt(), sent.second)
    }

    @Test
    fun movesTooSmallToSendOnTheirOwnAreCarriedUntilTheyAddUpToAPixel() {
        motion.reset()

        assertNull(motion.move(0.2f, 0f))
        assertNull(motion.move(0.2f, 0f))
        assertEquals(1 to 0, motion.move(0.2f, 0f))
    }

    @Test
    fun anyRandomSlideIsSentWithinLessThanOnePixelOfItsScaledTravelNeverDrifting() {
        val random = Random(7)
        repeat(200) {
            motion.speed = 0.5f + random.nextFloat() * 2.5f
            val deltas = List(random.nextInt(1, 300)) {
                (random.nextFloat() * 40f - 20f) to (random.nextFloat() * 40f - 20f)
            }

            val sent = slide(deltas)

            val scale = motion.speed * PointerMotion.BASE_PIXELS_PER_DP
            val travelX = deltas.sumOf { it.first.toDouble() } * scale
            val travelY = deltas.sumOf { it.second.toDouble() } * scale
            assertTrue("x sent ${sent.first} for $travelX", abs(travelX - sent.first) < 1.0001)
            assertTrue("y sent ${sent.second} for $travelY", abs(travelY - sent.second) < 1.0001)
        }
    }

    @Test
    fun pointerSpeedScalesTheMovement() {
        val deltas = listOf(10f to -4f, 6f to 2f)

        motion.speed = 1f
        val normal = slide(deltas)
        motion.speed = 2f
        val double = slide(deltas)

        assertEquals(normal.first * 2, double.first)
        assertEquals(normal.second * 2, double.second)
    }

    @Test
    fun aDenserScreenNeedsMoreFingerPixelsForTheSameMovement() {
        val dense = PointerMotion(density = 2f)
        dense.reset()

        assertEquals(10 to 0, dense.move(10f, 0f))
    }

    @Test
    fun aNewSlideStartsWithoutTheLastOnesLeftoverFraction() {
        motion.reset()
        assertNull(motion.move(0.4f, 0.4f))

        motion.reset()

        assertNull(motion.move(0.4f, 0.4f))
    }

    @Test
    fun aTouchWithoutMovementSendsNothing() {
        motion.reset()

        assertNull(motion.move(0f, 0f))
    }
}
