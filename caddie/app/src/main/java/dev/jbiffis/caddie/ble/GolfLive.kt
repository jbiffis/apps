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
    const val SMART_APP_REG = 13   // app-registration service

    /**
     * The watch only pushes golf data to a client that has registered itself as the
     * Garmin Golf app. These are the two registration Smart messages (service 13) the
     * real app sends at connect, captured verbatim — they declare app UUID
     * 8cd46041-06fe-4d04-b4be-776042af7e75 / "Garmin Golf". Replayed before polling.
     */
    private val REG_HEX = listOf(
        "6aab0142a8010a2438636434363034312d303666652d346430342d623462652d373736303432616637653735" +
            "1a0b4761726d696e20476f6c665a040802100062420888881c1a21636f6d2e676f6f676c652e616e64726f" +
            "69642e617070732e6d6573736167696e672219636f6d2e676f6f676c652e616e64726f69642e6469616c65" +
            "726a140802121025f508a229e12c960c339fd14badc21c720208017a020803820100b2010208018202020801",
        "6aaf0142ac010a2438636434363034312d303666652d346430342d623462652d373736303432616637653735" +
            "5201075a04080210016242089d881c1a21636f6d2e676f6f676c652e616e64726f69642e617070732e6d65" +
            "73736167696e672219636f6d2e676f6f676c652e616e64726f69642e6469616c65726a1408021210e536d6" +
            "c75dab008aabb2bace30d948b4720208017a0208038201008a01009a0100b20102080182020208019a0200ba02020801",
    )

    val registrationMessages: List<ByteArray> by lazy { REG_HEX.map { it.hexToBytes() } }

    /** True if this Smart message is an app-registration (service 13) response. */
    fun isAppReg(smart: ByteArray): Boolean =
        Protobuf.decode(smart).any { it.number == SMART_APP_REG }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte() }

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
