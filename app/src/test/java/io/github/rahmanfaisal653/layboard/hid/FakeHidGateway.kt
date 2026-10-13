package io.github.rahmanfaisal653.layboard.hid

/** Stands in for the Bluetooth stack: records what the app tells the Host and accepts or refuses it. */
class FakeHidGateway : HidGateway {
    sealed class Call {
        data class Report(val id: Int, val data: List<Byte>) : Call()
        data class Reply(val type: Int, val id: Int, val data: List<Byte>) : Call()
        data class Error(val code: Int) : Call()
    }

    /** What the stack answers to every call, e.g. false when it refuses a report. */
    var accepts = true

    val calls = mutableListOf<Call>()

    override fun sendReport(id: Int, data: ByteArray): Boolean {
        calls += Call.Report(id, data.toList())
        return accepts
    }

    override fun replyReport(type: Int, id: Int, data: ByteArray): Boolean {
        calls += Call.Reply(type, id, data.toList())
        return accepts
    }

    override fun reportError(error: Int): Boolean {
        calls += Call.Error(error)
        return accepts
    }
}
