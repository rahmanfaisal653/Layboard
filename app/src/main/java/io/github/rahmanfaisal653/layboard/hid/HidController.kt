package io.github.rahmanfaisal653.layboard.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Presents the Phone as a Bluetooth HID keyboard + mouse and sends reports to the connected Host.
 *
 * This is the thin Android edge of the HID stack: it turns [BluetoothHidDevice] callbacks into
 * [HidLifecycle] events and [HidLifecycle]'s stack calls back into the platform. The Registration and
 * Connection lifecycle itself — including the vendor-specific workarounds that used to live here — is
 * [HidLifecycle], so it is exercised without Android.
 *
 * The caller must hold the Bluetooth permission and have Bluetooth enabled before [start].
 * Registration only lasts while the app is in the foreground, so call [start] in onStart and
 * [stop] in onStop.
 */
@SuppressLint("MissingPermission")
class HidController(
    appContext: Context,
    listener: HidLifecycle.Listener<BluetoothDevice>,
) {
    // The proxy must outlive any single Activity instance.
    private val context: Context = appContext.applicationContext
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = HandlerScheduler(handler)
    private val trace = HidTrace { Log.d(HidTrace.TAG, it) }

    private var hid: BluetoothHidDevice? = null
    // Reports go out through this while a Host is connected.
    private var sender: ReportSender? = null
    private var senderHost: BluetoothDevice? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            lifecycle.onRegistration(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // A repeated CONNECTED for the same Host keeps its sender, so reports have one pause clock.
                    if (senderHost?.address != device.address) endReports()
                    senderHost = device
                    if (sender == null) {
                        sender = hid?.let { ReportSender(BluetoothHidGateway(it, device), trace, scheduler) }
                    }
                    lifecycle.onLink(device, HidLifecycle.Link.CONNECTED)
                }
                BluetoothProfile.STATE_CONNECTING ->
                    lifecycle.onLink(device, HidLifecycle.Link.CONNECTING)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (senderHost?.address == device.address) {
                        senderHost = null
                        endReports()
                    }
                    lifecycle.onLink(device, HidLifecycle.Link.DISCONNECTED)
                }
            }
        }

        // Every request a Host can make of a HID device gets an answer, not just connect and register.

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            requestsFrom(device)?.onGetReport(type.toInt(), id.toInt() and 0xFF, bufferSize)
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            requestsFrom(device)?.onSetReport(type.toInt(), id.toInt() and 0xFF, data)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            requestsFrom(device)?.onSetProtocol(protocol.toInt())
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            requestsFrom(device)?.onInterruptData(reportId.toInt() and 0xFF, data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            requestsFrom(device)?.onVirtualCableUnplug()
        }
    }

    private val serviceListener: BluetoothProfile.ServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            lifecycle.onProfileReady()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            endReports()
        }
    }

    // The platform calls HidLifecycle drives; the fake in tests stands in for this.
    private val stack: HidStack<BluetoothDevice> = object : HidStack<BluetoothDevice> {
        override val hasAdapter: Boolean get() = adapter != null

        override fun requestProfile(): Boolean =
            adapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) == true

        override fun register(): Boolean {
            val device = hid ?: return false
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Layboard",
                "Phone keyboard and mouse",
                "Layboard",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReports.DESCRIPTOR,
            )
            return device.registerApp(sdp, null, null, context.mainExecutor, callback)
        }

        override fun unregister(): Boolean = hid?.unregisterApp() == true

        override fun closeProfile() {
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }

        override fun connect(host: BluetoothDevice) {
            hid?.connect(host)
        }

        override fun disconnect(host: BluetoothDevice) {
            hid?.disconnect(host)
        }
    }

    private val lifecycle: HidLifecycle<BluetoothDevice> = HidLifecycle(stack, scheduler, trace, listener)

    /** Short log of what the Bluetooth stack answered, shown on the Status Screen for bug reports. */
    val diagnostics: String get() = trace.text

    /** Adds [message] to the same trace as the Bluetooth stack's answers, e.g. what the Phone keyboard did. */
    fun note(message: String) = trace.note(message)

    fun start() = lifecycle.start()

    fun stop() {
        endReports()
        lifecycle.stop()
    }

    /** Asks [device] to start the Connection. */
    fun connect(device: BluetoothDevice) = lifecycle.connect(device)

    /** Ends the Connection to the current Host. */
    fun disconnect() = lifecycle.disconnect()

    /** Presses and releases [usage] together with [modifiers]. */
    fun sendKey(modifiers: Int, usage: Int) {
        connectedSender()?.pressKey(modifiers, usage)
    }

    /** Presses and releases modifier keys on their own, e.g. a lone Win/Cmd key. */
    fun sendModifierTap(modifiers: Int) = sendKey(modifiers, 0)

    /** Moves the Host pointer by any amount; [ReportSender] merges and splits it into reports the link carries. */
    fun moveMouse(dx: Int, dy: Int) {
        connectedSender()?.moveMouse(dx, dy)
    }

    /** Presses or releases a Click Button; moves made while one is held drag on the Host. */
    fun setClickButton(mask: Int, down: Boolean) {
        connectedSender()?.setClickButton(mask, down)
    }

    private fun requestsFrom(device: BluetoothDevice): HostRequests? {
        val service = hid
        if (service == null) {
            trace.note("host request ignored: HID service gone")
            return null
        }
        return HostRequests(BluetoothHidGateway(service, device), trace)
    }

    // Reports still waiting are dropped; a key or Click Button already pressed is still released, so none stays held.
    private fun endReports() {
        sender?.clear()
        sender = null
    }

    private fun connectedSender(): ReportSender? {
        val connected = sender
        if (connected == null) trace.note("report dropped: no Host connected")
        return connected
    }
}
