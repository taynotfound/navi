package app.navi

// Turn-by-turn for a walk, worked out on the phone: where you are on the path, which turn comes next and how far
// it is. Steps from the server only carry their length, so each turn is placed by adding the lengths up along the
// path (scaled to the path's real length, they never match exactly).

import java.time.ZonedDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

private const val EARTH = 6_371_000.0
private const val OFF_ROUTE_M = 40.0
private const val ARRIVED_M = 20.0

/** Where you are on the walk right now. */
data class WalkFix(
    /** metres walked along the path */
    val along: Double,
    /** metres from the path; more than [OFF_ROUTE_M] counts as off it */
    val offBy: Double,
    /** direction of the path where you are, degrees from north */
    val pathBearing: Double,
    val snapped: Pair<Double, Double>,
) {
    val offRoute get() = offBy > OFF_ROUTE_M
}

/** What to show: the next thing to do and how far until it. [next] is null when the next thing is arriving. */
data class WalkCue(val next: Step?, val inMeters: Double, val then: Step?, val remaining: Double, val arrived: Boolean)

class WalkGuide(path: List<Pair<Double, Double>>, steps: List<Step>) {
    val points = path.filter { (la, lo) -> la in -90.0..90.0 && lo in -180.0..180.0 }
        .fold(mutableListOf<Pair<Double, Double>>()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }
    /** distance from the start to each point */
    private val cum = DoubleArray(points.size).also { c -> for (i in 1 until points.size) c[i] = c[i - 1] + dist(points[i - 1], points[i]) }
    val length get() = cum.lastOrNull() ?: 0.0

    /** Turns worth announcing, each with the distance along the path where it happens. */
    val turns: List<Pair<Step, Double>>

    init {
        val total = steps.sumOf { it.distance }.toDouble()
        val scale = if (total > 0 && length > 0) length / total else 1.0
        var at = 0.0
        val out = mutableListOf<Pair<Step, Double>>()
        steps.forEachIndexed { i, s ->
            if (i > 0 && s.kind != "START") out += s to at
            at += s.distance * scale
        }
        turns = out
    }

    fun locate(lat: Double, lon: Double): WalkFix? {
        if (points.isEmpty()) return null
        if (points.size == 1) return WalkFix(0.0, dist(points[0], lat to lon), 0.0, points[0])
        var best: WalkFix? = null
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            // flat projection around a, fine for a few hundred metres
            val kx = cos(Math.toRadians(a.first)) * EARTH * Math.PI / 180
            val ky = EARTH * Math.PI / 180
            val bx = (b.second - a.second) * kx; val by = (b.first - a.first) * ky
            val px = (lon - a.second) * kx; val py = (lat - a.first) * ky
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val dx = px - t * bx; val dy = py - t * by
            val d = sqrt(dx * dx + dy * dy)
            if (best == null || d < best.offBy) {
                best = WalkFix(
                    along = cum[i] + t * sqrt(len2),
                    offBy = d,
                    pathBearing = (Math.toDegrees(atan2(bx, by)) + 360) % 360,
                    snapped = (a.first + t * (b.first - a.first)) to (a.second + t * (b.second - a.second)),
                )
            }
        }
        return best
    }

    fun cue(fix: WalkFix, destination: Pair<Double, Double>, me: Pair<Double, Double>): WalkCue {
        val remaining = (length - fix.along).coerceAtLeast(0.0)
        val arrived = dist(me, destination) <= ARRIVED_M || (remaining <= ARRIVED_M && !fix.offRoute)
        // a turn you're standing on still counts for a few metres, so it doesn't vanish the moment you reach it
        val idx = turns.indexOfFirst { (_, at) -> at > fix.along - 5 }
        val next = turns.getOrNull(idx)
        return WalkCue(
            next = next?.first,
            inMeters = ((next?.second ?: length) - fix.along).coerceAtLeast(0.0),
            then = turns.getOrNull(idx + 1)?.first?.takeIf { next != null },
            remaining = remaining,
            arrived = arrived,
        )
    }

    companion object {
        fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dLat = Math.toRadians(b.first - a.first)
            val dLon = Math.toRadians(b.second - a.second) * cos(Math.toRadians((a.first + b.first) / 2))
            return sqrt(dLat * dLat + dLon * dLon) * EARTH
        }
    }
}

/**
 * The walk you should be doing now on [it]: the first part of the trip that hasn't ended yet, if that part is a walk.
 * Returns its index, or null.
 */
fun currentWalk(it: Itinerary, now: ZonedDateTime): Int? {
    val i = it.legs.indexOfFirst { l -> (parseTime(l.to.arrival ?: l.to.scheduledArrival) ?: return@indexOfFirst false).isAfter(now) }
    if (i < 0 || !it.legs[i].isWalk) return null
    // a first walk an hour before leaving isn't "now" yet
    val start = parseTime(it.legs[i].from.departure ?: it.legs[i].from.scheduledDeparture) ?: return i
    return i.takeIf { start.isBefore(now.plusMinutes(30)) }
}
