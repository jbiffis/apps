package dev.jbiffis.caddie.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches golf course geometry (fairways, greens, bunkers, tees, water, woods)
 * from OpenStreetMap via the Overpass API. Most golf courses are mapped in OSM,
 * and hole polygons usually carry a `ref=<hole number>` tag.
 */
object Overpass {

    // The main instance rate-limits aggressively (429/504); try mirrors in order,
    // then the main instance once more — transient overload usually clears fast.
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass-api.de/api/interpreter",
    )

    /** bbox: south, west, north, east (degrees). Blocking — call on Dispatchers.IO. */
    fun fetchCourseFeatures(south: Double, west: Double, north: Double, east: Double): List<CourseFeature> {
        val bbox = "$south,$west,$north,$east"
        val query = """
            [out:json][timeout:25];
            (
              way["golf"]($bbox);
              relation["golf"]($bbox);
              way["natural"~"^(water|wood|scrub|sand)$"]($bbox);
            );
            out tags geom;
        """.trimIndent()

        var lastError: Exception? = null
        for ((attempt, endpoint) in ENDPOINTS.withIndex()) {
            try {
                return parse(request(endpoint, query))
            } catch (e: Exception) {
                lastError = e
                // Brief pause before the next mirror — 429/504 mean "busy right now"
                if (attempt < ENDPOINTS.size - 1) Thread.sleep(1500)
            }
        }
        throw lastError ?: IllegalStateException("Overpass fetch failed")
    }

    private fun request(endpoint: String, query: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.setRequestProperty("User-Agent", "Caddie/0.1 (personal golf app)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val host = URL(endpoint).host
                throw java.io.IOException(
                    when (code) {
                        429 -> "$host is rate-limiting (HTTP 429)"
                        504, 503 -> "$host is overloaded (HTTP $code)"
                        else -> "$host returned HTTP $code"
                    }
                )
            }
            return conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String): List<CourseFeature> {
        val out = ArrayList<CourseFeature>()
        val elements = JSONObject(json).optJSONArray("elements") ?: return out
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val type = Lie.typeFromOsm(tags.optString("golf", null), tags.optString("natural", null)) ?: continue
            val holeRef = tags.optString("ref", "").toIntOrNull()

            when (el.getString("type")) {
                "way" -> {
                    val points = readGeometry(el.optJSONArray("geometry")) ?: continue
                    out.add(CourseFeature(type, holeRef, points))
                }
                "relation" -> {
                    // Multipolygon: each outer member becomes its own polygon
                    val members = el.optJSONArray("members") ?: continue
                    for (m in 0 until members.length()) {
                        val member = members.getJSONObject(m)
                        if (member.optString("role") == "inner") continue
                        val points = readGeometry(member.optJSONArray("geometry")) ?: continue
                        out.add(CourseFeature(type, holeRef, points))
                    }
                }
            }
        }
        return out
    }

    private fun readGeometry(geometry: org.json.JSONArray?): List<Pair<Double, Double>>? {
        if (geometry == null || geometry.length() < 3) return null
        val points = ArrayList<Pair<Double, Double>>(geometry.length())
        for (i in 0 until geometry.length()) {
            val p = geometry.optJSONObject(i) ?: continue
            points.add(p.getDouble("lat") to p.getDouble("lon"))
        }
        return if (points.size >= 3) points else null
    }
}
