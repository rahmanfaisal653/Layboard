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

    /** A keyboard report holding [modifiers] and up to six [usages]. */
    private fun keys(modifiers: Int, vararg usages: Int) =
        Call.Report(HidReports.ID_KEYBOARD, bytes(modifiers, 0, *(usages + IntArray(6 - usages.size))))

    private val ctrl = Key(0, HidReports.MOD_CTRL)
    private val alt = Key(0, HidReports.MOD_ALT)

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
        sender.moveMouse(5, -3)

        assertEquals(1, trace.lines.size)
        assertTrue("00 05 fd" in trace.lines[0])
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
        paced.moveMouse(1, 1)
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
        // Press 1 is out; everything behind it is dropped and a fresh release takes the place of its own.
        sender.clear()

        assertTrue(trace.lines.last(), "dropped 5 unsent reports" in trace.lines.last())
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
        sender.moveMouse(1, 0)
        sender.pressKey(0, 0x04)
        sender.clear()
        settle()

        assertEquals(listOf(Call.Report(HidReports.ID_MOUSE, bytes(0, 1, 0))), gateway.calls)
    }

    @Test
    fun movesMadeWhileTheLinkRestsAreMergedIntoOneReport() {
        sender.moveMouse(1, 1)
        sender.moveMouse(2, -3)
        sender.moveMouse(4, 5)
        settle()

        assertEquals(listOf(mouse(0, 1, 1), mouse(0, 6, 2)), gateway.calls)
    }

    @Test
    fun aMoveTooBigForOneReportIsSplitAndNothingIsLost() {
        sender.moveMouse(300, -200)
        settle()

        assertEquals(listOf(mouse(0, 127, -127), mouse(0, 127, -73), mouse(0, 46, 0)), gateway.calls)
    }

    @Test
    fun aFastSwipeAddsUpToItsFullTravelInReportsTheLinkCanCarry() {
        val moves = List(500) { (it % 90) - 20 to 60 - (it % 70) }

        moves.forEach { (dx, dy) -> sender.moveMouse(dx, dy) }
        settle()

        assertEquals(moves.sumOf { it.first } to moves.sumOf { it.second }, movementSent())
        gateway.calls.filterIsInstance<Call.Report>().forEach { report ->
            assertTrue("${report.data} is beyond the report's limit", report.data.drop(1).all { it in -127..127 })
        }
    }

    @Test
    fun mergingStopsAtTheLimitAndTheRestGoesInTheNextReport() {
        sender.moveMouse(1, 0) // goes out at once
        sender.moveMouse(100, 0)
        sender.moveMouse(100, 0)
        settle()

        assertEquals(listOf(mouse(0, 1, 0), mouse(0, 127, 0), mouse(0, 73, 0)), gateway.calls)
    }

    @Test
    fun movesThatCancelOutWhileWaitingSendNothing() {
        sender.moveMouse(1, 0) // goes out at once
        sender.moveMouse(5, -2)
        sender.moveMouse(-5, 2)
        settle()

        assertEquals(listOf(mouse(0, 1, 0)), gateway.calls)
    }

    @Test
    fun aMoveIsNeverMergedIntoAButtonChangeOrAcrossOne() {
        sender.moveMouse(1, 0) // goes out at once
        sender.moveMouse(2, 0)
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.moveMouse(3, 0)
        sender.moveMouse(4, 0)
        sender.setClickButton(HidReports.BUTTON_LEFT, false)
        sender.moveMouse(5, 0)
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
        sender.moveMouse(1, 0) // goes out at once
        sender.moveMouse(2, 0)
        sender.pressKey(0, 0x04)
        sender.moveMouse(3, 0)
        settle()

        assertEquals(listOf(mouse(0, 1, 0), mouse(0, 2, 0), press(0x04), release, mouse(0, 3, 0)), gateway.calls)
    }

    @Test
    fun aStillMoveSendsNothing() {
        sender.moveMouse(0, 0)
        settle()

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun eachClickButtonSendsAPressWhenPressedAndAReleaseWhenReleased() {
        listOf(HidReports.BUTTON_LEFT, HidReports.BUTTON_RIGHT).forEach { button ->
            gateway.clear()

            sender.setClickButton(button, true)
            sender.setClickButton(button, false)
            settle()

            assertEquals(listOf(mouse(button, 0, 0), mouse(0, 0, 0)), gateway.calls)
        }
    }

    @Test
    fun movingWhileLeftIsHeldDragsWithTheButtonHeldUntilItIsReleased() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.moveMouse(10, 0)
        settle()
        sender.moveMouse(0, -4)
        settle()
        sender.setClickButton(HidReports.BUTTON_LEFT, false)
        sender.moveMouse(3, 3)
        settle()

        assertEquals(
            listOf(
                mouse(HidReports.BUTTON_LEFT, 0, 0),
                mouse(HidReports.BUTTON_LEFT, 10, 0),
                mouse(HidReports.BUTTON_LEFT, 0, -4),
                mouse(0, 0, 0),
                mouse(0, 3, 3),
            ),
            gateway.calls,
        )
    }

    @Test
    fun aDragTooFastForOneReportKeepsTheButtonHeldInEveryPart() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.moveMouse(200, 0)
        settle()

        assertEquals(
            listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), mouse(HidReports.BUTTON_LEFT, 127, 0), mouse(HidReports.BUTTON_LEFT, 73, 0)),
            gateway.calls,
        )
    }

    @Test
    fun bothButtonsCanBeHeldAndReleasingOneKeepsTheOther() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.setClickButton(HidReports.BUTTON_RIGHT, true)
        sender.setClickButton(HidReports.BUTTON_LEFT, false)
        sender.moveMouse(2, 0)
        sender.setClickButton(HidReports.BUTTON_RIGHT, false)
        settle()

        assertEquals(
            listOf(
                mouse(HidReports.BUTTON_LEFT, 0, 0),
                mouse(HidReports.BUTTON_LEFT or HidReports.BUTTON_RIGHT, 0, 0),
                mouse(HidReports.BUTTON_RIGHT, 0, 0),
                mouse(HidReports.BUTTON_RIGHT, 2, 0),
                mouse(0, 0, 0),
            ),
            gateway.calls,
        )
    }

    @Test
    fun aButtonThatDoesNotChangeSendsNothing() {
        sender.setClickButton(HidReports.BUTTON_LEFT, false)
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()

        assertEquals(listOf(mouse(HidReports.BUTTON_LEFT, 0, 0)), gateway.calls)
    }

    @Test
    fun clearingWhileAButtonIsHeldOnTheHostStillReleasesIt() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.moveMouse(5, 0)
        sender.moveMouse(5, 0)

        // The press is out; the drag waiting behind it is dropped, but the Host is not left holding Left.
        sender.clear()
        settle()

        assertEquals(listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), mouse(0, 0, 0)), gateway.calls)
    }

    @Test
    fun clearingWhileAButtonRestsStillOnAnIdleLinkReleasesItAtOnce() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()

        sender.clear()

        assertEquals(listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), mouse(0, 0, 0)), gateway.calls)
    }

    @Test
    fun clearingDuringAKeyAndADragReleasesBoth() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()
        sender.pressKey(0, 0x04)

        sender.clear()
        settle()

        assertEquals(listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), press(0x04), release, mouse(0, 0, 0)), gateway.calls)
    }

    @Test
    fun clearingAPressNotYetSentSendsNoRelease() {
        sender.moveMouse(1, 0) // goes out at once
        sender.setClickButton(HidReports.BUTTON_LEFT, true)

        sender.clear()
        settle()

        assertEquals(listOf(mouse(0, 1, 0)), gateway.calls)
    }

    @Test
    fun theConnectionClosesOnlyAfterEveryReleaseHasGoneOut() {
        var sentWhenClosed: List<Call>? = null
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()
        sender.pressKey(HidReports.MOD_CTRL, 0x06)

        sender.clear { sentWhenClosed = gateway.calls.toList() }
        assertEquals("closed while a release was still waiting", null, sentWhenClosed)
        settle()

        assertEquals(
            listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), press(0x06, modifiers = 0x01), release, mouse(0, 0, 0)),
            sentWhenClosed,
        )
    }

    @Test
    fun theConnectionClosesOnePauseAfterTheLastRelease() {
        var closedAt: Long? = null
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()

        sender.clear { closedAt = scheduler.now }
        settle()

        assertEquals(gateway.times.last() + ReportSender.DEFAULT_PAUSE_MS, closedAt)
    }

    @Test
    fun withNothingHeldAndNothingSendingTheConnectionClosesAtOnce() {
        var closed = false
        sender.pressKey(0, 0x04)
        settle()

        sender.clear { closed = true }

        assertTrue(closed)
    }

    @Test
    fun theConnectionIsClosedOnlyOnce() {
        var closes = 0
        sender.pressKey(0, 0x04)
        sender.clear { closes++ }
        settle()
        sender.pressKey(0, 0x05)
        settle()

        assertEquals(1, closes)
    }

    @Test
    fun clearingNowSendsEveryReleaseAtOnceWithoutThePauses() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()
        sender.pressKey(0, 0x04)

        sender.clearNow()

        assertEquals(listOf(mouse(HidReports.BUTTON_LEFT, 0, 0), press(0x04), release, mouse(0, 0, 0)), gateway.calls)
        assertEquals(1, gateway.times.drop(1).distinct().size)
    }

    @Test
    fun clearingNowForgetsAnEarlierCloseBecauseTheCallerClosesItself() {
        var closed = false
        sender.pressKey(0, 0x04)
        sender.clear { closed = true }

        sender.clearNow()
        settle()

        assertEquals(listOf(press(0x04), release), gateway.calls)
        assertFalse(closed)
    }

    @Test
    fun aDropWhileLeftIsHeldReleasesItEvenIfTheHostNoLongerAnswers() {
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        settle()
        gateway.accepts = false

        sender.clear()
        settle()

        assertEquals(mouse(0, 0, 0), gateway.calls.last())
    }

    @Test
    fun keysAndMouseMovesKeepTheOrderTheyWereMadeIn() {
        sender.pressKey(0, 0x04)
        sender.moveMouse(3, 4)
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

    @Test
    fun eachSpecialKeyIsHeldFromTouchDownUntilTouchUp() {
        listOf(KeyMap.ESCAPE, KeyMap.TAB, KeyMap.LEFT, KeyMap.UP, KeyMap.DOWN, KeyMap.RIGHT).forEach { usage ->
            gateway.clear()

            sender.holdKey(Key(usage))
            settle()
            assertEquals(listOf(press(usage)), gateway.calls)

            sender.releaseKey(Key(usage))
            settle()
            assertEquals(listOf(press(usage), release), gateway.calls)
        }
    }

    @Test
    fun aHeldModifierIsHeldOnItsOwn() {
        sender.holdKey(ctrl)
        settle()
        sender.releaseKey(ctrl)
        settle()

        assertEquals(listOf(keys(HidReports.MOD_CTRL), release), gateway.calls)
    }

    @Test
    fun severalSpecialKeysCanBeHeldAtOnceAndLetGoInAnyOrder() {
        sender.holdKey(ctrl)
        sender.holdKey(alt)
        sender.holdKey(Key(KeyMap.RIGHT))
        sender.holdKey(Key(KeyMap.UP))
        sender.releaseKey(Key(KeyMap.RIGHT))
        sender.releaseKey(ctrl)
        sender.releaseKey(Key(KeyMap.UP))
        sender.releaseKey(alt)
        settle()

        assertEquals(
            listOf(
                keys(0x01),
                keys(0x05),
                keys(0x05, KeyMap.RIGHT),
                keys(0x05, KeyMap.RIGHT, KeyMap.UP),
                keys(0x05, KeyMap.UP),
                keys(0x04, KeyMap.UP),
                keys(0x04),
                release,
            ),
            gateway.calls,
        )
    }

    @Test
    fun aTypedKeyIsSentWithTheHeldModifiersWhichStayHeldAfterIt() {
        sender.holdKey(ctrl)
        sender.pressKey(0, 0x06)
        sender.pressKey(0, KeyMap.BACKSPACE)
        sender.releaseKey(ctrl)
        settle()

        assertEquals(
            listOf(
                keys(0x01),
                keys(0x01, 0x06),
                keys(0x01),
                keys(0x01, KeyMap.BACKSPACE),
                keys(0x01),
                release,
            ),
            gateway.calls,
        )
    }

    @Test
    fun aTypedKeysOwnShiftCombinesWithTheHeldModifiersForThatKeyOnly() {
        sender.holdKey(ctrl)
        sender.pressKey(HidReports.MOD_SHIFT, 0x1D)
        settle()

        assertEquals(listOf(keys(0x01), keys(0x03, 0x1D), keys(0x01)), gateway.calls)
    }

    @Test
    fun aTypedKeyLeavesAHeldArrowHeld() {
        sender.holdKey(Key(KeyMap.RIGHT))
        sender.pressKey(0, 0x04)
        settle()

        assertEquals(listOf(keys(0, KeyMap.RIGHT), keys(0, KeyMap.RIGHT, 0x04), keys(0, KeyMap.RIGHT)), gateway.calls)
    }

    @Test
    fun noReportCarriesMoreThanSixKeys() {
        val usages = listOf(KeyMap.ESCAPE, KeyMap.TAB, KeyMap.LEFT, KeyMap.UP, KeyMap.DOWN, KeyMap.RIGHT)
        usages.forEach { sender.holdKey(Key(it)) }
        sender.pressKey(0, 0x04)
        settle()

        gateway.calls.filterIsInstance<Call.Report>().forEach { assertEquals(8, it.data.size) }
        assertEquals(keys(0, *usages.toIntArray()), gateway.calls.last())
    }

    @Test
    fun holdingAKeyAlreadyHeldOrReleasingOneNotHeldSendsNothing() {
        sender.releaseKey(ctrl)
        sender.holdKey(ctrl)
        sender.holdKey(ctrl)
        settle()

        assertEquals(listOf(keys(0x01)), gateway.calls)
    }

    @Test
    fun aClickWhileAModifierIsHeldReachesTheHostAfterTheModifier() {
        sender.holdKey(ctrl)
        sender.setClickButton(HidReports.BUTTON_LEFT, true)
        sender.setClickButton(HidReports.BUTTON_LEFT, false)
        sender.releaseKey(ctrl)
        settle()

        assertEquals(
            listOf(keys(0x01), mouse(HidReports.BUTTON_LEFT, 0, 0), mouse(0, 0, 0), release),
            gateway.calls,
        )
    }

    @Test
    fun clearingReleasesEveryHeldKeyBeforeTheConnectionCloses() {
        var sentWhenClosed: List<Call>? = null
        sender.holdKey(ctrl)
        sender.holdKey(Key(KeyMap.RIGHT))
        settle()

        sender.clear { sentWhenClosed = gateway.calls.toList() }
        settle()

        assertEquals(listOf(keys(0x01), keys(0x01, KeyMap.RIGHT), release), sentWhenClosed)
    }

    @Test
    fun clearingForgetsTheHeldKeysSoTheirLaterTouchUpSendsNothing() {
        sender.holdKey(ctrl)
        settle()
        sender.clear()
        settle()
        gateway.clear()

        sender.releaseKey(ctrl)
        sender.pressKey(0, 0x04)
        settle()

        assertEquals(listOf(press(0x04), release), gateway.calls)
    }

    @Test
    fun clearingAHoldNotYetSentSendsNoRelease() {
        sender.moveMouse(1, 0) // goes out at once
        sender.holdKey(ctrl)

        sender.clear()
        settle()

        assertEquals(listOf(mouse(0, 1, 0)), gateway.calls)
    }

    @Test
    fun clearingNowReleasesHeldKeysAtOnce() {
        sender.holdKey(alt)
        settle()

        sender.clearNow()

        assertEquals(listOf(keys(0x04), release), gateway.calls)
    }
}
