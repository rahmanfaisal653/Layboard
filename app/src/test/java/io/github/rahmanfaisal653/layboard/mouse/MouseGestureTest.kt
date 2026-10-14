package io.github.rahmanfaisal653.layboard.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseGestureTest {
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val scrolls = mutableListOf<Pair<Int, Int>>()
    private val gesture = MouseGesture(density = 1f, onMove = { dx, dy -> moves += dx to dy }, onScroll = { w, p -> scrolls += w to p })

    private fun at(x: Float, y: Float) = x to y

    private fun movedBy() = moves.sumOf { it.first } to moves.sumOf { it.second }

    private fun scrolledBy() = scrolls.sumOf { it.first } to scrolls.sumOf { it.second }

    @Test
    fun oneFingerMovesThePointerAndNeverScrolls() {
        gesture.update(listOf(at(10f, 10f)))
        gesture.update(listOf(at(20f, 15f)))

        assertEquals(20 to 10, movedBy())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun twoFingersScrollAndNeverMoveThePointer() {
        gesture.update(listOf(at(10f, 100f)))
        gesture.update(listOf(at(10f, 100f), at(30f, 100f)))
        gesture.update(listOf(at(10f, 60f), at(30f, 60f)))

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isNotEmpty())
    }

    @Test
    fun fingersMovingUpScrollTheContentUpWhichIsAWheelTowardTheUser() {
        gesture.update(listOf(at(10f, 200f), at(30f, 200f)))
        gesture.update(listOf(at(10f, 100f), at(30f, 100f)))

        val (wheel, pan) = scrolledBy()
        assertTrue("wheel $wheel", wheel < 0)
        assertEquals(0, pan)
    }

    @Test
    fun fingersMovingLeftScrollTheContentLeftWhichIsAPanRight() {
        gesture.update(listOf(at(200f, 10f), at(200f, 30f)))
        gesture.update(listOf(at(100f, 10f), at(100f, 30f)))

        val (wheel, pan) = scrolledBy()
        assertEquals(0, wheel)
        assertTrue("pan $pan", pan > 0)
    }

    @Test
    fun scrollingFollowsTheMiddleOfTheFingersNotOneOfThem() {
        gesture.update(listOf(at(0f, 0f), at(100f, 0f)))
        // One finger travels 100 and the other 0, so the middle travels 50.
        gesture.update(listOf(at(0f, 100f), at(100f, 0f)))
        val one = scrolledBy().first
        scrolls.clear()
        gesture.update(listOf())
        gesture.update(listOf(at(0f, 0f), at(100f, 0f)))
        gesture.update(listOf(at(0f, 100f), at(100f, 100f)))

        assertEquals(one * 2, scrolledBy().first)
    }

    @Test
    fun aSecondFingerLandingDoesNotMakeThePointerJumpOrScroll() {
        gesture.update(listOf(at(10f, 10f)))
        gesture.update(listOf(at(10f, 10f), at(300f, 400f)))

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun goingBackToOneFingerReturnsToThePointerWithoutAJumpOrAStrayScroll() {
        gesture.update(listOf(at(10f, 10f)))
        gesture.update(listOf(at(10f, 10f), at(300f, 400f)))
        gesture.update(listOf(at(300f, 400f))) // the first finger lifted; the middle jumps
        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())

        gesture.update(listOf(at(310f, 400f)))

        assertEquals(20 to 0, movedBy())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun fingersLandingAndLiftingOneByOneSendNothingByThemselves() {
        gesture.update(listOf(at(0f, 0f)))
        gesture.update(listOf(at(0f, 0f), at(50f, 0f)))
        gesture.update(listOf(at(0f, 0f), at(50f, 0f), at(500f, 500f)))
        gesture.update(listOf(at(50f, 0f), at(500f, 500f)))
        gesture.update(listOf(at(500f, 500f)))
        gesture.update(listOf())

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aLeftoverFractionOfAScrollIsNotCarriedIntoTheNextModeOrGesture() {
        gesture.update(listOf(at(0f, 0f), at(10f, 0f)))
        gesture.update(listOf(at(0f, 1f), at(10f, 1f))) // too little for one step yet
        assertTrue(scrolls.isEmpty())
        gesture.update(listOf())
        gesture.update(listOf(at(0f, 0f), at(10f, 0f)))

        gesture.update(listOf(at(0f, 1f), at(10f, 1f)))

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun smallScrollMovesAddUpToWholeUnitsWithoutDrifting() {
        gesture.update(listOf(at(0f, 0f), at(10f, 0f)))
        repeat(100) { n -> gesture.update(listOf(at(0f, (n + 1).toFloat()), at(10f, (n + 1).toFloat()))) }

        // 100 dp of travel at the fixed scroll speed.
        assertEquals((100 * MouseGesture.SCROLL_UNITS_PER_DP).toInt(), scrolledBy().first)
    }

    @Test
    fun pointerSpeedScalesThePointerButNotTheScroll() {
        gesture.pointerSpeed = 2f
        gesture.update(listOf(at(0f, 0f)))
        gesture.update(listOf(at(10f, 0f)))
        val doubled = movedBy().first
        gesture.update(listOf())
        gesture.pointerSpeed = 1f
        gesture.update(listOf(at(0f, 0f)))
        gesture.update(listOf(at(10f, 0f)))

        assertEquals(doubled, (movedBy().first - doubled) * 2)
    }

    @Test
    fun liftingEveryFingerEndsTheGestureAndTheNextTouchStartsAfresh() {
        gesture.update(listOf(at(0f, 0f)))
        gesture.update(listOf(at(10f, 0f)))
        gesture.update(listOf())
        moves.clear()

        gesture.update(listOf(at(500f, 500f)))

        assertTrue(moves.isEmpty())
    }

    @Test
    fun aTapWithOneOrTwoFingersSendsNothingAtAll() {
        gesture.update(listOf(at(5f, 5f)))
        gesture.update(listOf())
        gesture.update(listOf(at(5f, 5f)))
        gesture.update(listOf(at(5f, 5f), at(25f, 5f)))
        gesture.update(listOf())

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }
}
