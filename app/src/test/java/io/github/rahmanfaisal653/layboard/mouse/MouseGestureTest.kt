package io.github.rahmanfaisal653.layboard.mouse

import io.github.rahmanfaisal653.layboard.hid.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseGestureTest {
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val scrolls = mutableListOf<Pair<Int, Int>>()
    private val clock = FakeScheduler()
    private val gesture = MouseGesture(
        density = 1f,
        scheduler = clock,
        onMove = { dx, dy -> moves += dx to dy },
        onScroll = { w, p -> scrolls += w to p },
    )

    /** Feeds the fingers as they are at the fake clock's current time. */
    private fun touch(fingers: List<Pair<Float, Float>>) = gesture.update(fingers, clock.now)

    private fun at(x: Float, y: Float) = x to y

    private fun movedBy() = moves.sumOf { it.first } to moves.sumOf { it.second }

    private fun scrolledBy() = scrolls.sumOf { it.first } to scrolls.sumOf { it.second }

    @Test
    fun oneFingerMovesThePointerAndNeverScrolls() {
        touch(listOf(at(10f, 10f)))
        touch(listOf(at(20f, 15f)))

        assertEquals(20 to 10, movedBy())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun twoFingersScrollAndNeverMoveThePointer() {
        touch(listOf(at(10f, 100f)))
        touch(listOf(at(10f, 100f), at(30f, 100f)))
        touch(listOf(at(10f, 60f), at(30f, 60f)))

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isNotEmpty())
    }

    @Test
    fun fingersMovingUpScrollTheContentUpWhichIsAWheelTowardTheUser() {
        touch(listOf(at(10f, 200f), at(30f, 200f)))
        touch(listOf(at(10f, 100f), at(30f, 100f)))

        val (wheel, pan) = scrolledBy()
        assertTrue("wheel $wheel", wheel < 0)
        assertEquals(0, pan)
    }

    @Test
    fun fingersMovingLeftScrollTheContentLeftWhichIsAPanRight() {
        touch(listOf(at(200f, 10f), at(200f, 30f)))
        touch(listOf(at(100f, 10f), at(100f, 30f)))

        val (wheel, pan) = scrolledBy()
        assertEquals(0, wheel)
        assertTrue("pan $pan", pan > 0)
    }

    @Test
    fun scrollingFollowsTheMiddleOfTheFingersNotOneOfThem() {
        touch(listOf(at(0f, 0f), at(100f, 0f)))
        // One finger travels 100 and the other 0, so the middle travels 50.
        touch(listOf(at(0f, 100f), at(100f, 0f)))
        val one = scrolledBy().first
        scrolls.clear()
        touch(listOf())
        touch(listOf(at(0f, 0f), at(100f, 0f)))
        touch(listOf(at(0f, 100f), at(100f, 100f)))

        assertEquals(one * 2, scrolledBy().first)
    }

    @Test
    fun aSecondFingerLandingDoesNotMakeThePointerJumpOrScroll() {
        touch(listOf(at(10f, 10f)))
        touch(listOf(at(10f, 10f), at(300f, 400f)))

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun goingBackToOneFingerReturnsToThePointerWithoutAJumpOrAStrayScroll() {
        touch(listOf(at(10f, 10f)))
        touch(listOf(at(10f, 10f), at(300f, 400f)))
        touch(listOf(at(300f, 400f))) // the first finger lifted; the middle jumps
        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())

        touch(listOf(at(310f, 400f)))

        assertEquals(20 to 0, movedBy())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun fingersLandingAndLiftingOneByOneSendNothingByThemselves() {
        touch(listOf(at(0f, 0f)))
        touch(listOf(at(0f, 0f), at(50f, 0f)))
        touch(listOf(at(0f, 0f), at(50f, 0f), at(500f, 500f)))
        touch(listOf(at(50f, 0f), at(500f, 500f)))
        touch(listOf(at(500f, 500f)))
        touch(listOf())

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aLeftoverFractionOfAScrollIsNotCarriedIntoTheNextModeOrGesture() {
        touch(listOf(at(0f, 0f), at(10f, 0f)))
        touch(listOf(at(0f, 1f), at(10f, 1f))) // too little for one step yet
        assertTrue(scrolls.isEmpty())
        touch(listOf())
        touch(listOf(at(0f, 0f), at(10f, 0f)))

        touch(listOf(at(0f, 1f), at(10f, 1f)))

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun smallScrollMovesAddUpToWholeUnitsWithoutDrifting() {
        touch(listOf(at(0f, 0f), at(10f, 0f)))
        repeat(100) { n -> touch(listOf(at(0f, (n + 1).toFloat()), at(10f, (n + 1).toFloat()))) }

        // 100 dp of travel at the fixed scroll speed.
        assertEquals((100 * MouseGesture.SCROLL_UNITS_PER_DP).toInt(), scrolledBy().first)
    }

    @Test
    fun pointerSpeedScalesThePointerButNotTheScroll() {
        gesture.pointerSpeed = 2f
        touch(listOf(at(0f, 0f)))
        touch(listOf(at(10f, 0f)))
        val doubled = movedBy().first
        touch(listOf())
        gesture.pointerSpeed = 1f
        touch(listOf(at(0f, 0f)))
        touch(listOf(at(10f, 0f)))

        assertEquals(doubled, (movedBy().first - doubled) * 2)
    }

    @Test
    fun liftingEveryFingerEndsTheGestureAndTheNextTouchStartsAfresh() {
        touch(listOf(at(0f, 0f)))
        touch(listOf(at(10f, 0f)))
        touch(listOf())
        moves.clear()

        touch(listOf(at(500f, 500f)))

        assertTrue(moves.isEmpty())
    }

    @Test
    fun aTapWithOneOrTwoFingersSendsNothingAtAll() {
        touch(listOf(at(5f, 5f)))
        touch(listOf())
        touch(listOf(at(5f, 5f)))
        touch(listOf(at(5f, 5f), at(25f, 5f)))
        touch(listOf())

        assertTrue(moves.isEmpty())
        assertTrue(scrolls.isEmpty())
    }

    // Two fingers swipe up at [pxPerMs] for [ms], in 10 ms moves, and stay down.
    private fun swipeUp(pxPerMs: Float, ms: Int = 100) {
        var y = 1000f
        touch(listOf(at(10f, y), at(30f, y)))
        repeat(ms / 10) {
            clock.advanceBy(10)
            y -= pxPerMs * 10
            touch(listOf(at(10f, y), at(30f, y)))
        }
    }

    private fun liftBoth() = touch(listOf())

    @Test
    fun aFastTwoFingerFlickKeepsScrollingInTheSameDirectionAfterTheFingersLift() {
        swipeUp(pxPerMs = 2f)
        scrolls.clear()
        liftBoth()

        clock.advanceBy(300)

        assertTrue(scrolls.isNotEmpty())
        assertTrue("wheel ${scrolledBy().first}", scrolledBy().first < 0)
        assertEquals(0, scrolledBy().second)
    }

    @Test
    fun aFlickToTheLeftGlidesLeft() {
        var x = 1000f
        touch(listOf(at(x, 10f), at(x, 30f)))
        repeat(10) {
            clock.advanceBy(10)
            x -= 20f
            touch(listOf(at(x, 10f), at(x, 30f)))
        }
        scrolls.clear()
        liftBoth()

        clock.advanceBy(300)

        assertEquals(0, scrolledBy().first)
        assertTrue("pan ${scrolledBy().second}", scrolledBy().second > 0)
    }

    @Test
    fun theGlideSlowsDownAndComesToAStop() {
        swipeUp(pxPerMs = 2f)
        scrolls.clear()
        liftBoth()

        clock.advanceBy(100)
        val early = -scrolledBy().first
        scrolls.clear()
        clock.advanceBy(1000)
        scrolls.clear()
        clock.advanceBy(100)
        val late = -scrolledBy().first
        assertTrue("early $early late $late", early > late)

        clock.advanceBy(5000)
        scrolls.clear()
        clock.advanceBy(5000)
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aFasterFlickGlidesFurther() {
        swipeUp(pxPerMs = 1f)
        scrolls.clear()
        liftBoth()
        clock.advanceBy(5000)
        val slower = scrolledBy().first
        scrolls.clear()

        swipeUp(pxPerMs = 3f)
        scrolls.clear()
        liftBoth()
        clock.advanceBy(5000)

        assertTrue("slower $slower faster ${scrolledBy().first}", scrolledBy().first < slower)
    }

    @Test
    fun aSlowTwoFingerMoveDoesNotGlide() {
        swipeUp(pxPerMs = 0.1f, ms = 500)
        scrolls.clear()
        liftBoth()

        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun pausingBeforeLiftingDoesNotGlide() {
        swipeUp(pxPerMs = 2f)
        clock.advanceBy(300)
        scrolls.clear()
        liftBoth()

        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun liftingTheFingersOneAfterTheOtherStillGlides() {
        swipeUp(pxPerMs = 2f)
        clock.advanceBy(10)
        touch(listOf(at(30f, 800f)))
        clock.advanceBy(20)
        scrolls.clear()
        liftBoth()

        clock.advanceBy(300)

        assertTrue(scrolledBy().first < 0)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun aFingerLeftDownAfterTheOtherLiftsMovesThePointerAndDoesNotGlide() {
        swipeUp(pxPerMs = 2f)
        touch(listOf(at(30f, 800f)))
        clock.advanceBy(500)
        scrolls.clear()
        touch(listOf(at(40f, 800f)))
        liftBoth()

        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aOneFingerFlickNeverGlides() {
        var y = 1000f
        touch(listOf(at(10f, y)))
        repeat(10) {
            clock.advanceBy(10)
            y -= 20f
            touch(listOf(at(10f, y)))
        }
        liftBoth()

        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun touchingTheMouseWithAnyFingerStopsTheGlideAtOnce() {
        swipeUp(pxPerMs = 2f)
        liftBoth()
        clock.advanceBy(100)
        assertTrue(scrolls.isNotEmpty())

        touch(listOf(at(500f, 500f)))
        scrolls.clear()
        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun theGlideDoesNotResumeAfterTheTouchThatStoppedItLifts() {
        swipeUp(pxPerMs = 2f)
        liftBoth()
        clock.advanceBy(100)
        touch(listOf(at(500f, 500f)))
        liftBoth()
        scrolls.clear()

        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun stoppingTheGlideSendsNothingMoreEvenWithNoTouch() {
        swipeUp(pxPerMs = 2f)
        liftBoth()
        clock.advanceBy(100)

        gesture.stopGlide()
        scrolls.clear()
        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aCancelledTouchDoesNotGlide() {
        swipeUp(pxPerMs = 2f)
        scrolls.clear()

        gesture.cancel()
        clock.advanceBy(2000)

        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun aNewFlickWhileGlidingReplacesTheOldGlideInsteadOfAddingToIt() {
        swipeUp(pxPerMs = 2f)
        liftBoth()
        clock.advanceBy(100)
        swipeUp(pxPerMs = 2f)
        scrolls.clear()
        liftBoth()
        clock.advanceBy(5000)
        val once = scrolledBy().first

        scrolls.clear()
        swipeUp(pxPerMs = 2f)
        scrolls.clear()
        liftBoth()
        clock.advanceBy(5000)

        assertEquals(once, scrolledBy().first)
    }

    @Test
    fun theGlideRespectsTheSameScaleAsTheFingerScroll() {
        // The same flick glides the same whatever the screen density, as dp are what count.
        val dense = MouseGesture(density = 2f, scheduler = clock, onMove = { _, _ -> }, onScroll = { w, p -> scrolls += w to p })
        var y = 2000f
        dense.update(listOf(at(20f, y), at(60f, y)), clock.now)
        repeat(10) {
            clock.advanceBy(10)
            y -= 40f
            dense.update(listOf(at(20f, y), at(60f, y)), clock.now)
        }
        scrolls.clear()
        dense.update(listOf(), clock.now)
        clock.advanceBy(5000)
        val denseTotal = scrolledBy().first

        scrolls.clear()
        swipeUp(pxPerMs = 2f)
        scrolls.clear()
        liftBoth()
        clock.advanceBy(5000)

        assertTrue(denseTotal < 0)
        assertEquals(scrolledBy().first.toDouble(), denseTotal.toDouble(), 10.0)
    }
}
