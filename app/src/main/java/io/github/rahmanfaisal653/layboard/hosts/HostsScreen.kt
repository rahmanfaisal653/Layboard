package io.github.rahmanfaisal653.layboard.hosts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import io.github.rahmanfaisal653.layboard.Prefs
import io.github.rahmanfaisal653.layboard.R

/** Lists paired Hosts, starts pairing a new one, and holds the Pointer Speed and Scroll Speed sliders. */
class HostsScreen(
    context: Context,
    prefs: Prefs,
    private val onHostSelected: (BluetoothDevice) -> Unit,
    onPairNew: () -> Unit,
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.screen_hosts, null)

    private val message: TextView = view.findViewById(R.id.hosts_message)
    private val empty: TextView = view.findViewById(R.id.hosts_empty)
    private val adapter = HostAdapter(context)

    init {
        view.findViewById<ListView>(R.id.hosts_list).also { list ->
            list.adapter = adapter
            list.setOnItemClickListener { _, _, position, _ -> onHostSelected(adapter.getItem(position)) }
        }
        view.findViewById<View>(R.id.hosts_pair).setOnClickListener { onPairNew() }
        bindSpeedSlider(R.id.hosts_speed, prefs.speedStep) { prefs.speedStep = it }
        bindSpeedSlider(R.id.hosts_scroll_speed, prefs.scrollSpeedStep) { prefs.scrollSpeedStep = it }
    }

    private fun bindSpeedSlider(id: Int, step: Int, remember: (Int) -> Unit) {
        view.findViewById<SeekBar>(id).apply {
            max = Prefs.MAX_SPEED_STEP
            progress = step
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) remember(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
    }

    fun bind(hosts: List<BluetoothDevice>, notice: String?) {
        adapter.items = hosts
        empty.visibility = if (hosts.isEmpty()) View.VISIBLE else View.GONE
        message.text = notice
        message.visibility = if (notice == null) View.GONE else View.VISIBLE
    }

    @SuppressLint("MissingPermission")
    private class HostAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        var items: List<BluetoothDevice> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): BluetoothDevice = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_host, parent, false)
            val host = items[position]
            row.findViewById<TextView>(R.id.host_name).text = host.name ?: host.address
            row.findViewById<TextView>(R.id.host_address).text = host.address
            return row
        }
    }
}
