package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {
    @Test
    fun lowercaseLettersUseUnshiftedUsages() {
        assertEquals(Key(0x04), KeyMap.fromChar('a'))
        assertEquals(Key(0x1D), KeyMap.fromChar('z'))
    }

    @Test
    fun uppercaseLettersAddShift() {
        assertEquals(Key(0x04, HidReports.MOD_SHIFT), KeyMap.fromChar('A'))
    }

    @Test
    fun digitsMapToNumberRow() {
        assertEquals(Key(0x1E), KeyMap.fromChar('1'))
        assertEquals(Key(0x26), KeyMap.fromChar('9'))
        assertEquals(Key(0x27), KeyMap.fromChar('0'))
    }

    @Test
    fun shiftedNumberRowSymbols() {
        assertEquals(Key(0x1E, HidReports.MOD_SHIFT), KeyMap.fromChar('!'))
        assertEquals(Key(0x1F, HidReports.MOD_SHIFT), KeyMap.fromChar('@'))
        assertEquals(Key(0x27, HidReports.MOD_SHIFT), KeyMap.fromChar(')'))
    }

    @Test
    fun punctuationPairsShareAUsage() {
        assertEquals(Key(0x2D), KeyMap.fromChar('-'))
        assertEquals(Key(0x2D, HidReports.MOD_SHIFT), KeyMap.fromChar('_'))
        assertEquals(Key(0x38), KeyMap.fromChar('/'))
        assertEquals(Key(0x38, HidReports.MOD_SHIFT), KeyMap.fromChar('?'))
        assertEquals(Key(0x31), KeyMap.fromChar('\\'))
        assertEquals(Key(0x34, HidReports.MOD_SHIFT), KeyMap.fromChar('"'))
    }

    @Test
    fun whitespaceAndControlCharacters() {
        assertEquals(Key(KeyMap.SPACE), KeyMap.fromChar(' '))
        assertEquals(Key(KeyMap.ENTER), KeyMap.fromChar('\n'))
        assertEquals(Key(KeyMap.TAB), KeyMap.fromChar('\t'))
    }

    @Test
    fun charactersWithoutAUsKeyAreIgnored() {
        assertNull(KeyMap.fromChar('é'))
        assertNull(KeyMap.fromChar('€'))
    }

    @Test
    fun keyboardReportLayout() {
        assertArrayEquals(
            byteArrayOf(0x02, 0, 0x04, 0, 0, 0, 0, 0),
            HidReports.keyboard(HidReports.MOD_SHIFT, 0x04),
        )
    }

    @Test
    fun mouseReportEncodesSignedDeltas() {
        assertArrayEquals(
            byteArrayOf(HidReports.BUTTON_LEFT.toByte(), 5, (-3).toByte(), 2, (-1).toByte()),
            HidReports.mouse(HidReports.BUTTON_LEFT, 5, -3, 2, -1),
        )
    }
}
