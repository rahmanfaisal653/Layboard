package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Host switches high-resolution scrolling on or off per axis with a one-byte feature report:
 * bits 0-1 the wheel's resolution multiplier, bits 2-3 the pan's, each 0 (plain steps) or 1 (high resolution).
 */
class ScrollResolutionTest {
    private val resolution = ScrollResolution()

    @Test
    fun scrollingIsInPlainStepsUntilTheHostSwitchesHighResolutionOn() {
        assertFalse(resolution.wheelHighRes)
        assertFalse(resolution.panHighRes)
        assertEquals(listOf<Byte>(0), resolution.featureReport().toList())
    }

    @Test
    fun theHostCanSwitchTheWheelAndThePanOnAndOffSeparately() {
        assertTrue(resolution.apply(byteArrayOf(0b0001)))
        assertTrue(resolution.wheelHighRes)
        assertFalse(resolution.panHighRes)

        assertTrue(resolution.apply(byteArrayOf(0b0100)))
        assertFalse(resolution.wheelHighRes)
        assertTrue(resolution.panHighRes)

        assertTrue(resolution.apply(byteArrayOf(0b0101)))
        assertTrue(resolution.wheelHighRes && resolution.panHighRes)

        assertTrue(resolution.apply(byteArrayOf(0)))
        assertFalse(resolution.wheelHighRes || resolution.panHighRes)
    }

    @Test
    fun theFeatureReportAnswersWhatTheHostLastSet() {
        resolution.apply(byteArrayOf(0b0101))
        assertEquals(listOf<Byte>(0b0101), resolution.featureReport().toList())

        resolution.apply(byteArrayOf(0b0001))
        assertEquals(listOf<Byte>(0b0001), resolution.featureReport().toList())
    }

    @Test
    fun aReportThatStartsWithItsReportIdIsReadLikeOneWithout() {
        assertTrue(resolution.apply(byteArrayOf(HidReports.ID_MOUSE.toByte(), 0b0101)))

        assertTrue(resolution.wheelHighRes && resolution.panHighRes)
    }

    @Test
    fun aMultiplierBeyondTheDeclaredRangeIsRefusedAndChangesNothing() {
        resolution.apply(byteArrayOf(0b0001))

        assertFalse(resolution.apply(byteArrayOf(0b0010)))
        assertFalse(resolution.apply(byteArrayOf(0b1000)))

        assertEquals(listOf<Byte>(0b0001), resolution.featureReport().toList())
    }

    @Test
    fun aReportOfTheWrongLengthIsRefusedAndChangesNothing() {
        assertFalse(resolution.apply(byteArrayOf()))
        assertFalse(resolution.apply(byteArrayOf(0b0001, 0b0001)))
        assertFalse(resolution.apply(byteArrayOf(0b0001, 0b0001, 0b0001)))

        assertEquals(listOf<Byte>(0), resolution.featureReport().toList())
    }

    @Test
    fun aNewConnectionStartsBackInPlainSteps() {
        resolution.apply(byteArrayOf(0b0101))

        resolution.reset()

        assertFalse(resolution.wheelHighRes || resolution.panHighRes)
    }
}
