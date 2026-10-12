package io.github.rahmanfaisal653.layboard.status

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import io.github.rahmanfaisal653.layboard.R

/** Explains why the Input Screen is unavailable and offers one action to fix it. */
class StatusScreen(context: Context) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.screen_status, null)

    private val title: TextView = view.findViewById(R.id.status_title)
    private val body: TextView = view.findViewById(R.id.status_body)
    private val detail: TextView = view.findViewById(R.id.status_detail)
    private val action: Button = view.findViewById(R.id.status_action)

    fun bind(titleRes: Int, bodyRes: Int, actionRes: Int?, onAction: (() -> Unit)?, detailText: String? = null) {
        title.setText(titleRes)
        body.setText(bodyRes)
        detail.text = detailText
        detail.visibility = if (detailText == null) View.GONE else View.VISIBLE
        if (actionRes == null || onAction == null) {
            action.visibility = View.GONE
        } else {
            action.setText(actionRes)
            action.setOnClickListener { onAction() }
            action.visibility = View.VISIBLE
        }
    }
}
