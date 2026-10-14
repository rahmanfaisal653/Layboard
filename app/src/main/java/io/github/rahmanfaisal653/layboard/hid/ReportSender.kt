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
    /**
     * A report waiting its turn; [holdsKeys] says whether a keyboard report leaves any key down on the
     * Host, [buttons] the Click Buttons a mouse report holds, and [motion] a pointer move that later
     * moves may still be merged into.
     */
    private class Pending(
        val id: Int,
        val data: ByteArray,
        val holdsKeys: Boolean? = null,
        val buttons: Int? = null,
        val motion: Motion? = null,
    )

    private class Motion(val buttons: Int, val dx: Int, val dy: Int)

    private val queue = ArrayDeque<Pending>()

    // The Special Keys held now, in the order they went down, and whether the last keyboard report sent
    // left any key down on the Host.
    private val held = mutableListOf<Key>()
    private var keysOnHost = false

    // The Click Buttons as last asked for, and as the last mouse report sent left them on the Host.
    private var buttons = 0
    private var buttonsOnHost = 0

    // True while the pause after the last report is running.
    private var resting = false

    // Waits for the releases a clear() kept to go out.
    private var onReleasesSent: (() -> Unit)? = null

    /**
     * Presses [usage] together with [modifiers] and whatever Special Keys are held, then lets go of
     * [usage] and [modifiers] again, leaving the held keys down: Ctrl held while typing c is Ctrl+C.
     */
    fun pressKey(modifiers: Int, usage: Int) {
        queue.addLast(keys(heldModifiers() or modifiers, heldUsages() + usage))
        sendHeld()
    }

    /**
     * Holds [key] down on the Host until [releaseKey], as a finger holds a key on a real keyboard, so
     * later keys and clicks reach the Host with it held and a held arrow repeats there. A key already
     * held sends nothing.
     */
    fun holdKey(key: Key) {
        if (key in held) return
        held += key
        sendHeld()
    }

    /** Lets go of [key] held by [holdKey]; a key not held, e.g. one a [clear] already let go of, sends nothing. */
    fun releaseKey(key: Key) {
        if (!held.remove(key)) return
        sendHeld()
    }

    /**
     * Presses or releases the Click Button [mask], leaving any other held. Moves made while one is held
     * carry it, so the Host sees a drag until it is released. A button already in that state sends nothing.
     */
    fun setClickButton(mask: Int, down: Boolean) {
        val changed = if (down) buttons or mask else buttons and mask.inv()
        if (changed == buttons) return
        buttons = changed
        queue.addLast(buttonChange(buttons))
        sendNext()
    }

    /**
     * Moves the pointer by [dx], [dy] with the Click Buttons held now. A move still waiting at the end of
     * the queue with the same buttons absorbs it, so a burst made while the link rests goes out as one
     * report; what does not fit in one report's limit follows in as many more as it needs. Nothing is lost
     * on the way.
     */
    fun moveMouse(dx: Int, dy: Int) {
        var restX = dx
        var restY = dy
        val tail = queue.lastOrNull()?.motion
        if (tail != null && tail.buttons == buttons) {
            val mergedX = step(tail.dx + restX)
            val mergedY = step(tail.dy + restY)
            restX -= mergedX - tail.dx
            restY -= mergedY - tail.dy
            queue.removeLast()
            if (mergedX != 0 || mergedY != 0) queue.addLast(motion(buttons, mergedX, mergedY))
        }
        while (restX != 0 || restY != 0) {
            val stepX = step(restX)
            val stepY = step(restY)
            queue.addLast(motion(buttons, stepX, stepY))
            restX -= stepX
            restY -= stepY
        }
        sendNext()
    }

    /**
     * Drops every report not yet sent, and says so in the trace, and forgets the held keys. A key or
     * Click Button held down on the Host gets a release; they go out as soon as any pause still
     * running ends, so nothing is left held on the Host. [whenReleased] runs once, one pause
     * after the last of them, or at once when nothing needs releasing and the link is quiet: the
     * moment the link can be closed without cutting a release off.
     */
    fun clear(whenReleased: () -> Unit = {}) {
        val dropped = queue.size
        queue.clear()
        held.clear()
        if (keysOnHost) queue.addLast(keys(0, emptyList()))
        buttons = 0
        if (buttonsOnHost != 0) queue.addLast(buttonChange(0))
        if (dropped > 0) trace.note("dropped $dropped unsent reports")
        val earlier = onReleasesSent
        onReleasesSent = { earlier?.invoke(); whenReleased() }
        sendNext()
    }

    /**
     * Like [clear], but sends the releases at once, without the pauses, and forgets any earlier
     * whenReleased: for when the caller closes the Connection straight after and cannot wait.
     */
    fun clearNow() {
        clear()
        onReleasesSent = null
        while (true) send(queue.removeFirstOrNull() ?: break)
    }

    private fun keys(modifiers: Int, usages: List<Int>) = Pending(
        HidReports.ID_KEYBOARD,
        HidReports.keyboard(modifiers, *usages.distinct().toIntArray()),
        holdsKeys = modifiers != 0 || usages.any { it != 0 },
    )

    // Sends the held keys as they are now, e.g. after one goes down or up.
    private fun sendHeld() {
        queue.addLast(keys(heldModifiers(), heldUsages()))
        sendNext()
    }

    private fun heldModifiers() = held.fold(0) { modifiers, key -> modifiers or key.modifiers }

    private fun heldUsages() = held.map { it.usage }.filter { it != 0 }

    private fun buttonChange(buttons: Int) =
        Pending(HidReports.ID_MOUSE, HidReports.mouse(buttons, 0, 0), buttons = buttons)

    private fun motion(buttons: Int, dx: Int, dy: Int) =
        Pending(HidReports.ID_MOUSE, HidReports.mouse(buttons, dx, dy), buttons = buttons, motion = Motion(buttons, dx, dy))

    // As much of [distance] as one mouse report can carry.
    private fun step(distance: Int) = distance.coerceIn(-MAX_STEP, MAX_STEP)

    private fun sendNext() {
        if (resting) return
        val next = queue.removeFirstOrNull()
        if (next == null) {
            onReleasesSent?.let {
                onReleasesSent = null
                it()
            }
            return
        }
        send(next)
        resting = true
        scheduler.postDelayed(pauseMs) {
            resting = false
            sendNext()
        }
    }

    private fun send(report: Pending) {
        report.holdsKeys?.let { keysOnHost = it }
        report.buttons?.let { buttonsOnHost = it }
        trace.report(report.id, report.data, gateway.sendReport(report.id, report.data))
    }

    companion object {
        /** The pause between two reports, in milliseconds; the one value to tune if the link drops reports. */
        const val DEFAULT_PAUSE_MS = 10L

        /** The furthest one mouse report can move the pointer on each axis, as the descriptor declares. */
        const val MAX_STEP = 127
    }
}
