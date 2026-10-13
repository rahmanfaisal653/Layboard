package io.github.rahmanfaisal653.layboard.hid

/**
 * Builds keyboard and mouse reports and sends them to the Host through [gateway], one at a time in
 * the order they were made, with [pauseMs] between any two so the Bluetooth link never has to carry a
 * burst. Every send is recorded in [trace]. Not thread-safe: use it from the one thread [scheduler] runs on.
 */
class ReportSender(
    private val gateway: HidGateway,
    private val trace: HidTrace,
    private val scheduler: Scheduler,
    private val pauseMs: Long = DEFAULT_PAUSE_MS,
) {
    /** A report waiting its turn; [releaseFollows] marks a key press, which must never go out without its release. */
    private class Pending(val id: Int, val data: ByteArray, val releaseFollows: Boolean = false)

    private val queue = ArrayDeque<Pending>()

    // True from a key press going out until its release does.
    private var held = false

    // True while the pause after the last report is running.
    private var resting = false

    /** Presses [usage] together with [modifiers], then releases everything. */
    fun pressKey(modifiers: Int, usage: Int) {
        queue.addLast(Pending(HidReports.ID_KEYBOARD, HidReports.keyboard(modifiers, usage), releaseFollows = true))
        queue.addLast(Pending(HidReports.ID_KEYBOARD, HidReports.keyboard(0, 0)))
        sendNext()
    }

    fun sendMouse(buttons: Int, dx: Int, dy: Int) {
        queue.addLast(Pending(HidReports.ID_MOUSE, HidReports.mouse(buttons, dx, dy)))
        sendNext()
    }

    /**
     * Drops every report not yet sent, and says so in the trace. A key already pressed keeps its
     * release, which still goes out after the usual pause, so nothing is left held on the Host.
     */
    fun clear() {
        val release = if (held) queue.firstOrNull() else null
        val dropped = queue.size - (if (release != null) 1 else 0)
        queue.clear()
        if (release != null) queue.addLast(release)
        if (dropped > 0) trace.note("dropped $dropped unsent reports")
    }

    private fun sendNext() {
        if (resting) return
        val next = queue.removeFirstOrNull() ?: return
        held = next.releaseFollows
        trace.report(next.id, next.data, gateway.sendReport(next.id, next.data))
        resting = true
        scheduler.postDelayed(pauseMs) {
            resting = false
            sendNext()
        }
    }

    companion object {
        /** The pause between two reports, in milliseconds; the one value to tune if the link drops reports. */
        const val DEFAULT_PAUSE_MS = 10L
    }
}
