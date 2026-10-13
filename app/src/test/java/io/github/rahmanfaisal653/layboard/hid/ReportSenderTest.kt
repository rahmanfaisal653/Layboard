package io.github.rahmanfaisal653.layboard.hid

import io.github.rahmanfaisal653.layboard.hid.FakeHidGateway.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSenderTest {
    private val gateway = FakeHidGateway()
    private val trace = HidTrace()
    private val sender = ReportSender(gateway, trace)

    private fun bytes(vararg values: Int): List<Byte> = values.map { it.toByte() }

    @Test
    fun escapeTabAndEachArrowArePressedThenReleased() {
        val keys = listOf(
            "Esc" to (KeyMap.ESCAPE to 0x29),
            "Tab" to (KeyMap.TAB to 0x2B),
            "Right" to (KeyMap.RIGHT to 0x4F),
            "Left" to (KeyMap.LEFT to 0x50),
            "Down" to (KeyMap.DOWN to 0x51),
            "Up" to (KeyMap.UP to 0x52),
        )
        keys.forEach { (name, usages) ->
            gateway.calls.clear()

            sender.pressKey(0, usages.first)

            assertEquals(
                "$name should be pressed then released",
                listOf(
                    Call.Report(HidReports.ID_KEYBOARD, bytes(0, 0, usages.second, 0, 0, 0, 0, 0)),
                    Call.Report(HidReports.ID_KEYBOARD, bytes(0, 0, 0, 0, 0, 0, 0, 0)),
                ),
                gateway.calls,
            )
        }
    }

    @Test
    fun modifiersAreHeldWithTheKeyAndReleasedWithIt() {
        sender.pressKey(HidReports.MOD_CTRL or HidReports.MOD_ALT, 0x06)
        assertEquals(
            listOf(
                Call.Report(HidReports.ID_KEYBOARD, bytes(0x05, 0, 0x06, 0, 0, 0, 0, 0)),
                Call.Report(HidReports.ID_KEYBOARD, bytes(0, 0, 0, 0, 0, 0, 0, 0)),
            ),
            gateway.calls,
        )
    }

    @Test
    fun acceptedReportsAreRecordedInTheTrace() {
        sender.pressKey(0, KeyMap.ESCAPE)

        assertEquals(2, trace.lines.size)
        assertTrue("00 00 29 00 00 00 00 00" in trace.lines[0])
        assertTrue("00 00 00 00 00 00 00 00" in trace.lines[1])
        assertTrue(trace.lines.all { "accepted" in it })
    }

    @Test
    fun refusedReportsAreRecordedInTheTraceInsteadOfDisappearing() {
        gateway.accepts = false

        sender.pressKey(0, KeyMap.TAB)

        assertEquals(2, trace.lines.size)
        assertTrue("00 00 2b 00 00 00 00 00" in trace.lines[0])
        assertTrue(trace.lines.all { "refused" in it })
        assertFalse(trace.lines.any { "accepted" in it })
    }

    @Test
    fun mouseReportsAreRecordedInTheTraceToo() {
        sender.sendMouse(HidReports.BUTTON_LEFT, 5, -3)

        assertEquals(1, trace.lines.size)
        assertTrue("01 05 fd" in trace.lines[0])
    }
}
