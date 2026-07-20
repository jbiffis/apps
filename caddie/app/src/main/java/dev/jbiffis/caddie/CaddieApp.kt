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
        GarminBleClient(this, getSharedPreferences("ble_sync", MODE_PRIVATE)) { _, bytes ->
            when (val r = repository.importFit(bytes)) {
                is ImportResult.NewRound -> "NEW round: ${r.courseName} (${r.totalScore})"
                is ImportResult.ActivityAttached -> "activity attached to existing round"
                is ImportResult.ActivityStored -> "activity held: ${r.reason}"
                is ImportResult.Duplicate -> "already have round: ${r.what}"
                is ImportResult.Failed -> "skipped: ${r.reason}"
            }
        }
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
