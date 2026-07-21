package dev.jbiffis.caddie.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rounds", indices = [Index(value = ["scoreFileTimeS"], unique = true)])
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreFileTimeS: Long, // dedupe key: file_id.time_created of the SCORE file
    val deviceSerial: Long,
    val startedAtS: Long,
    val courseName: String,
    val teeName: String?,
    val playerName: String?,
    val frontPar: Int,
    val backPar: Int,
    val totalPar: Int,
    val frontScore: Int,
    val backScore: Int,
    val totalScore: Int,
    val totalPutts: Int?,
    val slope: Int?,
    val rating: Double?,
    val distanceWalkedM: Double?,
    // Filled in when a matching ACTIVITY file is imported:
    val activityTimeS: Long? = null,
    val totalCalories: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
)

@Entity(tableName = "holes", indices = [Index("roundId")])
data class HoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val hole: Int,
    val par: Int,
    val strokeIndex: Int?,
    val lengthM: Double?,
    val pinLat: Double?,
    val pinLon: Double?,
    val strokes: Int,
    val putts: Int?,
    val finishedAtS: Long?,
    // Wind at this hole (user-entered). Direction = degrees the wind blows FROM.
    val windSpeedKmh: Double? = null,
    val windDirDeg: Int? = null,
)

@Entity(tableName = "shots", indices = [Index("roundId"), Index("clubId")])
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val hole: Int,
    val timeS: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val clubId: Long, // 0 = putt / no club recorded
    val distanceM: Double,
    // Wind while this shot was played (copied from the hole), for analytics.
    val windSpeedKmh: Double? = null,
    val windDirDeg: Int? = null,
)

@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey val clubId: Long,
    val name: String,
)

@Entity(tableName = "track_points", indices = [Index("roundId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val timeS: Long,
    val lat: Double,
    val lon: Double,
    val heartRate: Int?,
)

@Entity(tableName = "course_features", indices = [Index("roundId")])
data class CourseFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val type: String,      // Lie.Type name
    val holeRef: Int?,     // OSM ref tag = hole number, when mapped
    val points: String,    // "lat,lon;lat,lon;..."
) {
    fun decode(): CourseFeature? {
        val type = runCatching { Lie.Type.valueOf(type) }.getOrNull() ?: return null
        val pts = points.split(';').mapNotNull { pair ->
            val comma = pair.indexOf(',')
            if (comma <= 0) return@mapNotNull null
            val lat = pair.substring(0, comma).toDoubleOrNull() ?: return@mapNotNull null
            val lon = pair.substring(comma + 1).toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }
        val min = if (type == Lie.Type.TREE) 1 else 3 // trees are single points
        return if (pts.size >= min) CourseFeature(type, holeRef, pts) else null
    }

    companion object {
        fun encode(roundId: Long, f: CourseFeature) = CourseFeatureEntity(
            roundId = roundId,
            type = f.type.name,
            holeRef = f.holeRef,
            points = f.points.joinToString(";") { "${it.first},${it.second}" },
        )
    }
}

/**
 * A single polygon (green / hole outline) pulled from a Garmin CourseView `.DAT`
 * file (GARMIN/Golf on the watch). Stored with its bounding box so the shot map
 * can query the shapes that fall inside a hole's view without a per-round link —
 * rounds don't record Garmin's numeric course id, so we match on geography.
 */
@Entity(tableName = "course_greens", indices = [Index("courseId")])
data class CourseGreenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val points: String, // "lat,lon;lat,lon;..."
) {
    fun polygon(): List<Pair<Double, Double>> = points.split(';').mapNotNull { pair ->
        val comma = pair.indexOf(',')
        if (comma <= 0) return@mapNotNull null
        val lat = pair.substring(0, comma).toDoubleOrNull() ?: return@mapNotNull null
        val lon = pair.substring(comma + 1).toDoubleOrNull() ?: return@mapNotNull null
        lat to lon
    }

    companion object {
        fun of(courseId: Long, poly: List<Pair<Double, Double>>): CourseGreenEntity {
            val lats = poly.map { it.first }
            val lons = poly.map { it.second }
            return CourseGreenEntity(
                courseId = courseId,
                minLat = lats.min(), minLon = lons.min(),
                maxLat = lats.max(), maxLon = lons.max(),
                points = poly.joinToString(";") { "${it.first},${it.second}" },
            )
        }
    }
}

data class ClubDistanceRow(
    val clubId: Long,
    val shots: Int,
    val avgM: Double,
    val maxM: Double,
)

@Dao
interface CaddieDao {

    // Rounds
    @Query("SELECT * FROM rounds ORDER BY startedAtS DESC")
    fun rounds(): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE id = :id")
    fun round(id: Long): Flow<RoundEntity?>

    @Query("SELECT * FROM rounds WHERE scoreFileTimeS = :ts LIMIT 1")
    suspend fun roundByFileTime(ts: Long): RoundEntity?

    @Query("SELECT * FROM rounds WHERE activityTimeS IS NULL")
    suspend fun roundsWithoutActivity(): List<RoundEntity>

    @Insert
    suspend fun insertRound(round: RoundEntity): Long

    @Query(
        "UPDATE rounds SET activityTimeS = :activityTimeS, totalCalories = :calories, " +
            "avgHeartRate = :avgHr, maxHeartRate = :maxHr WHERE id = :roundId"
    )
    suspend fun attachActivity(roundId: Long, activityTimeS: Long, calories: Int?, avgHr: Int?, maxHr: Int?)

    @Query("DELETE FROM rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Long)

    // Holes / shots / track
    @Insert
    suspend fun insertHoles(holes: List<HoleEntity>)

    @Query("SELECT * FROM holes WHERE roundId = :roundId ORDER BY hole")
    fun holes(roundId: Long): Flow<List<HoleEntity>>

    @Insert
    suspend fun insertShots(shots: List<ShotEntity>)

    @Insert
    suspend fun insertShot(shot: ShotEntity): Long

    @Update
    suspend fun updateShot(shot: ShotEntity)

    @Query("SELECT * FROM shots WHERE roundId = :roundId ORDER BY hole, timeS")
    fun shots(roundId: Long): Flow<List<ShotEntity>>

    @Query("SELECT * FROM shots ORDER BY timeS")
    fun allShots(): Flow<List<ShotEntity>>

    @Query("SELECT * FROM holes")
    fun allHoles(): Flow<List<HoleEntity>>

    @Query("DELETE FROM shots WHERE id = :shotId")
    suspend fun deleteShot(shotId: Long)

    @Query("UPDATE shots SET clubId = :clubId WHERE id = :shotId")
    suspend fun updateShotClub(shotId: Long, clubId: Long)

    // Wind — stored on the hole and stamped onto that hole's shots for analytics.
    @Query("UPDATE holes SET windSpeedKmh = :speed, windDirDeg = :dir WHERE roundId = :roundId AND hole = :hole")
    suspend fun setHoleWind(roundId: Long, hole: Int, speed: Double?, dir: Int?)

    @Query("UPDATE shots SET windSpeedKmh = :speed, windDirDeg = :dir WHERE roundId = :roundId AND hole = :hole")
    suspend fun stampShotsWind(roundId: Long, hole: Int, speed: Double?, dir: Int?)

    @Query("UPDATE holes SET windSpeedKmh = :speed, windDirDeg = :dir WHERE roundId = :roundId")
    suspend fun setRoundWind(roundId: Long, speed: Double?, dir: Int?)

    @Query("UPDATE shots SET windSpeedKmh = :speed, windDirDeg = :dir WHERE roundId = :roundId")
    suspend fun stampRoundShotsWind(roundId: Long, speed: Double?, dir: Int?)

    @Transaction
    suspend fun applyHoleWind(roundId: Long, hole: Int, speed: Double?, dir: Int?) {
        setHoleWind(roundId, hole, speed, dir)
        stampShotsWind(roundId, hole, speed, dir)
    }

    @Transaction
    suspend fun applyRoundWind(roundId: Long, speed: Double?, dir: Int?) {
        setRoundWind(roundId, speed, dir)
        stampRoundShotsWind(roundId, speed, dir)
    }

    @Query("SELECT * FROM shots WHERE roundId = :roundId ORDER BY hole, timeS")
    suspend fun shotsList(roundId: Long): List<ShotEntity>

    @Query("SELECT * FROM holes WHERE roundId = :roundId ORDER BY hole")
    suspend fun holesList(roundId: Long): List<HoleEntity>

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE roundId = :roundId AND timeS BETWEEN :fromS AND :toS ORDER BY timeS")
    suspend fun trackBetween(roundId: Long, fromS: Long, toS: Long): List<TrackPointEntity>

    @Query("DELETE FROM holes WHERE roundId = :roundId")
    suspend fun deleteHoles(roundId: Long)

    @Query("DELETE FROM shots WHERE roundId = :roundId")
    suspend fun deleteShots(roundId: Long)

    @Query("DELETE FROM track_points WHERE roundId = :roundId")
    suspend fun deleteTrack(roundId: Long)

    // Clubs
    @Query("SELECT * FROM clubs")
    fun clubs(): Flow<List<ClubEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClubIfMissing(club: ClubEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClub(club: ClubEntity)

    @Query(
        "SELECT clubId, COUNT(*) AS shots, AVG(distanceM) AS avgM, MAX(distanceM) AS maxM " +
            "FROM shots WHERE clubId != 0 AND distanceM >= :minDistanceM " +
            "GROUP BY clubId ORDER BY avgM DESC"
    )
    fun clubDistances(minDistanceM: Double = 10.0): Flow<List<ClubDistanceRow>>

    // Course features (OpenStreetMap polygons)
    @Query("SELECT * FROM course_features WHERE roundId = :roundId")
    fun features(roundId: Long): Flow<List<CourseFeatureEntity>>

    @Query("SELECT * FROM course_features")
    fun allFeatures(): Flow<List<CourseFeatureEntity>>

    @Query("SELECT COUNT(*) FROM course_features WHERE roundId = :roundId")
    suspend fun featureCount(roundId: Long): Int

    @Insert
    suspend fun insertFeatures(features: List<CourseFeatureEntity>)

    @Query("DELETE FROM course_features WHERE roundId = :roundId")
    suspend fun deleteFeatures(roundId: Long)

    @Transaction
    suspend fun replaceFeatures(roundId: Long, features: List<CourseFeatureEntity>) {
        deleteFeatures(roundId)
        insertFeatures(features)
    }

    // Course greens (Garmin CourseView .DAT geometry, matched to holes by location)
    @Insert
    suspend fun insertGreens(greens: List<CourseGreenEntity>)

    @Query("DELETE FROM course_greens WHERE courseId = :courseId")
    suspend fun deleteGreensForCourse(courseId: Long)

    @Query("SELECT COUNT(*) FROM course_greens")
    suspend fun greensCount(): Int

    @Query(
        "SELECT * FROM course_greens WHERE maxLat >= :south AND minLat <= :north " +
            "AND maxLon >= :west AND minLon <= :east"
    )
    suspend fun greensInBounds(south: Double, west: Double, north: Double, east: Double): List<CourseGreenEntity>

    @Transaction
    suspend fun replaceGreens(courseId: Long, greens: List<CourseGreenEntity>) {
        deleteGreensForCourse(courseId)
        insertGreens(greens)
    }

    @Transaction
    suspend fun deleteRoundCascade(roundId: Long) {
        deleteHoles(roundId)
        deleteShots(roundId)
        deleteTrack(roundId)
        deleteFeatures(roundId)
        deleteRound(roundId)
    }
}

@Database(
    entities = [
        RoundEntity::class, HoleEntity::class, ShotEntity::class,
        ClubEntity::class, TrackPointEntity::class, CourseFeatureEntity::class,
        CourseGreenEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class CaddieDb : RoomDatabase() {
    abstract fun dao(): CaddieDao

    companion object {
        @Volatile private var instance: CaddieDb? = null

        fun get(context: Context): CaddieDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CaddieDb::class.java, "caddie.db")
                // Pre-1.0: rebuild on schema change; rounds can be re-imported from FIT files
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
