package app.navi.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.navi.R
import app.navi.formatDuration
import app.navi.minutesUntil

// ---------- transport modes ----------

enum class ModeGroup(val label: Int, val icon: ImageVector, val color: Color) {
    LONG(R.string.mode_long, Icons.Filled.Train, Color(0xFF8E3B45)),
    REGIONAL(R.string.mode_regional, Icons.Filled.DirectionsRailway, Color(0xFF5A5290)),
    SUBURBAN(R.string.mode_suburban, Icons.Filled.DirectionsTransit, Color(0xFF3F6E47)),
    SUBWAY(R.string.mode_subway, Icons.Filled.DirectionsSubway, Color(0xFF3D5F8C)),
    TRAM(R.string.mode_tram, Icons.Filled.Tram, Color(0xFF9A5A36)),
    BUS(R.string.mode_bus, Icons.Filled.DirectionsBus, Color(0xFF6E4A85)),
    FERRY(R.string.mode_ferry, Icons.Filled.DirectionsBoat, Color(0xFF3D7479)),
    WALK(R.string.mode_walk, Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF6B7280)),
    OTHER(R.string.mode_other, Icons.Filled.DirectionsTransit, Color(0xFF56656D));

    companion object {
        fun of(mode: String): ModeGroup = when (mode) {
            "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> LONG
            "REGIONAL_FAST_RAIL", "REGIONAL_RAIL", "RAIL" -> REGIONAL
            "SUBURBAN" -> SUBURBAN
            "SUBWAY", "METRO" -> SUBWAY
            "TRAM", "CABLE_CAR", "FUNICULAR" -> TRAM
            "BUS", "COACH", "ODM" -> BUS
            "FERRY" -> FERRY
            "WALK", "BIKE", "CAR" -> WALK
            else -> OTHER
        }
    }
}

fun modeIcon(mode: String): ImageVector = when (mode) {
    "BIKE" -> Icons.AutoMirrored.Filled.DirectionsBike
    "CAR" -> Icons.Filled.DirectionsCar
    "AIRPLANE" -> Icons.Filled.Flight
    else -> ModeGroup.of(mode).icon
}

fun parseHex(hex: String?): Color? =
    hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Coloured line badge, e.g. [🚆 ICE 575]. Uses the operator's route colour when known. */
@Composable
fun LineChip(line: String?, mode: String, color: String? = null, textColor: String? = null, modifier: Modifier = Modifier) {
    val bg = (parseHex(color)?.takeIf { it != Color.White } ?: ModeGroup.of(mode).color).calm()
    val fg = Color.White.copy(alpha = .94f) // calm() keeps every chip dark enough for white text
    val modeLabel = stringResource(ModeGroup.of(mode).label)
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = "$modeLabel ${line.orEmpty()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(modeIcon(mode), contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        if (!line.isNullOrBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(line, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun delayColor(delay: Int?): Color = when {
    delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
    delay < 1 -> StatusColors.onTime
    delay < 5 -> StatusColors.late
    else -> StatusColors.veryLate
}

/** Scheduled time, and below it the live time coloured by delay (or "fällt aus"). */
@Composable
fun TimeColumn(scheduled: String?, actual: String?, delay: Int?, cancelled: Boolean, modifier: Modifier = Modifier, big: Boolean = true) {
    Column(modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.Start) {
        Text(
            app.navi.hhmm(scheduled),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
        val later = if (delay != null && delay > 0) stringResource(R.string.a11y_delay, delay, app.navi.hhmm(actual)) else stringResource(R.string.on_time)
        when {
            cancelled -> Text(stringResource(R.string.cancelled), color = StatusColors.veryLate, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            delay != null -> Text(
                if (delay == 0) stringResource(R.string.on_time) else if (delay > 0) "+$delay" else "$delay",
                color = delayColor(delay),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { contentDescription = later },
            )
        }
    }
}

/** "in 4 Min." for near departures — easier than doing clock maths. */
@Composable
fun Countdown(time: String?, cancelled: Boolean) {
    val m = minutesUntil(time) ?: return
    if (cancelled || m > 59 || m < -1) return
    val spoken = if (m <= 0) stringResource(R.string.now) else pluralStringResource(R.plurals.a11y_in_minutes, m.toInt(), m.toInt())
    Text(
        if (m <= 0) stringResource(R.string.now) else stringResource(R.string.in_min, m),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = spoken }, // "in 4 minutes", not "in 4 Min dot"
    )
}

/** Platform badge; highlighted when the platform changed. */
@Composable
fun PlatformBadge(platform: String?, scheduled: String? = null, modifier: Modifier = Modifier) {
    if (platform.isNullOrBlank()) return
    val changed = scheduled != null && scheduled != platform
    val bg = if (changed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (changed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val desc = if (changed) stringResource(R.string.a11y_platform_changed, platform, scheduled!!) else stringResource(R.string.a11y_platform, platform)
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (changed) Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.platform_short), style = MaterialTheme.typography.labelSmall, color = fg)
        Text(platform, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- states ----------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = stringResource(R.string.loading)) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Expressive connected button group: the replacement for segmented buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.CloudOff) {
    MessageState(icon, message, modifier) {
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageState(icon: ImageVector = Icons.Filled.Place, text: String, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun FullSize(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() }

val ItemSpacing = Arrangement.spacedBy(8.dp)

@Composable
fun dur(seconds: Int) = formatDuration(seconds, stringResource(R.string.unit_h), stringResource(R.string.unit_min))

@Composable
fun transfersText(n: Int) = if (n == 0) stringResource(R.string.direct) else pluralStringResource(R.plurals.transfers, n, n)

/** Messages for a ride: short reasons ("Reparatur an einem Signal") in full, long notices folded away. */
@Composable
fun ReasonList(notes: List<String>, modifier: Modifier = Modifier) {
    val (short, long) = notes.distinct().partition { it.length <= 120 }
    if (short.isEmpty() && long.isEmpty()) return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Column(modifier) {
        short.forEach { n ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, null, Modifier.padding(top = 2.dp).size(15.dp), tint = c.tertiary)
                Spacer(Modifier.width(6.dp))
                Text(n, style = MaterialTheme.typography.bodySmall, color = c.onSurface)
            }
        }
        if (long.isNotEmpty()) {
            Text(
                androidx.compose.ui.res.pluralStringResource(if (open) R.plurals.hide_notices else R.plurals.more_notices, long.size, long.size),
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { open = !open }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, color = c.primary,
            )
            if (open) long.forEach { n ->
                Text(n, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Hands a board row's messages to the trip view (they don't come with the trip itself). */
object TripContext {
    private var key: String? = null
    private var notes: List<String> = emptyList()
    private var attributes: List<String> = emptyList()
    fun put(tripId: String, notes: List<String>, attributes: List<String> = emptyList()) {
        key = tripId; this.notes = notes; this.attributes = attributes
    }
    fun take(tripId: String): Pair<List<String>, List<String>> = if (key == tripId) notes to attributes else emptyList<String>() to emptyList()
}
