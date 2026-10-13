package io.github.rahmanfaisal653.layboard.hid

import io.github.rahmanfaisal653.layboard.hid.FakeHidGateway.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSenderTest {
    private val scheduler = FakeScheduler()
    private val gateway = FakeHidGateway { scheduler.now }
    private val trace = HidTrace()
    private val sender = ReportSender(gateway, trace, scheduler)

    private fun bytes(vararg values: Int): List<Byte> = values.map { it.toByte() }

    private fun press(usage: Int, modifiers: Int = 0) =
        Call.Report(HidReports.ID_KEYBOARD, bytes(modifiers, 0, usage, 0, 0, 0, 0, 0))

    private val release = press(0)

    private fun mouse(buttons: Int, dx: Int, dy: Int) = Call.Report(HidReports.ID_MOUSE, bytes(buttons, dx, dy))

    private fun movementSent(): Pair<Int, Int> {
        val reports = gateway.calls.filterIsInstance<Call.Report>().filter { it.id == HidReports.ID_MOUSE }
        return reports.sumOf { it.data[1].toInt() } to reports.sumOf { it.data[2].toInt() }
    }

    /** Lets every queued report go out, however long the pauses between them. */
    private fun settle() = scheduler.advanceBy(60_000)

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
            gateway.clear()

            sender.pressKey(0, usages.first)
            settle()

            assertEquals("$name should be pressed then released", listOf(press(usages.second), release), gateway.calls)
        }
    }

    @Test
    fun modifiersAreHeldWithTheKeyAndReleasedWithIt() {
        sender.pressKey(HidReports.MOD_CTRL or HidReports.MOD_ALT, 0x06)
        settle()

        assertEquals(listOf(press(0x06, modifiers = 0x05), release), gateway.calls)
    }

    @Test
    fun acceptedReportsAreRecordedInTheTrace() {
        sender.pressKey(0, KeyMap.ESCAPE)
        settle()

        assertEquals(2, trace.lines.size)
        assertTrue("00 00 29 00 00 00 00 00" in trace.lines[0])
        assertTrue("00 00 00 00 00 00 00 00" in trace.lines[1])
        assertTrue(trace.lines.all { "accepted" in it })
    }

    @Test
    fun refusedReportsAreRecordedInTheTraceInsteadOfDisappearing() {
        gateway.accepts = false

        sender.pressKey(0, KeyMap.TAB)
        settle()

        assertEquals(2, trace.lines.size)
        assertTrue("00 00 2b 00 00 00 00 00" in trace.lines[0])
        assertTrue(trace.lines.all { "refused" in it })
        assertFalse(trace.lines.any { "accepted" in it })
    }

    @Test
    fun aRefusedPressIsStillFollowedByItsRelease() {
        gateway.accepts = false

        sender.pressKey(0, KeyMap.TAB)
        settle()

        assertEquals(listOf(press(0x2B), release), gateway.calls)
    }

    @Test
    fun mouseReportsAreRecordedInTheTraceToo() {
        sender.sendMouse(HidReports.BUTTON_LEFT, 5, -3)

        assertEquals(1, trace.lines.size)
        assertTrue("01 05 fd" in trace.lines[0])
    }

    @Test
    fun aBurstOfPressesProducesTwoReportsEachInTheOrderTheyWereMade() {
        val usages = listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A)

        usages.forEach { sender.pressKey(0, it) }
        settle()

        assertEquals(usages.flatMap { listOf(press(it), release) }, gateway.calls)
    }

    @Test
    fun theFirstReportGoesOutAtOnceAndLaterOnesWaitOnePauseEach() {
        val pauseMs = 25L
        val paced = ReportSender(gateway, trace, scheduler, pauseMs)

        repeat(3) { paced.pressKey(0, 0x04) }
        settle()

        assertEquals(listOf(0L, 25, 50, 75, 100, 125), gateway.times)
    }

    @Test
    fun consecutiveReportsAreNeverCloserThanThePauseEvenWhenTheyArriveInSeparateMoments() {
        val pauseMs = 7L
        val paced = ReportSender(gateway, trace, scheduler, pauseMs)

        paced.pressKey(0, 0x04)
        scheduler.advanceBy(3)
        paced.pressKey(0, 0x05)
        scheduler.advanceBy(1)
        paced.sendMouse(0, 1, 1)
        settle()

        assertEquals(5, gateway.calls.size)
        gateway.times.zipWithNext { earlier, later ->
            assertTrue("reports $earlier and $later are closer than ${pauseMs}ms", later - earlier >= pauseMs)
        }
    }

    @Test
    fun theDefaultPauseSeparatesReportsWhenNoneIsConfigured() {
        sender.pressKey(0, 0x04)
        settle()

        assertEquals(listOf(0L, ReportSender.DEFAULT_PAUSE_MS), gateway.times)
    }

    @Test
    fun aReportAfterQuietGoesOutAtOnce() {
        sender.pressKey(0, 0x04)
        settle()
        val quietUntil = scheduler.now

        sender.pressKey(0, 0x05)

        assertEquals(quietUntil, gateway.times.last())
        assertEquals(press(0x05), gateway.calls.last())
    }

    @Test
    fun clearingMidSequenceDropsTheKeysNotYetStartedButStillReleasesTheOneHeld() {
        sender.pressKey(0, 0x04)
        sender.pressKey(0, 0x05)
        sender.pressKey(0, 0x06)

        // Only the first press has gone out; its release is still waiting for the pause.
        assertEquals(listOf(press(0x04)), gateway.calls)
        sender.clear()
        settle()

        assertEquals(listOf(press(0x04), release), gateway.calls)
    }

    @Test
    fun theReleaseAfterClearingStillWaitsForThePause() {
        sender.pressKey(0, 0x04)
        sender.clear()
        settle()

        assertEquals(listOf(0L, ReportSender.DEFAULT_PAUSE_MS), gateway.times)
    }

    @Test
    fun clearingBetweenKeysDropsTheRestAndLeavesNothingHeld() {
        sender.pressKey(0, 0x04)
        sender.pressKey(0, 0x05)
        scheduler.advanceBy(ReportSender.DEFAULT_PAUSE_MS) // the first release has just gone out

        sender.clear()
        settle()

        assertEquals(listOf(press(0x04), release), gateway.calls)
    }

    @Test
    fun clearingNotesInTheTraceHowManyReportsWereDroppedUnsent() {
        sender.pressKey(0, 0x04)
        sender.pressKey(0, 0x05)
        sender.pressKey(0, 0x06)
        // Press 1 is out and its release is kept; presses 2 and 3 with their releases are dropped.
        sender.clear()

        assertTrue(trace.lines.last(), "dropped 4 unsent reports" in trace.lines.last())
    }

    @Test
    fun clearingWithNothingWaitingLeavesTheTraceAlone() {
        sender.pressKey(0, 0x04)
        settle()
        val before = trace.lines

        sender.clear()

        assertEquals(before, trace.lines)
    }

    @Test
    fun clearingAnIdleSenderDoesNothingAndKeepsItUsable() {
        sender.clear()
        sender.pressKey(0, 0x04)
        settle()

        assertEquals(listOf(press(0x04), release), gateway.calls)
    }

    @Test
    fun aPressWaitingBehindOtherReportsIsDroppedTogetherWithItsRelease() {
        sender.sendMouse(0, 1, 0)
        sender.pressKey(0, 0x04)
        sender.clear()
        settle()

        assertEquals(listOf(Call.Report(HidReports.ID_MOUSE, bytes(0, 1, 0))), gateway.calls)
    }

    @Test
    fun movesMadeWhileTheLinkRestsAreMergedIntoOneReport() {
        sender.moveMouse(0, 1, 1)
        sender.moveMouse(0, 2, -3)
        sender.moveMouse(0, 4, 5)
        settle()

        assertEquals(listOf(mouse(0, 1, 1), mouse(0, 6, 2)), gateway.calls)
    }

    @Test
    fun aMoveTooBigForOneReportIsSplitAndNothingIsLost() {
        sender.moveMouse(0, 300, -200)
        settle()

        assertEquals(listOf(mouse(0, 127, -127), mouse(0, 127, -73), mouse(0, 46, 0)), gateway.calls)
    }

    @Test
    fun aFastSwipeAddsUpToItsFullTravelInReportsTheLinkCanCarry() {
        val moves = List(500) { (it % 90) - 20 to 60 - (it % 70) }

        moves.forEach { (dx, dy) -> sender.moveMouse(0, dx, dy) }
        settle()

        assertEquals(moves.sumOf { it.first } to moves.sumOf { it.second }, movementSent())
        gateway.calls.filterIsInstance<Call.Report>().forEach { report ->
            assertTrue("${report.data} is beyond the report's limit", report.data.drop(1).all { it in -127..127 })
        }
    }

    @Test
    fun mergingStopsAtTheLimitAndTheRestGoesInTheNextReport() {
        sender.moveMouse(0, 1, 0) // goes out at once
        sender.moveMouse(0, 100, 0)
        sender.moveMouse(0, 100, 0)
        settle()

        assertEquals(listOf(mouse(0, 1, 0), mouse(0, 127, 0), mouse(0, 73, 0)), gateway.calls)
    }

    @Test
    fun movesThatCancelOutWhileWaitingSendNothing() {
        sender.moveMouse(0, 1, 0) // goes out at once
        sender.moveMouse(0, 5, -2)
        sender.moveMouse(0, -5, 2)
        settle()

        assertEquals(listOf(mouse(0, 1, 0)), gateway.calls)
    }

    @Test
    fun aMoveIsNeverMergedIntoAButtonChangeOrAcrossOne() {
        sender.moveMouse(0, 1, 0) // goes out at once
        sender.moveMouse(0, 2, 0)
        sender.sendMouse(HidReports.BUTTON_LEFT, 0, 0)
        sender.moveMouse(HidReports.BUTTON_LEFT, 3, 0)
        sender.moveMouse(HidReports.BUTTON_LEFT, 4, 0)
        sender.sendMouse(0, 0, 0)
        sender.moveMouse(0, 5, 0)
        settle()

        assertEquals(
            listOf(
                mouse(0, 1, 0),
                mouse(0, 2, 0),
                mouse(HidReports.BUTTON_LEFT, 0, 0),
                mouse(HidReports.BUTTON_LEFT, 7, 0),
                mouse(0, 0, 0),
                mouse(0, 5, 0),
            ),
            gateway.calls,
        )
    }

    @Test
    fun aMoveIsNeverMergedAcrossAKey() {
        sender.moveMouse(0, 1, 0) // goes out at once
        sender.moveMouse(0, 2, 0)
        sender.pressKey(0, 0x04)
        sender.moveMouse(0, 3, 0)
        settle()

        assertEquals(listOf(mouse(0, 1, 0), mouse(0, 2, 0), press(0x04), release, mouse(0, 3, 0)), gateway.calls)
    }

    @Test
    fun aStillMoveSendsNothing() {
        sender.moveMouse(0, 0, 0)
        settle()

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun keysAndMouseMovesKeepTheOrderTheyWereMadeIn() {
        sender.pressKey(0, 0x04)
        sender.sendMouse(0, 3, 4)
        sender.pressKey(0, 0x05)
        settle()

        assertEquals(
            listOf(
                press(0x04),
                release,
                Call.Report(HidReports.ID_MOUSE, bytes(0, 3, 4)),
                press(0x05),
                release,
            ),
            gateway.calls,
        )
    }
}
