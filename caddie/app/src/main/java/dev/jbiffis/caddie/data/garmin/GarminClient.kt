package dev.jbiffis.caddie.data.garmin

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Minimal Garmin Connect API client for golf data.
 *
 * Endpoints follow what the Garmin Connect / Garmin Golf apps use. Golf
 * scorecards live in the golf-community service as JSON; the original
 * ACTIVITY .fit (GPS track) is downloadable from the download service.
 */
class GarminClient(private val auth: GarminAuth, private val log: (String) -> Unit = {}) {

    companion object {
        private const val API = "https://connectapi.garmin.com"
        private const val UA = "com.garmin.android.apps.connectmobile"
    }

    private fun request(path: String, accept: String = "application/json"): ByteArray {
        var bearer = auth.bearer()
        repeat(2) { attempt ->
            val conn = URL("$API$path").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Authorization", "Bearer $bearer")
                conn.setRequestProperty("Accept", accept)
                val code = conn.responseCode
                if (code == 401 && attempt == 0) {
                    bearer = auth.bearer(forceRefresh = true)
                    return@repeat
                }
                if (code !in 200..299) {
                    throw GarminAuthException("GET $path → HTTP $code")
                }
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
        throw GarminAuthException("GET $path failed after token refresh")
    }

    private fun getJson(path: String): String = request(path).decodeToString()

    /** All golf scorecards, newest first. */
    fun scorecardSummaries(): List<JSONObject> {
        val body = getJson("/gcs-golfcommunity/api/v2/scorecard/summary?per-page=200&page=1")
        val root = JSONObject(body)
        val arr = root.optJSONArray("scorecardSummaries")
            ?: root.optJSONArray("summaries")
            ?: JSONArray()
        log("Garmin Connect: ${arr.length()} golf scorecards")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun scorecardDetail(id: Long): JSONObject? {
        val body = getJson("/gcs-golfcommunity/api/v2/scorecard/detail?scorecard-ids=$id")
        val root = JSONObject(body)
        val arr = root.optJSONArray("scorecardDetails") ?: return null
        if (arr.length() == 0) return null
        return arr.getJSONObject(0)
    }

    /** Shot locations for a scorecard. Endpoint varies by app version — try both. */
    fun shots(scorecardId: Long): JSONArray? {
        for (path in listOf(
            "/gcs-golfshot/api/v2/shot/scorecard/$scorecardId/hole",
            "/gcs-golfshot/api/v2/shot/scorecard/$scorecardId",
        )) {
            try {
                val body = getJson(path)
                val trimmed = body.trim()
                val holes = when {
                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    else -> {
                        val obj = JSONObject(trimmed)
                        obj.optJSONArray("holeShots") ?: obj.optJSONArray("holes") ?: obj.optJSONArray("shots")
                    }
                } ?: continue
                if (holes.length() > 0) return holes
            } catch (e: Exception) {
                log("Shots endpoint $path: ${e.message}")
            }
        }
        return null
    }

    /** Recent golf activities (for the GPS-track FIT download). */
    fun golfActivities(limit: Int = 30): List<JSONObject> {
        val body = getJson("/activitylist-service/activities/search/activities?limit=$limit&start=0")
        val arr = JSONArray(body)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optJSONObject("activityType")?.optString("typeKey")?.contains("golf") == true }
    }

    /** Download the original FIT file(s) for an activity (served as a ZIP). */
    fun downloadOriginalFit(activityId: Long): List<ByteArray> {
        val zip = request("/download-service/files/activity/$activityId", accept = "*/*")
        val files = ArrayList<ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".fit", ignoreCase = true)) {
                    val out = ByteArrayOutputStream()
                    zin.copyTo(out)
                    files.add(out.toByteArray())
                }
                entry = zin.nextEntry
            }
        }
        // Some accounts get a bare .fit instead of a zip
        if (files.isEmpty() && zip.size > 12 && zip[8] == '.'.code.toByte() && zip[9] == 'F'.code.toByte()) {
            files.add(zip)
        }
        return files
    }
}
