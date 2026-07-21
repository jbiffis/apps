package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.FitReader
import dev.jbiffis.caddie.fit.GarminCourseDat
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
    data class ClubsImported(val count: Int) : ImportResult()
    data class CourseDatImported(val courseId: Long, val greens: Int) : ImportResult()
    data class Failed(val reason: String) : ImportResult()
}

class Repository(private val dao: CaddieDao) {

    /** Pending activities that arrived before their score file, keyed by start time. */
    private val pendingActivities = ArrayList<GolfFit.ActivityFile>()

    /** Rounds whose OSM course map we've already re-fetched this app session. */
    private val courseRefreshedThisSession = java.util.Collections.synchronizedSet(HashSet<Long>())

    /**
     * Import any file pulled off the watch, routing by content: Garmin FIT files
     * (scorecards, activities, clubs) start with a ".FIT" tag at byte 8; everything
     * else off the golf folders is a CourseView `.DAT` (protobuf course geometry).
     * Content sniffing means callers don't need the file name.
     */
    suspend fun importFile(bytes: ByteArray): ImportResult =
        if (isFit(bytes)) importFit(bytes) else importCourseDat(bytes)

    private fun isFit(b: ByteArray): Boolean =
        b.size > 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() &&
            b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte()

    /** Store the green/hole outlines from a Garmin CourseView `.DAT` file. */
    private suspend fun importCourseDat(bytes: ByteArray): ImportResult {
        val course = try {
            GarminCourseDat.parse(bytes)
        } catch (e: Exception) {
            return ImportResult.Failed("not a course .DAT file: ${e.message}")
        } ?: return ImportResult.Failed("not a course .DAT file")
        if (course.polygons.isEmpty()) return ImportResult.Failed("course ${course.courseId}: no geometry")
        dao.replaceGreens(course.courseId, course.polygons.map { CourseGreenEntity.of(course.courseId, it) })
        return ImportResult.CourseDatImported(course.courseId, course.polygons.size)
    }

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
        val type = GolfFit.fileType(messages)
        // Route by content, not file_id.type: the vívoactive 5's native BLE files
        // report file_id.type=32, unlike the 38/4 seen in USB/Connect exports. A
        // golf scorecard is identified by its round-summary message (190). Wrap the
        // parse so one malformed file can never abort a bulk import.
        return try {
            when {
                GolfFit.hasClubs(messages) -> importClubs(GolfFit.parseClubs(messages))
                GolfFit.hasGolfScore(messages) -> importScore(GolfFit.parseScore(messages))
                type == GolfFit.FILE_TYPE_ACTIVITY || GolfFit.hasActivityData(messages) ->
                    importActivity(GolfFit.parseActivity(messages))
                else -> ImportResult.Failed(
                    "no golf/activity data (file_id.type=$type, messages=${GolfFit.messageInventory(messages)})"
                )
            }
        } catch (e: Exception) {
            ImportResult.Failed("parse error (file_id.type=$type): ${e.message}")
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
        val matched = pendingActivities.filter { overlaps(score.startedAtS, it) }
        for (activity in matched) attach(roundId, activity)
        pendingActivities.removeAll(matched)
        // NB: course polygons are fetched lazily by the shot view the first time a
        // round is opened (it auto-retries and has a manual button). We deliberately
        // do NOT download them here — that network call can block for minutes when
        // Overpass is slow, which would stall a bulk import (USB/BLE) after one round.
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

    /** Import the golf-club list (Clubs.fit) so shots show real club names. */
    private suspend fun importClubs(clubs: List<GolfFit.ClubInfo>): ImportResult {
        for (c in clubs) dao.upsertClub(ClubEntity(c.clubId, c.name))
        return ImportResult.ClubsImported(clubs.size)
    }

    /**
     * Re-fetch a round's OSM course map at most once per app session. This picks
     * up edits made to OpenStreetMap when the app is restarted, without re-hitting
     * Overpass every time a hole view is opened during the same session. Returns
     * the number of features now stored.
     */
    suspend fun refreshCourseFeaturesForSession(roundId: Long): Int {
        val firstThisSession = courseRefreshedThisSession.add(roundId)
        if (!firstThisSession) return dao.featureCount(roundId) // already tried this session
        return downloadCourseFeatures(roundId)
    }

    /**
     * Fetch golf course polygons from OpenStreetMap covering everywhere this
     * round was played. Returns the number of features stored.
     */
    suspend fun downloadCourseFeatures(roundId: Long): Int {
        val shots = dao.shotsList(roundId)
        val holes = dao.holesList(roundId)
        val lats = shots.flatMap { listOf(it.startLat, it.endLat) } + holes.mapNotNull { it.pinLat }
        val lons = shots.flatMap { listOf(it.startLon, it.endLon) } + holes.mapNotNull { it.pinLon }
        if (lats.isEmpty()) return 0
        val padLat = 300.0 / 111320.0
        val padLon = padLat / kotlin.math.cos(Math.toRadians(lats.average()))
        val features = Overpass.fetchCourseFeatures(
            south = lats.min() - padLat,
            west = lons.min() - padLon,
            north = lats.max() + padLat,
            east = lons.max() + padLon,
        )
        // Don't wipe a previously-good map on a transient empty response — only
        // replace when we actually got features (or there was nothing cached).
        if (features.isEmpty()) return dao.featureCount(roundId)
        dao.replaceFeatures(roundId, features.map { CourseFeatureEntity.encode(roundId, it) })
        return features.size
    }

    /**
     * Back-fill wind for a whole round from historical weather. Fetches the hourly
     * wind at the course for the day(s) played and sets each hole (and its shots)
     * to the wind at the time that hole was played. Returns the number of holes
     * updated, or -1 if there was no location/date or the weather lookup failed.
     */
    suspend fun fetchWindForRound(roundId: Long): Int {
        val round = dao.roundOnce(roundId) ?: return -1
        val holes = dao.holesList(roundId)
        val shots = dao.shotsList(roundId)
        val lats = shots.map { it.startLat } + holes.mapNotNull { it.pinLat }
        val lons = shots.map { it.startLon } + holes.mapNotNull { it.pinLon }
        if (lats.isEmpty() || holes.isEmpty()) return -1
        val lat = lats.average()
        val lon = lons.average()
        val times = (shots.map { it.timeS } + holes.mapNotNull { it.finishedAtS } + listOf(round.startedAtS))
            .filter { it > 0 }
        if (times.isEmpty()) return -1
        val startS = times.min()
        val endS = times.max()
        val hourly = Weather.fetchHourlyWind(lat, lon, utcDate(startS), utcDate(endS)) ?: return -1

        var updated = 0
        val span = (endS - startS).coerceAtLeast(1)
        for (h in holes) {
            // Prefer the hole's real finish time; otherwise interpolate across the round.
            val holeTime = h.finishedAtS?.takeIf { it > 0 }
                ?: (startS + span * (h.hole - 1) / holes.size.coerceAtLeast(1))
            val (speed, dir) = hourly.nearest(holeTime) ?: continue
            dao.applyHoleWind(roundId, h.hole, speed, dir)
            updated++
        }
        return updated
    }

    private fun utcDate(unixS: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(unixS * 1000))
    }

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
