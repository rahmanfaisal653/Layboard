package io.github.rahmanfaisal653.layboard.hid

/**
 * Reads a HID report descriptor the way a Host does and reports how many bits each report ID
 * declares, so tests can compare the declaration with the bytes the app really builds.
 */
object HidDescriptorParser {
    data class Declared(val inputBits: Map<Int, Int>, val outputBits: Map<Int, Int>, val featureBits: Map<Int, Int>)

    private const val TYPE_MAIN = 0
    private const val TYPE_GLOBAL = 1

    private const val MAIN_INPUT = 0x8
    private const val MAIN_OUTPUT = 0x9
    private const val MAIN_FEATURE = 0xB

    private const val GLOBAL_REPORT_SIZE = 0x7
    private const val GLOBAL_REPORT_ID = 0x8
    private const val GLOBAL_REPORT_COUNT = 0x9

    private const val LONG_ITEM = 0xFE

    fun parse(descriptor: ByteArray): Declared {
        val input = HashMap<Int, Int>()
        val output = HashMap<Int, Int>()
        val feature = HashMap<Int, Int>()
        val bitsByMainTag = mapOf(MAIN_INPUT to input, MAIN_OUTPUT to output, MAIN_FEATURE to feature)
        var reportId = 0
        var reportSize = 0
        var reportCount = 0

        var i = 0
        while (i < descriptor.size) {
            val prefix = descriptor[i].toInt() and 0xFF
            require(prefix != LONG_ITEM) { "long items are not used by this descriptor" }
            val dataSize = when (val code = prefix and 0x3) {
                3 -> 4
                else -> code
            }
            val type = (prefix shr 2) and 0x3
            val tag = prefix shr 4
            require(i + 1 + dataSize <= descriptor.size) { "descriptor ends inside an item at byte $i" }
            var value = 0
            for (n in 0 until dataSize) value = value or ((descriptor[i + 1 + n].toInt() and 0xFF) shl (8 * n))

            when (type) {
                TYPE_GLOBAL -> when (tag) {
                    GLOBAL_REPORT_SIZE -> reportSize = value
                    GLOBAL_REPORT_ID -> reportId = value
                    GLOBAL_REPORT_COUNT -> reportCount = value
                }
                TYPE_MAIN -> bitsByMainTag[tag]?.merge(reportId, reportSize * reportCount, Int::plus)
            }
            i += 1 + dataSize
        }
        return Declared(input, output, feature)
    }
}
