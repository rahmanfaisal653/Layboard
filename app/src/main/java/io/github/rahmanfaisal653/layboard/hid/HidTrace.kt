package io.github.rahmanfaisal653.layboard.hid

/**
 * A short running record of what the Phone and the Bluetooth stack told each other, so a refused
 * report is visible instead of silent. Only the latest lines are kept; every line is also passed
 * to [log] as it happens.
 */
class HidTrace(private val log: (String) -> Unit = {}) {
    private val entries = ArrayDeque<String>()

    val lines: List<String> get() = entries.toList()

    /** The trace on one line, for the Status Screen detail. */
    val text: String get() = entries.joinToString("; ")

    fun note(message: String) {
        if (entries.size == MAX_LINES) entries.removeFirst()
        entries.addLast(message)
        log(message)
    }

    /** Records whether the stack accepted the report of [id] carrying [data]. */
    fun report(id: Int, data: ByteArray, accepted: Boolean) {
        val bytes = data.joinToString(" ") { "%02x".format(it) }
        note("report $id [$bytes] ${verdict(accepted)}")
    }

    fun clear() = entries.clear()

    companion object {
        /** The one tag every Layboard Bluetooth log line is written under. */
        const val TAG = "Layboard"

        private const val MAX_LINES = 40

        /** How the stack's answer to a call is worded in the trace. */
        fun verdict(accepted: Boolean): String = if (accepted) "accepted" else "refused"
    }
}
