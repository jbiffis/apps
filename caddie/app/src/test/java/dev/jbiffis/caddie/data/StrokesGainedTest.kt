package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.data.StrokesGained.Category
import dev.jbiffis.caddie.data.StrokesGained.From
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokesGainedTest {

    private val YD = 1.0 / 1.0936133   // yards -> metres
    private val FT = 1.0 / 3.280839895 // feet  -> metres

    @Test
    fun `holed ball is worth no further strokes`() {
        assertEquals(0.0, StrokesGained.expected(From.GREEN, 0.0), 1e-9)
        assertEquals(0.0, StrokesGained.expected(From.FAIRWAY, 0.0), 1e-9)
    }

    @Test
    fun `table values are hit exactly at their knots`() {
        assertEquals(4.17, StrokesGained.expected(From.TEE, 400 * YD), 1e-3)
        assertEquals(2.80, StrokesGained.expected(From.FAIRWAY, 100 * YD), 1e-3)
        assertEquals(1.626, StrokesGained.expected(From.GREEN, 10 * FT), 1e-3)
    }

    @Test
    fun `values interpolate between knots`() {
        // Fairway 150 yd sits midway between 140 (2.91) and 160 (2.98).
        assertEquals(2.945, StrokesGained.expected(From.FAIRWAY, 150 * YD), 1e-3)
    }

    @Test
    fun `tables clamp beyond their ends`() {
        assertEquals(
            StrokesGained.expected(From.TEE, 600 * YD),
            StrokesGained.expected(From.TEE, 900 * YD),
            1e-9,
        )
    }

    @Test
    fun `rough costs more than fairway at the same distance`() {
        assertTrue(
            StrokesGained.expected(From.ROUGH, 150 * YD) >
                StrokesGained.expected(From.FAIRWAY, 150 * YD),
        )
    }

    @Test
    fun `a holed ten foot putt gains just over half a stroke`() {
        val sg = StrokesGained.shotValue(From.GREEN, 10 * FT, From.GREEN, 0.0)
        assertEquals(0.626, sg, 1e-3)
    }

    @Test
    fun `an average drive is worth about zero`() {
        // 400 yd par 4, drive finishes 150 yd out in the fairway.
        val sg = StrokesGained.shotValue(From.TEE, 400 * YD, From.FAIRWAY, 150 * YD)
        assertEquals(4.17 - 2.945 - 1.0, sg, 1e-3)
        assertTrue("a solid drive should not lose strokes", sg > 0)
    }

    @Test
    fun `a drive into the rough is worth less than the same drive in the fairway`() {
        val fairway = StrokesGained.shotValue(From.TEE, 400 * YD, From.FAIRWAY, 150 * YD)
        val rough = StrokesGained.shotValue(From.TEE, 400 * YD, From.ROUGH, 150 * YD)
        assertTrue(rough < fairway)
    }

    @Test
    fun `categories split by lie and distance`() {
        assertEquals(Category.OFF_THE_TEE, StrokesGained.categoryOf(From.TEE, 400 * YD, true))
        assertEquals(Category.PUTTING, StrokesGained.categoryOf(From.GREEN, 20 * FT, false))
        assertEquals(Category.SHORT_GAME, StrokesGained.categoryOf(From.FAIRWAY, 25 * YD, false))
        assertEquals(Category.APPROACH, StrokesGained.categoryOf(From.FAIRWAY, 150 * YD, false))
        // A par 3 tee shot is an approach, not a drive.
        assertEquals(Category.APPROACH, StrokesGained.categoryOf(From.TEE, 150 * YD, false))
    }

    @Test
    fun `lie falls back sensibly when the course is not mapped`() {
        val none = emptyList<CourseFeature>()
        assertEquals(From.TEE, StrokesGained.lieOf(0.0, 0.0, none, isTeeShot = true, isPutt = false, distanceToPinM = 300.0))
        assertEquals(From.GREEN, StrokesGained.lieOf(0.0, 0.0, none, isTeeShot = false, isPutt = true, distanceToPinM = 5.0))
        assertEquals(From.FAIRWAY, StrokesGained.lieOf(0.0, 0.0, none, isTeeShot = false, isPutt = false, distanceToPinM = 120.0))
    }

    @Test
    fun `mapped polygons win over the fallback`() {
        // A small bunker square around (0.0005, 0.0005).
        val bunker = CourseFeature(
            Lie.Type.BUNKER, null,
            listOf(0.0 to 0.0, 0.0 to 0.001, 0.001 to 0.001, 0.001 to 0.0),
        )
        val from = StrokesGained.lieOf(
            0.0005, 0.0005, listOf(bunker),
            isTeeShot = false, isPutt = false, distanceToPinM = 40.0,
        )
        assertEquals(From.SAND, from)
    }

    @Test
    fun `a hole with no pin cannot be measured`() {
        val hole = hole(par = 4, pinLat = null, pinLon = null)
        assertNull(StrokesGained.forHole(listOf(shot(1)), hole, emptyList()))
    }

    @Test
    fun `hole totals split across categories and count every tracked shot`() {
        // Pin at (0.0, 0.0). Tee ~180 m out, second shot to the green, then a putt.
        val hole = hole(par = 4, pinLat = 0.0, pinLon = 0.0)
        val shots = listOf(
            shot(timeS = 1, startLat = 0.00162, endLat = 0.00045, clubId = 1),  // ~180 m -> ~50 m
            shot(timeS = 2, startLat = 0.00045, endLat = 0.00003, clubId = 2),  // ~50 m -> ~3 m
            shot(timeS = 3, startLat = 0.00003, endLat = 0.0, clubId = 0),      // holed
        )
        val sg = StrokesGained.forHole(shots, hole, emptyList())
        assertNotNull(sg)
        sg!!
        assertEquals(3, sg.shotsCounted)
        assertTrue("course was unmapped, so lies are inferred", sg.approximate)
        assertTrue(sg[Category.OFF_THE_TEE] != 0.0)
        assertEquals(sg.byCategory.values.sum(), sg.net, 1e-9)
    }

    @Test
    fun `baseline average and relative comparison`() {
        val a = StrokesGained.HoleSg(1, 1, mapOf(Category.PUTTING to 0.4), 1, false)
        val b = StrokesGained.HoleSg(1, 2, mapOf(Category.PUTTING to -0.2), 1, false)
        val baseline = StrokesGained.average(listOf(a, b))
        assertEquals(0.1, baseline[Category.PUTTING]!!, 1e-9)

        val relative = StrokesGained.relativeTo(a.byCategory, baseline)
        assertEquals(0.3, relative[Category.PUTTING]!!, 1e-9)
        // Categories absent from the hole read as a straight deficit to the baseline.
        assertEquals(0.0, relative[Category.APPROACH]!!, 1e-9)
    }

    // --- fixtures --------------------------------------------------------

    private fun hole(par: Int, pinLat: Double?, pinLon: Double?) = HoleEntity(
        id = 1, roundId = 1, hole = 7, par = par, strokeIndex = null, lengthM = 350.0,
        pinLat = pinLat, pinLon = pinLon, strokes = 5, putts = 2, finishedAtS = null,
    )

    private fun shot(
        timeS: Long,
        startLat: Double = 0.001,
        endLat: Double = 0.0005,
        clubId: Long = 1,
    ) = ShotEntity(
        id = timeS, roundId = 1, hole = 7, timeS = timeS,
        startLat = startLat, startLon = 0.0, endLat = endLat, endLon = 0.0,
        clubId = clubId,
        distanceM = dev.jbiffis.caddie.fit.GolfFit.haversineM(startLat, 0.0, endLat, 0.0),
    )
}
