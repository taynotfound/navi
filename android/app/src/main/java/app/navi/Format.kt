package app.navi

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

fun parseTime(s: String?): ZonedDateTime? =
    s?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull() }

fun hhmm(s: String?): String = parseTime(s)?.format(HHMM) ?: "–"

fun hhmm(t: ZonedDateTime): String = t.format(HHMM)

/** 5400 → "1 Std. 30 Min.", 600 → "10 Min." (units are passed in localised) */
fun formatDuration(seconds: Int, h: String = "Std.", min: String = "Min."): String {
    val m = (seconds + 30) / 60
    return when {
        m < 60 -> "$m $min"
        m % 60 == 0 -> "${m / 60} $h"
        else -> "${m / 60} $h ${m % 60} $min"
    }
}

/** Minutes from now until [s], or null if unparseable. */
fun minutesUntil(s: String?, now: ZonedDateTime = ZonedDateTime.now()): Long? =
    parseTime(s)?.let { Duration.between(now, it).toMinutes() }

fun formatDistance(m: Double?): String? = when {
    m == null || m < 10 -> null
    m < 1000 -> "${(m / 10).toInt() * 10} m"
    else -> "%.1f km".format(m / 1000).replace('.', ',')
}

/** Decodes a Google encoded polyline with the given precision into (lat, lon) pairs. */
fun decodePolyline(encoded: String, precision: Int = 6): List<Pair<Double, Double>> {
    val factor = Math.pow(10.0, precision.toDouble())
    val out = ArrayList<Pair<Double, Double>>()
    var i = 0
    var lat = 0L
    var lon = 0L
    fun next(): Long {
        var result = 0L
        var shift = 0
        while (i < encoded.length) {
            val b = encoded[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    while (i < encoded.length) {
        lat += next()
        lon += next()
        out += (lat / factor) to (lon / factor)
    }
    return out
}
