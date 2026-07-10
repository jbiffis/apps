package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.FitReader
import dev.jbiffis.caddie.fit.GolfFit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class ImportResult {
    data class NewRound(val roundId: Long, val courseName: String, val totalScore: Int) : ImportResult()
    data class ActivityAttached(val roundId: Long) : ImportResult()
    data class ActivityStored(val reason: String) : ImportResult()
    data class Duplicate(val what: String) : ImportResult()
    data class Failed(val reason: String) : ImportResult()
}

class Repository(private val dao: CaddieDao) {

    /** Pending activities that arrived before their score file, keyed by start time. */
    private val pendingActivities = ArrayList<GolfFit.ActivityFile>()

    /**
     * Import any Garmin FIT file. SCORE files create a round; ACTIVITY files attach a
     * GPS track and heart-rate summary to the round they overlap in time.
     */
    suspend fun importFit(bytes: ByteArray): ImportResult {
        val messages = try {
            FitReader.decode(bytes)
        } catch (e: Exception) {
            return ImportResult.Failed("Could not decode FIT file: ${e.message}")
        }
        return when (GolfFit.fileType(messages)) {
            GolfFit.FILE_TYPE_GOLF_SCORE -> importScore(GolfFit.parseScore(messages))
            GolfFit.FILE_TYPE_ACTIVITY -> importActivity(GolfFit.parseActivity(messages))
            else -> ImportResult.Failed("Not a golf SCORE or ACTIVITY file (file_id.type=${GolfFit.fileType(messages)})")
        }
    }

    private suspend fun importScore(score: GolfFit.ScoreFile): ImportResult {
        if (dao.roundByFileTime(score.createdAtS) != null) return ImportResult.Duplicate(score.courseName)

        val roundId = dao.insertRound(
            RoundEntity(
                scoreFileTimeS = score.createdAtS,
                deviceSerial = score.serialNumber,
                startedAtS = score.startedAtS,
                courseName = score.courseName,
                teeName = score.teeName,
                playerName = score.playerName,
                frontPar = score.frontPar,
                backPar = score.backPar,
                totalPar = score.totalPar,
                frontScore = score.frontScore,
                backScore = score.backScore,
                totalScore = score.totalScore,
                totalPutts = score.totalPutts,
                slope = score.slope,
                rating = score.rating,
                distanceWalkedM = score.distanceWalkedM,
            )
        )
        dao.insertHoles(score.holes.map {
            HoleEntity(
                roundId = roundId, hole = it.hole, par = it.par, strokeIndex = it.strokeIndex,
                lengthM = it.lengthM, pinLat = it.pinLat, pinLon = it.pinLon,
                strokes = it.strokes, putts = it.putts, finishedAtS = it.finishedAtS,
            )
        })
        dao.insertShots(score.shots.map {
            ShotEntity(
                roundId = roundId, hole = it.hole, timeS = it.timeS,
                startLat = it.startLat, startLon = it.startLon,
                endLat = it.endLat, endLon = it.endLon,
                clubId = it.clubId, distanceM = it.distanceM,
            )
        })
        for (clubId in score.shots.map { it.clubId }.distinct().filter { it != 0L }) {
            dao.insertClubIfMissing(ClubEntity(clubId, defaultClubName(clubId)))
        }
        // An activity imported earlier may belong to this round.
        pendingActivities.removeAll { activity ->
            if (overlaps(score.startedAtS, activity)) {
                attach(roundId, activity); true
            } else false
        }
        return ImportResult.NewRound(roundId, score.courseName, score.totalScore)
    }

    private suspend fun importActivity(activity: GolfFit.ActivityFile): ImportResult {
        val match = dao.roundsWithoutActivity().firstOrNull { overlaps(it.startedAtS, activity) }
        return if (match != null) {
            attach(match.id, activity)
            ImportResult.ActivityAttached(match.id)
        } else {
            pendingActivities.add(activity)
            ImportResult.ActivityStored("No matching round yet — import the SCORE file from the same day")
        }
    }

    private fun overlaps(roundStartS: Long, activity: GolfFit.ActivityFile): Boolean =
        roundStartS in (activity.startTimeS - 3600)..(activity.endTimeS + 3600)

    private suspend fun attach(roundId: Long, activity: GolfFit.ActivityFile) {
        dao.deleteTrack(roundId)
        dao.insertTrackPoints(activity.track.map {
            TrackPointEntity(roundId = roundId, timeS = it.timeS, lat = it.lat, lon = it.lon, heartRate = it.heartRate)
        })
        dao.attachActivity(roundId, activity.startTimeS, activity.totalCalories, activity.avgHeartRate, activity.maxHeartRate)
    }

    private fun defaultClubName(clubId: Long) = "Club ${clubId % 1000}"

    companion object {
        const val M_TO_YD = 1.0936133

        /**
         * Signed lateral miss of a shot relative to the start→pin target line.
         * Positive = missed right, negative = missed left. Metres.
         */
        fun lateralMissM(
            startLat: Double, startLon: Double,
            endLat: Double, endLon: Double,
            pinLat: Double, pinLon: Double,
        ): Double {
            val cosLat = cos(Math.toRadians(startLat))
            // Local flat projection in metres around the start point
            val tx = (pinLon - startLon) * 111320.0 * cosLat
            val ty = (pinLat - startLat) * 111320.0
            val sx = (endLon - startLon) * 111320.0 * cosLat
            val sy = (endLat - startLat) * 111320.0
            val tLen = sqrt(tx * tx + ty * ty)
            if (tLen < 1.0) return 0.0
            // Cross product z-component: positive when the shot ends left of the target
            // line in a y-north/x-east frame, so negate for "right is positive".
            return -((tx * sy - ty * sx) / tLen)
        }

        /** Along-track progress toward the pin, minus distance to pin. Negative = short, positive = long. */
        fun depthMissM(
            startLat: Double, startLon: Double,
            endLat: Double, endLon: Double,
            pinLat: Double, pinLon: Double,
        ): Double {
            val cosLat = cos(Math.toRadians(startLat))
            val tx = (pinLon - startLon) * 111320.0 * cosLat
            val ty = (pinLat - startLat) * 111320.0
            val sx = (endLon - startLon) * 111320.0 * cosLat
            val sy = (endLat - startLat) * 111320.0
            val tLen = sqrt(tx * tx + ty * ty)
            if (tLen < 1.0) return 0.0
            return (tx * sx + ty * sy) / tLen - tLen
        }

        fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLon = Math.toRadians(lon2 - lon1)
            val la1 = Math.toRadians(lat1)
            val la2 = Math.toRadians(lat2)
            val y = sin(dLon) * cos(la2)
            val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }
    }
}
