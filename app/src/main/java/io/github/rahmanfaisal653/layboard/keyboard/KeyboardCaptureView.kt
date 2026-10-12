package io.github.rahmanfaisal653.layboard.keyboard

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap

/**
 * An invisible text target for the Phone's standard Android keyboard. Whatever the keyboard types
 * is forwarded to the Host as key presses instead of being stored.
 */
class KeyboardCaptureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var sender: KeyboardSender? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Visible-password mode disables suggestions and autocorrect so each tap is one key.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            EditorInfo.IME_ACTION_NONE
        return Connection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = keyFor(event) ?: return super.onKeyDown(keyCode, event)
        sender?.press(key)
        return true
    }

    private inner class Connection(view: View) : BaseInputConnection(view, false) {
        /** Text the keyboard still treats as editable; kept so corrections can be diffed. */
        private var composing = ""

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            replaceComposing(text.toString())
            composing = ""
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            val next = text.toString()
            replaceComposing(next)
            composing = next
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { sender?.backspace() }
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            sender?.press(KeyMap.ENTER)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) keyFor(event)?.let { sender?.press(it) }
            return true
        }

        /** Types only what differs between the composing text already sent and [next]. */
        private fun replaceComposing(next: String) {
            val common = composing.commonPrefixWith(next).length
            repeat(composing.length - common) { sender?.backspace() }
            for (i in common until next.length) sender?.type(next[i])
        }
    }

    private fun keyFor(event: KeyEvent): Key? = when (event.keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Key(KeyMap.ENTER)
        KeyEvent.KEYCODE_DEL -> Key(KeyMap.BACKSPACE)
        KeyEvent.KEYCODE_FORWARD_DEL -> Key(KeyMap.DELETE)
        KeyEvent.KEYCODE_TAB -> Key(KeyMap.TAB)
        KeyEvent.KEYCODE_ESCAPE -> Key(KeyMap.ESCAPE)
        KeyEvent.KEYCODE_DPAD_LEFT -> Key(KeyMap.LEFT)
        KeyEvent.KEYCODE_DPAD_RIGHT -> Key(KeyMap.RIGHT)
        KeyEvent.KEYCODE_DPAD_UP -> Key(KeyMap.UP)
        KeyEvent.KEYCODE_DPAD_DOWN -> Key(KeyMap.DOWN)
        else -> event.unicodeChar.takeIf { it > 0 }?.let { KeyMap.fromChar(it.toChar()) }
    }
}
