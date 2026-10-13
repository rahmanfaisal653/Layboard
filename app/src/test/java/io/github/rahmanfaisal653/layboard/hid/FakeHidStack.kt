package io.github.rahmanfaisal653.layboard.hid

/**
 * Stands in for Android's Bluetooth stack, recording what [HidLifecycle] asks of it. A test drives
 * the stack's answers by calling the lifecycle's own event methods, so nothing here calls back.
 */
class FakeHidStack : HidStack<String> {
    /** What [hasAdapter] answers; false stands for a Phone with no Bluetooth. */
    var bluetoothPresent = true

    /** What [requestProfile] answers; false stands for a stack that refuses the HID Device profile. */
    var profileAccepted = true

    /** What [register] answers; some ROMs answer false yet register anyway. */
    var registerAccepted = true

    var profileRequests = 0
    var registerCalls = 0
    var unregisterCalls = 0
    var closeCalls = 0
    val connections = mutableListOf<String>()
    val disconnections = mutableListOf<String>()

    override val hasAdapter: Boolean get() = bluetoothPresent

    override fun requestProfile(): Boolean {
        profileRequests++
        return profileAccepted
    }

    override fun register(): Boolean {
        registerCalls++
        return registerAccepted
    }

    override fun unregister(): Boolean {
        unregisterCalls++
        return true
    }

    override fun closeProfile() {
        closeCalls++
    }

    override fun connect(host: String) {
        connections += host
    }

    override fun disconnect(host: String) {
        disconnections += host
    }
}
