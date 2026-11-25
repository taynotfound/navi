package app.navi.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.navi.Itinerary
import app.navi.JourneyForm
import app.navi.L
import app.navi.Nav
import app.navi.Store
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens a saved trip the way the Saved tab does: itinerary written to and read back from the store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedTripTest {
    @get:Rule val compose = createComposeRule()

    private fun itinerary(): Itinerary {
        val text = javaClass.classLoader!!.getResource("itinerary.json")!!.readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(Itinerary.serializer(), text)
    }

    @Test
    fun opensSavedTripFromSavedTab() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        L.ctx = ctx
        Store.init(ctx)
        val it = itinerary()
        Store.toggleSaved(it, "A", "B")
        Store.init(ctx) // read back like after an app restart
        val saved = Store.savedTrips.single().itinerary
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                NaviTheme {
                    val nc = rememberNavController()
                    val nav = Nav(nc)
                    NavHost(nc, startDestination = "home") {
                        composable("home") { SavedTab(nav) }
                        composable("itinerary") { ItineraryScreen(nav) }
                        composable("trip?id={id}&on={on}&off={off}") { }
                        composable("settings") { }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("A → B").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        check(JourneyForm.selected == saved)
    }
}
