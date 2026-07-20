package dev.jbiffis.caddie.fit

import dev.jbiffis.caddie.ble.Protobuf

/**
 * Parser for Garmin CourseView `.DAT` files (GARMIN/Golf on the watch). These are
 * protobuf: field 2 = course id, field 3 = a course message holding repeated
 * feature messages (field 4). Each feature has a type (field 3) and repeated
 * point sub-messages (field 6) with lat (field 2) and lon (field 3) as semicircles
 * — lon is a signed 64-bit varint (negative in the western hemisphere).
 *
 * On the vívoactive 5 these files are sparse (a handful of green/hole outlines),
 * not full course maps — but where present they give the real Garmin geometry.
 */
object GarminCourseDat {

    private const val SEMI = 2147483648.0 / 180.0 // semicircles per degree

    data class Course(val courseId: Long, val polygons: List<List<Pair<Double, Double>>>)

    fun parse(bytes: ByteArray): Course? {
        val top = runCatching { Protobuf.decode(bytes) }.getOrNull() ?: return null
        val courseId = Protobuf.firstVarint(top, 2) ?: return null
        val courseMsg = Protobuf.firstBytes(top, 3) ?: return null
        val course = Protobuf.decode(courseMsg)
        val polygons = ArrayList<List<Pair<Double, Double>>>()
        for (featBytes in Protobuf.allBytes(course, 4)) {
            val feat = Protobuf.decode(featBytes)
            val pts = ArrayList<Pair<Double, Double>>()
            for (ptBytes in Protobuf.allBytes(feat, 6)) {
                val pt = Protobuf.decode(ptBytes)
                val lat = Protobuf.firstVarint(pt, 2) ?: continue
                val lon = Protobuf.firstVarint(pt, 3) ?: continue
                val la = lat / SEMI
                val lo = lon / SEMI
                if (la in -90.0..90.0 && lo in -180.0..180.0) pts.add(la to lo)
            }
            if (pts.size >= 3) polygons.add(pts) // keep closed shapes (greens/outlines)
        }
        return Course(courseId, polygons)
    }
}
