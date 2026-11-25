package app.navi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.navi.ui.NaviTheme
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping the live-trip notification: the app opens on the trip being followed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenTrackedTest {
    @get:Rule val compose = createComposeRule()

    private fun itinerary(): Itinerary {
        val text = javaClass.classLoader!!.getResource("itinerary.json")!!.readText()
            .replace("2026-11-23", java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).toString())
        return Json { ignoreUnknownKeys = true }.decodeFromString(Itinerary.serializer(), text)
    }

    @Test
    fun opensFollowedTrip() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        L.ctx = ctx
        Store.init(ctx)
        Store.onboarded = true
        val it = itinerary()
        TripService.start(ctx, it)
        JourneyForm.selected = null
        DeepLink.openTracked = true
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { NaviTheme { NaviApp() } }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        check(JourneyForm.selected == it)
        // second tap while the trip is already open
        DeepLink.openTracked = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        check(JourneyForm.selected == it)
    }

    @Test
    fun notificationTextIsReadable() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        L.ctx = ctx
        val it = itinerary()
        val start = parseTime(it.legs.first { l -> !l.isWalk }.from.departure ?: it.legs.first { l -> !l.isWalk }.from.scheduledDeparture)!!
        val s = tripStatus(it, start.minusMinutes(14))
        check(s.chip == "14 min") { "chip was ${s.chip}" }
        check(tripStatus(it, start.minusHours(3)).chip!!.matches(Regex("\\d\\d:\\d\\d")))
    }

    /** The crash: a trip refreshed by the server came back with precision 0, and the map got latitudes in the millions. */
    @Test
    fun refreshedLineStaysOnEarth() {
        val it = itinerary()
        val g = it.legs.first { l -> l.geometry != null }.geometry!!
        val pts = decodePolyline(g.points, 0)
        check(pts.isNotEmpty() && pts.all { p -> p.first in -90.0..90.0 && p.second in -180.0..180.0 }) { "bad points ${pts.take(2)}" }
        check(pts == decodePolyline(g.points, g.precision))
    }
}
