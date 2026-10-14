package io.github.rahmanfaisal653.layboard.hid

/**
 * Reads a HID report descriptor the way a Host does and reports how many bits each report ID
 * declares, so tests can compare the declaration with the bytes the app really builds.
 */
object HidDescriptorParser {
    /** [usages] holds every usage named, alone or in a range, as [usage] encodes it. */
    data class Declared(
        val inputBits: Map<Int, Int>,
        val outputBits: Map<Int, Int>,
        val featureBits: Map<Int, Int>,
        val usages: Set<Int>,
        /** The physical minimum and maximum in force at each variable (not padding) Feature item, in order. */
        val featureRanges: List<Pair<Int, Int>>,
    )

    private const val TYPE_MAIN = 0
    private const val TYPE_GLOBAL = 1
    private const val TYPE_LOCAL = 2

    private const val MAIN_INPUT = 0x8
    private const val MAIN_OUTPUT = 0x9
    private const val MAIN_FEATURE = 0xB

    private const val GLOBAL_USAGE_PAGE = 0x0
    private const val GLOBAL_PHYSICAL_MINIMUM = 0x3
    private const val GLOBAL_PHYSICAL_MAXIMUM = 0x4
    private const val GLOBAL_REPORT_SIZE = 0x7
    private const val GLOBAL_REPORT_ID = 0x8
    private const val GLOBAL_REPORT_COUNT = 0x9

    private const val LOCAL_USAGE = 0x0
    private const val LOCAL_USAGE_MINIMUM = 0x1
    private const val LOCAL_USAGE_MAXIMUM = 0x2

    private const val MAIN_CONSTANT = 0x1

    private const val LONG_ITEM = 0xFE

    /** One number for a usage on a usage page, so usages from different pages never collide. */
    fun usage(page: Int, id: Int): Int = (page shl 16) or id

    fun parse(descriptor: ByteArray): Declared {
        val input = HashMap<Int, Int>()
        val output = HashMap<Int, Int>()
        val feature = HashMap<Int, Int>()
        val bitsByMainTag = mapOf(MAIN_INPUT to input, MAIN_OUTPUT to output, MAIN_FEATURE to feature)
        var reportId = 0
        var reportSize = 0
        var reportCount = 0
        val usages = HashSet<Int>()
        var usagePage = 0
        var usageMinimum = 0
        var physicalMinimum = 0
        var physicalMaximum = 0
        val featureRanges = ArrayList<Pair<Int, Int>>()

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
                    GLOBAL_USAGE_PAGE -> usagePage = value
                    GLOBAL_PHYSICAL_MINIMUM -> physicalMinimum = value
                    GLOBAL_PHYSICAL_MAXIMUM -> physicalMaximum = value
                    GLOBAL_REPORT_SIZE -> reportSize = value
                    GLOBAL_REPORT_ID -> reportId = value
                    GLOBAL_REPORT_COUNT -> reportCount = value
                }
                TYPE_LOCAL -> when (tag) {
                    LOCAL_USAGE -> usages += usage(usagePage, value)
                    LOCAL_USAGE_MINIMUM -> usageMinimum = value
                    LOCAL_USAGE_MAXIMUM -> (usageMinimum..value).forEach { usages += usage(usagePage, it) }
                }
                TYPE_MAIN -> {
                    bitsByMainTag[tag]?.merge(reportId, reportSize * reportCount, Int::plus)
                    if (tag == MAIN_FEATURE && value and MAIN_CONSTANT == 0) featureRanges += physicalMinimum to physicalMaximum
                }
            }
            i += 1 + dataSize
        }
        return Declared(input, output, feature, usages, featureRanges)
    }
}
