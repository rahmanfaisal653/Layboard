package io.github.rahmanfaisal653.layboard.hid

/** Runs work later: the only way report sending touches time, so tests can drive it with a fake clock. */
interface Scheduler {
    /** Runs [task] once, [delayMs] milliseconds from now. */
    fun postDelayed(delayMs: Long, task: () -> Unit)
}
