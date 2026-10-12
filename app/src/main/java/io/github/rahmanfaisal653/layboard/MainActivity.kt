package io.github.rahmanfaisal653.layboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import io.github.rahmanfaisal653.layboard.hid.HidController
import io.github.rahmanfaisal653.layboard.hosts.HostsScreen
import io.github.rahmanfaisal653.layboard.input.InputScreen
import io.github.rahmanfaisal653.layboard.status.StatusScreen

/**
 * Single activity that decides which of the three screens to show:
 * Status when something blocks use, Input while connected, Hosts otherwise.
 */
@SuppressLint("MissingPermission")
class MainActivity : Activity(), HidController.Listener {

    private lateinit var prefs: Prefs
    private lateinit var hid: HidController
    private lateinit var container: FrameLayout
    private lateinit var hostsScreen: HostsScreen
    private lateinit var statusScreen: StatusScreen
    private lateinit var inputScreen: InputScreen

    private val bluetooth: BluetoothAdapter? by lazy {
        getSystemService(BluetoothManager::class.java)?.adapter
    }

    private var unsupported = false
    private var unsupportedReason: HidController.UnsupportedReason? = null
    private var unsupportedDetail: String? = null
    private var permissionPermanentlyDenied = false
    private var autoConnectTried = false
    private var connectedHost: BluetoothDevice? = null
    private var pendingHost: BluetoothDevice? = null
    private var notice: String? = null

    private val bluetoothEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        hid = HidController(this, this)

        container = FrameLayout(this)
        setContentView(container)
        applyWindowInsets()

        hostsScreen = HostsScreen(this, prefs, ::connectTo, ::pairNewHost)
        statusScreen = StatusScreen(this)
        inputScreen = InputScreen(this, hid, onDisconnect = hid::disconnect)
    }

    override fun onStart() {
        super.onStart()
        unsupported = false
        autoConnectTried = false
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothEvents, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothEvents, filter)
        }
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(bluetoothEvents)
        // Android drops the HID registration in the background anyway; the Connection ends with it.
        hid.stop()
        connectedHost = null
        pendingHost = null
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionPermanentlyDenied = !hasBluetoothPermission() &&
            !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        refresh()
    }

    // HidController.Listener

    override fun onUnsupported(reason: HidController.UnsupportedReason) {
        unsupported = true
        unsupportedReason = reason
        unsupportedDetail = listOf(
            reason.name,
            hid.diagnostics,
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
        ).filter { it.isNotEmpty() }.joinToString("\n")
        // A silent service keeps its proxy open so a late answer can still recover.
        if (reason != HidController.UnsupportedReason.NO_RESPONSE) hid.stop()
        refresh()
    }

    override fun onRegistered() {
        if (unsupported) {
            unsupported = false
            refresh()
        }
        if (autoConnectTried) return
        autoConnectTried = true
        val address = prefs.lastHostAddress ?: return
        pairedHosts().firstOrNull { it.address == address }?.let(::connectTo)
    }

    override fun onLinkChanged(host: BluetoothDevice, link: HidController.Link) {
        when (link) {
            HidController.Link.CONNECTING -> notice = getString(R.string.connecting, nameOf(host))
            HidController.Link.CONNECTED -> {
                connectedHost = host
                pendingHost = null
                prefs.lastHostAddress = host.address
                notice = null
            }
            HidController.Link.DISCONNECTED -> {
                notice = when {
                    connectedHost?.address == host.address -> getString(R.string.disconnected)
                    pendingHost?.address == host.address -> getString(R.string.could_not_connect, nameOf(host))
                    else -> notice
                }
                if (connectedHost?.address == host.address) connectedHost = null
                if (pendingHost?.address == host.address) pendingHost = null
            }
        }
        refresh()
    }

    // Screen selection

    private fun refresh() {
        val adapter = bluetooth
        if (adapter == null || unsupported) {
            val busy = unsupportedReason == HidController.UnsupportedReason.REGISTER_REFUSED
            showStatus(
                if (busy) R.string.status_busy_title else R.string.status_unsupported_title,
                if (busy) R.string.status_busy_body else R.string.status_unsupported_body,
                R.string.action_try_again,
                ::retry,
                unsupportedDetail,
            )
            return
        }
        when {
            !hasBluetoothPermission() -> {
                hid.stop()
                val action = if (permissionPermanentlyDenied) R.string.action_open_settings else R.string.action_allow_bluetooth
                showStatus(R.string.status_permission_title, R.string.status_permission_body, action, ::askForPermission)
            }
            !adapter.isEnabled -> {
                hid.stop()
                showStatus(
                    R.string.status_off_title,
                    R.string.status_off_body,
                    R.string.action_turn_on_bluetooth,
                    onAction = {
                        startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE)
                    },
                )
            }
            else -> {
                hid.start()
                val host = connectedHost
                if (host != null) showInput(host) else showHosts()
            }
        }
    }

    private fun showStatus(title: Int, body: Int, action: Int?, onAction: (() -> Unit)?, detail: String? = null) {
        inputScreen.hideKeyboard()
        statusScreen.bind(title, body, action, onAction, detail)
        show(statusScreen.view)
    }

    private fun showHosts() {
        inputScreen.hideKeyboard()
        hostsScreen.bind(pairedHosts(), notice)
        show(hostsScreen.view)
    }

    private fun showInput(host: BluetoothDevice) {
        inputScreen.bind(nameOf(host), prefs.speedMultiplier)
        val newlyShown = show(inputScreen.view)
        if (newlyShown) inputScreen.view.post { inputScreen.showKeyboard() }
    }

    /** Swaps [screen] into the container; returns true when it was not already showing. */
    private fun show(screen: View): Boolean {
        if (container.getChildAt(0) === screen) return false
        container.removeAllViews()
        container.addView(screen)
        return true
    }

    // Actions

    private fun connectTo(host: BluetoothDevice) {
        pendingHost = host
        notice = getString(R.string.connecting, nameOf(host))
        hid.connect(host)
        refresh()
    }

    private fun pairNewHost() {
        val request = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        startActivityForResult(request, REQUEST_DISCOVERABLE)
    }

    private fun retry() {
        unsupported = false
        hid.stop()
        refresh()
    }

    private fun askForPermission() {
        if (permissionPermanentlyDenied) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_PERMISSION)
        }
    }

    // Helpers

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Bonded devices that could be a computer; falls back to every bonded device if none look like one. */
    private fun pairedHosts(): List<BluetoothDevice> {
        val bonded = bluetooth?.bondedDevices.orEmpty().toList()
        val computers = bonded.filter {
            val major = it.bluetoothClass?.majorDeviceClass
            major == null || major == BluetoothClass.Device.Major.COMPUTER || major == BluetoothClass.Device.Major.UNCATEGORIZED
        }
        return (computers.ifEmpty { bonded }).sortedBy { nameOf(it).lowercase() }
    }

    private fun nameOf(device: BluetoothDevice): String = device.name ?: device.address

    /**
     * Targeting Android 15 draws the app behind the system bars and the keyboard,
     * so the content has to be padded by hand.
     */
    private fun applyWindowInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        container.setOnApplyWindowInsetsListener { view, insets ->
            val visible = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(visible.left, visible.top, visible.right, visible.bottom)
            insets
        }
    }

    private companion object {
        const val REQUEST_PERMISSION = 1
        const val REQUEST_ENABLE = 2
        const val REQUEST_DISCOVERABLE = 3
        const val DISCOVERABLE_SECONDS = 120
    }
}
