package io.github.rahmanfaisal653.layboard.keyboard

import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap

/**
 * Turns what the Phone's keyboard does to its text field into keys for the Host.
 *
 * A keyboard may rewrite the word being typed ("composing text") before it is final. Only the
 * difference from what was already sent goes out: Backspace for each character that changed, then
 * the new characters. Characters with no US key are skipped, and remembered as skipped, so a later
 * correction never backspaces over a character the Host did not get. Those Backspaces go to
 * [correct], not [press]: they fix text already sent rather than being a key the user pressed.
 *
 * Every event is also [note]d, by size rather than content to keep the trace short, so the trace
 * shows whether the keyboard is talking to Layboard at all.
 */
class TypedText(
    private val press: (Key) -> Unit,
    private val correct: (Key) -> Unit,
    private val note: (String) -> Unit,
) {
    /** The composing text as the Host received it: only the characters that had a US key. */
    private var sent = emptyList<Key>()

    /** The keyboard has (re)attached to the capture target; earlier composing text is final. */
    fun attached() {
        sent = emptyList()
        note("keyboard attached")
    }

    fun compose(text: CharSequence) {
        note("keyboard compose ${describe(text)}")
        replaceSent(text)
    }

    fun commit(text: CharSequence) {
        note("keyboard commit ${describe(text)}")
        replaceSent(text)
        sent = emptyList()
    }

    fun finishComposing() {
        sent = emptyList()
    }

    /** Deletes [count] characters before the cursor, which may be the end of the composing text. */
    fun deleteBefore(count: Int) {
        note("keyboard delete $count")
        repeat(count) { press(BACKSPACE) }
        sent = sent.dropLast(count)
    }

    /** A single key the keyboard sent as a key event; null when it has no key on the Host. */
    fun key(key: Key?) {
        if (key == null) {
            note("keyboard key ignored")
            return
        }
        note("keyboard key 0x%02x".format(key.usage))
        press(key)
    }

    private fun replaceSent(text: CharSequence) {
        val next = text.mapNotNull(KeyMap::fromChar)
        val common = sent.zip(next).takeWhile { (old, new) -> old == new }.size
        repeat(sent.size - common) { correct(BACKSPACE) }
        next.drop(common).forEach(press)
        sent = next
    }

    private fun describe(text: CharSequence): String {
        val ignored = text.count { KeyMap.fromChar(it) == null }
        val size = "${text.length} chars"
        return if (ignored == 0) size else "$size, $ignored without a US key ignored"
    }

    private companion object {
        val BACKSPACE = Key(KeyMap.BACKSPACE)
    }
}
