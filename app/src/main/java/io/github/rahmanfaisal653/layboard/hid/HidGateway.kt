package io.github.rahmanfaisal653.layboard.hid

/**
 * The Phone's HID device as one connected Host sees it, and the only place report traffic crosses
 * into Android's Bluetooth stack. Every call returns whether the stack accepted it.
 */
interface HidGateway {
    /** Sends an input report of [id] to the Host. [data] is the report body without the ID byte. */
    fun sendReport(id: Int, data: ByteArray): Boolean

    /** Answers a Host's request for a report of [type] and [id]. */
    fun replyReport(type: Int, id: Int, data: ByteArray): Boolean

    /** Answers a Host's request with a handshake code; 0 means success. */
    fun reportError(error: Int): Boolean
}
