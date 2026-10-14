package io.github.rahmanfaisal653.layboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsTest {
    @Test
    fun sliderPositionsMapToMultipliersFromHalfToTriple() {
        assertEquals(0.5f, Prefs.multiplierFor(0), 0f)
        assertEquals(1.5f, Prefs.multiplierFor(4), 0f)
        assertEquals(3.0f, Prefs.multiplierFor(Prefs.MAX_SPEED_STEP), 0f)
    }

    @Test
    fun aPositionOutsideTheSliderIsHeldToItsEnds() {
        assertEquals(0.5f, Prefs.multiplierFor(-3), 0f)
        assertEquals(3.0f, Prefs.multiplierFor(99), 0f)
    }
}
