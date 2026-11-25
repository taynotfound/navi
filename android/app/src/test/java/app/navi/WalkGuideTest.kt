package app.navi

import org.junit.Test

/** 100 m north, then right and 100 m east, in Bremen. */
class WalkGuideTest {
    private val north = 100 / 111_195.0
    private val east = 100 / (111_195.0 * Math.cos(Math.toRadians(53.0)))
    private val a = 53.0 to 8.8
    private val b = 53.0 + north to 8.8
    private val c = 53.0 + north to 8.8 + east
    private val guide = WalkGuide(listOf(a, b, c), listOf(Step("START", "Weg", 90), Step("RIGHT", "Lindenstraße", 90)))

    private fun near(x: Double, y: Double, tol: Double = 3.0) = check(kotlin.math.abs(x - y) < tol) { "$x != $y" }

    @Test fun lengthAndTurnPlacement() {
        near(guide.length, 200.0)
        // step lengths add up to 180 m, the path is 200 m: the turn is scaled onto the corner
        check(guide.turns.size == 1)
        near(guide.turns[0].second, 100.0)
    }

    @Test fun nextTurnAndDistance() {
        val me = 53.0 + north * 0.3 to 8.8
        val fix = guide.locate(me.first, me.second)!!
        near(fix.along, 30.0)
        near(fix.pathBearing, 0.0, 1.0)
        check(!fix.offRoute)
        val cue = guide.cue(fix, c, me)
        check(cue.next?.kind == "RIGHT")
        near(cue.inMeters, 70.0)
        near(cue.remaining, 170.0)
        check(!cue.arrived)
    }

    @Test fun afterTheTurnItsTheDestination() {
        val me = c.first to 8.8 + east * 0.5
        val fix = guide.locate(me.first, me.second)!!
        near(fix.pathBearing, 90.0, 1.0)
        val cue = guide.cue(fix, c, me)
        check(cue.next == null)
        near(cue.inMeters, 50.0)
    }

    @Test fun offRouteAndArrival() {
        val far = 53.0 + north * 0.5 to 8.8 + east * 0.6 // 60 m beside the first stretch
        check(guide.locate(far.first, far.second)!!.offRoute)
        val there = c.first to c.second - east * 0.05
        check(guide.cue(guide.locate(there.first, there.second)!!, c, there).arrived)
    }

    @Test fun currentWalkIsTheUnfinishedPart() {
        val it = Json.decodeFromString(Itinerary.serializer(), javaClass.classLoader!!.getResource("itinerary.json")!!.readText())
        val walkIdx = it.legs.indexOfFirst { l -> l.isWalk }
        check(walkIdx >= 0)
        val l = it.legs[walkIdx]
        val mid = parseTime(l.from.departure ?: l.from.scheduledDeparture)!!.plusSeconds(10)
        check(currentWalk(it, mid) == walkIdx)
        // during a ride: no walk
        val ride = it.legs.indexOfFirst { x -> !x.isWalk }
        check(currentWalk(it, parseTime(it.legs[ride].from.departure ?: it.legs[ride].from.scheduledDeparture)!!.plusSeconds(30)) == null)
    }

    private object Json { private val j = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun <T> decodeFromString(s: kotlinx.serialization.KSerializer<T>, t: String) = j.decodeFromString(s, t) }
}
