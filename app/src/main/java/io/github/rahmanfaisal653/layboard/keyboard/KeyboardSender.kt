package io.github.rahmanfaisal653.layboard.keyboard

import io.github.rahmanfaisal653.layboard.hid.HidReports
import io.github.rahmanfaisal653.layboard.hid.Key

/**
 * Sends keys and clicks to the Host. Ctrl, Alt and Win are Armed modifiers: tapped first, then
 * applied to the next Keyboard key and cleared, which is how shortcuts like Ctrl+C or Alt+Tab are
 * built from separate taps. Clicking neither uses nor clears them.
 */
class KeyboardSender(
    private val sendKey: (modifiers: Int, usage: Int) -> Unit,
    private val setClickButton: (mask: Int, down: Boolean) -> Unit,
) {
    /** The Armed modifiers, as HID modifier bits. */
    var armed = 0
        private set

    var onArmedChanged: ((armed: Int) -> Unit)? = null

    /** Arms [modifier], or clears it when it is already armed. Sends nothing. */
    fun toggleModifier(modifier: Int) {
        setArmed(armed xor modifier)
    }

    /** Clears every Armed modifier, e.g. when the Connection ends. */
    fun disarm() {
        if (armed != 0) setArmed(0)
    }

    /** Sends Win on its own, e.g. to open the Start menu; Armed modifiers stay as they are. */
    fun tapWin() = sendKey(HidReports.MOD_GUI, 0)

    fun press(key: Key) {
        sendKey(key.modifiers or armed, key.usage)
        disarm()
    }

    fun press(usage: Int) {
        press(Key(usage))
    }

    /** Sends [key] to fix text already sent, e.g. a Backspace; Armed modifiers are left for the next key. */
    fun correct(key: Key) = sendKey(key.modifiers, key.usage)

    /** Presses or releases a Click Button; Armed modifiers are left for the next key. */
    fun click(mask: Int, down: Boolean) = setClickButton(mask, down)

    private fun setArmed(modifiers: Int) {
        armed = modifiers
        onArmedChanged?.invoke(modifiers)
    }
}
