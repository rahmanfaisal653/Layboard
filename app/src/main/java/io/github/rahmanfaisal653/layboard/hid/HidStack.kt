package io.github.rahmanfaisal653.layboard.hid

/**
 * The part of Android's Bluetooth stack that [HidLifecycle] drives: asking for the HID Device
 * profile, announcing the Phone as a keyboard and mouse (Registration), and the Connection to a
 * Host. It is the seam a fake stands in for, so the lifecycle can be tested without Android.
 *
 * [H] is whatever the stack uses to name a Host: a `BluetoothDevice` in the app, a plain string in
 * tests.
 */
interface HidStack<H> {
    /** False when the Phone has no Bluetooth adapter at all. */
    val hasAdapter: Boolean

    /** Asks the stack for the HID Device profile; false when it refuses outright. */
    fun requestProfile(): Boolean

    /** Announces the Phone as a keyboard and mouse; false when the call itself is refused. */
    fun register(): Boolean

    /** Drops the Registration; false when the call itself is refused. */
    fun unregister(): Boolean

    /** Closes the profile opened by [requestProfile]. */
    fun closeProfile()

    /** Asks [host] to start the Connection. */
    fun connect(host: H)

    /** Ends the Connection to [host]. */
    fun disconnect(host: H)
}
