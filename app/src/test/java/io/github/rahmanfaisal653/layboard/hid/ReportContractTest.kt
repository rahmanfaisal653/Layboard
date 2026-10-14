package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertEquals
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
        assertEquals(5, declaredBytes(HidReports.ID_MOUSE))
        assertEquals(declaredBytes(HidReports.ID_MOUSE), HidReports.mouse(HidReports.BUTTON_LEFT, 5, -3, 2, -1).size)
    }

    @Test
    fun descriptorDeclaresExactlyTheKeyboardAndMouseInputReports() {
        assertEquals(setOf(HidReports.ID_KEYBOARD, HidReports.ID_MOUSE), declared.inputBits.keys)
    }

    @Test
    fun theMouseDeclaresMovementAVerticalWheelAndAHorizontalPan() {
        assertTrue(GENERIC_DESKTOP_X in declared.usages)
        assertTrue(GENERIC_DESKTOP_Y in declared.usages)
        assertTrue(GENERIC_DESKTOP_WHEEL in declared.usages)
        assertTrue(CONSUMER_AC_PAN in declared.usages)
    }

    @Test
    fun theMouseReportCarriesButtonsMovementWheelAndPanInThatOrder() {
        assertEquals(
            listOf(1, 5, -3, 2, -1).map { it.toByte() },
            HidReports.mouse(HidReports.BUTTON_LEFT, 5, -3, 2, -1).toList(),
        )
    }

    @Test
    fun aMouseReportWithoutScrollingLeavesWheelAndPanAtZero() {
        assertEquals(listOf<Byte>(0, 4, -4, 0, 0), HidReports.mouse(0, 4, -4).toList())
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
