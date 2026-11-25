package app.navi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

// ---------- models (mirror server/upstream.go) ----------

@Serializable
data class Location(
    val id: String,
    val type: String, // STOP | ADDRESS | PLACE | GPS
    val name: String,
    val area: String? = null,
    val lat: Double,
    val lon: Double,
    val modes: List<String> = emptyList(),
    val distance: Int? = null,
)

@Serializable
data class Row(
    val source: String = "",
    val id: String = "",
    val tripId: String? = null,
    val time: String,
    val scheduledTime: String,
    val delay: Int? = null,
    val realtime: Boolean = false,
    val cancelled: Boolean = false,
    val platform: String? = null,
    val scheduledPlatform: String? = null,
    val line: String = "",
    val number: String? = null,
    val mode: String = "",
    val color: String? = null,
    val textColor: String? = null,
    val headsign: String? = null,
    val via: List<String> = emptyList(),
    val operator: String? = null,
    val notes: List<String> = emptyList(),
    val attributes: List<String> = emptyList(),
    val usual: DelayStats? = null,
)

/** How late a line usually is, learned by the server from past live data. Minutes. */
@Serializable
data class DelayStats(
    val median: Int = 0,
    val likely: Int = 0, // 4 out of 5 runs are at most this late
    val onTime: Int = 0, // % less than 3 min late
    val cancelled: Int = 0, // %
    val samples: Int = 0,
    val scope: String = "line", // stop | line
)

@Serializable
data class BandStats(val band: String, val weekend: Boolean = false, val stats: DelayStats)

@Serializable
data class Punctuality(
    val line: String = "",
    val overall: DelayStats? = null,
    val atStop: DelayStats? = null,
    val bands: List<BandStats> = emptyList(),
    val histogram: List<Int> = emptyList(), // % on time, 3–5, 6–10, 11–20, >20 min, cancelled
)

@Serializable
data class Photo(val thumb: String, val page: String, val title: String = "", val author: String = "", val license: String = "")

@Serializable
data class BoardStop(val id: String, val name: String? = null, val hasStationInfo: Boolean = false)

@Serializable
data class Board(val stop: BoardStop, val rows: List<Row>)

@Serializable
data class Place(
    val name: String,
    val stopId: String? = null,
    val lat: Double,
    val lon: Double,
    val arrival: String? = null,
    val scheduledArrival: String? = null,
    val departure: String? = null,
    val scheduledDeparture: String? = null,
    val platform: String? = null,
    val scheduledPlatform: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
data class Geometry(val points: String, val precision: Int = 6)

@Serializable
data class Alert(val header: String? = null, val text: String? = null)

/** One walking instruction. kind: START | STRAIGHT | LEFT | RIGHT | SLIGHT_LEFT | SLIGHT_RIGHT | STAIRS | ELEVATOR | ESCALATOR */
@Serializable
data class Step(val kind: String, val street: String? = null, val distance: Int = 0)

@Serializable
data class Leg(
    val mode: String,
    val from: Place,
    val to: Place,
    val duration: Int = 0,
    val distance: Double? = null,
    val realtime: Boolean = false,
    val cancelled: Boolean = false,
    val line: String? = null,
    val number: String? = null,
    val headsign: String? = null,
    val color: String? = null,
    val textColor: String? = null,
    val operator: String? = null,
    val tripId: String? = null,
    val stops: List<Place> = emptyList(),
    val geometry: Geometry? = null,
    val alerts: List<Alert> = emptyList(),
    val steps: List<Step> = emptyList(),
    val notes: List<String> = emptyList(),
    /** passes | same-vehicle | same-line: Navi replaced a long wait at the end stop for the same line. */
    val stayOnBoard: String? = null,
    val depDelay: DelayStats? = null,
    val arrDelay: DelayStats? = null,
) {
    val isWalk get() = mode == "WALK" || mode == "BIKE" || mode == "CAR"
}

@Serializable
data class Itinerary(val start: String, val end: String, val duration: Int, val transfers: Int, val legs: List<Leg>)

@Serializable
data class Plan(
    val itineraries: List<Itinerary> = emptyList(),
    val direct: List<Itinerary> = emptyList(),
    val earlier: String? = null,
    val later: String? = null,
)

@Serializable
data class Accessibility(
    val level: String = "unknown",
    val stepFree: String = "unknown",
    val wheelchair: String = "unknown",
    val tactileGuidance: String = "unknown",
)

@Serializable
data class StationPlatform(
    val name: String,
    val heightCm: Int? = null,
    val lengthM: Double? = null,
    val stepFree: String = "unknown",
    val tracks: List<String> = emptyList(),
)

@Serializable
data class Equipment(
    val type: String,
    val name: String = "",
    val equipmentNumber: String? = null,
    val wheelchair: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val status: String? = null, // ACTIVE | INACTIVE | UNKNOWN
    val statusText: String? = null,
)

@Serializable
data class Station(
    val dhid: String,
    val name: String,
    val address: String? = null,
    val url: String? = null,
    val accessibility: Accessibility = Accessibility(),
    val entrances: List<String> = emptyList(),
    val platforms: List<StationPlatform> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val liveStatus: Boolean = false,
)

@Serializable
data class ShareLink(val id: String, val url: String)

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class AssistantLink(val kind: String, val label: String = "", val stopId: String? = null, val from: String? = null, val to: String? = null)

@Serializable
data class AssistantReply(val answer: String, val onTopic: Boolean = false, val links: List<AssistantLink> = emptyList())

@Serializable
private data class AssistantRequest(val messages: List<ChatMessage>, val lang: String, val lat: Double? = null, val lon: Double? = null)

// ---------- client ----------

class ApiException(message: String) : Exception(message)

object Api {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun search(q: String, stopsOnly: Boolean = false) =
        get("/v1/search", mapOf("q" to q, "type" to if (stopsOnly) "stop" else null), ListSerializer(Location.serializer()))

    suspend fun nearby(lat: Double, lon: Double, radius: Int = 800) =
        get("/v1/nearby", mapOf("lat" to "$lat", "lon" to "$lon", "radius" to "$radius"), ListSerializer(Location.serializer()))

    suspend fun board(stopId: String, arrivals: Boolean = false, time: String? = null) =
        get("/v1/board", mapOf("stop" to stopId, "arrivals" to if (arrivals) "1" else null, "time" to time), Board.serializer())

    suspend fun plan(
        from: String, to: String, time: String?, arriveBy: Boolean,
        wheelchair: Boolean, bike: Boolean, cursor: String? = null, modes: Set<String> = emptySet(),
    ) = get(
        "/v1/plan",
        mapOf(
            "from" to from, "to" to to, "time" to time, "cursor" to cursor,
            "arriveBy" to if (arriveBy) "1" else null,
            "wheelchair" to if (wheelchair) "1" else null,
            "bike" to if (bike) "1" else null,
            "modes" to modes.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
        ),
        Plan.serializer(),
    )

    suspend fun trip(id: String) = get("/v1/trip", mapOf("id" to id), Leg.serializer())

    suspend fun punctuality(line: String, operator: String?, stop: String?) =
        get("/v1/punctuality", mapOf("line" to line, "operator" to operator, "stop" to stop), Punctuality.serializer())

    suspend fun photos(line: String, operator: String?, mode: String) =
        get("/v1/photos", mapOf("line" to line, "operator" to operator, "mode" to mode), ListSerializer(Photo.serializer()))

    suspend fun station(stopId: String) = get("/v1/station", mapOf("stop" to stopId), Station.serializer())

    /** The itinerary with live times, platforms and cancellations. */
    suspend fun live(it: Itinerary) = get("/v1/live", emptyMap(), Itinerary.serializer(), json.encodeToString(Itinerary.serializer(), it))

    suspend fun share(it: Itinerary) = get("/v1/share", emptyMap(), ShareLink.serializer(), json.encodeToString(Itinerary.serializer(), it))

    suspend fun shared(id: String) = get("/v1/share", mapOf("id" to id), Itinerary.serializer())

    /** "Frag Navi": transit-only assistant on the server (free models). */
    suspend fun ask(messages: List<ChatMessage>, lang: String, lat: Double?, lon: Double?) = get(
        "/v1/assistant", emptyMap(), AssistantReply.serializer(),
        json.encodeToString(AssistantRequest.serializer(), AssistantRequest(messages.takeLast(12), lang, lat, lon)),
    )

    /** GET, or POST with a JSON [body]. */
    private suspend fun <T> get(path: String, params: Map<String, String?>, serializer: KSerializer<T>, body: String? = null): T =
        withContext(Dispatchers.IO) {
            val query = params.filterValues { it != null }
                .entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val conn = URL("${BuildConfig.API_URL}$path?$query").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept-Encoding", "gzip")
                conn.setRequestProperty("User-Agent", "Navi-Android/${BuildConfig.VERSION_NAME}")
                if (body != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = (if (code < 400) conn.inputStream else conn.errorStream)
                    ?: throw ApiException(friendlyError(code, null))
                val text = (if (conn.contentEncoding == "gzip") GZIPInputStream(stream) else stream)
                    .bufferedReader().use { it.readText() }
                if (code != 200) {
                    val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw ApiException(friendlyError(code, msg))
                }
                json.decodeFromString(serializer, text)
            } catch (e: SocketTimeoutException) {
                throw ApiException(L.s(R.string.err_timeout))
            } catch (e: IOException) {
                throw ApiException(L.s(R.string.err_offline))
            } finally {
                conn.disconnect()
            }
        }

    private fun friendlyError(code: Int, msg: String?) = when (code) {
        400 -> L.s(R.string.err_bad_request)
        404 -> L.s(R.string.err_not_found)
        429 -> L.s(R.string.err_rate_limited)
        else -> L.s(R.string.err_upstream)
    }.let { if (BuildConfig.DEBUG && msg != null) "$it ($msg)" else it }
}
