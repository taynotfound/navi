package app.navi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.navi.Itinerary
import app.navi.JourneyForm
import app.navi.L
import app.navi.Location
import app.navi.Nav
import app.navi.Store
import app.navi.TripService
import app.navi.WalkGuide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime

/**
 * Screenshots for the website, in German and English. Maps can't render here, so this writes what each map shows
 * (lines, points, camera) to build/site-shots/maps/ as JSON; tools/site-maps.js draws those with MapLibre GL JS and
 * the same OpenFreeMap style, and the next run puts the pictures into the map slots.
 *
 * Only runs when asked: ./gradlew :app:testDebugUnitTest --tests app.navi.ui.SiteScreenshots -PsiteShots
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SiteScreenshots {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val out = File("build/site-shots").apply { mkdirs() }
    private val maps = File(out, "maps").apply { mkdirs() }

    private fun enabled() = System.getProperty("siteShots") == "true"

    private fun tripIn(minutes: Long): Itinerary {
        val text = javaClass.classLoader!!.getResource("itinerary.json")!!.readText()
        val it = Json { ignoreUnknownKeys = true }.decodeFromString(Itinerary.serializer(), text)
        val shift = java.time.Duration.between(app.navi.parseTime(it.start)!!, ZonedDateTime.now().plusMinutes(minutes))
        fun t(s: String?) = s?.let { v -> app.navi.parseTime(v)!!.plus(shift).toOffsetDateTime().toString() }
        fun p(p: app.navi.Place) = p.copy(arrival = t(p.arrival), scheduledArrival = t(p.scheduledArrival), departure = t(p.departure), scheduledDeparture = t(p.scheduledDeparture))
        return it.copy(start = t(it.start)!!, end = t(it.end)!!, legs = it.legs.map { l -> l.copy(from = p(l.from), to = p(l.to), stops = l.stops.map(::p)) })
    }

    private fun setUp() {
        L.ctx = ctx
        ctx.getSharedPreferences("navi", 0).edit().clear().commit()
        Store.init(ctx)
        Store.theme = "dark"
        JourneyForm.from = null; JourneyForm.to = null
    }

    private fun show(route: String, map: String? = null) {
        val snapshot = map?.let { File(maps, "$it.png") }?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path).asImageBitmap() }
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalMapSnapshot provides snapshot) {
                NaviTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val nc = rememberNavController()
                        val nav = Nav(nc)
                        NavHost(nc, startDestination = route) {
                            composable("journey") { JourneyTab(nav) }
                            composable("itinerary") { ItineraryScreen(nav) }
                            composable("walk") { WalkMapScreen(nav) }
                            composable("results") { }
                            composable("settings") { }
                            composable("assistant") { }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // ----- map specs -----

    private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
    private fun pts(p: List<Pair<Double, Double>>) = JsonArray(p.map { (la, lo) -> JsonArray(listOf(JsonPrimitive(lo), JsonPrimitive(la))) })
    private fun line(l: MapLine) = buildJsonObject { put("color", hex(l.color)); put("dashed", l.dashed); put("points", pts(l.points)) }
    private fun point(p: MapPoint) = buildJsonObject {
        put("lon", p.lon); put("lat", p.lat); put("color", hex(p.color)); put("label", p.label.orEmpty()); put("radius", p.radius)
    }
    private fun spec(name: String, o: JsonObject) = File(maps, "$name.json").writeText(o.toString())

    /** Dark scheme colours, as the screens use them. */
    private fun scheme(): androidx.compose.material3.ColorScheme {
        var s: androidx.compose.material3.ColorScheme? = null
        compose.setContent { NaviTheme { s = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        return s!!
    }

    private fun walking() = tripIn(-15)

    @Test
    fun mapSpecs() {
        if (!enabled()) return
        setUp()
        val c = scheme()
        val riding = tripIn(-5)
        val transit = riding.legs.filterNot { it.isWalk }
        spec("itinerary", buildJsonObject {
            put("width", 379); put("height", 240)
            put("lines", JsonArray(riding.legs.map { line(legLine(it)) }))
            put("points", JsonArray(transit.flatMap { l ->
                listOf(MapPoint("", l.from.lat, l.from.lon, legColor(l), l.from.name), MapPoint("", l.to.lat, l.to.lon, legColor(l), l.to.name))
            }.map(::point)))
            put("fit", true); put("padding", 56); put("maxZoom", 17)
        })
        val (walk, next, me) = walkSetup()
        val guide = WalkGuide(legLine(walk).points, walk.steps)
        val fix = guide.locate(me.first, me.second)!!
        var acc = 0.0; var cut = guide.points.lastIndex
        for (i in 1 until guide.points.size) { acc += WalkGuide.dist(guide.points[i - 1], guide.points[i]); if (acc >= fix.along) { cut = i; break } }
        spec("walk", buildJsonObject {
            put("width", 411); put("height", 891)
            put("lines", JsonArray(listOf(
                line(MapLine(guide.points.take(cut) + listOf(fix.snapped), c.outline)),
                line(MapLine(listOf(fix.snapped) + guide.points.drop(cut), c.primary)),
            )))
            put("points", JsonArray(listOf(
                MapPoint("", walk.from.lat, walk.from.lon, c.secondary, walk.from.name),
                MapPoint("", walk.to.lat, walk.to.lon, legColor(next), next.from.platform?.let { "${walk.to.name} · $it" } ?: walk.to.name, radius = 9f),
            ).map(::point)))
            put("center", pts(listOf(fix.snapped))[0]); put("zoom", 18); put("bearing", fix.pathBearing); put("pitch", 50)
            put("me", pts(listOf(fix.snapped))[0])
        })
    }

    private data class WalkSetup(val walk: app.navi.Leg, val next: app.navi.Leg, val me: Pair<Double, Double>)
    private fun walkSetup(): WalkSetup {
        val t = walking()
        val w = t.legs.indexOfFirst { it.isWalk }
        val walk = t.legs[w]
        val pts = WalkGuide(legLine(walk).points, walk.steps).points
        // halfway along the longest stretch in the first half, so the way ahead points straight up
        val k = (0 until (pts.size / 2).coerceAtLeast(1)).maxBy { i -> WalkGuide.dist(pts[i], pts[i + 1]) }
        val me = (pts[k].first + pts[k + 1].first) / 2 to (pts[k].second + pts[k + 1].second) / 2
        return WalkSetup(walk, t.legs.drop(w + 1).first { !it.isWalk }, me)
    }

    private fun journey(lang: String) {
        if (!enabled()) return
        setUp()
        TripService.start(ctx, walking())
        Store.saveHome(Location("home", "ADDRESS", "Lindenstraße 12", lat = 51.53, lon = 9.93))
        Store.rememberJourney(Location("a", "STOP", "Göttingen", lat = 51.536, lon = 9.926), Location("b", "STOP", "Kassel-Wilhelmshöhe", lat = 51.313, lon = 9.447))
        Store.rememberJourney(Location("c", "STOP", "Göttingen Ithweg", lat = 51.52, lon = 9.95), Location("d", "STOP", "Hann. Münden Bahnhof", lat = 51.41, lon = 9.65))
        show("journey")
        shot("journey-$lang")
        TripService.stop(ctx)
    }

    private fun trip(lang: String) {
        if (!enabled()) return
        setUp()
        // the recorded trip counts 4 min of "walking" on the same platform at Eichenberg, which reads as a missed change
        val t = tripIn(-5)
        JourneyForm.selected = t.copy(legs = t.legs.map { l -> if (l.isWalk && (l.distance ?: 0.0) < 1) l.copy(duration = 60) else l })
        show("itinerary", "itinerary")
        shot("trip-$lang")
    }

    private fun walkNav(lang: String) {
        if (!enabled()) return
        setUp()
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val (walk, next, me) = walkSetup()
        WalkContext.set(walk, next, navigate = true)
        show("walk", "walk")
        val lm = ctx.getSystemService(android.location.LocationManager::class.java)
        org.robolectric.Shadows.shadowOf(lm).simulateLocation(android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = me.first; longitude = me.second; accuracy = 5f; time = System.currentTimeMillis()
        })
        compose.waitForIdle()
        shot("walk-$lang")
    }

    @Test @Config(qualifiers = "+de") fun journeyDe() = journey("de")
    @Test @Config(qualifiers = "+en") fun journeyEn() = journey("en")
    @Test @Config(qualifiers = "+de") fun tripDe() = trip("de")
    @Test @Config(qualifiers = "+en") fun tripEn() = trip("en")
    @Test @Config(qualifiers = "+de") fun walkDe() = walkNav("de")
    @Test @Config(qualifiers = "+en") fun walkEn() = walkNav("en")
}
