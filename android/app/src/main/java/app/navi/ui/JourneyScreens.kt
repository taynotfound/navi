package app.navi.ui

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
    var timeDialog by remember { mutableStateOf(false) }

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

        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = ItemSpacing) {
            FilterChip(
                selected = JourneyForm.time != null,
                onClick = { timeDialog = true },
                label = { Text(timeLabel()) },
                leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = JourneyForm.wheelchair,
                onClick = { JourneyForm.wheelchair = !JourneyForm.wheelchair },
                label = { Text(stringResource(R.string.step_free)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Accessible, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = JourneyForm.bike,
                onClick = { JourneyForm.bike = !JourneyForm.bike },
                label = { Text(stringResource(R.string.with_bike)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, Modifier.size(18.dp)) },
            )
        }

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

        // "Frag Navi": ask in your own words instead of filling the form
        androidx.compose.material3.OutlinedButton(
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
    if (timeDialog) TimeDialog { timeDialog = false }
}

@Composable
private fun timeLabel(): String {
    val t = JourneyForm.time ?: return stringResource(R.string.leave_now)
    val days = Duration.between(ZonedDateTime.now().toLocalDate().atStartOfDay(), t.toLocalDate().atStartOfDay()).toDays()
    val day = when (days) {
        0L -> stringResource(R.string.today_lc); 1L -> stringResource(R.string.tomorrow_lc); 2L -> stringResource(R.string.day_after_lc)
        else -> t.toLocalDate().toString()
    }
    return stringResource(if (JourneyForm.arriveBy) R.string.arrive_at else R.string.depart_at, day, hhmm(t))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(onClose: () -> Unit) {
    val now = ZonedDateTime.now()
    val initial = JourneyForm.time ?: now
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var arrive by remember { mutableStateOf(JourneyForm.arriveBy) }
    var dayOffset by remember {
        mutableIntStateOf(Duration.between(now.toLocalDate().atStartOfDay(), initial.toLocalDate().atStartOfDay()).toDays().toInt().coerceIn(0, 2))
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                JourneyForm.arriveBy = arrive
                JourneyForm.time = now.toLocalDate().plusDays(dayOffset.toLong()).atTime(state.hour, state.minute).atZone(now.zone)
                onClose()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = {
            TextButton(onClick = { JourneyForm.time = null; JourneyForm.arriveBy = false; onClose() }) { Text(stringResource(R.string.now_button)) }
        },
        title = { Text(stringResource(R.string.when_q)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState())) {
                ConnectedChoice(listOf(stringResource(R.string.departure), stringResource(R.string.arrival)), if (arrive) 1 else 0, { arrive = it == 1 })
                Spacer(Modifier.height(12.dp))
                ConnectedChoice(listOf(stringResource(R.string.today), stringResource(R.string.tomorrow), stringResource(R.string.day_after)), dayOffset, { dayOffset = it })
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
            }
        },
    )
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
                    JourneyForm.wheelchair, JourneyForm.bike, cursor,
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
    LaunchedEffect(from.id, to.id, JourneyForm.time, JourneyForm.arriveBy, JourneyForm.wheelchair, JourneyForm.bike) { load(null, "initial") }

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
            when {
                loading == "initial" -> item { LoadingState(text = stringResource(R.string.searching_connections)) }
                error != null && items.isEmpty() -> item { ErrorState(error!!, onRetry = { load(null, "initial") }) }
                items.isEmpty() && direct == null -> item {
                    MessageState(Icons.Filled.Search, stringResource(R.string.no_connections))
                }
                else -> {
                    if (earlier != null) item {
                        TextButton(onClick = { load(earlier, "earlier") }, enabled = loading == null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (loading == "earlier") R.string.loading_short else R.string.earlier))
                        }
                    }
                    direct?.let { d ->
                        item {
                            Card(
                                onClick = { JourneyForm.selected = d; nav.itinerary() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null)
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
                        OutlinedButton(onClick = { load(later, "later") }, enabled = loading == null, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
    val transit = it.legs.filterNot { l -> l.isWalk }
    val first = transit.firstOrNull()
    val last = transit.lastOrNull()
    val cancelled = it.legs.any { l -> l.cancelled || l.from.cancelled || l.to.cancelled }
    val depDelay = first?.let { l -> delayOf(l.from.departure, l.from.scheduledDeparture, l.realtime) }
    val arrDelay = last?.let { l -> delayOf(l.to.arrival, l.to.scheduledArrival, l.realtime) }
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hhmm(it.start), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("  –  ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hhmm(it.end), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    }
                    if ((depDelay ?: 0) > 0 || (arrDelay ?: 0) > 0) {
                        val dep = stringResource(R.string.departure)
                        val arr = stringResource(R.string.arrival)
                        Text(
                            listOfNotNull(depDelay?.takeIf { d -> d > 0 }?.let { d -> "$dep +$d" }, arrDelay?.takeIf { d -> d > 0 }?.let { d -> "$arr +$d" }).joinToString(", "),
                            color = delayColor(maxOf(depDelay ?: 0, arrDelay ?: 0)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else if (first?.realtime == true) {
                        Text(stringResource(R.string.on_time), color = StatusColors.onTime, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dur(it.duration), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(transfersText(it.transfers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                it.legs.forEach { l ->
                    if (l.isWalk) {
                        if (l.duration >= 300) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(modeIcon(l.mode), stringResource(R.string.mode_walk), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(l.duration + 30) / 60}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else LineChip(l.line, l.mode, l.color, l.textColor)
                }
            }
            if (first != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        first.from.platform?.let { p -> stringResource(R.string.from_stop_platform, first.from.name, p) } ?: stringResource(R.string.from_stop, first.from.name),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val m = minutesUntil(it.start)
                    if (m != null && m in 0..90) Text(stringResource(R.string.in_min, m), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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

fun legColor(l: Leg): Color = parseHex(l.color)?.takeIf { it != Color.White } ?: ModeGroup.of(l.mode).color

fun legLine(l: Leg) = MapLine(
    l.geometry?.let { decodePolyline(it.points, it.precision) } ?: listOf(l.from.lat to l.from.lon, l.to.lat to l.to.lon),
    legColor(l),
    dashed = l.isWalk,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(nav: Nav) {
    val planned = JourneyForm.selected ?: run { LaunchedEffect(Unit) { nav.back() }; return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var it by remember(planned) { mutableStateOf(planned) }
    var updatedAt by remember(planned) { mutableStateOf<ZonedDateTime?>(null) }
    var sharing by remember { mutableStateOf(false) }
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
                runCatching { Api.live(it) }.onSuccess { fresh -> it = fresh; updatedAt = ZonedDateTime.now(); Store.updateSaved(fresh) }
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
                    val saved = Store.isSaved(it)
                    IconButton(onClick = { Store.toggleSaved(it, fromName, toName) }) {
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
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item { LiveStatusCard(it, updatedAt) }
            item {
                NaviMap(
                    Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                    lines = lines, points = points, camera = camera, showMyLocation = true,
                )
                Spacer(Modifier.height(8.dp))
            }
            it.legs.forEachIndexed { i, l ->
                item(key = i) {
                    if (l.isWalk) WalkLeg(l, isTransfer = i > 0 && i < it.legs.lastIndex)
                    else TransitLeg(l, nav)
                }
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
    val amber = Color(0xFFEBBF89)
    val next = Color(0xFFB0C8EC)
    val text = Color(0xFFE2E5EF)
    val dim = Color(0xFFA7ABB4)
    val track = Color(0xFF444850)
    val live = Color(0xFF8BD8A0)
    val alarm = Color(0xFFFF9993)
}

/** The trip as one line, walks included: each ride in its line colour, the part already behind you in amber, the next stop as a blue ring. */
@Composable
private fun RouteStrip(transit: List<Leg>, now: ZonedDateTime, modifier: Modifier = Modifier) {
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
private fun WalkLeg(l: Leg, isTransfer: Boolean) {
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

private fun stepIcon(kind: String) = when (kind) {
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
private fun stepText(st: Step): String {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineChip(l.line, l.mode, l.color, l.textColor)
                    Spacer(Modifier.width(8.dp))
                    Text("→ ${l.headsign.orEmpty()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                l.operator?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (l.cancelled) WarningLine(stringResource(R.string.trip_cancelled), error = true)
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
                            if (l.stops.isNotEmpty()) expanded = !expanded else l.tripId?.let(nav::trip)
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
                Spacer(Modifier.height(6.dp))
                StopLine(l.to, departure = false, realtime = l.realtime, nav = nav)
            }
        }
    }
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
