package io.github.rahmanfaisal653.layboard.hid

import android.os.Handler

/** [Scheduler] on an Android [handler]; the Input Screen and the Bluetooth callbacks share its thread. */
class HandlerScheduler(private val handler: Handler) : Scheduler {
    override fun postDelayed(delayMs: Long, task: () -> Unit) {
        handler.postDelayed(task, delayMs)
    }
}
