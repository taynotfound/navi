package app.navi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime

/** Localised strings outside Compose. Points at the current activity so the in-app language applies. */
object L {
    @SuppressLint("StaticFieldLeak") // replaced on every activity (re)creation
    lateinit var ctx: Context
    val ready get() = ::ctx.isInitialized
    fun s(id: Int, vararg args: Any): String = ctx.getString(id, *args)
}

@Serializable
data class SavedJourney(val from: Location, val to: Location)

@Serializable
data class SavedTrip(val key: String, val from: String, val to: String, val itinerary: Itinerary, val savedAt: Long)

fun tripKey(it: Itinerary) = it.start + "|" + it.legs.firstOrNull { l -> l.tripId != null }?.tripId

/** Favourites, history, saved trips and settings, persisted in SharedPreferences. Observable from Compose. */
object Store {
    private lateinit var prefs: SharedPreferences
    val ready get() = ::prefs.isInitialized
    private val json = Json { ignoreUnknownKeys = true }
    private val places = ListSerializer(Location.serializer())
    private val journeys = ListSerializer(SavedJourney.serializer())
    private val trips = ListSerializer(SavedTrip.serializer())

    var favorites by mutableStateOf(listOf<Location>()); private set
    var recentPlaces by mutableStateOf(listOf<Location>()); private set
    var recentJourneys by mutableStateOf(listOf<SavedJourney>()); private set
    var savedTrips by mutableStateOf(listOf<SavedTrip>()); private set
    /** One-tap destinations on the journey tab. */
    var home by mutableStateOf<Location?>(null); private set
    var work by mutableStateOf<Location?>(null); private set

    // settings
    var theme by mutableStateOf("dark") // dark | light | system
    var dynamicColor by mutableStateOf(false)
    var onboarded by mutableStateOf(false)
    var wheelchairDefault by mutableStateOf(false)
    var assistantEnabled by mutableStateOf(true) // "Frag Navi" button on the journey tab

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("navi", Context.MODE_PRIVATE)
        favorites = read("favorites", places)
        recentPlaces = read("recentPlaces", places)
        recentJourneys = read("recentJourneys", journeys)
        // trips are kept until a day after arrival
        val cutoff = ZonedDateTime.now().minusDays(1)
        savedTrips = read("savedTrips", trips).filter { it.itinerary.legs.isNotEmpty() && (parseTime(it.itinerary.end) ?: cutoff) >= cutoff }.distinctBy { it.key }
        home = readPlace("home"); work = readPlace("work")
        theme = prefs.getString("theme", "dark")!!
        dynamicColor = prefs.getBoolean("dynamicColor", false)
        onboarded = prefs.getBoolean("onboarded", false)
        wheelchairDefault = prefs.getBoolean("wheelchairDefault", false)
        assistantEnabled = prefs.getBoolean("assistantEnabled", true)
        JourneyForm.wheelchair = wheelchairDefault
        prefs.edit().putInt("launches", prefs.getInt("launches", 0) + 1)
            .apply { if (!prefs.contains("firstLaunch")) putLong("firstLaunch", System.currentTimeMillis()) }.apply()
    }

    fun saveSettings() = prefs.edit()
        .putString("theme", theme).putBoolean("dynamicColor", dynamicColor)
        .putBoolean("onboarded", onboarded).putBoolean("wheelchairDefault", wheelchairDefault)
        .putBoolean("assistantEnabled", assistantEnabled).apply()

    // ----- support prompt: once, after a week and ~10 launches, never again after a decision -----
    fun shouldAskForSupport(): Boolean {
        if (prefs.getBoolean("kofiDone", false)) return false
        val days = (System.currentTimeMillis() - prefs.getLong("firstLaunch", System.currentTimeMillis())) / 86_400_000
        return days >= 7 && prefs.getInt("launches", 0) >= prefs.getInt("kofiAt", 10)
    }
    fun supportAnswered(later: Boolean) {
        if (later) prefs.edit().putInt("kofiAt", prefs.getInt("launches", 0) + 15).apply()
        else prefs.edit().putBoolean("kofiDone", true).apply()
    }

    // ----- home & work -----
    private fun readPlace(key: String) = prefs.getString(key, null)?.let { runCatching { json.decodeFromString(Location.serializer(), it) }.getOrNull() }

    fun saveHome(l: Location?) { home = l; writePlace("home", l) }
    fun saveWork(l: Location?) { work = l; writePlace("work", l) }
    private fun writePlace(key: String, l: Location?) =
        prefs.edit().apply { if (l == null) remove(key) else putString(key, json.encodeToString(Location.serializer(), l)) }.apply()

    /** Put back a trip removed by mistake (the Undo in the snackbar). */
    fun restoreSaved(t: SavedTrip) {
        if (isSavedKey(t.key)) return
        savedTrips = (savedTrips + t).sortedBy { parseTime(it.itinerary.start) }
        write("savedTrips", trips, savedTrips)
    }

    // ----- favourites / history -----
    fun isFavorite(id: String) = favorites.any { it.id == id }

    fun toggleFavorite(loc: Location) {
        favorites = if (isFavorite(loc.id)) favorites.filterNot { it.id == loc.id } else favorites + loc
        write("favorites", places, favorites)
    }

    /** Undo for a removed favourite: back where it was. */
    fun restoreFavorite(loc: Location, at: Int) {
        if (isFavorite(loc.id)) return
        favorites = favorites.toMutableList().apply { add(at.coerceIn(0, size), loc) }
        write("favorites", places, favorites)
    }

    fun rememberPlace(loc: Location) {
        if (loc.type == "GPS") return
        recentPlaces = (listOf(loc) + recentPlaces.filterNot { it.id == loc.id }).take(8)
        write("recentPlaces", places, recentPlaces)
    }

    fun rememberJourney(from: Location, to: Location) {
        val j = SavedJourney(from, to)
        recentJourneys = (listOf(j) + recentJourneys.filterNot { it.from.id == from.id && it.to.id == to.id }).take(5)
        write("recentJourneys", journeys, recentJourneys)
    }

    fun clearHistory() {
        recentPlaces = emptyList(); recentJourneys = emptyList()
        write("recentPlaces", places, recentPlaces)
        write("recentJourneys", journeys, recentJourneys)
    }

    // ----- saved trips -----
    fun isSaved(it: Itinerary) = savedTrips.any { t -> t.key == tripKey(it) }

    fun toggleSaved(it: Itinerary, from: String, to: String) {
        val key = tripKey(it)
        savedTrips = if (isSaved(it)) savedTrips.filterNot { t -> t.key == key }
        else (savedTrips + SavedTrip(key, from, to, it, System.currentTimeMillis())).sortedBy { t -> parseTime(t.itinerary.start) }
        write("savedTrips", trips, savedTrips)
    }

    /** Keep a saved trip's live data so it shows the latest state offline. */
    /** Live data moves the start time, which is part of the key: update by the key the trip was saved under. */
    fun updateSaved(key: String, it: Itinerary) {
        if (savedTrips.none { t -> t.key == key }) return
        savedTrips = savedTrips.map { t -> if (t.key == key) t.copy(itinerary = it) else t }
        write("savedTrips", trips, savedTrips)
    }

    fun isSavedKey(key: String) = savedTrips.any { t -> t.key == key }

    fun toggleSavedKey(key: String, it: Itinerary, from: String, to: String) {
        savedTrips = if (isSavedKey(key)) savedTrips.filterNot { t -> t.key == key }
        else (savedTrips + SavedTrip(key, from, to, it, System.currentTimeMillis())).sortedBy { t -> parseTime(t.itinerary.start) }
        write("savedTrips", trips, savedTrips)
    }

    private fun <T> read(key: String, s: KSerializer<List<T>>): List<T> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(s, it) }.getOrNull() } ?: emptyList()

    private fun <T> write(key: String, s: KSerializer<List<T>>, v: List<T>) =
        prefs.edit().putString(key, json.encodeToString(s, v)).apply()
}

/** The journey form lives here so it survives navigating to results and back. */
object JourneyForm {
    var from by mutableStateOf<Location?>(null)
    var to by mutableStateOf<Location?>(null)
    var time by mutableStateOf<ZonedDateTime?>(null) // null = now
    var arriveBy by mutableStateOf(false)
    var wheelchair by mutableStateOf(false)
    var bike by mutableStateOf(false)
    var modes by mutableStateOf<Set<String>>(emptySet()) // type filter: long, regional, suburban, subway, tram, bus, ferry; empty = all
    var via by mutableStateOf<Location?>(null) // one stop to pass through
    var minTransfer by mutableStateOf(0) // extra minutes for every change
    var walkSpeed by mutableStateOf("normal") // slow | normal | fast
    var selected by mutableStateOf<Itinerary?>(null)

    fun swap() { val f = from; from = to; to = f }

    /** Only regional and local transport: what the Deutschlandticket covers. */
    val dTicketModes = setOf("regional", "suburban", "subway", "tram", "bus", "ferry")
}

/**
 * What's on screen, written when the app goes to the background and read back if Android killed the process
 * meanwhile. The nav back stack restores itself; without this the trip, walk and form behind it would be empty.
 */
object Session {
    @Serializable
    private data class Snapshot(
        val tab: Int = 0,
        val from: Location? = null,
        val to: Location? = null,
        val via: Location? = null,
        val time: String? = null,
        val arriveBy: Boolean = false,
        val wheelchair: Boolean = false,
        val bike: Boolean = false,
        val modes: Set<String> = emptySet(),
        val minTransfer: Int = 0,
        val walkSpeed: String = "normal",
        val selected: Itinerary? = null,
        val walk: Leg? = null,
        val walkNext: Leg? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context) = java.io.File(ctx.noBackupFilesDir, "session.json")

    fun save(ctx: Context) {
        val f = JourneyForm
        val s = Snapshot(
            HomeTab.current, f.from, f.to, f.via, f.time?.toString(), f.arriveBy, f.wheelchair, f.bike, f.modes,
            f.minTransfer, f.walkSpeed, f.selected, app.navi.ui.WalkContext.walk, app.navi.ui.WalkContext.next,
        )
        runCatching { file(ctx).writeText(json.encodeToString(Snapshot.serializer(), s)) }
    }

    fun restore(ctx: Context) {
        val s = runCatching { json.decodeFromString(Snapshot.serializer(), file(ctx).readText()) }.getOrNull() ?: return
        val f = JourneyForm
        HomeTab.current = s.tab
        f.from = s.from; f.to = s.to; f.via = s.via
        f.time = s.time?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() }
        f.arriveBy = s.arriveBy; f.wheelchair = s.wheelchair; f.bike = s.bike; f.modes = s.modes
        f.minTransfer = s.minTransfer; f.walkSpeed = s.walkSpeed; f.selected = s.selected
        s.walk?.let { app.navi.ui.WalkContext.set(it, s.walkNext) }
    }
}

fun myLocation(lat: Double, lon: Double) = Location(id = "$lat,$lon", type = "GPS", name = L.s(R.string.my_location), lat = lat, lon = lon)

fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Current position via the platform LocationManager (no Google Play Services, F-Droid friendly). */
@SuppressLint("MissingPermission")
suspend fun currentPosition(ctx: Context): android.location.Location? {
    if (!hasLocationPermission(ctx)) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = lm.getProviders(true)
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000) return last
    // Ask every enabled provider at once and take the first fix: fused alone can stay silent on de-Googled phones.
    val result = CompletableDeferred<android.location.Location>()
    val signals = mutableListOf<android.os.CancellationSignal>()
    val listener = android.location.LocationListener { if (!result.isCompleted) result.complete(it) }
    for (p in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = android.os.CancellationSignal().also { signals += it }
            lm.getCurrentLocation(p, signal, ctx.mainExecutor) { if (it != null && !result.isCompleted) result.complete(it) }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(p, listener, ctx.mainLooper)
        }
    }
    return try {
        withTimeoutOrNull(10_000) { result.await() } ?: last
    } finally {
        signals.forEach { it.cancel() }
        lm.removeUpdates(listener)
    }
}
