package app.navi.ui

// The two things on the journey tab that save the most taps: what's happening right now, and going home.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navi.Itinerary
import app.navi.JourneyForm
import app.navi.Location
import app.navi.Nav
import app.navi.R
import app.navi.Store
import app.navi.TripService
import app.navi.currentPosition
import app.navi.myLocation
import app.navi.parseTime
import app.navi.tripStatus
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** How far ahead a saved trip shows up as "next". */
private const val NEXT_TRIP_HOURS = 12L

/**
 * The trip being followed live, or else the next saved trip in the coming hours, as one dark-glass line:
 * what to do now, where to. Tap to open it. Nothing at all when there's neither.
 */
@Composable
fun NowCard(nav: Nav, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); now = ZonedDateTime.now() } }
    val followed = TripService.tracked
    val (trip, live) = followed?.let { it to true }
        ?: Store.savedTrips.map { t -> t.itinerary }.firstOrNull { t ->
            val start = parseTime(t.start) ?: return@firstOrNull false
            val end = parseTime(t.end) ?: return@firstOrNull false
            end.isAfter(now) && start.isBefore(now.plusHours(NEXT_TRIP_HOURS))
        }?.let { it to false }
        ?: return
    val status = remember(trip, now) { tripStatus(trip, now) }
    val walk = remember(trip, now) { app.navi.currentWalk(trip, now) }
    NowCardContent(
        trip, live, status.title, status.detail, modifier,
        onNavigate = walk?.let { w -> { openWalkNavigation(trip, w, nav) } },
    ) {
        JourneyForm.selected = trip
        nav.itinerary()
    }
}

@Composable
private fun NowCardContent(
    trip: Itinerary, live: Boolean, title: String, detail: String, modifier: Modifier,
    onNavigate: (() -> Unit)?, onClick: () -> Unit,
) {
    val route = "${trip.legs.first().from.name} → ${trip.legs.last().to.name}"
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Display.glass,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Display.live))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(if (live) R.string.now_following else R.string.now_next_trip),
                        style = MaterialTheme.typography.labelMedium, color = Display.dim,
                    )
                }
                Text(
                    title, Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Display.amber,
                )
                if (detail.isNotBlank()) Text(
                    detail, style = MaterialTheme.typography.bodyMedium, color = Display.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    route, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = Display.dim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (onNavigate != null) androidx.compose.material3.Button(
                    onClick = onNavigate,
                    modifier = Modifier.padding(top = 12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Display.amber, contentColor = Display.glass),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.walk_navigate))
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.open_trip), tint = Display.dim)
        }
    }
}

/**
 * Home and Work, one tap each: from where you are (or the start you picked) straight to the connections.
 * Not set yet: the tap asks where it is. Long-press changes it.
 */
@Composable
fun HomeWorkRow(nav: Nav, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var setting by remember { mutableStateOf<String?>(null) } // "home" | "work"
    var locating by remember { mutableStateOf<String?>(null) }

    fun go(target: Location, which: String) {
        locating = which
        scope.launch {
            val from = JourneyForm.from?.takeIf { f -> f.id != target.id }
                ?: currentPosition(ctx)?.let { p -> myLocation(p.latitude, p.longitude) }
            locating = null
            JourneyForm.to = target
            JourneyForm.from = from
            if (from != null) {
                if (from.type != "GPS") Store.rememberJourney(from, target)
                nav.results()
            }
        }
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceTile(
            Icons.Filled.Home, stringResource(R.string.home_place), Store.home?.name, locating == "home", Modifier.weight(1f),
            onClick = { Store.home?.let { go(it, "home") } ?: run { setting = "home" } },
            onLongClick = { setting = "home" },
        )
        PlaceTile(
            Icons.Filled.Work, stringResource(R.string.work_place), Store.work?.name, locating == "work", Modifier.weight(1f),
            onClick = { Store.work?.let { go(it, "work") } ?: run { setting = "work" } },
            onLongClick = { setting = "work" },
        )
    }

    setting?.let { which ->
        PlacePicker(
            title = stringResource(if (which == "home") R.string.set_home else R.string.set_work),
            onDismiss = { setting = null },
            onPick = { l -> if (which == "home") Store.saveHome(l) else Store.saveWork(l); setting = null },
            allowMyLocation = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceTile(
    icon: ImageVector, label: String, place: String?, busy: Boolean, modifier: Modifier,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val changeLabel = stringResource(R.string.change_place, label)
    Surface(
        modifier = modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(
            role = Role.Button,
            onLongClickLabel = changeLabel,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = RoundedCornerShape(20.dp),
        color = c.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        busy -> stringResource(R.string.locating)
                        place == null -> stringResource(R.string.tap_to_set)
                        else -> place
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
