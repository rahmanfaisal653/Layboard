package io.github.rahmanfaisal653.layboard.hid

/**
 * Builds keyboard and mouse reports and sends them to the Host through [gateway], recording in
 * [trace] whether the stack accepted each one.
 */
class ReportSender(
    private val gateway: HidGateway,
    private val trace: HidTrace,
) {
    /** Presses [usage] together with [modifiers], then releases everything. */
    fun pressKey(modifiers: Int, usage: Int) {
        sendKeyboard(modifiers, usage)
        sendKeyboard(0, 0)
    }

    private fun sendKeyboard(modifiers: Int, usage: Int) = send(HidReports.ID_KEYBOARD, HidReports.keyboard(modifiers, usage))

    fun sendMouse(buttons: Int, dx: Int, dy: Int) = send(HidReports.ID_MOUSE, HidReports.mouse(buttons, dx, dy))

    private fun send(id: Int, data: ByteArray) {
        trace.report(id, data, gateway.sendReport(id, data))
    }
}
