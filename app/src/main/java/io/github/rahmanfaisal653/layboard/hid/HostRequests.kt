package io.github.rahmanfaisal653.layboard.hid

/**
 * Answers what a Host may ask of a HID device, so none of it goes unanswered: a report, setting
 * a report, switching protocol, or interrupt data. The Phone only has keyboard and mouse input
 * reports, so everything else is refused with an explicit error. Each request and the stack's
 * verdict on the answer is recorded in [trace].
 */
class HostRequests(
    private val gateway: HidGateway,
    private val trace: HidTrace,
) {
    /** The Host asked for the current [type] report [id]: a keyboard or mouse report with nothing pressed. */
    fun onGetReport(type: Int, id: Int, bufferSize: Int) {
        val request = "GET_REPORT type=$type id=$id buffer=$bufferSize"
        val idle = if (type == REPORT_TYPE_INPUT) idleInputReport(id) else null
        if (idle != null) {
            record(request, "reply", gateway.replyReport(type, id, idle))
        } else {
            answerWithError(request, ERROR_INVALID_REPORT_ID)
        }
    }

    /** The Host tried to set a [type] report [id]; the Phone declares no output or feature reports. */
    fun onSetReport(type: Int, id: Int, data: ByteArray) {
        answerWithError("SET_REPORT type=$type id=$id bytes=${data.size}", ERROR_INVALID_REPORT_ID)
    }

    /** The Host switched to [protocol]. Reports are only built for report mode, not boot mode. */
    fun onSetProtocol(protocol: Int) {
        answerWithError(
            "SET_PROTOCOL protocol=$protocol",
            if (protocol == PROTOCOL_REPORT) ERROR_SUCCESS else ERROR_UNSUPPORTED_REQUEST,
        )
    }

    /** The Host sent report [id] over the interrupt channel; there is nothing the Phone accepts there. */
    fun onInterruptData(id: Int, data: ByteArray) {
        answerWithError("INTERRUPT_DATA id=$id bytes=${data.size}", ERROR_INVALID_REPORT_ID)
    }

    /** The Host asked to drop the HID link. It expects no answer; the Connection ends on its own. */
    fun onVirtualCableUnplug() {
        trace.note("host VIRTUAL_CABLE_UNPLUG")
    }

    private fun idleInputReport(id: Int): ByteArray? = when (id) {
        HidReports.ID_KEYBOARD -> HidReports.keyboard(0, 0)
        HidReports.ID_MOUSE -> HidReports.mouse(0, 0, 0)
        else -> null
    }

    private fun answerWithError(request: String, code: Int) {
        record(request, "error $code", gateway.reportError(code))
    }

    private fun record(request: String, answer: String, accepted: Boolean) {
        trace.note("host $request: $answer ${HidTrace.verdict(accepted)}")
    }

    private companion object {
        const val REPORT_TYPE_INPUT = 1

        const val PROTOCOL_REPORT = 1

        const val ERROR_SUCCESS = 0
        const val ERROR_INVALID_REPORT_ID = 2
        const val ERROR_UNSUPPORTED_REQUEST = 3
    }
}
