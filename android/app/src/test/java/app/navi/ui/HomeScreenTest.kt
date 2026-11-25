package app.navi.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime

/** The journey and saved tabs: next-trip card, home/work, undo. Also writes screenshots to build/screens/. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** The recorded trip, moved so it leaves [minutes] from now. */
    private fun tripIn(minutes: Long): Itinerary {
        val text = javaClass.classLoader!!.getResource("itinerary.json")!!.readText()
        val it = Json { ignoreUnknownKeys = true }.decodeFromString(Itinerary.serializer(), text)
        val shift = java.time.Duration.between(app.navi.parseTime(it.start)!!, ZonedDateTime.now().plusMinutes(minutes))
        fun t(s: String?) = s?.let { v -> app.navi.parseTime(v)!!.plus(shift).toOffsetDateTime().toString() }
        fun p(p: app.navi.Place) = p.copy(arrival = t(p.arrival), scheduledArrival = t(p.scheduledArrival), departure = t(p.departure), scheduledDeparture = t(p.scheduledDeparture))
        return it.copy(start = t(it.start)!!, end = t(it.end)!!, legs = it.legs.map { l -> l.copy(from = p(l.from), to = p(l.to), stops = l.stops.map(::p)) })
    }

    @Before fun setUp() {
        L.ctx = ctx
        ctx.getSharedPreferences("navi", 0).edit().clear().commit()
        Store.init(ctx)
        JourneyForm.from = null; JourneyForm.to = null
    }

    private fun show(dark: Boolean = true, start: String = "home") {
        Store.theme = if (dark) "dark" else "light"
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                NaviTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val nc = rememberNavController()
                        val nav = Nav(nc)
                        NavHost(nc, startDestination = start) {
                            composable("home") { JourneyTab(nav) }
                            composable("saved") { Box { SavedTab(nav) } }
                            composable("results") { androidx.compose.material3.Text("RESULTS") }
                            composable("itinerary") { androidx.compose.material3.Text("ITINERARY") }
                            composable("settings") { }
                            composable("assistant") { }
                            composable("walk") { WalkMapScreen(nav) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val dir = java.io.File("build/screens").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun journeyTabWithNextTripAndHome() {
        val trip = tripIn(25)
        Store.toggleSaved(trip, "Bremen Hbf", "Oldenburg")
        Store.saveHome(Location("home1", "ADDRESS", "Lindenstraße 12", lat = 53.1, lon = 8.8))
        Store.rememberJourney(Location("a", "STOP", "Bremen Hbf", lat = 53.08, lon = 8.81), Location("b", "STOP", "Oldenburg (Oldb)", lat = 53.14, lon = 8.22))
        show()
        compose.onNodeWithText("Your next trip").assertExists()
        shot("journey-dark")
        // tap the card: opens the trip
        compose.onNodeWithContentDescription("Open trip").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ITINERARY").assertExists()
        check(JourneyForm.selected == trip)
    }

    @Test
    fun journeyTabLight() {
        show(dark = false)
        shot("journey-light-empty")
    }

    @Test
    fun homeGoesToResultsFromChosenStart() {
        val home = Location("home1", "ADDRESS", "Lindenstraße 12", lat = 53.1, lon = 8.8)
        Store.saveHome(home)
        JourneyForm.from = Location("a", "STOP", "Bremen Hbf", lat = 53.08, lon = 8.81)
        show()
        compose.onNodeWithText("Lindenstraße 12").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RESULTS").assertExists()
        check(JourneyForm.to == home)
    }

    @Test
    fun removedTripCanBeUndone() {
        Store.toggleSaved(tripIn(60 * 26), "Bremen Hbf", "Oldenburg")
        show(start = "saved")
        compose.onNodeWithContentDescription("Remove saved journey").performClick()
        compose.waitForIdle()
        check(Store.savedTrips.isEmpty())
        shot("saved-undo")
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        check(Store.savedTrips.size == 1)
    }

    @Test
    fun walkNavigation() {
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val trip = tripIn(-15) // first ride done, walking to the next one now
        val w = trip.legs.indexOfFirst { it.isWalk }
        WalkContext.set(trip.legs[w], trip.legs.drop(w + 1).first { !it.isWalk }, navigate = true)
        show(start = "walk")
        // a GPS fix a third of the way along the path
        val pts = legLine(trip.legs[w]).points
        val p = pts[pts.size / 3]
        val lm = ctx.getSystemService(android.location.LocationManager::class.java)
        org.robolectric.Shadows.shadowOf(lm).setProviderEnabled(android.location.LocationManager.GPS_PROVIDER, true)
        compose.waitForIdle()
        org.robolectric.Shadows.shadowOf(lm).simulateLocation(android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = p.first; longitude = p.second; accuracy = 5f; time = System.currentTimeMillis()
        })
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Stop navigation").assertExists()
        shot("walk-nav")
    }
}
