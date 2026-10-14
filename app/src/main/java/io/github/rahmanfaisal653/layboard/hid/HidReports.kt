package io.github.rahmanfaisal653.layboard.hid

/** HID report descriptor and report builders for a combined keyboard + relative mouse with a wheel and a horizontal pan. */
object HidReports {
    const val ID_KEYBOARD = 1
    const val ID_MOUSE = 2

    const val BUTTON_LEFT = 0x01
    const val BUTTON_RIGHT = 0x02

    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08

    private val DESCRIPTOR_INTS = intArrayOf(
        // Keyboard: 1 modifier byte, 1 reserved byte, 6 key slots.
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, ID_KEYBOARD,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        // Mouse: 3 buttons, relative X and Y, vertical wheel and horizontal pan (ADR 0002).
        0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, ID_MOUSE,
        0x09, 0x01, 0xA1, 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7F,
        0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
        0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, 0x81, 0x25, 0x7F,
        0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
        0xC0, 0xC0,
    )

    val DESCRIPTOR: ByteArray = ByteArray(DESCRIPTOR_INTS.size) { DESCRIPTOR_INTS[it].toByte() }

    /** How many keys besides the modifiers one keyboard report can hold down, as the descriptor declares. */
    const val KEY_SLOTS = 6

    /**
     * Keyboard report body (without the report ID). A usage of 0 means "no key pressed"; keys beyond
     * [KEY_SLOTS] do not fit and are left out.
     */
    fun keyboard(modifiers: Int, vararg usages: Int): ByteArray {
        val report = ByteArray(2 + KEY_SLOTS)
        report[0] = modifiers.toByte()
        usages.filter { it != 0 }.take(KEY_SLOTS).forEachIndexed { slot, usage -> report[2 + slot] = usage.toByte() }
        return report
    }

    /**
     * Mouse report body (without the report ID). Every distance must be within -127..127. A positive
     * [wheel] scrolls the Host's view up and a positive [pan] scrolls it to the right.
     */
    fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0): ByteArray =
        byteArrayOf(buttons.toByte(), dx.toByte(), dy.toByte(), wheel.toByte(), pan.toByte())
}
