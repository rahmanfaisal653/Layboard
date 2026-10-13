package io.github.rahmanfaisal653.layboard.hid

import io.github.rahmanfaisal653.layboard.hid.HidLifecycle.Link
import io.github.rahmanfaisal653.layboard.hid.HidLifecycle.UnsupportedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Registration and Connection lifecycle as one state machine, driven through the fake stack and
 * read back as the outcomes a screen would show. The scenarios are the ones issue #5 asks for, and
 * the last few pin down each vendor-specific workaround the machine carries.
 */
class HidLifecycleTest {
    private val stack = FakeHidStack()
    private val scheduler = FakeScheduler()
    private val listener = RecordingListener()
    private val lifecycle = HidLifecycle(stack, scheduler, HidTrace(), listener)

    private val host = "AA:BB:CC:DD:EE:FF"

    // Registration

    @Test
    fun aNormalRegistrationIsAnnouncedAndReported() {
        lifecycle.start()
        assertEquals(1, stack.profileRequests)

        lifecycle.onProfileReady()
        assertEquals(1, stack.registerCalls)

        lifecycle.onRegistration(true)

        assertEquals(listOf(Outcome.Registered), listener.outcomes)
    }

    @Test
    fun aServiceThatNeverAnswersIsReportedAsUnsupported() {
        lifecycle.start()

        scheduler.advanceBy(10_000)

        assertEquals(listOf(Outcome.Unsupported(UnsupportedReason.NO_RESPONSE)), listener.outcomes)
    }

    @Test
    fun aPhoneWithoutBluetoothIsReportedAsUnsupported() {
        stack.bluetoothPresent = false

        lifecycle.start()

        assertEquals(listOf(Outcome.Unsupported(UnsupportedReason.NO_BLUETOOTH)), listener.outcomes)
    }

    @Test
    fun aProfileTheStackRefusesIsReportedAsUnsupported() {
        stack.profileAccepted = false

        lifecycle.start()

        assertEquals(listOf(Outcome.Unsupported(UnsupportedReason.PROFILE_REFUSED)), listener.outcomes)
    }

    @Test
    fun aRefusedRegistrationIsReportedAsBusy() {
        lifecycle.start()
        lifecycle.onProfileReady()

        lifecycle.onRegistration(false)

        assertEquals(listOf(Outcome.Unsupported(UnsupportedReason.REGISTER_REFUSED)), listener.outcomes)
    }

    @Test
    fun afterTryAgainARefusedRegistrationIsAccepted() {
        lifecycle.start()
        lifecycle.onProfileReady()
        lifecycle.onRegistration(false)

        lifecycle.stop()
        lifecycle.start()
        lifecycle.onProfileReady()
        lifecycle.onRegistration(true)

        assertEquals(
            listOf(Outcome.Unsupported(UnsupportedReason.REGISTER_REFUSED), Outcome.Registered),
            listener.outcomes,
        )
        assertEquals(2, stack.profileRequests)
    }

    @Test
    fun aSlowServiceThatAnswersLateStillRegisters() {
        lifecycle.start()
        scheduler.advanceBy(10_000)

        lifecycle.onProfileReady()
        lifecycle.onRegistration(true)

        assertEquals(
            listOf(Outcome.Unsupported(UnsupportedReason.NO_RESPONSE), Outcome.Registered),
            listener.outcomes,
        )
    }

    @Test
    fun aRegisteredPhoneIsNotRegisteredAgainOnItsOwn() {
        registerNormally()

        scheduler.advanceBy(60_000)

        assertEquals(1, stack.registerCalls)
    }

    @Test
    fun registrationIsReportedOnceEvenIfTheStackAnswersTwice() {
        registerNormally()

        lifecycle.onRegistration(true)

        assertEquals(1, listener.outcomes.count { it == Outcome.Registered })
    }

    // Connection

    @Test
    fun aConnectionStartedByTheHostIsReported() {
        registerNormally()

        lifecycle.onLink(host, Link.CONNECTED)

        assertEquals(
            listOf(Outcome.Registered, Outcome.LinkChanged(host, Link.CONNECTED)),
            listener.outcomes,
        )
    }

    @Test
    fun aHostDisconnectIsReportedSoTheHostsScreenCanShowANotice() {
        registerNormally()
        lifecycle.onLink(host, Link.CONNECTED)

        lifecycle.onLink(host, Link.DISCONNECTED)

        assertEquals(
            listOf(
                Outcome.Registered,
                Outcome.LinkChanged(host, Link.CONNECTED),
                Outcome.LinkChanged(host, Link.DISCONNECTED),
            ),
            listener.outcomes,
        )
    }

    // Leaving and coming back

    @Test
    fun leavingWhileConnectedDropsTheLinkBeforeReleasing() {
        registerNormally()
        lifecycle.onLink(host, Link.CONNECTED)

        lifecycle.stop()

        assertEquals(listOf(host), stack.disconnections)
        assertEquals("release must wait for the link to drop", 0, stack.unregisterCalls)

        lifecycle.onLink(host, Link.DISCONNECTED)

        assertEquals(1, stack.unregisterCalls)
        assertEquals(1, stack.closeCalls)
    }

    @Test
    fun aHostThatNeverReportsTheDropIsReleasedAfterAGracePeriod() {
        registerNormally()
        lifecycle.onLink(host, Link.CONNECTED)
        lifecycle.stop()

        scheduler.advanceBy(1_000)

        assertEquals(1, stack.unregisterCalls)
        assertEquals(1, stack.closeCalls)
    }

    @Test
    fun comingBackWhileAReleaseIsStillPendingRegistersAgain() {
        registerNormally()
        lifecycle.onLink(host, Link.CONNECTED)
        lifecycle.stop()

        lifecycle.start()
        lifecycle.onLink(host, Link.DISCONNECTED)

        assertEquals(2, stack.profileRequests)
    }

    @Test
    fun leavingWithoutAConnectionReleasesStraightAway() {
        registerNormally()

        lifecycle.stop()

        assertEquals(1, stack.unregisterCalls)
        assertEquals(1, stack.closeCalls)
    }

    // The vendor-specific workarounds, each as one named transition

    @Test
    fun theRegisterCallReturnValueIsIgnoredBecauseSomeRomsLie() {
        stack.registerAccepted = false

        registerNormally()

        assertTrue(Outcome.Registered in listener.outcomes)
    }

    @Test
    fun anUnansweredRegistrationIsClearedAndRetriedOnce() {
        lifecycle.start()
        lifecycle.onProfileReady()

        scheduler.advanceBy(4_000)

        assertEquals("the stale registration is cleared", 1, stack.unregisterCalls)
        assertEquals("and the registration is sent once more", 2, stack.registerCalls)

        lifecycle.onRegistration(true)

        assertTrue(Outcome.Registered in listener.outcomes)
    }

    @Test
    fun aRegistrationStillUnansweredAfterTheRetryIsBusy() {
        lifecycle.start()
        lifecycle.onProfileReady()

        scheduler.advanceBy(7_000)

        assertEquals(listOf(Outcome.Unsupported(UnsupportedReason.REGISTER_REFUSED)), listener.outcomes)
    }

    /** Drives the lifecycle through a normal Registration, leaving it ready for a Host. */
    private fun registerNormally() {
        lifecycle.start()
        lifecycle.onProfileReady()
        lifecycle.onRegistration(true)
    }

    /** The outcomes a screen would react to, in the order the lifecycle reported them. */
    private sealed interface Outcome {
        data class Unsupported(val reason: UnsupportedReason) : Outcome
        data object Registered : Outcome
        data class LinkChanged(val host: String, val link: Link) : Outcome
    }

    private class RecordingListener : HidLifecycle.Listener<String> {
        val outcomes = mutableListOf<Outcome>()

        override fun onUnsupported(reason: UnsupportedReason) {
            outcomes += Outcome.Unsupported(reason)
        }

        override fun onRegistered() {
            outcomes += Outcome.Registered
        }

        override fun onLinkChanged(host: String, link: Link) {
            outcomes += Outcome.LinkChanged(host, link)
        }
    }
}
