package app.navi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Switch
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.navi.JourneyForm
import app.navi.R
import app.navi.Store
import app.navi.hhmm
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Type filter groups, in the order shown, with the key the server takes. */
val modeFilters = listOf(
    ModeGroup.LONG to "long", ModeGroup.REGIONAL to "regional", ModeGroup.SUBURBAN to "suburban",
    ModeGroup.SUBWAY to "subway", ModeGroup.TRAM to "tram", ModeGroup.BUS to "bus", ModeGroup.FERRY to "ferry",
)

/**
 * The search options, kept quiet: the time, then only the options that are switched on (tap one to switch it
 * off), then one "Options" chip with everything else. Same row on the search form and above the results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripOptionChips(modifier: Modifier = Modifier) {
    var timeDialog by remember { mutableStateOf(false) }
    var optionsDialog by remember { mutableStateOf(false) }
    FlowRow(modifier, horizontalArrangement = ItemSpacing) {
        FilterChip(
            selected = JourneyForm.time != null,
            onClick = { timeDialog = true },
            label = { Text(timeLabel()) },
            leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) },
        )
        val dTicket = JourneyForm.modes == JourneyForm.dTicketModes
        if (dTicket) ActiveChip(stringResource(R.string.d_ticket), Icons.Filled.ConfirmationNumber) { JourneyForm.modes = emptySet() }
        else if (JourneyForm.modes.isNotEmpty()) ActiveChip(modesLabel(), Icons.Filled.Tune) { JourneyForm.modes = emptySet() }
        if (JourneyForm.wheelchair) ActiveChip(stringResource(R.string.step_free), Icons.AutoMirrored.Filled.Accessible) { JourneyForm.wheelchair = false }
        if (JourneyForm.bike) ActiveChip(stringResource(R.string.with_bike), Icons.AutoMirrored.Filled.DirectionsBike) { JourneyForm.bike = false }
        JourneyForm.via?.let { v -> ActiveChip(stringResource(R.string.via, v.name), Icons.Filled.Place) { JourneyForm.via = null } }
        if (JourneyForm.minTransfer > 0 || JourneyForm.walkSpeed != "normal") {
            ActiveChip(stringResource(R.string.options_custom_pace), Icons.Filled.AccessTime) { JourneyForm.minTransfer = 0; JourneyForm.walkSpeed = "normal" }
        }
        AssistChip(
            onClick = { optionsDialog = true },
            label = { Text(stringResource(R.string.options)) },
            leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) },
        )
    }
    if (timeDialog) TimeDialog { timeDialog = false }
    if (optionsDialog) OptionsDialog { optionsDialog = false }
}

/** A switched-on option; tapping it switches it off. */
@Composable
private fun ActiveChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onOff: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onOff,
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(18.dp)) },
        modifier = Modifier.widthIn(max = 220.dp),
    )
}

private val transferChoices = listOf(0, 5, 10, 15)
private val walkChoices = listOf("slow", "normal", "fast")

/** Every search option in one place. Changes apply straight away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsDialog(onClose: () -> Unit) {
    var pickVia by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = {
                JourneyForm.via = null; JourneyForm.minTransfer = 0; JourneyForm.walkSpeed = "normal"
                JourneyForm.modes = emptySet(); JourneyForm.wheelchair = Store.wheelchairDefault; JourneyForm.bike = false
            }) {
                Text(stringResource(R.string.reset))
            }
        },
        title = { Text(stringResource(R.string.options)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.types_title), style = MaterialTheme.typography.titleSmall)
                // nothing ticked means all types; the last type can't be unticked
                val all = modeFilters.map { it.second }.toSet()
                val sel = JourneyForm.modes.ifEmpty { all }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modeFilters.forEach { (g, key) ->
                        FilterChip(
                            selected = key in sel,
                            onClick = {
                                val next = if (key in sel) sel - key else sel + key
                                if (next.isNotEmpty()) JourneyForm.modes = if (next == all) emptySet() else next
                            },
                            label = { Text(stringResource(g.label)) },
                            leadingIcon = { Icon(g.icon, null, Modifier.size(18.dp)) },
                        )
                    }
                    val dTicket = JourneyForm.modes == JourneyForm.dTicketModes
                    FilterChip(
                        selected = dTicket,
                        onClick = { JourneyForm.modes = if (dTicket) emptySet() else JourneyForm.dTicketModes },
                        label = { Text(stringResource(R.string.d_ticket_only)) },
                        leadingIcon = { Icon(Icons.Filled.ConfirmationNumber, null, Modifier.size(18.dp)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OptionSwitch(stringResource(R.string.step_free), Icons.AutoMirrored.Filled.Accessible, JourneyForm.wheelchair) { JourneyForm.wheelchair = it }
                OptionSwitch(stringResource(R.string.with_bike), Icons.AutoMirrored.Filled.DirectionsBike, JourneyForm.bike) { JourneyForm.bike = it }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.via_stop), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = JourneyForm.via != null,
                        onClick = { pickVia = true },
                        label = { Text(JourneyForm.via?.name ?: stringResource(R.string.via_none), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Place, null, Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (JourneyForm.via != null) IconButton(onClick = { JourneyForm.via = null }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.remove_via))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.change_time), style = MaterialTheme.typography.titleSmall)
                ConnectedChoice(
                    transferChoices.map { if (it == 0) stringResource(R.string.change_time_normal) else "+$it" },
                    transferChoices.indexOf(JourneyForm.minTransfer).coerceAtLeast(0),
                    { JourneyForm.minTransfer = transferChoices[it] },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.walk_pace), style = MaterialTheme.typography.titleSmall)
                ConnectedChoice(
                    listOf(stringResource(R.string.walk_slow), stringResource(R.string.walk_normal), stringResource(R.string.walk_fast)),
                    walkChoices.indexOf(JourneyForm.walkSpeed).coerceAtLeast(1),
                    { JourneyForm.walkSpeed = walkChoices[it] },
                )
                if (JourneyForm.wheelchair) Text(
                    stringResource(R.string.walk_pace_step_free),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    if (pickVia) PlacePicker(
        stringResource(R.string.via_stop), { pickVia = false }, { JourneyForm.via = it; pickVia = false },
        stopsOnly = true, allowMyLocation = false,
    )
}

@Composable
private fun OptionSwitch(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = set).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
fun timeLabel(): String {
    val t = JourneyForm.time ?: return stringResource(R.string.leave_now)
    val days = Duration.between(ZonedDateTime.now().toLocalDate().atStartOfDay(), t.toLocalDate().atStartOfDay()).toDays()
    val day = when (days) {
        0L -> stringResource(R.string.today_lc); 1L -> stringResource(R.string.tomorrow_lc); 2L -> stringResource(R.string.day_after_lc)
        else -> t.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d.M."))
    }
    return stringResource(if (JourneyForm.arriveBy) R.string.arrive_at else R.string.depart_at, day, hhmm(t))
}

@Composable
private fun modesLabel(): String {
    val m = JourneyForm.modes
    if (m.isEmpty()) return stringResource(R.string.all_types)
    val names = modeFilters.filter { it.second in m }.map { stringResource(it.first.label) }
    return if (names.size <= 2) names.joinToString(", ") else stringResource(R.string.n_types, names.size)
}

/** Departure or arrival, any day, any time. Typing is the default; the clock face is one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(onClose: () -> Unit) {
    val now = ZonedDateTime.now()
    val initial = JourneyForm.time ?: now
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var arrive by remember { mutableStateOf(JourneyForm.arriveBy) }
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var dial by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val today = now.toLocalDate()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.when_q), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                ConnectedChoice(listOf(stringResource(R.string.departure), stringResource(R.string.arrival)), if (arrive) 1 else 0, { arrive = it == 1 })
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(date == today, { date = today }, { Text(stringResource(R.string.today)) })
                    FilterChip(date == today.plusDays(1), { date = today.plusDays(1) }, { Text(stringResource(R.string.tomorrow)) })
                    val other = date != today && date != today.plusDays(1)
                    FilterChip(
                        other, { pickDate = true },
                        { Text(if (other) date.format(java.time.format.DateTimeFormatter.ofPattern("EE d.M.")) else stringResource(R.string.pick_date)) },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (dial) TimePicker(state = state) else TimeInput(state = state)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { dial = !dial }) {
                        Icon(if (dial) Icons.Filled.Keyboard else Icons.Filled.AccessTime, stringResource(if (dial) R.string.use_keyboard else R.string.use_dial))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { JourneyForm.time = null; JourneyForm.arriveBy = false; onClose() }) { Text(stringResource(R.string.now_button)) }
                    TextButton(onClick = {
                        JourneyForm.arriveBy = arrive
                        JourneyForm.time = date.atTime(state.hour, state.minute).atZone(now.zone)
                        onClose()
                    }) { Text(stringResource(R.string.apply)) }
                }
            }
        }
    }

    if (pickDate) {
        val startMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val ds = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= startMillis
                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.apply)) }
            },
        ) { DatePicker(ds) }
    }
}
