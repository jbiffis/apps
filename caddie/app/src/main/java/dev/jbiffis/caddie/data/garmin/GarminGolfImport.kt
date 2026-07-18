package dev.jbiffis.caddie.data.garmin

import dev.jbiffis.caddie.data.CaddieDao
import dev.jbiffis.caddie.data.ClubEntity
import dev.jbiffis.caddie.data.HoleEntity
import dev.jbiffis.caddie.data.Repository
import dev.jbiffis.caddie.data.RoundEntity
import dev.jbiffis.caddie.data.ShotEntity
import dev.jbiffis.caddie.fit.GolfFit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Imports Garmin Connect golf-scorecard JSON into the same Room tables the FIT
 * importer fills. Garmin's cloud stores scorecards as JSON (the watch's SCORE
 * .fit is consumed at upload), so this parser is deliberately tolerant about
 * field names and missing values.
 */
class GarminGolfImport(private val dao: CaddieDao, private val repository: Repository, private val log: (String) -> Unit) {

    suspend fun sync(client: GarminClient): String {
        var imported = 0
        var skipped = 0
        val summaries = client.scorecardSummaries()
        for (summary in summaries) {
            try {
                val id = summary.optLong("id", -1)
                if (id <= 0) continue
                if (importScorecard(client, id, summary)) imported++ else skipped++
            } catch (e: Exception) {
                log("Scorecard failed: ${e.message}")
            }
        }
        // Attach GPS tracks: download the original golf activity FIT files
        var tracks = 0
        try {
            for (activity in client.golfActivities()) {
                val roundsMissingTrack = dao.roundsWithoutActivity()
                if (roundsMissingTrack.isEmpty()) break
                val startS = parseTime(activity.optString("startTimeGMT")) ?: continue
                if (roundsMissingTrack.none { kotlin.math.abs(it.startedAtS - startS) < 6 * 3600 }) continue
                val activityId = activity.optLong("activityId", -1)
                if (activityId <= 0) continue
                for (fit in client.downloadOriginalFit(activityId)) {
                    repository.importFit(fit)
                    tracks++
                }
            }
        } catch (e: Exception) {
            log("Activity download: ${e.message}")
        }
        return "Imported $imported new rounds ($skipped already present, $tracks GPS tracks)"
    }

    /** @return true if a new round was created. */
    private suspend fun importScorecard(client: GarminClient, id: Long, summary: JSONObject): Boolean {
        // Dedupe key: negative scorecard id (FIT imports use the positive file timestamp)
        if (dao.roundByFileTime(-id) != null) return false

        val startS = parseTime(
            summary.optString("startTime").ifEmpty { summary.optString("roundStartTime") },
        ) ?: 0L
        val courseName = summary.optString("courseName")
            .ifEmpty { summary.optJSONObject("course")?.optString("name") ?: "" }
            .ifEmpty { "Unknown course" }

        // A FIT import of the same round may already exist (same course, same day)
        val duplicate = dao.roundsAll().any {
            it.courseName.equals(courseName, ignoreCase = true) &&
                kotlin.math.abs(it.startedAtS - startS) < 8 * 3600
        }
        if (duplicate) return false

        val detail = client.scorecardDetail(id)
        val scorecard = detail?.optJSONObject("scorecard") ?: detail ?: summary
        val holesJson = scorecard.optJSONArray("holes") ?: JSONArray()

        val holes = ArrayList<HoleEntity>()
        for (i in 0 until holesJson.length()) {
            val h = holesJson.getJSONObject(i)
            val number = h.optInt("number", h.optInt("holeNumber", i + 1))
            holes.add(
                HoleEntity(
                    roundId = 0,
                    hole = number,
                    par = h.optInt("par", 0),
                    strokeIndex = h.optInt("handicap").takeIf { it > 0 },
                    lengthM = h.optDouble("length", Double.NaN).takeIf { !it.isNaN() },
                    pinLat = h.optDouble("pinLat", Double.NaN).takeIf { !it.isNaN() },
                    pinLon = h.optDouble("pinLon", Double.NaN).takeIf { !it.isNaN() },
                    strokes = h.optInt("strokes", h.optInt("score", 0)),
                    putts = h.optInt("putts", -1).takeIf { it >= 0 },
                    finishedAtS = parseTime(h.optString("lastModifiedDate")),
                )
            )
        }

        // Shot locations (fetched before inserting holes so missing pin
        // positions can be approximated by where the final putt finished)
        val shotEntities = ArrayList<ShotEntity>()
        client.shots(id)?.let { holesArr ->
            for (i in 0 until holesArr.length()) {
                val holeObj = holesArr.optJSONObject(i) ?: continue
                val holeNumber = holeObj.optInt("holeNumber", holeObj.optInt("number", i + 1))
                val shotsArr = holeObj.optJSONArray("shots") ?: continue
                for (j in 0 until shotsArr.length()) {
                    val s = shotsArr.getJSONObject(j)
                    val start = latLon(s, "startLoc") ?: latLon(s, "startLocation") ?: continue
                    val end = latLon(s, "endLoc") ?: latLon(s, "endLocation") ?: continue
                    val clubId = s.optLong("clubId", 0)
                    shotEntities.add(
                        ShotEntity(
                            roundId = 0,
                            hole = holeNumber,
                            timeS = parseTime(s.optString("shotTime")) ?: (s.optLong("shotTimestamp", 0) / 1000),
                            startLat = start.first, startLon = start.second,
                            endLat = end.first, endLon = end.second,
                            clubId = clubId,
                            distanceM = s.optDouble("meters", Double.NaN).takeIf { !it.isNaN() }
                                ?: GolfFit.haversineM(start.first, start.second, end.first, end.second),
                        )
                    )
                }
            }
        }
        val lastShotByHole = shotEntities.groupBy { it.hole }
            .mapValues { (_, v) -> v.maxByOrNull { it.timeS } }
        val holesWithPins = holes.map { h ->
            if (h.pinLat == null) {
                lastShotByHole[h.hole]?.let { h.copy(pinLat = it.endLat, pinLon = it.endLon) } ?: h
            } else h
        }

        val strokes = scorecard.optInt("strokes", summary.optInt("strokes", holes.sumOf { it.strokes }))
        val front = holes.filter { it.hole <= 9 }.sumOf { it.strokes }
        val back = holes.filter { it.hole > 9 }.sumOf { it.strokes }
        val roundId = dao.insertRound(
            RoundEntity(
                scoreFileTimeS = -id,
                deviceSerial = 0,
                startedAtS = startS,
                courseName = courseName,
                teeName = scorecard.optString("teeBox").ifEmpty { null },
                playerName = null,
                frontPar = holes.filter { it.hole <= 9 }.sumOf { it.par },
                backPar = holes.filter { it.hole > 9 }.sumOf { it.par },
                totalPar = holes.sumOf { it.par },
                frontScore = front,
                backScore = back,
                totalScore = if (strokes > 0) strokes else front + back,
                totalPutts = holes.mapNotNull { it.putts }.takeIf { it.isNotEmpty() }?.sum(),
                slope = null,
                rating = null,
                distanceWalkedM = null,
            )
        )
        dao.insertHoles(holesWithPins.map { it.copy(roundId = roundId) })

        if (shotEntities.isNotEmpty()) {
            dao.insertShots(shotEntities.map { it.copy(roundId = roundId) })
            for (clubId in shotEntities.map { it.clubId }.distinct().filter { it != 0L }) {
                dao.insertClubIfMissing(ClubEntity(clubId, "Club ${clubId % 1000}"))
            }
        }
        log("Imported $courseName (${if (strokes > 0) strokes else front + back}) — ${shotEntities.size} shots")

        runCatching { repository.downloadCourseFeatures(roundId) }
        return true
    }

    private fun latLon(obj: JSONObject, key: String): Pair<Double, Double>? {
        val loc = obj.optJSONObject(key) ?: return null
        val lat = loc.optDouble("lat", loc.optDouble("latitude", Double.NaN))
        val lon = loc.optDouble("lon", loc.optDouble("longitude", Double.NaN))
        if (lat.isNaN() || lon.isNaN()) return null
        return lat to lon
    }

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrEmpty()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (f in formats) {
            try {
                val fmt = SimpleDateFormat(f, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                return fmt.parse(value)!!.time / 1000
            } catch (_: Exception) {}
        }
        return null
    }
}
