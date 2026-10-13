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
 * The caller must hold the Bluetooth permission and have Bluetooth enabled before [start].
 * Registration only lasts while the app is in the foreground, so call [start] in onStart and
 * [stop] in onStop.
 */
@SuppressLint("MissingPermission")
class HidController(
    appContext: Context,
    private val listener: Listener,
) {
    // The proxy must outlive any single Activity instance.
    private val context: Context = appContext.applicationContext

    enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

    /** Why the Phone could not act as a HID device; shown to help diagnose unsupported phones. */
    enum class UnsupportedReason { NO_BLUETOOTH, PROFILE_REFUSED, NO_RESPONSE, REGISTER_REFUSED }

    interface Listener {
        /** The Phone's Bluetooth stack cannot act as a HID device. */
        fun onUnsupported(reason: UnsupportedReason)

        /** The Phone is advertised as a keyboard + mouse and can connect to Hosts. */
        fun onRegistered()

        fun onLinkChanged(host: BluetoothDevice, link: Link)
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var mouseButtons = 0
    private var started = false
    // Reports go out through this while a Host is connected.
    private var sender: ReportSender? = null
    private val trace = HidTrace { Log.d(HidTrace.TAG, it) }

    /** Short log of what the Bluetooth stack answered, shown on the Status Screen for bug reports. */
    val diagnostics: String get() = trace.text

    // Keeps the proxy open: a slow Bluetooth service may still answer after the verdict.
    private val unsupportedTimeout = Runnable {
        note("service silent after ${UNSUPPORTED_TIMEOUT_MS / 1000}s")
        listener.onUnsupported(UnsupportedReason.NO_RESPONSE)
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            note("app registered=$registered")
            if (registered) {
                listener.onRegistered()
            } else if (started) {
                listener.onUnsupported(UnsupportedReason.REGISTER_REFUSED)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    sender = hid?.let { ReportSender(BluetoothHidGateway(it, device), trace) }
                    listener.onLinkChanged(device, Link.CONNECTED)
                }
                BluetoothProfile.STATE_CONNECTING ->
                    listener.onLinkChanged(device, Link.CONNECTING)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host?.address == device.address) {
                        host = null
                        sender = null
                    }
                    mouseButtons = 0
                    listener.onLinkChanged(device, Link.DISCONNECTED)
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

    private fun requestsFrom(device: BluetoothDevice): HostRequests? {
        val service = hid
        if (service == null) {
            note("host request ignored: HID service gone")
            return null
        }
        return HostRequests(BluetoothHidGateway(service, device), trace)
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            note("service up")
            handler.removeCallbacks(unsupportedTimeout)
            val device = proxy as BluetoothHidDevice
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Layboard",
                "Phone keyboard and mouse",
                "Layboard",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReports.DESCRIPTOR,
            )
            val sent = device.registerApp(sdp, null, null, context.mainExecutor, callback)
            note("register sent=$sent")
            if (!sent) listener.onUnsupported(UnsupportedReason.REGISTER_REFUSED)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            sender = null
        }
    }

    fun start() {
        if (started) return
        trace.clear()
        val bluetooth = adapter
        if (bluetooth == null) {
            note("no adapter")
            listener.onUnsupported(UnsupportedReason.NO_BLUETOOTH)
            return
        }
        val requested = bluetooth.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        note("proxy requested=$requested")
        if (!requested) {
            listener.onUnsupported(UnsupportedReason.PROFILE_REFUSED)
            return
        }
        started = true
        // Some vendor stacks never answer when the HID Device service is missing.
        handler.postDelayed(unsupportedTimeout, UNSUPPORTED_TIMEOUT_MS)
    }

    fun stop() {
        handler.removeCallbacks(unsupportedTimeout)
        hid?.let {
            it.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hid = null
        host = null
        sender = null
        mouseButtons = 0
        started = false
    }

    private fun note(message: String) = trace.note(message)

    fun connect(device: BluetoothDevice) {
        hid?.connect(device)
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    /** Presses and releases [usage] together with [modifiers]. */
    fun sendKey(modifiers: Int, usage: Int) {
        connectedSender()?.pressKey(modifiers, usage)
    }

    /** Presses and releases modifier keys on their own, e.g. a lone Win/Cmd key. */
    fun sendModifierTap(modifiers: Int) = sendKey(modifiers, 0)

    fun moveMouse(dx: Int, dy: Int) {
        var remainingX = dx
        var remainingY = dy
        while (remainingX != 0 || remainingY != 0) {
            val stepX = remainingX.coerceIn(-MAX_STEP, MAX_STEP)
            val stepY = remainingY.coerceIn(-MAX_STEP, MAX_STEP)
            sendMouse(stepX, stepY)
            remainingX -= stepX
            remainingY -= stepY
        }
    }

    fun setMouseButton(mask: Int, down: Boolean) {
        mouseButtons = if (down) mouseButtons or mask else mouseButtons and mask.inv()
        sendMouse(0, 0)
    }

    private fun sendMouse(dx: Int, dy: Int) {
        connectedSender()?.sendMouse(mouseButtons, dx, dy)
    }

    private fun connectedSender(): ReportSender? {
        val connected = sender
        if (connected == null) note("report dropped: no Host connected")
        return connected
    }

    private companion object {
        const val MAX_STEP = 127
        const val UNSUPPORTED_TIMEOUT_MS = 10_000L
    }
}
