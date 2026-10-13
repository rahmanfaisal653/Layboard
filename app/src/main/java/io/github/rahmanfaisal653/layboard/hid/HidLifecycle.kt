package io.github.rahmanfaisal653.layboard.hid

/**
 * The Registration and Connection lifecycle as one state machine, driven through a [HidStack] so it
 * can be tested without Android. It replaces the vendor-specific workarounds that used to live in
 * `HidController`; each one is a named transition here, called out where it sits.
 *
 * [H] names a Host: a `BluetoothDevice` in the app, a plain string in tests.
 */
class HidLifecycle<H>(
    private val stack: HidStack<H>,
    private val scheduler: Scheduler,
    private val trace: HidTrace,
    private val listener: Listener<H>,
) {
    /** The live link between the Phone and a Host. */
    enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

    /** Why the Phone could not act as a HID device; shown to help diagnose unsupported phones. */
    enum class UnsupportedReason { NO_BLUETOOTH, PROFILE_REFUSED, NO_RESPONSE, REGISTER_REFUSED }

    /** What a screen reacts to, in the order the lifecycle reported it. */
    interface Listener<H> {
        fun onUnsupported(reason: UnsupportedReason)

        fun onRegistered()

        fun onLinkChanged(host: H, link: Link)
    }

    /** Where Registration stands; a timer that fires out of stage does nothing. */
    private enum class Stage { IDLE, AWAITING_PROFILE, AWAITING_REGISTRATION, REGISTERED }

    private var stage = Stage.IDLE
    private var started = false
    private var profileOpen = false
    private var connected: H? = null
    private var registerAttempt = 0
    private var pendingRelease = false
    private var startAfterRelease = false
    // Our own unregister() calls answer with registered=false, which is not a refusal.
    private var expectedUnregisters = 0

    /** Short log of what the Phone and the stack told each other, for the Status Screen. */
    val diagnostics: String get() = trace.text

    // Workaround 1: some vendor stacks never answer when the HID Device service is missing.
    private val profileTimeout: () -> Unit = {
        if (stage == Stage.AWAITING_PROFILE) {
            trace.note("service silent after ${UNSUPPORTED_TIMEOUT_MS / 1000}s")
            listener.onUnsupported(UnsupportedReason.NO_RESPONSE)
        }
    }

    private val registrationRetry: () -> Unit = {
        if (stage == Stage.AWAITING_REGISTRATION) register()
    }

    // Workaround 2: registerApp() can return false while the stack still registers (seen on
    // realme/ColorOS), so only the callback is trusted. Without one, the stack most likely still
    // holds the registration of an earlier process that died (that ROM never cleans it up): clear it
    // and retry once, then give up as busy.
    private val registrationTimeout: () -> Unit = {
        if (stage == Stage.AWAITING_REGISTRATION) {
            if (registerAttempt == 0) {
                trace.note("no answer after ${REGISTER_TIMEOUT_MS / 1000}s, clearing old registration")
                if (stack.unregister()) expectedUnregisters++
                registerAttempt = 1
                scheduler.postDelayed(CLEANUP_DELAY_MS, registrationRetry)
            } else {
                trace.note("no registration answer after retry")
                listener.onUnsupported(UnsupportedReason.REGISTER_REFUSED)
            }
        }
    }

    fun start() {
        if (started) return
        if (pendingRelease) {
            startAfterRelease = true
            return
        }
        trace.clear()
        if (!stack.hasAdapter) {
            trace.note("no adapter")
            listener.onUnsupported(UnsupportedReason.NO_BLUETOOTH)
            return
        }
        if (!stack.requestProfile()) {
            trace.note("profile refused")
            listener.onUnsupported(UnsupportedReason.PROFILE_REFUSED)
            return
        }
        started = true
        stage = Stage.AWAITING_PROFILE
        scheduler.postDelayed(UNSUPPORTED_TIMEOUT_MS, profileTimeout)
    }

    /** The stack answered the profile request; announce the Phone as a keyboard and mouse. */
    fun onProfileReady() {
        if (stage != Stage.AWAITING_PROFILE) return
        trace.note("service up")
        profileOpen = true
        register()
    }

    private fun register() {
        stage = Stage.AWAITING_REGISTRATION
        // The boolean is deliberately ignored: some ROMs answer false yet register anyway.
        stack.register()
        trace.note("register#${registerAttempt + 1}")
        scheduler.postDelayed(REGISTER_TIMEOUT_MS, registrationTimeout)
    }

    /** The stack's app-status callback: the only trusted word on whether the Phone is registered. */
    fun onRegistration(registered: Boolean) {
        trace.note("app registered=$registered")
        if (registered) {
            if (stage == Stage.REGISTERED) return
            stage = Stage.REGISTERED
            listener.onRegistered()
        } else if (expectedUnregisters > 0) {
            expectedUnregisters--
        } else if (started) {
            listener.onUnsupported(UnsupportedReason.REGISTER_REFUSED)
        }
    }

    /** A Host's link changed. A DISCONNECTED also finishes any release waiting on it. */
    fun onLink(host: H, link: Link) {
        if (link == Link.DISCONNECTED && pendingRelease) release()
        if (!started) return
        if (link == Link.CONNECTED) connected = host
        if (link == Link.DISCONNECTED && connected == host) connected = null
        listener.onLinkChanged(host, link)
    }

    /** Asks [host] to start the Connection. */
    fun connect(host: H) = stack.connect(host)

    /** Ends the Connection to the current Host. */
    fun disconnect() = connected?.let { stack.disconnect(it) }

    fun stop() {
        registerAttempt = 0
        expectedUnregisters = 0
        startAfterRelease = false
        val wasConnected = connected
        val wasOpen = profileOpen
        connected = null
        started = false
        stage = Stage.IDLE
        if (!wasOpen) return
        if (wasConnected == null) {
            release()
            return
        }
        // Workaround 3: while a Host is still connected this ROM ignores unregister() and keeps the
        // registration forever, so the next start is refused. Drop the link first and release once
        // it is down, or after a grace period if the drop never comes.
        stack.disconnect(wasConnected)
        pendingRelease = true
        scheduler.postDelayed(DISCONNECT_GRACE_MS) { if (pendingRelease) release() }
    }

    private fun release() {
        pendingRelease = false
        profileOpen = false
        stack.unregister()
        stack.closeProfile()
        if (startAfterRelease) {
            startAfterRelease = false
            start()
        }
    }

    private companion object {
        const val UNSUPPORTED_TIMEOUT_MS = 10_000L
        const val REGISTER_TIMEOUT_MS = 3_000L
        const val CLEANUP_DELAY_MS = 1_000L
        const val DISCONNECT_GRACE_MS = 1_000L
    }
}
