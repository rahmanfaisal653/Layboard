package io.github.rahmanfaisal653.layboard.keyboard

import io.github.rahmanfaisal653.layboard.hid.HidController
import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap

/**
 * Sends keys to the Host. Ctrl, Alt and Win are "armed" first and then applied to the next key,
 * which is how shortcuts like Ctrl+C or Alt+Tab are built from separate taps.
 */
class KeyboardSender(private val hid: HidController) {
    private var armedModifiers = 0

    var onArmedChanged: ((armed: Int) -> Unit)? = null

    fun toggleModifier(modifier: Int) {
        armedModifiers = armedModifiers xor modifier
        onArmedChanged?.invoke(armedModifiers)
    }

    fun tapModifier(modifier: Int) = hid.sendModifierTap(modifier)

    fun type(c: Char) {
        KeyMap.fromChar(c)?.let { press(it) }
    }

    fun press(key: Key) {
        hid.sendKey(key.modifiers or armedModifiers, key.usage)
        if (armedModifiers != 0) {
            armedModifiers = 0
            onArmedChanged?.invoke(0)
        }
    }

    fun press(usage: Int) {
        press(Key(usage))
    }

    fun backspace() {
        press(KeyMap.BACKSPACE)
    }
}
