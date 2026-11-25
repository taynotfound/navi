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

    // settings
    var theme by mutableStateOf("dark") // dark | light | system
    var dynamicColor by mutableStateOf(false)
    var onboarded by mutableStateOf(false)
    var wheelchairDefault by mutableStateOf(false)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("navi", Context.MODE_PRIVATE)
        favorites = read("favorites", places)
        recentPlaces = read("recentPlaces", places)
        recentJourneys = read("recentJourneys", journeys)
        // trips are kept until a day after arrival
        val cutoff = ZonedDateTime.now().minusDays(1)
        savedTrips = read("savedTrips", trips).filter { it.itinerary.legs.isNotEmpty() && (parseTime(it.itinerary.end) ?: cutoff) >= cutoff }.distinctBy { it.key }
        theme = prefs.getString("theme", "dark")!!
        dynamicColor = prefs.getBoolean("dynamicColor", false)
        onboarded = prefs.getBoolean("onboarded", false)
        wheelchairDefault = prefs.getBoolean("wheelchairDefault", false)
        JourneyForm.wheelchair = wheelchairDefault
        prefs.edit().putInt("launches", prefs.getInt("launches", 0) + 1)
            .apply { if (!prefs.contains("firstLaunch")) putLong("firstLaunch", System.currentTimeMillis()) }.apply()
    }

    fun saveSettings() = prefs.edit()
        .putString("theme", theme).putBoolean("dynamicColor", dynamicColor)
        .putBoolean("onboarded", onboarded).putBoolean("wheelchairDefault", wheelchairDefault).apply()

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

    // ----- favourites / history -----
    fun isFavorite(id: String) = favorites.any { it.id == id }

    fun toggleFavorite(loc: Location) {
        favorites = if (isFavorite(loc.id)) favorites.filterNot { it.id == loc.id } else favorites + loc
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
    var selected by mutableStateOf<Itinerary?>(null)

    fun swap() { val f = from; from = to; to = f }
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
