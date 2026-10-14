package io.github.rahmanfaisal653.layboard.hid

/**
 * Whether the Host has switched high-resolution scrolling on, for the wheel and for the pan separately.
 * The Host does it by setting the descriptor's one-byte Resolution Multiplier feature report: bits 0-1
 * are the wheel's multiplier and bits 2-3 the pan's, each 0 for plain steps or 1 for
 * [HidReports.SCROLL_UNITS_PER_STEP] units per step. A Host that never sets it leaves both in plain steps.
 */
class ScrollResolution {
    var wheelHighRes = false
        private set
    var panHighRes = false
        private set

    /** The feature report body (without the report ID) answering a Host that asks what is set. */
    fun featureReport(): ByteArray =
        byteArrayOf(((if (wheelHighRes) WHEEL_BIT else 0) or (if (panHighRes) PAN_BIT else 0)).toByte())

    /**
     * Applies a feature report the Host set and returns whether it was valid; an invalid one changes
     * nothing. A report that starts with its report ID is read like one without, since a stack may
     * pass the ID along with the body.
     */
    fun apply(data: ByteArray): Boolean {
        val body = when {
            data.size == REPORT_SIZE -> data[0]
            data.size == REPORT_SIZE + 1 && data[0].toInt() == HidReports.ID_MOUSE -> data[1]
            else -> return false
        }.toInt()
        val wheel = body and MULTIPLIER_MASK
        val pan = (body shr PAN_SHIFT) and MULTIPLIER_MASK
        if (wheel > 1 || pan > 1 || body shr (2 * PAN_SHIFT) != 0) return false
        wheelHighRes = wheel == 1
        panHighRes = pan == 1
        return true
    }

    /** Back to plain steps, as every new Connection starts: the Host sets it again after connecting. */
    fun reset() {
        wheelHighRes = false
        panHighRes = false
    }

    companion object {
        /** The feature report's length in bytes, as the descriptor declares. */
        const val REPORT_SIZE = 1

        private const val WHEEL_BIT = 0x01
        private const val PAN_SHIFT = 2
        private const val PAN_BIT = 1 shl PAN_SHIFT
        private const val MULTIPLIER_MASK = 0x03
    }
}
