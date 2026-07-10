package dev.jbiffis.caddie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LieTest {

    // A hole playing due north from (45, -75): pin 300 m north,
    // fairway a rectangle 50..250 m downrange, 20 m either side of centre.
    private val originLat = 45.0
    private val originLon = -75.0
    private fun north(m: Double) = originLat + m / 111320.0
    private fun east(m: Double) = originLon + m / (111320.0 * Math.cos(Math.toRadians(originLat)))
    private val pinLat = north(300.0)
    private val pinLon = originLon

    private val fairway = CourseFeature(
        Lie.Type.FAIRWAY, null,
        listOf(
            north(50.0) to east(-20.0),
            north(50.0) to east(20.0),
            north(250.0) to east(20.0),
            north(250.0) to east(-20.0),
        ),
    )
    private val green = CourseFeature(
        Lie.Type.GREEN, null,
        listOf(
            north(285.0) to east(-15.0),
            north(285.0) to east(15.0),
            north(315.0) to east(15.0),
            north(315.0) to east(-15.0),
        ),
    )
    private val features = listOf(fairway, green)

    @Test
    fun pointInPolygon() {
        assertTrue(Lie.pointInPolygon(north(150.0), east(0.0), fairway.points))
        assertFalse(Lie.pointInPolygon(north(150.0), east(50.0), fairway.points))
        assertFalse(Lie.pointInPolygon(north(300.0), east(0.0), fairway.points))
    }

    @Test
    fun lieAtPrefersMostSpecific() {
        assertEquals(Lie.Type.FAIRWAY, Lie.lieAt(north(150.0), east(0.0), features))
        assertEquals(Lie.Type.GREEN, Lie.lieAt(north(300.0), east(0.0), features))
        assertEquals(Lie.Type.ROUGH, Lie.lieAt(north(150.0), east(80.0), features))
        assertEquals(Lie.Type.UNKNOWN, Lie.lieAt(north(150.0), east(0.0), emptyList()))
    }

    @Test
    fun classifiesDrives() {
        fun drive(endNorthM: Double, endEastM: Double) = Lie.classifyMiss(
            originLat, originLon, north(endNorthM), east(endEastM), pinLat, pinLon, features,
        )
        assertEquals(Lie.Miss.FAIRWAY, drive(150.0, 0.0))     // middle of the fairway
        assertEquals(Lie.Miss.GREEN, drive(300.0, 0.0))       // drove the green
        assertEquals(Lie.Miss.LEFT, drive(150.0, -40.0))      // rough left of the fairway
        assertEquals(Lie.Miss.RIGHT, drive(150.0, 40.0))      // rough right of the fairway
        assertEquals(Lie.Miss.SHORT, drive(30.0, 0.0))        // straight but short of the fairway
    }

    @Test
    fun featureEntityRoundTrips() {
        val entity = CourseFeatureEntity.encode(7L, fairway)
        val decoded = entity.decode()!!
        assertEquals(Lie.Type.FAIRWAY, decoded.type)
        assertEquals(fairway.points.size, decoded.points.size)
        assertEquals(fairway.points[0].first, decoded.points[0].first, 1e-9)
    }
}
