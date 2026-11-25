package app.navi.ui

import android.annotation.SuppressLint
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.navi.Step
import app.navi.WalkGuide
import app.navi.hasLocationPermission
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navi.Leg
import app.navi.Nav
import app.navi.R
import app.navi.currentPosition
import app.navi.formatDistance
import app.navi.hhmm

/** The walk the user tapped, and the ride it leads to (for the platform). [navigate]: open straight into navigation. */
object WalkContext {
    var walk: Leg? = null; private set
    var next: Leg? = null; private set
    var navigate = false
    fun set(walk: Leg, next: Leg?, navigate: Boolean = false) { this.walk = walk; this.next = next; this.navigate = navigate }
}

private val LOCATION_PERMS = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * One walk, like a maps app. Overview: the whole path, the turns, where the platform is.
 * Navigation: the map follows you heading-up, a big banner says the next turn and how far it is, the bottom says
 * how long is left and whether the connection still works. The screen stays on while navigating.
 */
@Composable
fun WalkMapScreen(nav: Nav) {
    val l = WalkContext.walk ?: run { LaunchedEffect(Unit) { nav.back() }; return }
    val ctx = LocalContext.current
    var navigating by rememberSaveable { mutableStateOf(WalkContext.navigate && hasLocationPermission(ctx)) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) navigating = true
    }
    fun start() { if (hasLocationPermission(ctx)) navigating = true else askLocation.launch(LOCATION_PERMS) }
    // ask for location right away when we were sent here to navigate and don't have it yet
    LaunchedEffect(Unit) { if (WalkContext.navigate && !navigating) start(); WalkContext.navigate = false }
    BackHandler(navigating) { navigating = false }

    if (navigating) WalkNavigation(l, WalkContext.next, onStop = { navigating = false }, onDone = nav::back)
    else WalkOverview(l, WalkContext.next, nav, onStart = ::start)
}

@Composable
private fun WalkOverview(l: Leg, next: Leg?, nav: Nav, onStart: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val line = remember(l) { legLine(l).copy(color = c.primary) }
    val dest = l.to
    val points = remember(l) { walkPoints(l, next, c.secondary, next?.let(::legColor) ?: c.tertiary) }
    val whole = remember(l) { MapCamera(line.points, maxZoom = 19.0) }
    var camera by remember { mutableStateOf(whole) }
    var zoomedOnMe by remember { mutableStateOf(false) }
    val steps = l.steps.filter { it.distance > 0 || it.kind == "ELEVATOR" }

    Box(Modifier.fillMaxSize()) {
        NaviMap(Modifier.fillMaxSize(), lines = listOf(line), points = points, camera = camera, showMyLocation = true)

        // where to: platform first, that's what people look for
        Surface(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), color = c.surfaceContainerHigh, shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        next?.from?.platform?.let { stringResource(R.string.walk_to_platform, it) } ?: stringResource(R.string.walk_to, dest.name),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            dest.name.takeIf { next?.from?.platform != null },
                            dur(l.duration), formatDistance(l.distance),
                            next?.let { stringResource(R.string.walk_next_ride, it.line.orEmpty(), hhmm(it.from.departure ?: it.from.scheduledDeparture)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        val me = if (zoomedOnMe) null else currentPosition(ctx)
                        camera = if (me != null) MapCamera(listOf(me.latitude to me.longitude, dest.lat to dest.lon), maxZoom = 19.0) else whole
                        zoomedOnMe = me != null
                    }
                },
                Modifier.align(Alignment.End).padding(12.dp),
            ) { Icon(Icons.Filled.MyLocation, stringResource(R.string.recenter)) }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = c.surfaceContainer, shadowElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().padding(top = 8.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(32.dp, 4.dp).clip(CircleShape).background(c.outlineVariant))
                    if (steps.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 220.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        items(steps) { st ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                    Icon(stepIcon(st.kind), null, Modifier.size(18.dp), tint = if (st.kind in setOf("STAIRS", "ELEVATOR", "ESCALATOR")) c.tertiary else c.onSurface)
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(stepText(st), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                formatDistance(st.distance.toDouble())?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = {
                            val gmaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${dest.lat},${dest.lon}&mode=w"))
                            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${dest.lat},${dest.lon}?q=${dest.lat},${dest.lon}(${Uri.encode(dest.name)})"))
                            runCatching { ctx.startActivity(gmaps) }.onFailure { runCatching { ctx.startActivity(geo) } }
                        }) { Text(stringResource(R.string.open_in_maps)) }
                        Button(onClick = onStart) {
                            Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.nav_start))
                        }
                    }
                }
            }
        }
    }
}

private fun walkPoints(l: Leg, next: Leg?, start: Color, end: Color) = listOf(
    MapPoint("from", l.from.lat, l.from.lon, start, l.from.name),
    MapPoint("to", l.to.lat, l.to.lon, end, next?.from?.platform?.let { "${l.to.name} · $it" } ?: l.to.name, radius = 9f),
)

/** GPS and network fixes, about once a second, while on screen. Null until the first one. */
@SuppressLint("MissingPermission")
@Composable
private fun rememberLiveLocation(): android.location.Location? {
    val ctx = LocalContext.current
    var loc by remember { mutableStateOf<android.location.Location?>(null) }
    DisposableEffect(Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !hasLocationPermission(ctx)) return@DisposableEffect onDispose { }
        fun take(l: android.location.Location) {
            val cur = loc
            // a fresh fix wins; an older one only if it's clearly more precise
            if (cur == null || l.time - cur.time > 5_000 || l.accuracy <= cur.accuracy || l.provider == cur.provider) loc = l
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(l: android.location.Location) = take(l)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("old API")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        providers.forEach { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.takeIf { System.currentTimeMillis() - it.time < 60_000 }?.let(::take)
            runCatching { lm.requestLocationUpdates(p, 1_000L, 0f, listener, Looper.getMainLooper()) }
        }
        onDispose { lm.removeUpdates(listener) }
    }
    return loc
}

@Composable
private fun WalkNavigation(l: Leg, next: Leg?, onStop: () -> Unit, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val haptic = LocalHapticFeedback.current

    val guide = remember(l) { WalkGuide(legLine(l).points, l.steps) }
    val dest = l.to.lat to l.to.lon
    val loc = rememberLiveLocation()
    val me = loc?.let { it.latitude to it.longitude }
    val fix = remember(me) { me?.let { guide.locate(it.first, it.second) } }
    val cue = remember(fix) { fix?.let { guide.cue(it, dest, me!!) } }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10_000); now = ZonedDateTime.now() } }

    // a buzz when a turn is close, once per turn
    var buzzed by remember { mutableStateOf<Step?>(null) }
    LaunchedEffect(cue?.next, (cue?.inMeters ?: 99.0) < 15) {
        val n = cue?.next ?: return@LaunchedEffect
        if (cue.inMeters < 15 && buzzed !== n) { buzzed = n; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    // walked part grey, the rest in the accent colour
    val lines = remember(fix?.along?.toInt()) {
        val pts = guide.points
        if (fix == null || pts.size < 2) listOf(MapLine(pts, c.primary))
        else {
            var acc = 0.0
            var cut = pts.lastIndex
            for (i in 1 until pts.size) { acc += WalkGuide.dist(pts[i - 1], pts[i]); if (acc >= fix.along) { cut = i; break } }
            listOf(
                MapLine(pts.take(cut) + listOf(fix.snapped), c.outline),
                MapLine(listOf(fix.snapped) + pts.drop(cut), c.primary),
            )
        }
    }
    val points = remember(l) { walkPoints(l, next, c.secondary, next?.let(::legColor) ?: c.tertiary) }
    var overview by remember { mutableStateOf(false) }
    val whole = remember(l) { MapCamera(guide.points, maxZoom = 19.0) }
    // follow: your real position when you're off the path, else the point on it (steadier); heading from your
    // movement when walking, from the path when standing
    val camera = if (overview || me == null || fix == null) whole else {
        val at = if (fix.offRoute) me else fix.snapped
        val heading = loc.takeIf { it.hasBearing() && it.speed > 0.8f }?.bearing?.toDouble() ?: fix.pathBearing
        MapCamera(listOf(at), zoom = 18.0, bearing = heading, tilt = 50.0)
    }

    // speed for the time left: this walk's own pace, else an ordinary one
    val speed = ((l.distance ?: 0.0) / l.duration.coerceAtLeast(1)).takeIf { it in 0.5..2.5 } ?: 1.25
    val remaining = cue?.remaining ?: guide.length
    val minutesLeft = kotlin.math.ceil(remaining / speed / 60).toLong()
    val arriveAt = now.plusSeconds((remaining / speed).toLong())

    Box(Modifier.fillMaxSize()) {
        NaviMap(Modifier.fillMaxSize(), lines = lines, points = points, camera = camera, showMyLocation = true)

        // the instruction: icon, distance, what to do
        val (bg, fg) = when {
            cue?.arrived == true -> c.tertiaryContainer to c.onTertiaryContainer
            fix?.offRoute == true -> c.errorContainer to c.onErrorContainer
            else -> c.primaryContainer to c.onPrimaryContainer
        }
        Surface(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg, shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        cue?.arrived == true -> Icons.Filled.Flag
                        fix?.offRoute == true -> Icons.Filled.WrongLocation
                        me == null -> Icons.Filled.MyLocation
                        else -> cue?.next?.let { stepIcon(it.kind) } ?: Icons.Filled.Flag
                    }
                    Icon(icon, null, Modifier.size(44.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        when {
                            me == null -> Text(stringResource(R.string.locating), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            cue?.arrived == true -> {
                                Text(stringResource(R.string.nav_arrived), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(next?.from?.platform?.let { stringResource(R.string.walk_to_platform, it) } ?: l.to.name, style = MaterialTheme.typography.bodyLarge)
                            }
                            fix?.offRoute == true -> {
                                Text(stringResource(R.string.nav_off_route), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.nav_off_route_hint, navDistance(WalkGuide.dist(me, dest))), style = MaterialTheme.typography.bodyLarge)
                            }
                            else -> {
                                Text(navDistance(cue?.inMeters ?: remaining), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    cue?.next?.let { stepText(it) } ?: stringResource(R.string.nav_arrive_at_dest, l.to.name),
                                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                val then = cue?.then
                if (then != null && fix?.offRoute != true && cue.arrived.not()) Row(
                    Modifier.padding(top = 10.dp).clip(CircleShape).background(fg.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.nav_then), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(stepIcon(then.kind), stepText(then), Modifier.size(18.dp))
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SmallFloatingActionButton(onClick = { overview = !overview }, Modifier.align(Alignment.End).padding(12.dp)) {
                Icon(if (overview) Icons.Filled.Navigation else Icons.Filled.ZoomOutMap, stringResource(if (overview) R.string.nav_follow else R.string.nav_overview))
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = c.surfaceContainer, shadowElevation = 6.dp) {
                Row(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (cue?.arrived == true) {
                            Text(stringResource(R.string.nav_arrived), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(
                                stringResource(R.string.chip_min, minutesLeft),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.primary,
                            )
                            Text(
                                listOfNotNull(navDistance(remaining), stringResource(R.string.nav_arrive_at, hhmm(arriveAt))).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                            )
                        }
                        // the reason for walking: will the next ride still work?
                        next?.let { n ->
                            val dep = app.navi.parseTime(n.from.departure ?: n.from.scheduledDeparture)
                            if (dep != null) {
                                val spare = java.time.Duration.between(arriveAt, dep).toMinutes()
                                val ok = spare >= 0
                                Text(
                                    stringResource(R.string.walk_next_ride, n.line.orEmpty(), hhmm(dep)) + " · " +
                                        (if (ok) stringResource(R.string.nav_spare, spare) else stringResource(R.string.nav_short, -spare)),
                                    Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (ok) c.onSurface else c.error,
                                )
                            }
                        }
                    }
                    if (cue?.arrived == true) Button(onClick = onDone) { Text(stringResource(R.string.done)) }
                    else FilledTonalIconButton(onClick = onStop, Modifier.size(52.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.nav_exit)) }
                }
            }
        }
    }
}

/** Distances the way you read them while walking: "Now", "80 m", "1,2 km". */
@Composable
private fun navDistance(m: Double): String = when {
    m < 10 -> stringResource(R.string.nav_now)
    m < 1000 -> "${(kotlin.math.round(m / 10) * 10).toInt()} m"
    else -> "%.1f km".format(m / 1000).let { if (java.util.Locale.getDefault().language == "de") it.replace('.', ',') else it }
}
