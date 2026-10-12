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

/**
 * Presents the Phone as a Bluetooth HID keyboard + mouse and sends reports to the connected Host.
 *
 * The caller must hold the Bluetooth permission and have Bluetooth enabled before [start].
 * Registration only lasts while the app is in the foreground, so call [start] in onStart and
 * [stop] in onStop.
 */
@SuppressLint("MissingPermission")
class HidController(
    private val context: Context,
    private val listener: Listener,
) {
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

    private val unsupportedTimeout = Runnable {
        stop()
        listener.onUnsupported(UnsupportedReason.NO_RESPONSE)
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) listener.onRegistered()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    listener.onLinkChanged(device, Link.CONNECTED)
                }
                BluetoothProfile.STATE_CONNECTING ->
                    listener.onLinkChanged(device, Link.CONNECTING)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host?.address == device.address) host = null
                    mouseButtons = 0
                    listener.onLinkChanged(device, Link.DISCONNECTED)
                }
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
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
            if (!device.registerApp(sdp, null, null, context.mainExecutor, callback)) {
                stop()
                listener.onUnsupported(UnsupportedReason.REGISTER_REFUSED)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) hid = null
        }
    }

    fun start() {
        if (started) return
        val bluetooth = adapter
        if (bluetooth == null) {
            listener.onUnsupported(UnsupportedReason.NO_BLUETOOTH)
            return
        }
        if (!bluetooth.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)) {
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
        mouseButtons = 0
        started = false
    }

    fun connect(device: BluetoothDevice) {
        hid?.connect(device)
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    /** Presses and releases [usage] together with [modifiers]. */
    fun sendKey(modifiers: Int, usage: Int) {
        sendKeyboard(modifiers, usage)
        sendKeyboard(0, 0)
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

    private fun sendKeyboard(modifiers: Int, usage: Int) {
        val target = host ?: return
        hid?.sendReport(target, HidReports.ID_KEYBOARD, HidReports.keyboard(modifiers, usage))
    }

    private fun sendMouse(dx: Int, dy: Int) {
        val target = host ?: return
        hid?.sendReport(target, HidReports.ID_MOUSE, HidReports.mouse(mouseButtons, dx, dy))
    }

    private companion object {
        const val MAX_STEP = 127
        const val UNSUPPORTED_TIMEOUT_MS = 10_000L
    }
}
