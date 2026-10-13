package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Host trusts the descriptor to say how long each report is, so the bytes we build must match it. */
class ReportContractTest {
    private val declared = HidDescriptorParser.parse(HidReports.DESCRIPTOR)

    private fun declaredBytes(id: Int): Int {
        val bits = declared.inputBits.getValue(id)
        assertEquals("report $id is not a whole number of bytes", 0, bits % 8)
        return bits / 8
    }

    @Test
    fun keyboardReportIsAsLongAsTheDescriptorDeclares() {
        assertEquals(8, declaredBytes(HidReports.ID_KEYBOARD))
        assertEquals(declaredBytes(HidReports.ID_KEYBOARD), HidReports.keyboard(HidReports.MOD_CTRL, 0x04).size)
    }

    @Test
    fun mouseReportIsAsLongAsTheDescriptorDeclares() {
        assertEquals(3, declaredBytes(HidReports.ID_MOUSE))
        assertEquals(declaredBytes(HidReports.ID_MOUSE), HidReports.mouse(HidReports.BUTTON_LEFT, 5, -3).size)
    }

    @Test
    fun descriptorDeclaresExactlyTheKeyboardAndMouseInputReports() {
        assertEquals(setOf(HidReports.ID_KEYBOARD, HidReports.ID_MOUSE), declared.inputBits.keys)
    }

    @Test
    fun theMouseCanOnlyMoveSoTheHostIsNeverSentAScroll() {
        assertTrue(GENERIC_DESKTOP_X in declared.usages)
        assertTrue(GENERIC_DESKTOP_Y in declared.usages)
        assertFalse(GENERIC_DESKTOP_WHEEL in declared.usages)
        assertFalse(CONSUMER_AC_PAN in declared.usages)
    }

    @Test
    fun descriptorDeclaresNoOutputOrFeatureReports() {
        assertTrue(declared.outputBits.isEmpty())
        assertTrue(declared.featureBits.isEmpty())
    }

    private companion object {
        val GENERIC_DESKTOP_X = HidDescriptorParser.usage(0x01, 0x30)
        val GENERIC_DESKTOP_Y = HidDescriptorParser.usage(0x01, 0x31)
        val GENERIC_DESKTOP_WHEEL = HidDescriptorParser.usage(0x01, 0x38)
        val CONSUMER_AC_PAN = HidDescriptorParser.usage(0x0C, 0x238)
    }
}
