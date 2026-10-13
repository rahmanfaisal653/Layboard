package io.github.rahmanfaisal653.layboard.hid

/** A clock that only moves when a test says so, running posted tasks in time order. */
class FakeScheduler : Scheduler {
    private class Task(val at: Long, val run: () -> Unit)

    private val tasks = mutableListOf<Task>()

    /** The current time in milliseconds. */
    var now = 0L
        private set

    override fun postDelayed(delayMs: Long, task: () -> Unit) {
        tasks += Task(now + delayMs, task)
    }

    /** Moves time forward by [ms], running every task that falls due on the way, in order. */
    fun advanceBy(ms: Long) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { it.at <= end }.minByOrNull { it.at } ?: break
            tasks.remove(next)
            now = next.at
            next.run()
        }
        now = end
    }
}
