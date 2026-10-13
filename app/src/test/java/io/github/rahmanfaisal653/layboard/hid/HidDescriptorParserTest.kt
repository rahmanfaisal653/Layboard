package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertEquals
import org.junit.Test

/** The contract test is only as trustworthy as the parser, so the parser is checked on a descriptor written by hand. */
class HidDescriptorParserTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun sumsInputOutputAndFeatureBitsPerReportId() {
        val declared = HidDescriptorParser.parse(
            bytes(
                0x85, 0x01, // report ID 1
                0x75, 0x08, 0x95, 0x02, 0x81, 0x02, // input: 2 x 8 bits
                0x75, 0x01, 0x95, 0x03, 0x91, 0x02, // output: 3 x 1 bit
                0x85, 0x02, // report ID 2
                0x75, 0x10, 0x95, 0x01, 0xB1, 0x02, // feature: 1 x 16 bits
                0x75, 0x04, 0x95, 0x01, 0x81, 0x02, // input: 1 x 4 bits
                0x75, 0x04, 0x95, 0x01, 0x81, 0x02, // input: 1 x 4 bits more
            ),
        )

        assertEquals(mapOf(1 to 16, 2 to 8), declared.inputBits)
        assertEquals(mapOf(1 to 3), declared.outputBits)
        assertEquals(mapOf(2 to 16), declared.featureBits)
    }

    @Test
    fun readsMultiByteItemValues() {
        val declared = HidDescriptorParser.parse(
            bytes(0x85, 0x01, 0x76, 0x00, 0x01, 0x95, 0x01, 0x81, 0x02), // report size 256 (2-byte item)
        )

        assertEquals(mapOf(1 to 256), declared.inputBits)
    }
}
