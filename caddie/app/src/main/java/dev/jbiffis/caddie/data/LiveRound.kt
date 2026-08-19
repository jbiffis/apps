package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.GolfFit

/**
 * Pure merge logic for updating an in-progress ("live") round from a newer watch
 * file, while keeping the edits a player might have made mid-round.
 *
 * The watch is authoritative for the *shape* of the round — which holes are done,
 * their scores, and the shot geometry it recorded. But two things are the user's,
 * not the watch's, and must survive an overwrite:
 *
 *  - **Wind** (per hole, and the copy stamped onto each shot) — entered by the user
 *    or auto-filled from weather; the golf file never carries it.
 *  - **Club assignments** the user set on a shot the watch left unlabelled
 *    (clubId 0 = putt / no club recorded). If the watch later reports a real club
 *    for that shot we defer to it; otherwise we keep the user's choice.
 *
 * Shots are matched across imports by (hole, timeS): the shot timestamp is stable,
 * whereas the Room row id changes on every delete+reinsert.
 */
object LiveRound {

    data class Contents(val holes: List<HoleEntity>, val shots: List<ShotEntity>)

    /**
     * Build the holes and shots to store for [roundId] from a freshly parsed [score],
     * folding in preserved wind (by hole) and club overrides (by hole+time) from the
     * currently stored [oldHoles]/[oldShots].
     */
    fun merge(
        roundId: Long,
        score: GolfFit.ScoreFile,
        oldHoles: List<HoleEntity>,
        oldShots: List<ShotEntity>,
    ): Contents {
        val windByHole = oldHoles.associateBy({ it.hole }, { it.windSpeedKmh to it.windDirDeg })
        // Only a genuine user override matters: a non-zero club the watch didn't supply.
        val clubOverride = oldShots
            .filter { it.clubId != 0L }
            .associateBy({ it.hole to it.timeS }, { it.clubId })

        val holes = score.holes.map { h ->
            val wind = windByHole[h.hole]
            HoleEntity(
                roundId = roundId, hole = h.hole, par = h.par, strokeIndex = h.strokeIndex,
                lengthM = h.lengthM, pinLat = h.pinLat, pinLon = h.pinLon,
                strokes = h.strokes, putts = h.putts, finishedAtS = h.finishedAtS,
                windSpeedKmh = wind?.first, windDirDeg = wind?.second,
            )
        }
        val shots = score.shots.map { s ->
            val wind = windByHole[s.hole]
            // Watch's club wins when it recorded one; otherwise keep any user override.
            val club = if (s.clubId != 0L) s.clubId else (clubOverride[s.hole to s.timeS] ?: 0L)
            ShotEntity(
                roundId = roundId, hole = s.hole, timeS = s.timeS,
                startLat = s.startLat, startLon = s.startLon,
                endLat = s.endLat, endLon = s.endLon,
                clubId = club, distanceM = s.distanceM,
                windSpeedKmh = wind?.first, windDirDeg = wind?.second,
            )
        }
        return Contents(holes, shots)
    }
}
