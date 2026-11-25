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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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

/** Time, types, step-free and bike: the same chips on the search form and above the results. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripOptionChips(modifier: Modifier = Modifier) {
    var timeDialog by remember { mutableStateOf(false) }
    var modesDialog by remember { mutableStateOf(false) }
    FlowRow(modifier, horizontalArrangement = ItemSpacing) {
        FilterChip(
            selected = JourneyForm.time != null,
            onClick = { timeDialog = true },
            label = { Text(timeLabel()) },
            leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = JourneyForm.modes.isNotEmpty(),
            onClick = { modesDialog = true },
            label = { Text(modesLabel()) },
            leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) },
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
    if (timeDialog) TimeDialog { timeDialog = false }
    if (modesDialog) ModesDialog { modesDialog = false }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModesDialog(onClose: () -> Unit) {
    var sel by remember { mutableStateOf(JourneyForm.modes.ifEmpty { modeFilters.map { it.second }.toSet() }) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(enabled = sel.isNotEmpty(), onClick = {
                JourneyForm.modes = if (sel.size == modeFilters.size) emptySet() else sel
                onClose()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = { sel = modeFilters.map { it.second }.toSet() }) { Text(stringResource(R.string.all_types)) } },
        title = { Text(stringResource(R.string.types_title)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modeFilters.forEach { (g, key) ->
                    FilterChip(
                        selected = key in sel,
                        onClick = { sel = if (key in sel) sel - key else sel + key },
                        label = { Text(stringResource(g.label)) },
                        leadingIcon = { Icon(g.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
        },
    )
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
