package io.github.rahmanfaisal653.layboard.keyboard

import io.github.rahmanfaisal653.layboard.hid.HidReports
import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSenderTest {
    private val keys = mutableListOf<Key>()
    private val clicks = mutableListOf<Pair<Int, Boolean>>()
    private val lit = mutableListOf<Int>()
    private val sender = KeyboardSender(
        sendKey = { modifiers, usage -> keys += Key(usage, modifiers) },
        setClickButton = { mask, down -> clicks += mask to down },
    ).apply { onArmedChanged = { lit += it } }

    private val c = KeyMap.fromChar('c')!!

    @Test
    fun anArmedModifierAppliesToTheNextKeyAndThenClears() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.press(c)
        sender.press(c)

        assertEquals(listOf(Key(c.usage, HidReports.MOD_CTRL), c), keys)
        assertEquals(listOf(HidReports.MOD_CTRL, 0), lit)
        assertEquals(0, sender.armed)
    }

    @Test
    fun anArmedModifierAppliesToASpecialKeyToo() {
        sender.toggleModifier(HidReports.MOD_ALT)
        sender.press(KeyMap.TAB)

        assertEquals(listOf(Key(KeyMap.TAB, HidReports.MOD_ALT)), keys)
    }

    @Test
    fun severalArmedModifiersAllApplyToTheNextKey() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.toggleModifier(HidReports.MOD_ALT)
        sender.press(KeyMap.DELETE)

        assertEquals(listOf(Key(KeyMap.DELETE, HidReports.MOD_CTRL or HidReports.MOD_ALT)), keys)
    }

    @Test
    fun armedModifiersCombineWithTheKeysOwnShift() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.press(KeyMap.fromChar('Z')!!)

        val z = KeyMap.fromChar('z')!!
        assertEquals(listOf(Key(z.usage, HidReports.MOD_CTRL or HidReports.MOD_SHIFT)), keys)
    }

    @Test
    fun tappingALitModifierAgainClearsItWithoutSendingAnything() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.press(c)

        assertEquals(listOf(c), keys)
        assertEquals(listOf(HidReports.MOD_CTRL, 0), lit)
    }

    @Test
    fun clickingNeitherUsesNorClearsAnArmedModifier() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.click(HidReports.BUTTON_LEFT, true)
        sender.click(HidReports.BUTTON_LEFT, false)

        assertEquals(listOf(HidReports.BUTTON_LEFT to true, HidReports.BUTTON_LEFT to false), clicks)
        assertTrue(keys.isEmpty())
        assertEquals(listOf(HidReports.MOD_CTRL), lit)

        sender.press(c)
        assertEquals(listOf(Key(c.usage, HidReports.MOD_CTRL)), keys)
    }

    @Test
    fun aLongPressOnWinSendsWinAlone() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.tapWin()

        assertEquals(listOf(Key(0, HidReports.MOD_GUI)), keys)
        assertEquals(HidReports.MOD_CTRL, sender.armed)
    }

    @Test
    fun aCorrectionIsSentWithoutTheArmedModifierAndLeavesItForTheNextKey() {
        val backspace = Key(KeyMap.BACKSPACE)
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.correct(backspace)
        sender.press(c)

        assertEquals(listOf(backspace, Key(c.usage, HidReports.MOD_CTRL)), keys)
    }

    @Test
    fun disarmingClearsEveryArmedModifier() {
        sender.toggleModifier(HidReports.MOD_CTRL)
        sender.toggleModifier(HidReports.MOD_GUI)
        sender.disarm()
        sender.press(c)

        assertEquals(listOf(c), keys)
        assertEquals(0, lit.last())
    }

    @Test
    fun disarmingWithNothingArmedChangesNothing() {
        sender.disarm()

        assertTrue(lit.isEmpty())
    }
}
