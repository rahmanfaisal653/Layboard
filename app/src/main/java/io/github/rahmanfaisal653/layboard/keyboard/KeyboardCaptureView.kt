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

    /** Where the keyboard's typing goes; set by the Input Screen. */
    var typing: TypedText? = null

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
        typing?.attached()
        return Connection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = keyFor(event) ?: return super.onKeyDown(keyCode, event)
        typing?.key(key)
        return true
    }

    /** Forwards the keyboard's edits to [typing] and keeps the field itself empty. */
    private inner class Connection(view: View) : BaseInputConnection(view, false) {
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            typing?.commit(text)
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            typing?.compose(text)
            return true
        }

        override fun finishComposingText(): Boolean {
            typing?.finishComposing()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            typing?.deleteBefore(beforeLength)
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            typing?.deleteBefore(beforeLength)
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            typing?.key(Key(KeyMap.ENTER))
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) typing?.key(keyFor(event))
            return true
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
