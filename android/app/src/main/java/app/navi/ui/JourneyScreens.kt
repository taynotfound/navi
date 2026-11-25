package app.navi.ui

import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.ui.text.withStyle
import app.navi.Step
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Escalator
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.navi.Api
import app.navi.R
import app.navi.Nav
import app.navi.Itinerary
import app.navi.JourneyForm
import app.navi.Leg
import app.navi.L
import app.navi.Location
import app.navi.Place
import app.navi.Store
import app.navi.currentPosition
import app.navi.decodePolyline
import app.navi.formatDistance
import app.navi.formatDuration
import app.navi.hasLocationPermission
import app.navi.hhmm
import app.navi.minutesUntil
import app.navi.myLocation
import app.navi.parseTime
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

fun delayOf(actual: String?, scheduled: String?, realtime: Boolean): Int? {
    if (!realtime) return null
    val a = parseTime(actual) ?: return null
    val s = parseTime(scheduled) ?: return null
    return Duration.between(s, a).toMinutes().toInt()
}

// =====================================================================
// Search form (tab)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JourneyTab(nav: Nav) {
    val ctx = LocalContext.current
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "from" | "to"

    // Idiot-proof default: start where you are.
    LaunchedEffect(Unit) {
        if (JourneyForm.from == null && hasLocationPermission(ctx)) {
            currentPosition(ctx)?.let { if (JourneyForm.from == null) JourneyForm.from = myLocation(it.latitude, it.longitude) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.where_to),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        )
        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp)) {
            Box {
                Column(Modifier.padding(vertical = 8.dp)) {
                    PlaceField(stringResource(R.string.from), JourneyForm.from, stringResource(R.string.choose_start), dotFilled = true) { picking = "from" }
                    HorizontalDivider(Modifier.padding(start = 52.dp, end = 72.dp))
                    PlaceField(stringResource(R.string.to), JourneyForm.to, stringResource(R.string.choose_destination), dotFilled = false) { picking = "to" }
                }
                FilledTonalIconButton(
                    onClick = { JourneyForm.swap() },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                ) { Icon(Icons.Filled.SwapVert, stringResource(R.string.swap)) }
            }
        }

        TripOptionChips(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

        val ready = JourneyForm.from != null && JourneyForm.to != null && JourneyForm.from?.id != JourneyForm.to?.id
        Button(
            onClick = {
                Store.rememberJourney(JourneyForm.from!!, JourneyForm.to!!)
                nav.results()
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Filled.Search, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.search_connections), style = MaterialTheme.typography.titleMedium)
        }
        if (!ready) {
            Text(
                stringResource(when {
                    JourneyForm.from == null -> R.string.hint_choose_start
                    JourneyForm.to == null -> R.string.hint_choose_destination
                    else -> R.string.hint_same_places
                }),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // "Frag Navi": ask in your own words instead of filling the form (can be hidden in Settings)
        if (Store.assistantEnabled) androidx.compose.material3.OutlinedButton(
            onClick = nav::assistant,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp).height(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ask_entry))
        }

        if (Store.recentJourneys.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recent))
            Store.recentJourneys.forEach { j ->
                // each recent trip drawn as a tiny route: start dot, line, destination ring
                Row(
                    Modifier.fillMaxWidth().clickable {
                        JourneyForm.from = j.from; JourneyForm.to = j.to
                        nav.results()
                    }.padding(horizontal = 24.dp, vertical = 10.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val c = MaterialTheme.colorScheme
                    Canvas(Modifier.width(14.dp).fillMaxHeight().padding(vertical = 6.dp)) {
                        val x = size.width / 2; val r = 5.dp.toPx()
                        drawLine(c.outlineVariant, Offset(x, r), Offset(x, size.height - r), 3.dp.toPx())
                        drawCircle(c.primary, r, Offset(x, r))
                        drawCircle(c.surface, r, Offset(x, size.height - r))
                        drawCircle(c.tertiary, r - 1.dp.toPx(), Offset(x, size.height - r), style = Stroke(2.5.dp.toPx()))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(j.from.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onSurfaceVariant)
                        Text(j.to.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.onSurfaceVariant)
                }
            }
        }
    }

    picking?.let { target ->
        PlacePicker(
            title = stringResource(if (target == "from") R.string.from_where else R.string.to_where),
            onDismiss = { picking = null },
            onPick = { if (target == "from") JourneyForm.from = it else JourneyForm.to = it; picking = null },
        )
    }
}

@Composable
private fun PlaceField(label: String, value: Location?, placeholder: String, dotFilled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 20.dp, end = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = MaterialTheme.colorScheme.primary
        Box(
            Modifier.size(14.dp).clip(CircleShape)
                .then(if (dotFilled) Modifier.background(c) else Modifier.border(3.dp, MaterialTheme.colorScheme.tertiary, CircleShape)),
        )
        Spacer(Modifier.width(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value?.name ?: placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =====================================================================
// Results
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(nav: Nav) {
    val from = JourneyForm.from
    val to = JourneyForm.to
    if (from == null || to == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Itinerary>>(emptyList()) }
    var direct by remember { mutableStateOf<Itinerary?>(null) }
    var earlier by remember { mutableStateOf<String?>(null) }
    var later by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf<String?>("initial") }
    var error by remember { mutableStateOf<String?>(null) }

    fun key(i: Itinerary) = i.start + i.end + i.legs.joinToString { it.line.orEmpty() }
    fun load(cursor: String?, where: String) {
        loading = where; error = null
        scope.launch {
            try {
                val p = Api.plan(
                    from.id, to.id, JourneyForm.time?.toOffsetDateTime()?.toString(), JourneyForm.arriveBy,
                    JourneyForm.wheelchair, JourneyForm.bike, cursor, JourneyForm.modes,
                    JourneyForm.via?.id, JourneyForm.minTransfer, JourneyForm.walkSpeed,
                )
                val merged = when (where) { "earlier" -> p.itineraries + items; "later" -> items + p.itineraries; else -> p.itineraries }
                items = merged.distinctBy(::key).sortedBy { parseTime(it.start) }
                if (where != "later") earlier = p.earlier
                if (where != "earlier") later = p.later
                if (where == "initial") direct = p.direct.firstOrNull()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message
            } finally {
                loading = null
            }
        }
    }
    LaunchedEffect(from.id, to.id, JourneyForm.time, JourneyForm.arriveBy, JourneyForm.wheelchair, JourneyForm.bike, JourneyForm.modes,
        JourneyForm.via, JourneyForm.minTransfer, JourneyForm.walkSpeed) { load(null, "initial") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    Column {
                        Text("${from.name} → ${to.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(timeLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
            item { TripOptionChips(Modifier.padding(horizontal = 16.dp)) }
            when {
                loading == "initial" -> item { LoadingState(text = stringResource(R.string.searching_connections)) }
                error != null && items.isEmpty() -> item { ErrorState(error!!, onRetry = { load(null, "initial") }) }
                items.isEmpty() && direct == null -> item {
                    MessageState(Icons.Filled.Search, stringResource(R.string.no_connections))
                }
                else -> {
                    if (earlier != null) item {
                        OutlinedButton(onClick = { load(earlier, "earlier") }, enabled = loading == null, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(if (loading == "earlier") R.string.loading_short else R.string.earlier))
                        }
                    }
                    direct?.let { d ->
                        item {
                            Card(
                                onClick = { JourneyForm.selected = d; nav.itinerary() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.on_foot, dur(d.duration)), style = MaterialTheme.typography.titleSmall)
                                    d.legs.firstOrNull()?.distance?.let { Text(", ${formatDistance(it)}") }
                                }
                            }
                        }
                    }
                    items(items, key = ::key) { it ->
                        ItineraryCard(it) { JourneyForm.selected = it; nav.itinerary() }
                    }
                    if (error != null) item { Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    if (later != null) item {
                        OutlinedButton(onClick = { load(later, "later") }, enabled = loading == null, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(if (loading == "later") R.string.loading_short else R.string.later))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItineraryCard(it: Itinerary, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val transit = it.legs.filterNot { l -> l.isWalk }
    val first = transit.firstOrNull()
    val cancelled = it.legs.any { l -> l.cancelled || l.from.cancelled || l.to.cancelled }
    val depDelay = first?.let { l -> delayOf(l.from.departure, l.from.scheduledDeparture, l.realtime) }
    val arrDelay = transit.lastOrNull()?.let { l -> delayOf(l.to.arrival, l.to.scheduledArrival, l.realtime) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            // door to door
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(hhmm(it.start))
                        withStyle(androidx.compose.ui.text.SpanStyle(color = c.onSurfaceVariant, fontWeight = FontWeight.Normal)) { append(" – ") }
                        append(hhmm(it.end))
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(dur(it.duration), style = MaterialTheme.typography.titleMedium)
            }
            val status = when {
                (arrDelay ?: 0) > 0 -> stringResource(R.string.arrives_late, arrDelay!!) to delayColor(arrDelay)
                first?.realtime == true -> stringResource(R.string.on_time) to StatusColors.onTime
                else -> null
            }
            Row(Modifier.padding(top = 2.dp)) {
                Text(transfersText(it.transfers), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                status?.let { (t, col) -> Text(", $t", style = MaterialTheme.typography.bodyMedium, color = col) }
            }

            // the chain: every walk and every ride, in order
            FlowRow(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val parts = it.legs.filter { l -> !l.isWalk || l.duration >= 60 }
                parts.forEachIndexed { i, l ->
                    if (i > 0) Box(Modifier.width(10.dp).height(2.dp).background(c.outlineVariant))
                    if (l.isWalk) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(modeIcon(l.mode), null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                        Text(stringResource(R.string.n_min, (l.duration + 30) / 60), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                    } else LineChip(l.line, l.mode, l.color, l.textColor)
                }
            }

            // where the first ride leaves, and when
            if (first != null) {
                HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 10.dp), color = c.outlineVariant.copy(alpha = .5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hhmm(first.from.scheduledDeparture), style = MaterialTheme.typography.labelLarge)
                    if ((depDelay ?: 0) > 0) Text(" +$depDelay", style = MaterialTheme.typography.labelLarge, color = delayColor(depDelay))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        first.from.platform?.let { p -> stringResource(R.string.from_stop_platform, first.from.name, p) } ?: stringResource(R.string.from_stop, first.from.name),
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val m = minutesUntil(first.from.departure ?: first.from.scheduledDeparture)
                    if (m != null && m in 0..90) Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = stringResource(R.string.in_min, m), style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }
            if (cancelled) WarningLine(stringResource(R.string.part_cancelled), error = true)
            else if (it.legs.any { l -> l.alerts.isNotEmpty() }) WarningLine(stringResource(R.string.has_notices), error = false)
        }
    }
}

@Composable
private fun WarningLine(text: String, error: Boolean) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
        Icon(if (error) Icons.Filled.Warning else Icons.Filled.Info, null, Modifier.size(18.dp), tint = c)
        Spacer(Modifier.width(6.dp))
        Text(text, color = c, style = MaterialTheme.typography.labelLarge)
    }
}

// =====================================================================
// Itinerary detail
// =====================================================================

fun legColor(l: Leg): Color = (parseHex(l.color)?.takeIf { it != Color.White } ?: ModeGroup.of(l.mode).color).calm()

fun legLine(l: Leg) = MapLine(
    l.geometry?.let { decodePolyline(it.points, it.precision) } ?: listOf(l.from.lat to l.from.lon, l.to.lat to l.to.lon),
    legColor(l),
    dashed = l.isWalk,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(nav: Nav) {
    val planned = JourneyForm.selected?.takeIf { p -> p.legs.isNotEmpty() } ?: run { LaunchedEffect(Unit) { nav.back() }; return }
    val savedKey = remember(planned) { app.navi.tripKey(planned) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var it by remember(planned) { mutableStateOf(planned) }
    var updatedAt by remember(planned) { mutableStateOf<ZonedDateTime?>(null) }
    var sharing by remember { mutableStateOf(false) }
    // notifications: live trip + alerts, and reminders for saved trips
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun ensureNotify() {
        if (!app.navi.canNotify(ctx) && android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val transit = it.legs.filterNot { l -> l.isWalk }
    val fromName = it.legs.first().from.name
    val toName = it.legs.last().to.name

    // Live: refresh from the vehicles' current runs while the trip is relevant (3 h before until 30 min after).
    LaunchedEffect(planned) {
        while (true) {
            val now = ZonedDateTime.now()
            val start = parseTime(it.start) ?: now
            val end = parseTime(it.end) ?: now
            if (transit.isNotEmpty() && now.isAfter(start.minusHours(3)) && now.isBefore(end.plusMinutes(30))) {
                runCatching { Api.live(it) }.onSuccess { fresh ->
                    if (fresh.legs.size == it.legs.size) { it = fresh; updatedAt = ZonedDateTime.now(); Store.updateSaved(savedKey, fresh) }
                }
            }
            kotlinx.coroutines.delay(30_000)
        }
    }

    val lines = remember(it) { it.legs.map(::legLine) }
    val points = remember(it) {
        transit.flatMap { l ->
            listOf(MapPoint(l.from.stopId ?: l.from.name, l.from.lat, l.from.lon, legColor(l), l.from.name), MapPoint(l.to.stopId ?: l.to.name, l.to.lat, l.to.lon, legColor(l), l.to.name))
        }
    }
    val camera = remember(planned) { MapCamera(lines.flatMap { l -> l.points }) }
    val shareTitle = stringResource(R.string.share_trip)
    val summary = shareText(it)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    Column {
                        Text("${hhmm(it.start)} – ${hhmm(it.end)}", style = MaterialTheme.typography.titleMedium)
                        Text("${dur(it.duration)}, ${transfersText(it.transfers)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    val saved = Store.isSavedKey(savedKey)
                    IconButton(onClick = { if (!saved) ensureNotify(); Store.toggleSavedKey(savedKey, it, fromName, toName) }) {
                        Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, stringResource(if (saved) R.string.unsave_trip else R.string.save_trip))
                    }
                    IconButton(enabled = !sharing, onClick = {
                        sharing = true
                        scope.launch {
                            // a live link when the server is reachable, plain text otherwise
                            val link = runCatching { Api.share(it).url }.getOrNull()
                            sharing = false
                            val text = if (link != null) "$summary\n\n$link" else summary
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), shareTitle))
                        }
                    }) { Icon(Icons.Filled.Share, shareTitle) }
                    IconButton(onClick = {
                        // the device's calendar app fills in an event; nothing is written without the user saving it
                        val add = Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, parseTime(it.start)?.toInstant()?.toEpochMilli())
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, parseTime(it.end)?.toInstant()?.toEpochMilli())
                            .putExtra(android.provider.CalendarContract.Events.TITLE, "$fromName – $toName")
                            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, fromName)
                            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, summary)
                        runCatching { ctx.startActivity(add) }
                            .onFailure { android.widget.Toast.makeText(ctx, R.string.no_calendar_app, android.widget.Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Filled.EventAvailable, stringResource(R.string.add_to_calendar)) }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item { LiveStatusCard(it, updatedAt) }
            if (transit.isNotEmpty() && (parseTime(it.end) ?: ZonedDateTime.now()).isAfter(ZonedDateTime.now())) item {
                val following = app.navi.TripService.tracked?.let { t -> t.legs.size == it.legs.size && t.legs.firstOrNull { l -> l.tripId != null }?.tripId == it.legs.firstOrNull { l -> l.tripId != null }?.tripId } == true
                androidx.compose.material3.FilledTonalButton(
                    onClick = { if (following) app.navi.TripService.stop(ctx) else { ensureNotify(); app.navi.TripService.start(ctx, it) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(if (following) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (following) R.string.live_stop else R.string.live_start))
                }
            }
            item { Failover(it, nav) }
            item {
                NaviMap(
                    Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                    lines = lines, points = points, camera = camera, showMyLocation = true,
                )
                Spacer(Modifier.height(8.dp))
            }
            it.legs.forEachIndexed { i, l ->
                item(key = "leg$i") {
                    if (l.isWalk) WalkLeg(l, isTransfer = i > 0 && i < it.legs.lastIndex) {
                        WalkContext.set(l, it.legs.getOrNull(i + 1)?.takeIf { n -> !n.isWalk }); nav.walk()
                    }
                    else TransitLeg(l, nav)
                }
            }
        }
    }
}

/** Where the trip breaks, if it does: a cancelled ride, or a change that live times no longer allow. */
private data class Break(val keep: Int, val from: Location, val time: ZonedDateTime, val message: String)

@Composable
private fun findBreak(it: Itinerary): Break? {
    val now = ZonedDateTime.now()
    fun place(p: Place) = Location(p.stopId ?: "${p.lat},${p.lon}", if (p.stopId != null) "STOP" else "GPS", p.name, lat = p.lat, lon = p.lon)
    val idx = it.legs.indices.filter { i -> !it.legs[i].isWalk }
    for ((n, i) in idx.withIndex()) {
        val l = it.legs[i]
        val dep = parseTime(l.from.departure ?: l.from.scheduledDeparture) ?: continue
        if (dep.isBefore(now.minusMinutes(1)) && !l.cancelled) continue
        if (l.cancelled || l.from.cancelled || l.to.cancelled) {
            return Break(i, place(l.from), if (dep.isBefore(now)) now else dep, stringResource(R.string.risk_cancelled, l.line.orEmpty()))
        }
        val prev = idx.getOrNull(n - 1)?.let { p -> it.legs[p] } ?: continue
        val arr = parseTime(prev.to.arrival ?: prev.to.scheduledArrival) ?: continue
        val walk = it.legs.subList(idx[n - 1] + 1, i).sumOf { w -> w.duration } / 60
        val buffer = Duration.between(arr, dep).toMinutes() - walk
        if (buffer < 0) return Break(idx[n - 1] + 1, place(prev.to), arr, stringResource(R.string.risk_missed, prev.to.name, -buffer))
        if (buffer < 2 && (prev.realtime || l.realtime)) return Break(idx[n - 1] + 1, place(prev.to), arr, stringResource(R.string.risk_tight, prev.to.name, buffer))
        // no live data yet: warn when this line is usually later than the change allows
        val usual = prev.arrDelay?.likely ?: 0
        if (!prev.realtime && usual > buffer && usual >= 3) {
            return Break(idx[n - 1] + 1, place(prev.to), arr.plusMinutes(usual.toLong()), stringResource(R.string.risk_often_late, prev.line.orEmpty(), usual, prev.to.name, buffer.toInt()))
        }
    }
    return null
}

/** Automatic plan B: when the trip breaks, search from that point on and offer the result, merged with what's already behind you. */
@Composable
private fun Failover(it: Itinerary, nav: Nav) {
    val br = findBreak(it) ?: return
    val c = MaterialTheme.colorScheme
    val last = it.legs.last().to
    val dest = last.stopId ?: "${last.lat},${last.lon}"
    var alts by remember(br.from.id, br.keep) { mutableStateOf<List<Itinerary>?>(null) }
    var failed by remember(br.from.id, br.keep) { mutableStateOf(false) }
    LaunchedEffect(br.from.id, br.keep) {
        runCatching { Api.plan(br.from.id, dest, br.time.toOffsetDateTime().toString(), false, JourneyForm.wheelchair, JourneyForm.bike, modes = JourneyForm.modes,
            minTransfer = JourneyForm.minTransfer, walkSpeed = JourneyForm.walkSpeed) }
            .onSuccess { p -> alts = p.itineraries.filter { a -> a.legs.any { l -> !l.isWalk } }.take(3) }
            .onFailure { failed = true }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(c.errorContainer.copy(alpha = .35f)).padding(vertical = 16.dp),
    ) {
        Text(br.message, Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        Text(stringResource(R.string.alternatives_from, br.from.name), Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        when {
            failed -> Text(stringResource(R.string.alternatives_none), Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium)
            alts == null -> Text(stringResource(R.string.alternatives_searching), Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            alts!!.isEmpty() -> Text(stringResource(R.string.alternatives_none), Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium)
            else -> alts!!.forEach { a ->
                val merged = Itinerary(
                    start = it.start, end = a.end,
                    duration = (Duration.between(parseTime(it.start) ?: ZonedDateTime.now(), parseTime(a.end) ?: ZonedDateTime.now()).seconds).toInt(),
                    transfers = (it.legs.take(br.keep) + a.legs).count { l -> !l.isWalk } - 1,
                    legs = it.legs.take(br.keep) + a.legs,
                )
                ItineraryCard(a) { JourneyForm.selected = merged; nav.itinerary() }
            }
        }
    }
}

/** "What do I do now?" — one big, plain-language line that follows the trip live. */
@Composable
fun LiveStatusCard(it: Itinerary, updatedAt: ZonedDateTime?) {
    val transit = it.legs.filterNot { l -> l.isWalk }
    if (transit.isEmpty()) return
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); now = ZonedDateTime.now() } }
    fun dep(l: Leg) = parseTime(l.from.departure ?: l.from.scheduledDeparture) ?: now
    fun arr(l: Leg) = parseTime(l.to.arrival ?: l.to.scheduledArrival) ?: now
    fun minsUntil(t: ZonedDateTime) = Duration.between(now, t).toMinutes().coerceAtLeast(0)
    val cancelled = it.legs.any { l -> l.cancelled || l.from.cancelled || l.to.cancelled }

    val (title, detail) = when {
        cancelled -> stringResource(R.string.part_cancelled) to stringResource(R.string.part_cancelled_hint)
        now.isBefore(dep(transit[0])) -> {
            val f = transit[0]
            val m = minsUntil(dep(f))
            (if (m <= 120) stringResource(R.string.status_departs_in, m) else stringResource(R.string.status_departs_at, hhmm(dep(f)))) to
                (f.from.platform?.let { p -> stringResource(R.string.status_board_platform, f.line.orEmpty(), f.from.name, p) }
                    ?: stringResource(R.string.status_board, f.line.orEmpty(), f.from.name))
        }
        else -> {
            var t: Pair<String, String>? = null
            for ((i, l) in transit.withIndex()) {
                val next = transit.getOrNull(i + 1)
                if (!now.isBefore(dep(l)) && now.isBefore(arr(l))) {
                    t = stringResource(R.string.status_riding, l.line.orEmpty(), l.headsign ?: l.to.name) to
                        stringResource(R.string.status_get_off, l.to.name, hhmm(arr(l)), minsUntil(arr(l)))
                    break
                }
                if (next != null && !now.isBefore(arr(l)) && now.isBefore(dep(next))) {
                    t = stringResource(R.string.status_change, next.from.name, minsUntil(dep(next))) to
                        listOfNotNull(
                            l.to.platform?.let { p -> stringResource(R.string.platform_n, p) },
                            next.from.platform?.let { p -> stringResource(R.string.platform_n, p) },
                        ).joinToString(" → ").ifEmpty { next.line.orEmpty() }
                    break
                }
            }
            t ?: (stringResource(R.string.status_arrived) to toNameOf(it))
        }
    }
    // Looks like the passenger display above a train door, in light mode too.
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp)).background(Display.glass).padding(20.dp),
    ) {
        Text(
            title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            color = if (cancelled) Display.alarm else Display.amber,
        )
        if (detail.isNotBlank()) Text(detail, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = Display.text)
        RouteStrip(it.legs, now, Modifier.padding(top = 18.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hhmm(it.legs.first().from.departure ?: it.start), style = MaterialTheme.typography.labelMedium, color = Display.dim)
            Spacer(Modifier.weight(1f))
            if (updatedAt != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Display.live))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.live_updated, hhmm(updatedAt)), style = MaterialTheme.typography.labelMedium, color = Display.dim)
                Spacer(Modifier.weight(1f))
            }
            Text(hhmm(it.legs.last().to.arrival ?: it.end), style = MaterialTheme.typography.labelMedium, color = Display.dim)
        }
    }
}

/** Fixed colours of the passenger display; it stays dark glass whatever the app theme is. */
object Display {
    val glass = Color(0xFF161A1F)
    val amber = Color(0xFFD6C3A5)
    val next = Color(0xFFAFBCCF)
    val text = Color(0xFFE2E5EF)
    val dim = Color(0xFFA7ABB4)
    val track = Color(0xFF444850)
    val live = Color(0xFF9DC7A8)
    val alarm = Color(0xFFE8A19B)
}

/** The trip as one line, walks included: each ride in its line colour, the part already behind you in amber, the next stop as a blue ring. */
@Composable
private fun RouteStrip(transit: List<Leg>, now: ZonedDateTime, modifier: Modifier = Modifier) {
    if (transit.isEmpty()) return
    // walks draw as plain track, only rides get a colour
    val times = transit.flatMap { l ->
        listOf(parseTime(l.from.departure ?: l.from.scheduledDeparture), parseTime(l.to.arrival ?: l.to.scheduledArrival))
    }.map { it?.toEpochSecond() ?: now.toEpochSecond() }
    val start = times.first(); val span = (times.last() - start).coerceAtLeast(1)
    fun x(t: Long, w: Float) = ((t - start).toFloat() / span).coerceIn(0f, 1f) * w
    val nowS = now.toEpochSecond()
    val colors = transit.map { if (it.isWalk) Display.track else legColor(it) }
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val y = size.height / 2; val r = 7.dp.toPx(); val w = size.width - 2 * r
        val stroke = 5.dp.toPx()
        translate(left = r) {
            drawLine(Display.track, Offset(0f, y), Offset(w, y), stroke, StrokeCap.Round)
            transit.indices.forEach { i ->
                drawLine(colors[i].copy(alpha = .55f), Offset(x(times[2 * i], w), y), Offset(x(times[2 * i + 1], w), y), stroke, StrokeCap.Round)
            }
            drawLine(Display.amber, Offset(0f, y), Offset(x(nowS, w), y), stroke, StrokeCap.Round)
            val nextIdx = times.indexOfFirst { it > nowS }
            times.forEachIndexed { i, t ->
                val c = Offset(x(t, w), y)
                when {
                    t <= nowS -> drawCircle(Display.amber, r, c)
                    i == nextIdx -> { drawCircle(Display.glass, r, c); drawCircle(Display.next, r - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx())) }
                    else -> { drawCircle(Display.glass, r, c); drawCircle(Display.track, r - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx())) }
                }
            }
        }
    }
}

private fun toNameOf(it: Itinerary) = it.legs.last().to.name

private fun shareText(it: Itinerary) = buildString {
    it.legs.filterNot { l -> l.isWalk }.forEach { l ->
        appendLine("${hhmm(l.from.scheduledDeparture)} ${l.from.name}${l.from.platform?.let { p -> " (${L.s(R.string.platform_n, p)})" } ?: ""}")
        appendLine("   ${l.line.orEmpty()} → ${l.headsign.orEmpty()}")
        appendLine("${hhmm(l.to.scheduledArrival)} ${l.to.name}${l.to.platform?.let { p -> " (${L.s(R.string.platform_n, p)})" } ?: ""}")
        appendLine()
    }
    append(L.s(R.string.planned_with_navi))
}

@Composable
private fun WalkLeg(l: Leg, isTransfer: Boolean, onMap: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val steps = l.steps.filter { it.distance > 0 || it.kind == "ELEVATOR" }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(20.dp)).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = steps.size > 1) { open = !open }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) { Icon(modeIcon(l.mode), null, tint = c.onSurfaceVariant) }
            val what = stringResource(when (l.mode) { "BIKE" -> R.string.bike; "CAR" -> R.string.car; else -> if (isTransfer) R.string.transfer_walk else R.string.mode_walk })
            Text(
                listOfNotNull("$what ${dur(l.duration)}", formatDistance(l.distance)).joinToString(", "),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            IconButton(onClick = onMap) { Icon(Icons.Filled.Map, stringResource(R.string.walk_map), tint = c.primary) }
            if (steps.size > 1) {
                Text(stringResource(if (open) R.string.hide_directions else R.string.show_directions), style = MaterialTheme.typography.labelLarge, color = c.primary)
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.padding(start = 2.dp).size(20.dp), tint = c.primary)
            }
        }
        if (open) Column(Modifier.padding(start = 14.dp, bottom = 8.dp)) {
            steps.forEach { st ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(stepIcon(st.kind), null, Modifier.size(18.dp), tint = if (st.kind in setOf("STAIRS", "ELEVATOR", "ESCALATOR")) c.tertiary else c.onSurface)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(stepText(st), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    formatDistance(st.distance.toDouble())?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                }
            }
        }
    }
}

internal fun stepIcon(kind: String) = when (kind) {
    "LEFT" -> Icons.Filled.TurnLeft
    "RIGHT" -> Icons.Filled.TurnRight
    "SLIGHT_LEFT" -> Icons.Filled.TurnSlightLeft
    "SLIGHT_RIGHT" -> Icons.Filled.TurnSlightRight
    "STAIRS" -> Icons.Filled.Stairs
    "ELEVATOR" -> Icons.Filled.Elevator
    "ESCALATOR" -> Icons.Filled.Escalator
    else -> Icons.Filled.Straight
}

@Composable
internal fun stepText(st: Step): String {
    val street = st.street?.takeIf { it.isNotBlank() }
    return when (st.kind) {
        "START" -> if (street != null) stringResource(R.string.step_start_on, street) else stringResource(R.string.step_start)
        "LEFT", "SLIGHT_LEFT" -> if (street != null) stringResource(R.string.step_left_on, street) else stringResource(R.string.step_left)
        "RIGHT", "SLIGHT_RIGHT" -> if (street != null) stringResource(R.string.step_right_on, street) else stringResource(R.string.step_right)
        "STAIRS" -> stringResource(R.string.step_stairs)
        "ELEVATOR" -> stringResource(R.string.step_elevator)
        "ESCALATOR" -> stringResource(R.string.step_escalator)
        else -> if (street != null) stringResource(R.string.step_straight_on, street) else stringResource(R.string.step_straight)
    }
}

@Composable
private fun TransitLeg(l: Leg, nav: Nav) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val c = legColor(l)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 12.dp)) {
            Box(Modifier.padding(start = 16.dp).width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c))
            Column(Modifier.padding(horizontal = 12.dp)) {
                StopLine(l.from, departure = true, realtime = l.realtime, nav = nav)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = l.tripId != null) { l.tripId?.let { TripContext.put(it, l.notes); nav.trip(it, l.from.stopId, l.to.stopId) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineChip(l.line, l.mode, l.color, l.textColor)
                    Spacer(Modifier.width(8.dp))
                    Text("→ ${l.headsign.orEmpty()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                l.operator?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                stayText(l)?.let { t ->
                    Text(
                        t, Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (!l.realtime) UsualDelayLine(l.depDelay, Modifier.padding(top = 4.dp))
                if (l.cancelled) WarningLine(stringResource(R.string.trip_cancelled), error = true)
                ReasonList(l.notes, Modifier.padding(top = 4.dp))
                l.alerts.forEach { a ->
                    Text(
                        listOfNotNull(a.header, a.text).distinct().joinToString(": "),
                        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = l.stops.isNotEmpty() || l.tripId != null) {
                            if (l.stops.isNotEmpty()) expanded = !expanded else l.tripId?.let { TripContext.put(it, l.notes); nav.trip(it, l.from.stopId, l.to.stopId) }
                        }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(
                            dur(l.duration),
                            l.stops.size.takeIf { it > 0 }?.let { n -> androidx.compose.ui.res.pluralStringResource(R.plurals.stops, n, n) },
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (l.stops.isNotEmpty()) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(if (expanded) R.string.hide_stops else R.string.show_stops), Modifier.size(18.dp))
                }
                if (expanded) l.stops.forEach { s ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(hhmm(s.scheduledDeparture ?: s.scheduledArrival), Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                        Text(s.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (l.tripId != null) androidx.compose.material3.TextButton(
                    onClick = { TripContext.put(l.tripId, l.notes); nav.trip(l.tripId, l.from.stopId, l.to.stopId) },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.whole_ride))
                }
                Spacer(Modifier.height(6.dp))
                StopLine(l.to, departure = false, realtime = l.realtime, nav = nav)
            }
        }
    }
}

/** Navi fixed "ride to the end, wait, same line again": say so, so nobody gets off. */
@Composable
private fun stayText(l: Leg): String? = when (l.stayOnBoard) {
    "passes" -> stringResource(R.string.stay_passes, l.to.name)
    "same-vehicle" -> stringResource(R.string.stay_same_vehicle, l.to.name, l.line.orEmpty())
    "same-line" -> stringResource(R.string.stay_same_line, l.to.name, l.line.orEmpty(), hhmm(l.from.scheduledDeparture))
    else -> null
}

@Composable
private fun StopLine(p: Place, departure: Boolean, realtime: Boolean, nav: Nav) {
    val sched = if (departure) p.scheduledDeparture else p.scheduledArrival
    val actual = if (departure) p.departure else p.arrival
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeColumn(sched, actual, delayOf(actual, sched, realtime), p.cancelled, big = false)
        Spacer(Modifier.width(8.dp))
        Text(
            p.name,
            Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(enabled = p.stopId != null) {
                nav.board(Location(p.stopId!!, "STOP", p.name, lat = p.lat, lon = p.lon))
            },
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        )
        PlatformBadge(p.platform, p.scheduledPlatform)
    }
}
