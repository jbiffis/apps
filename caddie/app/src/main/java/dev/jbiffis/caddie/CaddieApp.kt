package dev.jbiffis.caddie

import android.app.Application
import dev.jbiffis.caddie.ble.GarminBleClient
import dev.jbiffis.caddie.data.CaddieDb
import dev.jbiffis.caddie.data.ImportResult
import dev.jbiffis.caddie.data.Repository
import org.osmdroid.config.Configuration

class CaddieApp : Application() {

    val db by lazy { CaddieDb.get(this) }
    val repository by lazy { Repository(db.dao()) }
    val bleClient by lazy {
        GarminBleClient(
            this,
            getSharedPreferences("ble_sync", MODE_PRIVATE),
            onPartialFile = { _, bytes -> summarize(repository.importPartialFit(bytes)) },
        ) { _, bytes -> summarize(repository.importFile(bytes)) }
    }

    private fun summarize(r: ImportResult): String = when (r) {
        is ImportResult.NewRound -> "NEW round: ${r.courseName} (${r.totalScore})"
        is ImportResult.UpdatedRound ->
            "${if (r.finalized) "finalized" else "updated"} round: ${r.courseName} " +
                "(${r.totalScore}, ${r.holesPlayed} holes)"
        is ImportResult.ActivityAttached -> "activity attached to existing round"
        is ImportResult.ActivityStored -> "activity held: ${r.reason}"
        is ImportResult.Duplicate -> "already have round: ${r.what}"
        is ImportResult.ClubsImported -> "clubs: ${r.count} imported"
        is ImportResult.CourseDatImported -> "course ${r.courseId}: ${r.greens} outlines"
        is ImportResult.Failed -> "skipped: ${r.reason}"
    }

    override fun onCreate() {
        super.onCreate()
        // osmdroid: cache tiles in app storage and identify ourselves to tile servers
        Configuration.getInstance().apply {
            userAgentValue = "dev.jbiffis.caddie"
            osmdroidBasePath = cacheDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
    }
}
