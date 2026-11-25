package app.navi.ui

import app.navi.Place
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Escalator
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.navi.Api
import app.navi.R
import app.navi.Nav
import app.navi.Equipment
import app.navi.Location
import app.navi.Row as BoardRow
import app.navi.Station
import app.navi.Store
import app.navi.currentPosition
import app.navi.hasLocationPermission
import app.navi.parseTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

private val LOCATION_PERMS = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)

// =====================================================================
// Departures tab: search, nearby, favourites
// =====================================================================

@Composable
fun DeparturesTab(nav: Nav) {
    val ctx = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    var permitted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permitted = it.values.any { g -> g } }
    val noLocation = stringResource(R.string.location_unavailable)
    val nearby = rememberLoad(permitted) {
        if (!permitted) emptyList() else {
            val pos = currentPosition(ctx) ?: throw Exception(noLocation)
            Api.nearby(pos.latitude, pos.longitude)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                stringResource(R.string.departures),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            )
            Surface(
                onClick = { picking = true },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.search_station), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val favs = Store.favorites.filter { it.type == "STOP" }
        if (favs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.favorites)) }
            items(favs, key = { "f" + it.id }) { PlaceRow(it, Icons.Filled.Star) { nav.board(it) } }
        }
        item { SectionHeader(stringResource(R.string.nearby)) }
        val n = nearby.value
        when {
            !permitted -> item {
                MessageState(Icons.Filled.NearMe, stringResource(R.string.nearby_permission)) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { permission.launch(LOCATION_PERMS) }) { Text(stringResource(R.string.allow_location)) }
                }
            }
            n.data == null && n.loading -> item { LoadingState(text = stringResource(R.string.searching_nearby)) }
            n.error != null && n.data.isNullOrEmpty() -> item { ErrorState(n.error, nearby.reload, icon = Icons.Filled.LocationOff) }
            n.data.isNullOrEmpty() -> item { MessageState(Icons.Filled.NearMe, stringResource(R.string.nearby_empty)) }
            else -> items(n.data, key = { "n" + it.id }) { PlaceRow(it, null) { nav.board(it) } }
        }
    }

    if (picking) PlacePicker(stringResource(R.string.station_or_stop), { picking = false }, { picking = false; nav.board(it) }, stopsOnly = true, allowMyLocation = false)
}

// =====================================================================
// Board
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(stop: Location, nav: Nav) {
    var arrivals by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(setOf<String>()) }
    val board = rememberLoad(stop.id, arrivals) { Api.board(stop.id, arrivals) }
    LaunchedEffect(board) { // keep it live while the screen is open
        while (true) { delay(30_000); board.reload() }
    }
    val b = board.value
    val title = b.data?.stop?.name ?: stop.name

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (b.data?.stop?.hasStationInfo == true) IconButton(onClick = { nav.station(stop.id, title) }) { Icon(Icons.Filled.Info, stringResource(R.string.station_info)) }
                    IconButton(onClick = { nav.planTo(stop.copy(name = title)) }) { Icon(Icons.Filled.Directions, stringResource(R.string.route_here)) }
                    val fav = Store.isFavorite(stop.id)
                    IconButton(onClick = { Store.toggleFavorite(stop.copy(name = title)) }) {
                        Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(if (fav) R.string.unfavorite else R.string.favorite))
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = b.loading && b.data != null, onRefresh = board.reload, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    ConnectedChoice(
                        listOf(stringResource(R.string.departures), stringResource(R.string.arrivals)), if (arrivals) 1 else 0, { arrivals = it == 1 },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val rows = b.data?.rows.orEmpty()
                val groups = rows.map { ModeGroup.of(it.mode) }.distinct().sortedBy { it.ordinal }
                if (groups.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = ItemSpacing) {
                        items(groups) { g ->
                            FilterChip(
                                selected = g.name in filter,
                                onClick = { filter = if (g.name in filter) filter - g.name else filter + g.name },
                                label = { Text(stringResource(g.label)) },
                                leadingIcon = { Icon(g.icon, null, Modifier.size(18.dp), tint = g.color) },
                            )
                        }
                    }
                }
                val shown = rows.filter { filter.isEmpty() || ModeGroup.of(it.mode).name in filter }
                when {
                    b.data == null && b.loading -> item { LoadingState() }
                    b.data == null && b.error != null -> item { ErrorState(b.error, board.reload) }
                    shown.isEmpty() -> item { MessageState(Icons.Filled.Info, stringResource(if (arrivals) R.string.no_arrivals else R.string.no_departures)) }
                    else -> {
                        if (b.error != null) item { Text(stringResource(R.string.refresh_failed), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        items(shown, key = { it.source + it.id + it.scheduledTime + it.line }) { r ->
                            DepartureRow(r, arrivals) { r.tripId?.let { TripContext.put(it, r.notes, r.attributes); nav.trip(it) } }
                            HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                        item {
                            Text(
                                stringResource(R.string.board_footer),
                                Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(r: BoardRow, arrivals: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = r.tripId != null, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimeColumn(r.scheduledTime, r.time, r.delay, r.cancelled, Modifier.width(60.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineChip(r.line, r.mode, r.color, r.textColor)
                Spacer(Modifier.width(8.dp))
                Countdown(r.time, r.cancelled)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (arrivals) stringResource(R.string.from_origin, r.headsign ?: "–") else r.headsign ?: "–",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                textDecoration = if (r.cancelled) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (r.via.isNotEmpty()) Text(stringResource(R.string.via, r.via.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!r.realtime) UsualDelayLine(r.usual, Modifier.padding(top = 2.dp))
            r.notes.minByOrNull { it.length }?.let {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(14.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        PlatformBadge(r.platform, r.scheduledPlatform)
    }
}

// =====================================================================
// Trip (one vehicle's run)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(id: String, nav: Nav, on: String? = null, off: String? = null) {
    val trip = rememberLoad(id) { Api.trip(id) }
    val t = trip.value
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    t.data?.let { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LineChip(l.line, l.mode, l.color, l.textColor)
                            Spacer(Modifier.width(8.dp))
                            Text("→ ${l.headsign ?: l.to.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
            )
        },
    ) { pad ->
        val l = t.data
        when {
            l == null && t.loading -> Box(Modifier.padding(pad)) { LoadingState() }
            l == null -> Box(Modifier.padding(pad)) { ErrorState(t.error ?: stringResource(R.string.trip_not_found), trip.reload) }
            else -> {
                val all = listOf(l.from) + l.stops + listOf(l.to)
                var now by remember { mutableStateOf(ZonedDateTime.now()) }
                // live: refetch every 30 s, move the "now" marker every 15 s
                LaunchedEffect(id) { while (true) { kotlinx.coroutines.delay(30_000); trip.reload() } }
                LaunchedEffect(id) { while (true) { kotlinx.coroutines.delay(15_000); now = ZonedDateTime.now() } }
                fun dep(p: Place) = parseTime(p.departure ?: p.scheduledDeparture ?: p.arrival ?: p.scheduledArrival)
                fun arr(p: Place) = parseTime(p.arrival ?: p.scheduledArrival ?: p.departure ?: p.scheduledDeparture)
                val nextIdx = all.indexOfFirst { (arr(it) ?: now) > now }
                val lineColor = legColor(l)
                val line = remember(l) { legLine(l) }
                // estimated vehicle position: between the last stop it left and the next one, by time
                val vehicle = if (nextIdx > 0) {
                    val a = all[nextIdx - 1]; val b = all[nextIdx]
                    val t0 = dep(a); val t1 = arr(b)
                    if (t0 != null && t1 != null && now.isAfter(t0)) {
                        val f = (java.time.Duration.between(t0, now).seconds.toDouble() / java.time.Duration.between(t0, t1).seconds.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                        MapPoint("vehicle", a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, Display.amber, null, 9f)
                    } else null
                } else null
                val pts = all.map { MapPoint(it.stopId ?: it.name, it.lat, it.lon, lineColor, null, 5f) } + listOfNotNull(vehicle)
                val c = MaterialTheme.colorScheme
                // your part of the ride, when opened from a journey; stops outside it are dimmed
                val onIdx = on?.let { s -> all.indexOfFirst { it.stopId == s } }?.takeIf { it >= 0 }
                val offIdx = off?.let { s -> all.indexOfLast { it.stopId == s } }?.takeIf { it >= 0 && it > (onIdx ?: -1) }
                fun outside(i: Int) = (onIdx != null && i < onIdx) || (offIdx != null && i > offIdx)
                LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        NaviMap(
                            Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                            lines = listOf(line), points = pts, camera = remember(l) { MapCamera(line.points) }, showMyLocation = true,
                            onPointClick = { pid -> all.firstOrNull { (it.stopId ?: it.name) == pid }?.let { p -> p.stopId?.let { s -> nav.board(Location(s, "STOP", p.name, lat = p.lat, lon = p.lon)) } } },
                        )
                        Text(
                            listOfNotNull(
                                l.operator,
                                dur(l.duration),
                                androidx.compose.ui.res.pluralStringResource(R.plurals.stops, all.size, all.size),
                                if (vehicle != null) stringResource(R.string.vehicle_estimated) else null,
                            ).joinToString(", "),
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                        )
                    }
                    item {
                        val (notes, attrs) = remember(id) { TripContext.take(id) }
                        val all2 = (notes + l.notes + l.alerts.map { a -> listOfNotNull(a.header, a.text).distinct().joinToString(": ") }).distinct()
                        if (all2.isNotEmpty() || attrs.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            ReasonList(all2)
                            if (attrs.isNotEmpty()) Text(attrs.joinToString(", "), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                    l.line?.let { ln ->
                        item { PunctualitySection(ln, l.operator, on) }
                        item { SpotterPhotos(ln, l.operator, l.mode) }
                    }
                    items(all.size) { i ->
                        val p = all[i]
                        val passed = nextIdx == -1 || i < nextIdx
                        val sched = if (i == 0) p.scheduledDeparture else p.scheduledArrival ?: p.scheduledDeparture
                        val actual = if (i == 0) p.departure else p.arrival ?: p.departure
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (outside(i)) .45f else 1f)
                                .clickable(enabled = p.stopId != null) { nav.board(Location(p.stopId!!, "STOP", p.name, lat = p.lat, lon = p.lon)) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimeColumn(sched, actual, delayOf(actual, sched, l.realtime), p.cancelled, Modifier.width(60.dp).padding(vertical = 10.dp), big = false)
                            // the line itself: amber behind you, line colour ahead, next stop as a ring
                            Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                                val x = size.width / 2; val y = size.height / 2; val w = 5.dp.toPx(); val r = 8.dp.toPx()
                                val ahead = lineColor
                                // segment k -> k+1 is behind you once k+1 is passed, or while you're on it
                                fun done(k: Int) = nextIdx == -1 || k + 1 < nextIdx || (k + 1 == nextIdx && vehicle != null)
                                if (i > 0) drawLine(if (done(i - 1)) Display.amber else ahead, Offset(x, 0f), Offset(x, y), w)
                                if (i < all.lastIndex) drawLine(if (done(i)) Display.amber else ahead, Offset(x, y), Offset(x, size.height), w)
                                when {
                                    passed -> drawCircle(Display.amber, r, Offset(x, y))
                                    i == nextIdx -> { drawCircle(c.surface, r + 1.dp.toPx(), Offset(x, y)); drawCircle(c.tertiary, r, Offset(x, y), style = Stroke(3.5.dp.toPx())) }
                                    else -> { drawCircle(c.surface, r, Offset(x, y)); drawCircle(ahead, r - 1.dp.toPx(), Offset(x, y), style = Stroke(3.dp.toPx())) }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(
                                    p.name, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (i == 0 || i == all.lastIndex || i == nextIdx || i == onIdx || i == offIdx) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (passed) c.onSurfaceVariant else c.onSurface,
                                )
                                val tag = listOfNotNull(
                                    if (i == onIdx) stringResource(R.string.you_board) else null,
                                    if (i == offIdx) stringResource(R.string.you_get_off) else null,
                                    if (i == nextIdx) stringResource(R.string.next_stop) else null,
                                ).joinToString(", ")
                                if (tag.isNotEmpty()) Text(tag, style = MaterialTheme.typography.labelMedium, color = if (i == nextIdx) c.tertiary else c.primary)
                            }
                            PlatformBadge(p.platform, p.scheduledPlatform)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Station: accessibility, lifts (live), platforms, map
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(stopId: String, name: String, nav: Nav) {
    val ctx = LocalContext.current
    val station = rememberLoad(stopId) { Api.station(stopId) }
    val s = station.value
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(s.data?.name ?: name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    ) { pad ->
        val st = s.data
        when {
            st == null && s.loading -> Box(Modifier.padding(pad)) { LoadingState() }
            st == null -> Box(Modifier.padding(pad)) { ErrorState(s.error ?: stringResource(R.string.no_station_data), station.reload) }
            else -> PullToRefreshBox(isRefreshing = s.loading, onRefresh = station.reload, modifier = Modifier.padding(pad).fillMaxSize()) {
                StationContent(st) { url -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    }
}

@Composable
private fun StationContent(st: Station, openUrl: (String) -> Unit) {
    val moving = st.equipment.filter { it.type == "lift" || it.type == "escalator" }
    val broken = moving.filter { it.status == "INACTIVE" }
    val toilets = st.equipment.filter { it.type == "toilet" }
    val located = st.equipment.filter { it.lat != null && it.lon != null && it.type != "ramp" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            st.address?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = ItemSpacing) {
                item { AccessChip(stringResource(R.string.step_free), st.accessibility.stepFree) }
                item { AccessChip(stringResource(R.string.wheelchair), st.accessibility.wheelchair) }
                item { AccessChip(stringResource(R.string.tactile_guidance), st.accessibility.tactileGuidance) }
            }
        }
        if (moving.isNotEmpty()) item {
            val ok = broken.isEmpty()
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = if (!st.liveStatus) MaterialTheme.colorScheme.surfaceContainerHigh else if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when {
                            !st.liveStatus -> stringResource(R.string.lifts_no_live)
                            ok -> stringResource(R.string.lifts_all_ok)
                            else -> androidx.compose.ui.res.pluralStringResource(R.plurals.lifts_broken, broken.size, broken.size)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
        if (located.isNotEmpty()) item {
            SectionHeader(stringResource(R.string.station_map))
            val pts = remember(st) {
                located.map { e ->
                    MapPoint(
                        e.equipmentNumber ?: e.name, e.lat!!, e.lon!!,
                        when { e.type == "toilet" -> Color(0xFF1565C0); e.status == "INACTIVE" -> Color(0xFFC62828); e.status == "ACTIVE" -> Color(0xFF2E7D32); else -> Color(0xFF757575) },
                        e.name.takeIf { it.isNotBlank() }, 7f,
                    )
                }
            }
            NaviMap(
                Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                points = pts, camera = remember(st) { MapCamera(pts.map { it.lat to it.lon }, zoom = 17.0, maxZoom = 17.0) }, showMyLocation = true,
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(Color(0xFF2E7D32), stringResource(R.string.in_service)); Legend(Color(0xFFC62828), stringResource(R.string.out_of_service)); Legend(Color(0xFF1565C0), stringResource(R.string.wc))
            }
        }
        if (moving.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.lifts_escalators)) }
            items(moving.sortedBy { it.status != "INACTIVE" }) { EquipmentRow(it) }
        }
        if (st.platforms.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.platforms)) }
            items(st.platforms) { p ->
                ListItem(
                    headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(listOfNotNull(
                            p.tracks.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.platform_n, it.joinToString(", ")) },
                            p.heightCm?.let { stringResource(R.string.height_cm, it) },
                            p.lengthM?.let { stringResource(R.string.length_m, it.toInt()) },
                            when (p.stepFree) { "true" -> stringResource(R.string.step_free_lc); "false" -> stringResource(R.string.not_step_free); else -> null },
                        ).joinToString(", "))
                    },
                )
            }
        }
        if (toilets.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.toilets)) }
            items(toilets) { t ->
                ListItem(
                    headlineContent = { Text(t.name.ifBlank { stringResource(R.string.toilet) }) },
                    leadingContent = { Icon(if (t.wheelchair) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.Wc, null) },
                )
            }
        }
        if (st.entrances.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.entrances)) }
            items(st.entrances) { ListItem(headlineContent = { Text(it) }) }
        }
        item {
            st.url?.let {
                OutlinedButton(onClick = { openUrl(it) }, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.more_on_bahnhof))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                }
            }
            Text(
                stringResource(R.string.station_footer),
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessChip(label: String, value: String) {
    val (text, color) = when (value) {
        "true" -> stringResource(R.string.yes) to StatusColors.onTime
        "partial" -> stringResource(R.string.partial) to StatusColors.late
        "false" -> stringResource(R.string.no) to StatusColors.veryLate
        else -> stringResource(R.string.unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(onClick = {}, label = { Text("$label: $text") }, leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) })
}

@Composable
private fun Legend(c: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun EquipmentRow(e: Equipment) {
    val (label, color) = when (e.status) {
        "ACTIVE" -> stringResource(R.string.in_service) to StatusColors.onTime
        "INACTIVE" -> stringResource(R.string.out_of_service) to StatusColors.veryLate
        else -> stringResource(R.string.unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val kind = stringResource(if (e.type == "lift") R.string.lift else R.string.escalator)
    ListItem(
        headlineContent = { Text(e.name.ifBlank { kind }) },
        supportingContent = { Text(kind) },
        leadingContent = { Icon(if (e.type == "lift") Icons.Filled.Elevator else Icons.Filled.Escalator, null) },
        trailingContent = { Text(label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge) },
    )
}

// =====================================================================
// Map tab
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTab(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    var stops by remember { mutableStateOf<List<Location>>(emptyList()) }
    var zoom by remember { mutableStateOf(5.0) }
    var selected by remember { mutableStateOf<Location?>(null) }
    var query by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }

    fun locate() = scope.launch { currentPosition(ctx)?.let { camera = MapCamera(listOf(it.latitude to it.longitude), 15.5) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = it.values.any { g -> g }
        if (permitted) locate()
    }
    LaunchedEffect(Unit) { if (permitted) locate() else permission.launch(LOCATION_PERMS) }
    LaunchedEffect(query) {
        val (lat, lon, z) = query ?: return@LaunchedEffect
        if (z < 13.5) { stops = emptyList(); return@LaunchedEffect }
        delay(300) // wait until panning settles
        runCatching { Api.nearby(lat, lon, if (z >= 15) 900 else 2000) }.onSuccess { stops = it }
    }

    Box(Modifier.fillMaxSize()) {
        NaviMap(
            Modifier.fillMaxSize(),
            points = remember(stops) {
                stops.map { s -> MapPoint(s.id, s.lat, s.lon, s.modes.map { ModeGroup.of(it) }.minByOrNull { it.ordinal }?.color ?: ModeGroup.OTHER.color, s.name) }
            },
            camera = camera,
            showMyLocation = permitted,
            onPointClick = { id -> selected = stops.firstOrNull { it.id == id } },
            onCameraIdle = { lat, lon, z -> zoom = z; query = Triple(lat, lon, z) },
        )
        if (zoom < 13.5) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(16.dp),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ZoomIn, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.zoom_in_hint), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        FloatingActionButton(
            onClick = { if (permitted) locate() else permission.launch(LOCATION_PERMS) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.MyLocation, stringResource(R.string.my_location)) }
    }

    selected?.let { stop ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            StopSheet(stop, nav) { selected = null }
        }
    }
}

@Composable
private fun StopSheet(stop: Location, nav: Nav, close: () -> Unit) {
    val board = rememberLoad(stop.id) { Api.board(stop.id) }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(stop.name, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = ItemSpacing) {
            Button(onClick = { close(); nav.board(stop) }) { Text(stringResource(R.string.all_departures)) }
            OutlinedButton(onClick = { close(); nav.planTo(stop) }) {
                Icon(Icons.Filled.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.route_here_short))
            }
        }
        val b = board.value
        when {
            b.data == null && b.loading -> LoadingState()
            b.data == null -> ErrorState(b.error ?: "", board.reload)
            b.data.rows.isEmpty() -> Text(stringResource(R.string.no_departures_now), Modifier.padding(20.dp))
            else -> b.data.rows.take(5).forEach { r -> DepartureRow(r, false) { r.tripId?.let { TripContext.put(it, r.notes, r.attributes); close(); nav.trip(it) } } }
        }
    }
}
