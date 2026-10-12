package io.github.rahmanfaisal653.layboard.hid

/** A key on the Host: a HID keyboard usage plus the modifiers held with it. */
data class Key(val usage: Int, val modifiers: Int = 0)

/** Maps typed characters to HID keyboard usages for a US QWERTY layout. */
object KeyMap {
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val DELETE = 0x4C
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    private const val PLAIN = "-=[]\\;'`,./"
    private const val SHIFTED = "_+{}|:\"~<>?"
    private const val NUMBER_ROW_SHIFTED = "!@#\$%^&*()"
    private val SYMBOL_USAGES = intArrayOf(0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)

    private val symbols: Map<Char, Key> = HashMap<Char, Key>().apply {
        PLAIN.forEachIndexed { i, c -> put(c, Key(SYMBOL_USAGES[i])) }
        SHIFTED.forEachIndexed { i, c -> put(c, Key(SYMBOL_USAGES[i], HidReports.MOD_SHIFT)) }
        NUMBER_ROW_SHIFTED.forEachIndexed { i, c -> put(c, Key(0x1E + i, HidReports.MOD_SHIFT)) }
    }

    /** Returns the key for [c], or null when the character has no key on a US layout. */
    fun fromChar(c: Char): Key? = when (c) {
        in 'a'..'z' -> Key(0x04 + (c - 'a'))
        in 'A'..'Z' -> Key(0x04 + (c - 'A'), HidReports.MOD_SHIFT)
        in '1'..'9' -> Key(0x1E + (c - '1'))
        '0' -> Key(0x27)
        '\n' -> Key(ENTER)
        '\t' -> Key(TAB)
        ' ' -> Key(SPACE)
        else -> symbols[c]
    }
}
