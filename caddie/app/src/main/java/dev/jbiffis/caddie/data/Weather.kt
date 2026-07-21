package dev.jbiffis.caddie.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Historical hourly wind from Open-Meteo (free, no API key). Used to back-fill the
 * wind for imported rounds: we fetch the hour-by-hour wind at the course for the
 * day it was played, then match each hole to the nearest hour.
 *
 * Direction is the compass bearing the wind blows FROM (meteorological), matching
 * how wind is stored on holes and shots.
 */
object Weather {

    class Hourly(val timesS: LongArray, val speedKmh: DoubleArray, val dirDeg: DoubleArray) {
        /** Nearest valid sample to [targetS]; returns (speed km/h, direction °) or null. */
        fun nearest(targetS: Long): Pair<Double, Int>? {
            var best = -1
            var bestD = Long.MAX_VALUE
            for (i in timesS.indices) {
                if (speedKmh[i].isNaN() || dirDeg[i].isNaN()) continue
                val d = abs(timesS[i] - targetS)
                if (d < bestD) { bestD = d; best = i }
            }
            return if (best < 0) null else speedKmh[best] to ((dirDeg[best].roundToInt() % 360 + 360) % 360)
        }
    }

    // Forecast API covers recent days and up to ~92 days back; archive covers older.
    private val ENDPOINTS = listOf(
        "https://api.open-meteo.com/v1/forecast",
        "https://archive-api.open-meteo.com/v1/archive",
    )

    /** [startDate]/[endDate] are UTC "yyyy-MM-dd". Blocking — call on Dispatchers.IO. */
    fun fetchHourlyWind(lat: Double, lon: Double, startDate: String, endDate: String): Hourly? {
        val q = "latitude=$lat&longitude=$lon&start_date=$startDate&end_date=$endDate" +
            "&hourly=wind_speed_10m,wind_direction_10m&wind_speed_unit=kmh" +
            "&timeformat=unixtime&timezone=UTC"
        for (base in ENDPOINTS) {
            try {
                val h = parse(request("$base?$q"))
                if (h != null && h.timesS.isNotEmpty() && h.speedKmh.any { !it.isNaN() }) return h
            } catch (_: Exception) {
                // try the next endpoint
            }
        }
        return null
    }

    private fun request(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Caddie/0.1 (personal golf app)")
            if (conn.responseCode != 200) throw java.io.IOException("weather HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): Hourly? {
        val hourly = JSONObject(json).optJSONObject("hourly") ?: return null
        val time = hourly.optJSONArray("time") ?: return null
        val spd = hourly.optJSONArray("wind_speed_10m") ?: return null
        val dir = hourly.optJSONArray("wind_direction_10m") ?: return null
        val n = time.length()
        val ts = LongArray(n) { time.getLong(it) }
        val ss = DoubleArray(n) { if (spd.isNull(it)) Double.NaN else spd.getDouble(it) }
        val dd = DoubleArray(n) { if (dir.isNull(it)) Double.NaN else dir.getDouble(it) }
        return Hourly(ts, ss, dd)
    }
}
