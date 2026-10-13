package io.github.rahmanfaisal653.layboard.hid

/**
 * Stands in for the Bluetooth stack: records what the app tells the Host and accepts or refuses it.
 * [now] stamps each call, so a test can check how far apart reports went out.
 */
class FakeHidGateway(private val now: () -> Long = { 0L }) : HidGateway {
    sealed class Call {
        data class Report(val id: Int, val data: List<Byte>) : Call()
        data class Reply(val type: Int, val id: Int, val data: List<Byte>) : Call()
        data class Error(val code: Int) : Call()
    }

    /** What the stack answers to every call, e.g. false when it refuses a report. */
    var accepts = true

    val calls = mutableListOf<Call>()

    /** When each entry of [calls] was made, in the same order. */
    val times = mutableListOf<Long>()

    override fun sendReport(id: Int, data: ByteArray): Boolean {
        record(Call.Report(id, data.toList()))
        return accepts
    }

    override fun replyReport(type: Int, id: Int, data: ByteArray): Boolean {
        record(Call.Reply(type, id, data.toList()))
        return accepts
    }

    override fun reportError(error: Int): Boolean {
        record(Call.Error(error))
        return accepts
    }

    /** Forgets every call and its time, so the two lists never drift apart. */
    fun clear() {
        calls.clear()
        times.clear()
    }

    private fun record(call: Call) {
        calls += call
        times += now()
    }
}
