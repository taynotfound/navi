package app.navi

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.navi.ui.BoardScreen
import app.navi.ui.DeparturesTab
import app.navi.ui.ItineraryScreen
import app.navi.ui.JourneyTab
import app.navi.ui.MapTab
import app.navi.ui.NaviTheme
import app.navi.ui.Onboarding
import app.navi.ui.ResultsScreen
import app.navi.ui.SavedTab
import app.navi.ui.SettingsScreen
import app.navi.ui.StationScreen
import app.navi.ui.SupportDialog
import app.navi.ui.TripScreen
import app.navi.ui.isAppDark
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.ctx = this
        MapLibre.getInstance(this)
        Store.init(this)
        // Back after Android dropped the process: put the screens' data back before the nav stack restores them.
        // A recreated activity (rotation, process death) has already handled its launch intent.
        if (savedInstanceState != null) Session.restore(this) else handleLink(intent)
        setContent {
            NaviTheme {
                val dark = isAppDark()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                }
                if (!Store.onboarded) Onboarding { Store.onboarded = true; Store.saveSettings() }
                else NaviApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Session.save(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** …/navi/trip/?id=<id> (website) or https://<api>/j/<id> → open the shared trip. */
    private fun handleLink(intent: Intent?) {
        if (intent?.getBooleanExtra("openTracked", false) == true) DeepLink.openTracked = true
        intent?.getStringExtra("journey")?.let { s ->
            runCatching { kotlinx.serialization.json.Json.decodeFromString(SavedJourney.serializer(), s) }.getOrNull()?.let { j ->
                JourneyForm.from = j.from; JourneyForm.to = j.to; DeepLink.openResults = true
            }
        }
        intent?.getStringExtra("board")?.let { s ->
            runCatching { kotlinx.serialization.json.Json.decodeFromString(Location.serializer(), s) }.getOrNull()?.let { DeepLink.openBoard = it }
        }
        val uri: Uri = intent?.data ?: return
        val id = uri.getQueryParameter("id")
            ?: uri.pathSegments.takeIf { it.size == 2 && it[0] == "j" }?.get(1)
        if (id != null && id.matches(Regex("[a-z2-9]{10}"))) DeepLink.shareId = id
    }
}

object DeepLink {
    var shareId by mutableStateOf<String?>(null)
    var openTracked by mutableStateOf(false)
    var openResults by mutableStateOf(false)
    var openBoard by mutableStateOf<Location?>(null) // from the home-screen widget
}

object HomeTab {
    var current by mutableIntStateOf(0)
}

class Nav(private val nc: NavHostController) {
    private fun enc(s: String) = Uri.encode(s)
    fun back() { nc.popBackStack() }
    fun results() = nc.navigate("results")
    fun itinerary() = nc.navigate("itinerary")
    fun walk() = nc.navigate("walk")
    fun settings() = nc.navigate("settings")
    fun assistant() = nc.navigate("assistant")
    fun board(l: Location) = nc.navigate("board?id=${enc(l.id)}&name=${enc(l.name)}&lat=${l.lat}&lon=${l.lon}")
    /** [on]/[off]: stop ids where the user boards and leaves, highlighted in the trip view. */
    fun trip(id: String, on: String? = null, off: String? = null) =
        nc.navigate("trip?id=${enc(id)}&on=${enc(on.orEmpty())}&off=${enc(off.orEmpty())}")
    fun station(id: String, name: String) = nc.navigate("station?id=${enc(id)}&name=${enc(name)}")

    /** Make [l] the destination and jump to the planner. */
    fun planTo(l: Location) {
        JourneyForm.to = l
        HomeTab.current = 0
        nc.popBackStack("home", inclusive = false)
    }
}

@Composable
fun NaviApp() {
    val nc = rememberNavController()
    val nav = remember(nc) { Nav(nc) }
    var askSupport by remember { mutableStateOf(Store.shouldAskForSupport()) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(DeepLink.openTracked) {
        if (!DeepLink.openTracked) return@LaunchedEffect
        DeepLink.openTracked = false
        TripService.tracked?.let { JourneyForm.selected = it; nav.itinerary() }
    }
    LaunchedEffect(DeepLink.openResults) {
        if (!DeepLink.openResults) return@LaunchedEffect
        DeepLink.openResults = false
        if (JourneyForm.from != null && JourneyForm.to != null) nav.results()
    }
    LaunchedEffect(Store.savedTrips) { runCatching { Reminders.sync(ctx) } }
    LaunchedEffect(Store.recentJourneys) { Shortcuts.sync(ctx) }
    LaunchedEffect(Store.favorites) { runCatching { DeparturesWidget.updateAll(ctx) } }
    LaunchedEffect(DeepLink.openBoard) {
        val stop = DeepLink.openBoard ?: return@LaunchedEffect
        DeepLink.openBoard = null
        nav.board(stop)
    }

    LaunchedEffect(DeepLink.shareId) {
        val id = DeepLink.shareId ?: return@LaunchedEffect
        DeepLink.shareId = null
        runCatching { Api.shared(id) }.onSuccess { JourneyForm.selected = it; nav.itinerary() }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(nc, startDestination = "home") {
        composable("home") { Home(nav) }
        composable("results") { ResultsScreen(nav) }
        composable("itinerary") { ItineraryScreen(nav) }
        composable("walk") { app.navi.ui.WalkMapScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("assistant") { app.navi.ui.AssistantScreen(nav) }
        composable("board?id={id}&name={name}&lat={lat}&lon={lon}") { e ->
            val a = e.arguments!!
            BoardScreen(
                Location(a.getString("id")!!, "STOP", a.getString("name").orEmpty(), lat = a.getString("lat")?.toDoubleOrNull() ?: 0.0, lon = a.getString("lon")?.toDoubleOrNull() ?: 0.0),
                nav,
            )
        }
        composable("trip?id={id}&on={on}&off={off}") { e ->
            val a = e.arguments!!
            TripScreen(a.getString("id")!!, nav, a.getString("on")?.ifEmpty { null }, a.getString("off")?.ifEmpty { null })
        }
        composable("station?id={id}&name={name}") { e -> StationScreen(e.arguments!!.getString("id")!!, e.arguments!!.getString("name").orEmpty(), nav) }
    }
    Api.staleSince?.let { OfflineBanner(it, Modifier.align(Alignment.BottomCenter)) }
    }
    if (askSupport) SupportDialog { askSupport = false }
}

/** Shown while screens run on the offline copy, so nobody mistakes it for live data. */
@Composable
private fun OfflineBanner(since: Long, modifier: Modifier) {
    val time = hhmm(java.time.Instant.ofEpochMilli(since).atZone(java.time.ZoneId.systemDefault()))
    Surface(
        modifier.navigationBarsPadding().padding(bottom = 88.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.offline_banner, time), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(nav: Nav) {
    val tabs = listOf(
        Triple(R.string.tab_journey, Icons.Filled.Route, 0),
        Triple(R.string.tab_departures, Icons.Filled.DepartureBoard, 1),
        Triple(R.string.tab_map, Icons.Filled.Map, 2),
        Triple(R.string.tab_saved, Icons.Filled.Bookmarks, 3),
    )
    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                tabs.forEach { (label, icon, i) ->
                    ShortNavigationBarItem(
                        selected = HomeTab.current == i,
                        onClick = { HomeTab.current = i },
                        icon = { Icon(icon, null) }, // the label below names it
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (HomeTab.current) {
                0 -> JourneyTab(nav)
                1 -> DeparturesTab(nav)
                2 -> MapTab(nav)
                else -> SavedTab(nav)
            }
        }
    }
}
