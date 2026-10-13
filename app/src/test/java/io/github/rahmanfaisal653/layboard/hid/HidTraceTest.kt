package io.github.rahmanfaisal653.layboard.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidTraceTest {
    @Test
    fun keepsOnlyTheMostRecentLinesInOrder() {
        val trace = HidTrace()

        repeat(500) { trace.note("line $it") }

        val kept = trace.lines.size
        assertTrue(kept in 1 until 500)
        assertEquals((500 - kept until 500).map { "line $it" }, trace.lines)
    }

    @Test
    fun everyLineIsAlsoPassedToTheLogEvenAfterTheTraceDroppedIt() {
        val logged = mutableListOf<String>()
        val trace = HidTrace(logged::add)

        repeat(500) { trace.note("line $it") }

        assertEquals((0 until 500).map { "line $it" }, logged)
    }

    @Test
    fun clearForgetsEarlierLines() {
        val trace = HidTrace()
        trace.note("old")

        trace.clear()
        trace.note("new")

        assertEquals(listOf("new"), trace.lines)
    }

    @Test
    fun textJoinsTheLinesForTheStatusScreen() {
        val trace = HidTrace()
        trace.note("one")
        trace.note("two")

        assertEquals("one; two", trace.text)
    }
}
