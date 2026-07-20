package dev.jbiffis.caddie.fit

import kotlin.math.cos

/**
 * Parser for Garmin Golf FIT files.
 *
 * Garmin does not document its golf messages in the public FIT profile. The layout
 * below was reverse-engineered from vivoactive 5 SCORE files (file_id.type = 38):
 *
 *   mesg 190  round summary   0=courseGlobalId 1=courseName 2/3/4=timestamps(fit epoch)
 *                             8=frontPar 9=backPar 10=totalPar 11=teeName 12=slope
 *                             13=distanceWalked(dm) 20=totalPutts 21=courseRating(f32)
 *   mesg 191  player          0=name 2=frontScore 3=backScore 4=totalScore
 *   mesg 192  hole score      253=ts 0=playerIdx 1=hole 2=strokes 3=strokesExclPutts
 *   mesg 193  hole info       253=ts 0=hole 1=holeLength(cm) 2=par 3=strokeIndex
 *                             4=pinLat 5=pinLon (semicircles)
 *   mesg 194  shot            253=ts 0=playerIdx 1=hole 2=startLat 3=startLon
 *                             4=endLat 5=endLon 7=clubId (0 = putt / no club recorded)
 *
 * The companion ACTIVITY file (file_id.type = 4, sport = golf) is a standard FIT
 * activity: session summary + 1 Hz GPS/heart-rate records.
 */
object GolfFit {

    const val FILE_TYPE_ACTIVITY = 4
    const val FILE_TYPE_GOLF_SCORE = 38

    private const val SEMICIRCLE = 180.0 / 2147483648.0

    data class ScoreFile(
        val serialNumber: Long,
        val createdAtS: Long, // unix seconds
        val courseName: String,
        val teeName: String?,
        val frontPar: Int,
        val backPar: Int,
        val totalPar: Int,
        val slope: Int?,
        val rating: Double?,
        val distanceWalkedM: Double?,
        val totalPutts: Int?,
        val startedAtS: Long,
        val playerName: String?,
        val frontScore: Int,
        val backScore: Int,
        val totalScore: Int,
        val holes: List<Hole>,
        val shots: List<Shot>,
    )

    data class Hole(
        val hole: Int,
        val par: Int,
        val strokeIndex: Int?,
        val lengthM: Double?,
        val pinLat: Double?,
        val pinLon: Double?,
        val strokes: Int,
        val putts: Int?,
        val finishedAtS: Long?,
    )

    data class Shot(
        val timeS: Long,
        val hole: Int,
        val startLat: Double,
        val startLon: Double,
        val endLat: Double,
        val endLon: Double,
        val clubId: Long, // 0 = putt or club not recorded
    ) {
        val distanceM: Double get() = haversineM(startLat, startLon, endLat, endLon)
    }

    data class ActivityFile(
        val serialNumber: Long,
        val startTimeS: Long,
        val endTimeS: Long,
        val totalDistanceM: Double?,
        val totalCalories: Int?,
        val avgHeartRate: Int?,
        val maxHeartRate: Int?,
        val track: List<TrackPoint>,
    )

    data class TrackPoint(val timeS: Long, val lat: Double, val lon: Double, val heartRate: Int?)

    fun fileType(messages: List<FitMessage>): Int? =
        messages.firstOrNull { it.globalNum == 0 }?.int(0)

    /** Distinct global message numbers present, sorted — for diagnostics. */
    fun messageInventory(messages: List<FitMessage>): List<Int> =
        messages.map { it.globalNum }.distinct().sorted()

    /**
     * True if this file carries a golf scorecard — the round summary (190).
     * Detected by content, not file_id.type: the vívoactive 5's native BLE files
     * report file_id.type=32, not the 38 seen in USB/Connect exports.
     */
    fun hasGolfScore(messages: List<FitMessage>): Boolean =
        messages.any { it.globalNum == 190 }

    /** True if this file carries standard activity records (session/lap/record). */
    fun hasActivityData(messages: List<FitMessage>): Boolean =
        messages.any { it.globalNum == 18 || it.globalNum == 19 || it.globalNum == 20 }

    fun fitToUnixS(fitTs: Long): Long = fitTs + FitReader.FIT_EPOCH_OFFSET_S

    fun parseScore(messages: List<FitMessage>): ScoreFile {
        val fileId = messages.first { it.globalNum == 0 }
        // Classified by content (message 190), not file_id.type — native BLE golf
        // files report type 32, USB/Connect exports report 38.

        val round = messages.firstOrNull { it.globalNum == 190 }
            ?: throw IllegalArgumentException("No round summary (mesg 190) in score file")
        val player = messages.firstOrNull { it.globalNum == 191 }

        val holeInfo = messages.filter { it.globalNum == 193 }.associateBy { it.int(0) ?: -1 }
        val holeScores = messages.filter { it.globalNum == 192 }
            .filter { (it.int(0) ?: 0) == 0 } // first player only
            .associateBy { it.int(1) ?: -1 }

        val holeNums = (holeInfo.keys + holeScores.keys).filter { it > 0 }.sorted()
        val holes = holeNums.map { h ->
            val info = holeInfo[h]
            val score = holeScores[h]
            val strokes = score?.int(2) ?: 0
            val exPutts = score?.int(3)
            Hole(
                hole = h,
                par = info?.int(2) ?: 0,
                strokeIndex = info?.int(3),
                lengthM = info?.long(1)?.let { it / 100.0 },
                pinLat = info?.long(4)?.let { it * SEMICIRCLE },
                pinLon = info?.long(5)?.let { it * SEMICIRCLE },
                strokes = strokes,
                putts = exPutts?.let { (strokes - it).coerceAtLeast(0) },
                finishedAtS = score?.long(253)?.let { fitToUnixS(it) },
            )
        }

        val shots = messages.filter { it.globalNum == 194 }
            .filter { (it.int(0) ?: 0) == 0 }
            .mapNotNull { m ->
                val hole = m.int(1) ?: return@mapNotNull null
                val sLat = m.long(2) ?: return@mapNotNull null
                val sLon = m.long(3) ?: return@mapNotNull null
                val eLat = m.long(4) ?: return@mapNotNull null
                val eLon = m.long(5) ?: return@mapNotNull null
                Shot(
                    timeS = m.long(253)?.let { fitToUnixS(it) } ?: 0L,
                    hole = hole,
                    startLat = sLat * SEMICIRCLE,
                    startLon = sLon * SEMICIRCLE,
                    endLat = eLat * SEMICIRCLE,
                    endLon = eLon * SEMICIRCLE,
                    clubId = m.long(7) ?: 0L,
                )
            }

        return ScoreFile(
            serialNumber = fileId.long(3) ?: 0L,
            createdAtS = fileId.long(4)?.let { fitToUnixS(it) } ?: 0L,
            courseName = round.string(1) ?: "Unknown course",
            teeName = round.string(11),
            frontPar = round.int(8) ?: 0,
            backPar = round.int(9) ?: 0,
            totalPar = round.int(10) ?: ((round.int(8) ?: 0) + (round.int(9) ?: 0)),
            slope = round.int(12),
            rating = round.double(21),
            distanceWalkedM = round.long(13)?.let { it / 10.0 },
            totalPutts = round.int(20),
            startedAtS = round.long(3)?.let { fitToUnixS(it) } ?: 0L,
            playerName = player?.string(0),
            frontScore = player?.int(2) ?: holes.filter { it.hole <= 9 }.sumOf { it.strokes },
            backScore = player?.int(3) ?: holes.filter { it.hole > 9 }.sumOf { it.strokes },
            totalScore = player?.int(4) ?: holes.sumOf { it.strokes },
            holes = holes,
            shots = shots,
        )
    }

    fun parseActivity(messages: List<FitMessage>, trackEveryS: Int = 5): ActivityFile {
        val fileId = messages.first { it.globalNum == 0 }
        // Classified by content (session/record messages), not file_id.type.
        val session = messages.firstOrNull { it.globalNum == 18 }

        val track = ArrayList<TrackPoint>()
        var lastKeptS = 0L
        for (m in messages) {
            if (m.globalNum != 20) continue
            val ts = m.long(253) ?: continue
            val lat = m.long(0) ?: continue
            val lon = m.long(1) ?: continue
            val t = fitToUnixS(ts)
            if (t - lastKeptS < trackEveryS) continue
            lastKeptS = t
            track.add(TrackPoint(t, lat * SEMICIRCLE, lon * SEMICIRCLE, m.int(3)))
        }

        val start = session?.long(2)?.let { fitToUnixS(it) } ?: track.firstOrNull()?.timeS ?: 0L
        val elapsed = session?.long(7)?.let { it / 1000 } ?: 0L
        return ActivityFile(
            serialNumber = fileId.long(3) ?: 0L,
            startTimeS = start,
            endTimeS = if (elapsed > 0) start + elapsed else (track.lastOrNull()?.timeS ?: start),
            totalDistanceM = session?.long(9)?.let { it / 100.0 },
            totalCalories = session?.int(11),
            avgHeartRate = session?.int(16),
            maxHeartRate = session?.int(17),
            track = track,
        )
    }

    /** Fast equirectangular distance in metres — plenty accurate at golf-shot scale. */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * 111320.0
        val dx = (lon2 - lon1) * 111320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
