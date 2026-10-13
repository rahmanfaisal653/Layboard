package io.github.rahmanfaisal653.layboard.keyboard

import io.github.rahmanfaisal653.layboard.hid.HidReports
import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedTextTest {
    private val pressed = mutableListOf<Key>()
    private val corrections = mutableListOf<Key>()
    private val notes = mutableListOf<String>()
    private val typing = TypedText(
        press = pressed::add,
        correct = { corrections += it; pressed += it },
        note = notes::add,
    )

    private val backspace = Key(KeyMap.BACKSPACE)

    private fun keysOf(text: String): List<Key> = text.map { KeyMap.fromChar(it)!! }

    @Test
    fun committedTextIsTypedInOrder() {
        typing.commit("Hi!")

        assertEquals(
            listOf(Key(0x0B, HidReports.MOD_SHIFT), Key(0x0C), Key(0x1E, HidReports.MOD_SHIFT)),
            pressed,
        )
    }

    @Test
    fun growingComposingTextSendsOnlyTheNewCharacters() {
        typing.compose("t")
        typing.compose("th")
        typing.compose("the")
        typing.commit("the")

        assertEquals(keysOf("the"), pressed)
    }

    @Test
    fun correctingComposingTextSendsBackspacesThenTheDifference() {
        typing.compose("teh")
        pressed.clear()

        typing.commit("the ")

        assertEquals(listOf(backspace, backspace) + keysOf("he "), pressed)
    }

    @Test
    fun backspacesFromACorrectionAreSentAsCorrectionsNotAsTheNextKey() {
        typing.compose("teh")
        typing.compose("the")

        assertEquals(listOf(backspace, backspace), corrections)
    }

    @Test
    fun backspacesTheKeyboardAsksForAreKeys() {
        typing.deleteBefore(1)

        assertTrue(corrections.isEmpty())
    }

    @Test
    fun shrinkingComposingTextSendsOnlyBackspaces() {
        typing.compose("cat")
        pressed.clear()

        typing.compose("c")

        assertEquals(listOf(backspace, backspace), pressed)
    }

    @Test
    fun finishedComposingTextIsNotCorrectedByTheNextWord() {
        typing.compose("one")
        typing.finishComposing()
        pressed.clear()

        typing.compose("two")

        assertEquals(keysOf("two"), pressed)
    }

    @Test
    fun aNewKeyboardAttachmentForgetsComposingText() {
        typing.compose("one")
        typing.attached()
        pressed.clear()

        typing.compose("two")

        assertEquals(keysOf("two"), pressed)
    }

    @Test
    fun charactersWithoutAUsKeyAreSkippedAndNeverBackspacedOver() {
        typing.compose("café")
        assertEquals(keysOf("caf"), pressed)
        pressed.clear()

        // The é never reached the Host, so replacing it with e must not erase the f.
        typing.commit("cafe")

        assertEquals(keysOf("e"), pressed)
    }

    @Test
    fun deletingBeforeTheCursorSendsOneBackspaceEach() {
        typing.deleteBefore(3)

        assertEquals(List(3) { backspace }, pressed)
    }

    @Test
    fun deletingInsideComposingTextShortensWhatTheHostHas() {
        typing.compose("abc")
        typing.deleteBefore(1)
        pressed.clear()

        typing.compose("abd")

        assertEquals(keysOf("d"), pressed)
    }

    @Test
    fun keysFromKeyEventsArePressedAndUnknownOnesIgnored() {
        typing.key(Key(KeyMap.ENTER))
        typing.key(null)

        assertEquals(listOf(Key(KeyMap.ENTER)), pressed)
    }

    @Test
    fun theTraceRecordsTextArrivingFromThePhoneKeyboard() {
        typing.attached()
        typing.compose("ab")
        typing.commit("abc")
        typing.deleteBefore(1)
        typing.key(Key(KeyMap.ENTER))

        assertEquals(
            listOf(
                "keyboard attached",
                "keyboard compose 2 chars",
                "keyboard commit 3 chars",
                "keyboard delete 1",
                "keyboard key 0x28",
            ),
            notes,
        )
    }

    @Test
    fun theTraceNamesCharactersIgnoredForHavingNoUsKey() {
        typing.commit("€é")

        assertTrue(pressed.isEmpty())
        assertEquals(listOf("keyboard commit 2 chars, 2 without a US key ignored"), notes)
    }

    @Test
    fun theTraceDoesNotRecordWhatWasTyped() {
        typing.commit("secret")

        assertTrue(notes.none { "secret" in it })
    }
}
