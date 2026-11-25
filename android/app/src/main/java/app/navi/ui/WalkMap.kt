package app.navi.ui

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
import androidx.compose.material.icons.filled.Directions
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
import kotlinx.coroutines.launch

/** The walk the user tapped, and the ride it leads to (for the platform). */
object WalkContext {
    var walk: Leg? = null; private set
    var next: Leg? = null; private set
    fun set(walk: Leg, next: Leg?) { this.walk = walk; this.next = next }
}

/** A maps-app view of one walk: the path on a full-screen map, where you are, where the platform is, and the turns. */
@Composable
fun WalkMapScreen(nav: Nav) {
    val l = WalkContext.walk ?: run { LaunchedEffect(Unit) { nav.back() }; return }
    val next = WalkContext.next
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val line = remember(l) { legLine(l).copy(color = c.primary) }
    val dest = l.to
    val points = remember(l) {
        listOf(
            MapPoint("from", l.from.lat, l.from.lon, c.secondary, l.from.name),
            MapPoint("to", dest.lat, dest.lon, next?.let(::legColor) ?: c.tertiary, next?.from?.platform?.let { "${dest.name} · $it" } ?: dest.name, radius = 9f),
        )
    }
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
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = {
                            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${dest.lat},${dest.lon}&mode=w"))
                            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${dest.lat},${dest.lon}?q=${dest.lat},${dest.lon}(${Uri.encode(dest.name)})"))
                            runCatching { ctx.startActivity(nav) }.onFailure { runCatching { ctx.startActivity(geo) } }
                        }) {
                            Icon(Icons.Filled.Directions, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_in_maps))
                        }
                    }
                }
            }
        }
    }
}
