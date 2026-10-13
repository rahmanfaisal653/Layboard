package io.github.rahmanfaisal653.layboard.hid

import io.github.rahmanfaisal653.layboard.hid.FakeHidGateway.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Host may ask a HID device for a report, set one, switch protocol or send interrupt data, and
 * each of those must be answered. The expected numbers are the Bluetooth HID profile's own:
 * report types 1 input, 2 output, 3 feature; handshake codes 0 success, 2 invalid report ID,
 * 3 unsupported request; protocols 0 boot, 1 report.
 */
class HostRequestsTest {
    private val gateway = FakeHidGateway()
    private val trace = HidTrace()
    private val requests = HostRequests(gateway, trace)

    private fun zeros(count: Int): List<Byte> = List(count) { 0 }

    @Test
    fun hostAskingForTheKeyboardReportGetsAnIdleKeyboardReport() {
        requests.onGetReport(type = 1, id = HidReports.ID_KEYBOARD, bufferSize = 0)

        assertEquals(listOf(Call.Reply(1, HidReports.ID_KEYBOARD, zeros(8))), gateway.calls)
    }

    @Test
    fun hostAskingForTheMouseReportGetsAnIdleMouseReport() {
        requests.onGetReport(type = 1, id = HidReports.ID_MOUSE, bufferSize = 0)

        assertEquals(listOf(Call.Reply(1, HidReports.ID_MOUSE, zeros(3))), gateway.calls)
    }

    @Test
    fun hostAskingForAReportThatDoesNotExistGetsAnInvalidReportIdError() {
        requests.onGetReport(type = 1, id = 9, bufferSize = 0)

        assertEquals(listOf(Call.Error(2)), gateway.calls)
    }

    @Test
    fun hostAskingForAKeyboardFeatureReportGetsAnInvalidReportIdError() {
        requests.onGetReport(type = 3, id = HidReports.ID_KEYBOARD, bufferSize = 0)

        assertEquals(listOf(Call.Error(2)), gateway.calls)
    }

    @Test
    fun hostSettingAReportGetsAnInvalidReportIdErrorBecauseNoneCanBeSet() {
        requests.onSetReport(type = 2, id = HidReports.ID_KEYBOARD, data = byteArrayOf(1))

        assertEquals(listOf(Call.Error(2)), gateway.calls)
    }

    @Test
    fun hostSwitchingToReportProtocolGetsSuccess() {
        requests.onSetProtocol(protocol = 1)

        assertEquals(listOf(Call.Error(0)), gateway.calls)
    }

    @Test
    fun hostSwitchingToBootProtocolGetsAnUnsupportedRequestError() {
        requests.onSetProtocol(protocol = 0)

        assertEquals(listOf(Call.Error(3)), gateway.calls)
    }

    @Test
    fun hostSendingInterruptDataGetsAnInvalidReportIdError() {
        requests.onInterruptData(id = HidReports.ID_KEYBOARD, data = byteArrayOf(1))

        assertEquals(listOf(Call.Error(2)), gateway.calls)
    }

    @Test
    fun virtualCableUnplugHasNoAnswerButIsRecorded() {
        requests.onVirtualCableUnplug()

        assertTrue(gateway.calls.isEmpty())
        assertTrue(trace.lines.single().contains("VIRTUAL_CABLE_UNPLUG"))
    }

    @Test
    fun eachRequestAndItsAnswerAreRecordedInTheTrace() {
        requests.onGetReport(type = 1, id = HidReports.ID_KEYBOARD, bufferSize = 0)
        requests.onSetReport(type = 2, id = HidReports.ID_KEYBOARD, data = byteArrayOf(1))
        requests.onSetProtocol(protocol = 1)
        requests.onInterruptData(id = HidReports.ID_KEYBOARD, data = byteArrayOf(1))

        val names = listOf("GET_REPORT", "SET_REPORT", "SET_PROTOCOL", "INTERRUPT_DATA")
        assertEquals(names.size, trace.lines.size)
        names.zip(trace.lines).forEach { (name, line) ->
            assertTrue("$line should mention $name", name in line)
            assertTrue("$line should say the answer was accepted", "accepted" in line)
        }
    }

    @Test
    fun anAnswerTheStackRefusesIsRecordedAsRefused() {
        gateway.accepts = false

        requests.onGetReport(type = 1, id = HidReports.ID_KEYBOARD, bufferSize = 0)

        assertTrue(trace.lines.single().contains("refused"))
    }
}
