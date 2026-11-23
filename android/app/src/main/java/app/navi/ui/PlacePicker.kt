package app.navi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import app.navi.Api
import app.navi.R
import app.navi.Location
import app.navi.Store
import app.navi.currentPosition
import app.navi.myLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen place search: type to search, or pick "Mein Standort", a favourite or a recent place.
 * [stopsOnly] restricts results to stations/stops (for departure boards).
 */
@Composable
fun PlacePicker(
    title: String,
    onDismiss: () -> Unit,
    onPick: (Location) -> Unit,
    stopsOnly: Boolean = false,
    allowMyLocation: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            PlacePickerContent(title, onDismiss, { Store.rememberPlace(it); onPick(it) }, stopsOnly, allowMyLocation)
        }
    }
}

@Composable
private fun PlacePickerContent(
    title: String,
    onDismiss: () -> Unit,
    onPick: (Location) -> Unit,
    stopsOnly: Boolean,
    allowMyLocation: Boolean,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Location>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val noLocation = stringResource(R.string.location_unavailable)
    val noPermission = stringResource(R.string.location_denied_type)
    fun useMyLocation() {
        locating = true
        scope.launch {
            val pos = currentPosition(ctx)
            locating = false
            if (pos == null) error = noLocation
            else onPick(myLocation(pos.latitude, pos.longitude))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) useMyLocation() else error = noPermission
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        error = null
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(250) // debounce typing
        searching = true
        try {
            results = Api.search(query.trim(), stopsOnly)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        } finally {
            searching = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp).imePadding()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(focus),
            placeholder = { Text(title) },
            singleLine = true,
            leadingIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
            trailingIcon = {
                when {
                    searching -> CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                    query.isNotEmpty() -> IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_input)) }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = MaterialTheme.shapes.extraLarge,
        )
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (error != null) item { Text(error!!, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
            if (query.trim().length < 2) {
                if (allowMyLocation) item {
                    ListItem(
                        headlineContent = { Text(stringResource(if (locating) R.string.locating else R.string.my_location), fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickableRow {
                            if (app.navi.hasLocationPermission(ctx)) useMyLocation()
                            else permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                    )
                }
                val favs = Store.favorites.filter { !stopsOnly || it.type == "STOP" }
                if (favs.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.favorites)) }
                    items(favs, key = { "f" + it.id }) { PlaceRow(it, Icons.Filled.Star) { onPick(it) } }
                }
                val recent = Store.recentPlaces.filter { (!stopsOnly || it.type == "STOP") && !Store.isFavorite(it.id) }
                if (recent.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.recent)) }
                    items(recent, key = { "r" + it.id }) { PlaceRow(it, Icons.Filled.History) { onPick(it) } }
                }
                if (favs.isEmpty() && recent.isEmpty()) item {
                    MessageState(Icons.Filled.Search, stringResource(R.string.search_hint))
                }
            } else {
                if (!searching && results.isEmpty() && error == null) item {
                    MessageState(Icons.Filled.Search, stringResource(R.string.search_empty))
                }
                items(results, key = { it.id + it.name }) { PlaceRow(it, null) { onPick(it) } }
            }
        }
    }
}

@Composable
fun PlaceRow(loc: Location, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    val main = loc.modes.map { ModeGroup.of(it) }.minByOrNull { it.ordinal }
    ListItem(
        headlineContent = { Text(loc.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            val parts = listOfNotNull(
                loc.area,
                when (loc.type) { "ADDRESS" -> stringResource(R.string.address); "PLACE" -> stringResource(R.string.place); else -> null },
                loc.distance?.let { "$it m" },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(", "))
        },
        leadingContent = {
            Icon(
                icon ?: when (loc.type) { "STOP" -> main?.icon ?: Icons.Filled.Place; "ADDRESS" -> Icons.Filled.Home; else -> Icons.Filled.Place },
                null,
                tint = if (icon == null && main != null) main.color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickableRow(onClick),
    )
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
