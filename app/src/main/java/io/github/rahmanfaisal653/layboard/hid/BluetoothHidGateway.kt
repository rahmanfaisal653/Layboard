package io.github.rahmanfaisal653.layboard.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice

/** [HidGateway] over Android's [BluetoothHidDevice], talking to one connected [host]. */
@SuppressLint("MissingPermission")
class BluetoothHidGateway(
    private val hid: BluetoothHidDevice,
    private val host: BluetoothDevice,
) : HidGateway {
    override fun sendReport(id: Int, data: ByteArray): Boolean = hid.sendReport(host, id, data)

    override fun replyReport(type: Int, id: Int, data: ByteArray): Boolean =
        hid.replyReport(host, type.toByte(), id.toByte(), data)

    override fun reportError(error: Int): Boolean = hid.reportError(host, error.toByte())
}
