package dev.jbiffis.caddie.ble

/**
 * Garmin "live golf" service — Smart-message field 7. Reverse-engineered from an
 * HCI capture of the Garmin Golf app during a round.
 *
 * While a round is in progress the phone polls the watch and the watch answers with
 * the entire current scorecard as a golf FIT file (the same 190/192/193 messages the
 * app already parses). One update cycle:
 *
 *   phone -> watch  7:{ 3:{ 1:seq } }                 poll ("send current scorecard")
 *   watch -> phone  7:{ 4:{ 1:seq, 2:1 } }            poll ack
 *   watch -> phone  7:{ 5:{ 1:seq, 2:0, 3:<FIT> } }   the scorecard, as a FIT blob
 *   phone -> watch  7:{ 6:{ 1:seq, 2:1 } }            receipt ack
 *   watch -> phone  7:{ 8:{ 1:n } }                   transfer descriptor (secondary)
 *   phone -> watch  7:{ 9:{ 1:1, 2:n, 3:{1:id,2:hash} } }
 *
 * Each pushed FIT is freshly generated (its file_id timestamp changes every push),
 * so a round is identified by its stable start time (mesg 190 field 3), not file_id.
 */
object GolfLive {
    const val SMART_GOLF = 7

    // Sub-message field numbers under service 7
    private const val POLL = 3        // phone->watch { 1:seq }
    private const val POLL_ACK = 4    // watch->phone { 1:seq, 2:1 }
    private const val PUSH = 5        // watch->phone { 1:seq, 2:0, 3:FIT }
    private const val RECV_ACK = 6    // phone->watch { 1:seq, 2:1 }
    private const val XFER = 8        // watch->phone { 1:n }
    private const val XFER_ACK = 9    // phone->watch { 1:1, 2:n, 3:{1:id,2:hash} }

    private fun smart(field: Int, sub: Protobuf.Writer): ByteArray =
        Protobuf.Writer().message(SMART_GOLF, Protobuf.Writer().message(field, sub)).toByteArray()

    /** Ask the watch for the current scorecard: Smart{ 7:{ 3:{ 1:seq } } }. */
    fun buildPoll(seq: Int): ByteArray = smart(POLL, Protobuf.Writer().varint(1, seq.toLong()))

    /** Acknowledge a received scorecard push: Smart{ 7:{ 6:{ 1:seq, 2:1 } } }. */
    fun buildReceiveAck(seq: Long): ByteArray =
        smart(RECV_ACK, Protobuf.Writer().varint(1, seq).varint(2, 1))

    /** Reply to the secondary transfer descriptor: Smart{ 7:{ 9:{ 1:1, 2:n, 3:{..} } } }. */
    fun buildXferAck(n: Long, id: Long, hash: Long): ByteArray {
        val idMsg = Protobuf.Writer().fixed64(1, id).fixed64(2, hash)
        return smart(XFER_ACK, Protobuf.Writer().varint(1, 1).varint(2, n).message(3, idMsg))
    }

    fun isGolf(smart: ByteArray): Boolean = golfServiceOf(smart) != null

    private fun golfServiceOf(smart: ByteArray): ByteArray? =
        Protobuf.firstBytes(Protobuf.decode(smart), SMART_GOLF)

    data class Push(val seq: Long, val fit: ByteArray)

    /** Extract seq + FIT bytes from a Smart{ 7:{ 5:{ 1:seq, 2:0, 3:FIT } } } push. */
    fun parsePush(smart: ByteArray): Push? {
        val svc = golfServiceOf(smart) ?: return null
        val push = Protobuf.firstBytes(Protobuf.decode(svc), PUSH) ?: return null
        val f = Protobuf.decode(push)
        val fit = Protobuf.firstBytes(f, 3) ?: return null
        return Push(Protobuf.firstVarint(f, 1) ?: 0, fit)
    }

    /** The 'n' from a Smart{ 7:{ 8:{ 1:n } } } transfer descriptor, if this is one. */
    fun parseXfer(smart: ByteArray): Long? {
        val svc = golfServiceOf(smart) ?: return null
        val xfer = Protobuf.firstBytes(Protobuf.decode(svc), XFER) ?: return null
        return Protobuf.firstVarint(Protobuf.decode(xfer), 1)
    }
}
