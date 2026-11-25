package app.navi.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.navi.Api
import app.navi.BuildConfig
import app.navi.DelayStats
import app.navi.Photo
import app.navi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** "Usually 4 min late here (often up to 9)", from what Navi has seen of this line before. Null when there's nothing to say. */
@Composable
fun usualDelayText(s: DelayStats?): String? {
    if (s == null || s.samples < 8) return null
    return when {
        s.likely < 3 -> stringResource(R.string.usually_on_time)
        s.median < 3 -> stringResource(R.string.often_late, s.likely)
        else -> stringResource(R.string.usually_late, s.median, s.likely)
    }
}

@Composable
fun UsualDelayLine(s: DelayStats?, modifier: Modifier = Modifier) {
    val text = usualDelayText(s) ?: return
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = c.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (s!!.likely >= 6) c.tertiary else c.onSurfaceVariant)
    }
}

/** How punctual this line is: share of runs per delay class and the typical delay by time of day. */
@Composable
fun PunctualitySection(line: String, operator: String?, stopId: String?) {
    val p by produceState<app.navi.Punctuality?>(null, line, operator, stopId) {
        value = runCatching { Api.punctuality(line, operator, stopId) }.getOrNull()
    }
    val data = p ?: return
    val overall = data.overall ?: return
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerLow).padding(18.dp),
    ) {
        Text(stringResource(R.string.punctuality), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.punctuality_summary, overall.onTime, overall.median, overall.samples),
            Modifier.padding(top = 2.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
        )
        val labels = listOf(
            R.string.delay_class_on_time, R.string.delay_class_3, R.string.delay_class_6,
            R.string.delay_class_11, R.string.delay_class_21, R.string.delay_class_cancelled,
        )
        val colors = listOf(c.primary, c.secondary, c.tertiary, c.tertiary, c.error, c.error)
        data.histogram.take(labels.size).forEachIndexed { i, pct ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(labels[i]), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct.coerceIn(0, 100) / 100f).clip(RoundedCornerShape(6.dp)).background(colors[i]))
                }
                Text("$pct %", Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (data.bands.isNotEmpty()) {
            Text(stringResource(R.string.punctuality_by_time), Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge)
            data.bands.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(bandLabel(b.band, b.weekend), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(usualDelayText(b.stats).orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
        }
        data.atStop?.let { s ->
            usualDelayText(s)?.let { Text(stringResource(R.string.at_your_stop, it), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun bandLabel(band: String, weekend: Boolean): String {
    val b = stringResource(
        when (band) {
            "night" -> R.string.band_night
            "morning" -> R.string.band_morning
            "day" -> R.string.band_day
            "evening-peak" -> R.string.band_evening_peak
            else -> R.string.band_evening
        },
    )
    return if (weekend) stringResource(R.string.band_weekend, b) else b
}

/** Pictures of this line's vehicles from Wikimedia Commons. Only loaded on request: the photos come straight from Wikimedia. */
@Composable
fun SpotterPhotos(line: String, operator: String?, mode: String) {
    var show by rememberSaveable(line) { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (!show) {
            OutlinedButton(onClick = { show = true }, Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_photos))
            }
            return@Column
        }
        val photos by produceState<List<Photo>?>(null, line, operator, mode) {
            value = runCatching { Api.photos(line, operator, mode) }.getOrDefault(emptyList())
        }
        Text(stringResource(R.string.spotter_photos), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.spotter_photos_hint), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        when {
            photos == null -> Text(stringResource(R.string.loading), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            photos!!.isEmpty() -> Text(stringResource(R.string.no_photos), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(photos!!, key = { it.page }) { PhotoCard(it) }
            }
        }
    }
}

@Composable
private fun PhotoCard(p: Photo) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, p.thumb) { value = loadImage(p.thumb) }
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer)
            .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.page))) } },
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp).background(c.surfaceContainerHigh)) {
            img?.let { Image(it, p.title, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop) }
        }
        Text(p.title, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(p.author.ifBlank { "Wikimedia Commons" }, p.license).filter { it.isNotBlank() }.joinToString(" · "),
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val imageCache = object : android.util.LruCache<String, ImageBitmap>(12) {}

private suspend fun loadImage(url: String): ImageBitmap? = imageCache.get(url) ?: withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Navi-Android/${BuildConfig.VERSION_NAME} (https://github.com/taynotfound/navi)")
            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()?.also { imageCache.put(url, it) }
}
