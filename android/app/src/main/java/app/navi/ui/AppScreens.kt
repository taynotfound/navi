package app.navi.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.navi.BuildConfig
import app.navi.JourneyForm
import app.navi.Nav
import app.navi.R
import app.navi.SavedTrip
import app.navi.Store
import app.navi.hasLocationPermission
import app.navi.hhmm
import app.navi.parseTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val KOFI_URL = "https://ko-fi.com/taygotkofi"

private fun open(ctx: android.content.Context, url: String) =
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

// =====================================================================
// Saved tab
// =====================================================================

@Composable
fun SavedTab(nav: Nav) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.saved), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = nav::settings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
            }
        }
        item { SectionHeader(stringResource(R.string.your_trips)) }
        if (Store.savedTrips.isEmpty()) item {
            MessageState(Icons.Filled.BookmarkBorder, stringResource(R.string.no_saved_trips))
        }
        items(Store.savedTrips, key = { it.key }) { t -> SavedTripCard(t, nav) }

        val favs = Store.favorites
        if (favs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.favorites)) }
            items(favs, key = { "f" + it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.name, fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        IconButton(onClick = { Store.toggleFavorite(f) }) { Icon(Icons.Filled.Delete, stringResource(R.string.unfavorite)) }
                    },
                    modifier = Modifier.clickableRow { nav.board(f) },
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SupportCard { open(ctx, KOFI_URL) }
        }
    }
}

@Composable
private fun SavedTripCard(t: SavedTrip, nav: Nav) {
    val it = t.itinerary
    val start = parseTime(it.start)
    val past = (parseTime(it.end) ?: ZonedDateTime.now()).isBefore(ZonedDateTime.now())
    val cancelled = it.legs.any { l -> l.cancelled || l.from.cancelled || l.to.cancelled }
    ElevatedCard(
        onClick = { JourneyForm.selected = it; nav.itinerary() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    start?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text("${t.from} → ${t.to}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${hhmm(it.start)} – ${hhmm(it.end)}, ${transfersText(it.transfers)}" + if (past) ", " + stringResource(R.string.past) else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cancelled) Text(stringResource(R.string.part_cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = { Store.toggleSavedKey(t.key, it, t.from, t.to) }) { Icon(Icons.Filled.Delete, stringResource(R.string.unsave_trip)) }
        }
    }
}

@Composable
fun SupportCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.support_short), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

/** Shown once after about a week of use. Never nags twice after "no". */
@Composable
fun SupportDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { Store.supportAnswered(later = true); onClose() },
        icon = { Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text(stringResource(R.string.support_prompt_title)) },
        text = { Text(stringResource(R.string.support_prompt_text)) },
        confirmButton = {
            Button(onClick = { Store.supportAnswered(later = false); open(ctx, KOFI_URL); onClose() }) { Text(stringResource(R.string.support_button)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Store.supportAnswered(later = false); onClose() }) { Text(stringResource(R.string.never_ask)) }
                TextButton(onClick = { Store.supportAnswered(later = true); onClose() }) { Text(stringResource(R.string.later_button)) }
            }
        },
    )
}

// =====================================================================
// Settings
// =====================================================================

fun currentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(
    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.settings)) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                SectionHeader(stringResource(R.string.appearance))
                ThemePicker(Modifier.padding(horizontal = 16.dp))
                if (Build.VERSION.SDK_INT >= 31) SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_hint), Store.dynamicColor) {
                    Store.dynamicColor = it; Store.saveSettings()
                }
            }
            item {
                SectionHeader(stringResource(R.string.language))
                LanguagePicker()
            }
            item {
                SectionHeader(stringResource(R.string.travel))
                SwitchRow(stringResource(R.string.step_free_default), stringResource(R.string.step_free_default_hint), Store.wheelchairDefault) {
                    Store.wheelchairDefault = it; JourneyForm.wheelchair = it; Store.saveSettings()
                }
                SwitchRow(stringResource(R.string.assistant_setting), stringResource(R.string.assistant_setting_hint), Store.assistantEnabled) {
                    Store.assistantEnabled = it; Store.saveSettings()
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_history)) },
                    supportingContent = { Text(stringResource(R.string.clear_history_hint)) },
                    modifier = Modifier.clickableRow { Store.clearHistory() },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.rerun_setup)) },
                    modifier = Modifier.clickableRow { Store.onboarded = false; Store.saveSettings() },
                )
            }
            item {
                SectionHeader(stringResource(R.string.support_title))
                SupportCard { open(ctx, KOFI_URL) }
            }
            item {
                SectionHeader(stringResource(R.string.about))
                ListItem(
                    headlineContent = { Text("Navi ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text(stringResource(R.string.about_text)) },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.data_sources)) },
                    supportingContent = { Text(stringResource(R.string.data_sources_text)) },
                )
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
fun ThemePicker(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("dark", R.string.theme_dark, Icons.Filled.DarkMode),
            Triple("light", R.string.theme_light, Icons.Filled.LightMode),
            Triple("system", R.string.theme_system, Icons.Filled.SettingsBrightness),
        ).forEach { (key, label, icon) ->
            ThemeOption(stringResource(label), icon, Store.theme == key, key, Modifier.weight(1f)) {
                Store.theme = key; Store.saveSettings()
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, icon: ImageVector, selected: Boolean, key: String, modifier: Modifier, onClick: () -> Unit) {
    // a tiny preview of each look
    val (bg, fg) = when (key) {
        "dark" -> Color(0xFF070F24) to Color(0xFF4FD8C0)
        "light" -> Color(0xFFF7F9FF) to Color(0xFF00786D)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.primary
    }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun LanguagePicker() {
    val current = currentLanguage()
    listOf("" to R.string.lang_system, "de" to R.string.lang_de, "en" to R.string.lang_en).forEach { (tag, label) ->
        ListItem(
            headlineContent = { Text(stringResource(label)) },
            leadingContent = { RadioButton(selected = current == tag, onClick = null) },
            modifier = Modifier.selectable(selected = current == tag, role = Role.RadioButton) { setLanguage(tag) },
        )
    }
}

// =====================================================================
// First-launch setup
// =====================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val steps = 4
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { step++ }
    fun next() { if (step < steps - 1) step++ else onDone() }
    val progress by androidx.compose.animation.core.animateFloatAsState((step + 1f) / steps, label = "progress")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            AnimatedContent(step, Modifier.weight(1f), label = "setup") { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp)) {
                    when (s) {
                        0 -> {
                            androidx.compose.foundation.Image(painterResource(R.drawable.logo), null, Modifier.size(132.dp))
                            Spacer(Modifier.height(36.dp))
                            SetupTitle(stringResource(R.string.welcome_title), stringResource(R.string.welcome_text))
                            Spacer(Modifier.height(32.dp))
                            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            val langs = listOf("", "de", "en")
                            ConnectedChoice(
                                listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_de), stringResource(R.string.lang_en)),
                                langs.indexOf(currentLanguage()).coerceAtLeast(0),
                                { setLanguage(langs[it]) },
                            )
                        }
                        1 -> {
                            SetupShape(MaterialShapes.Sunny, Icons.Filled.DarkMode)
                            SetupTitle(stringResource(R.string.setup_look_title), stringResource(R.string.setup_look_text))
                            Spacer(Modifier.height(32.dp))
                            ThemePicker()
                        }
                        2 -> {
                            SetupShape(MaterialShapes.Pill, Icons.Filled.NearMe)
                            SetupTitle(stringResource(R.string.setup_location_title), stringResource(R.string.setup_location_text))
                        }
                        else -> {
                            SetupShape(MaterialShapes.Clover4Leaf, Icons.AutoMirrored.Filled.Accessible)
                            SetupTitle(stringResource(R.string.setup_access_title), stringResource(R.string.setup_access_text))
                            Spacer(Modifier.height(24.dp))
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                SwitchRow(stringResource(R.string.step_free_default), stringResource(R.string.step_free_default_hint), Store.wheelchairDefault) {
                                    Store.wheelchairDefault = it; JourneyForm.wheelchair = it
                                }
                            }
                        }
                    }
                }
            }
            val askLocation = step == 2 && !hasLocationPermission(ctx)
            Button(
                onClick = {
                    if (askLocation) permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    else next()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(
                    stringResource(if (askLocation) R.string.allow_location else when (step) { 0 -> R.string.get_started; steps - 1 -> R.string.done; else -> R.string.next }),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // keeps the layout steady between steps
            TextButton(onClick = ::next, enabled = askLocation, modifier = Modifier.fillMaxWidth().height(48.dp).alpha(if (askLocation) 1f else 0f)) {
                Text(stringResource(R.string.not_now))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupShape(shape: androidx.graphics.shapes.RoundedPolygon, icon: ImageVector) {
    Box(
        Modifier.size(112.dp).clip(shape.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun SetupTitle(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
