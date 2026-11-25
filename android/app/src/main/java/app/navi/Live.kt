package app.navi

// Live trip in the notification shade, Android's take on the Dynamic Island: on Android 16 an ongoing
// ProgressStyle notification is promoted to a "Live Update" (status bar chip, lock screen, Samsung's Now Bar);
// older versions get the same notification with a progress bar. A foreground service refreshes it with live
// data and posts alerts: leave now, change, get off, delays, cancellations. Plus reminders for saved trips.

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.navi.ui.legColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.ZonedDateTime

private const val CH_TRIP = "trip"
private const val CH_ALERTS = "alerts"
private const val CH_REMIND = "reminders"
private const val ID_TRIP = 1
private const val ID_ALERT = 2

private val json = Json { ignoreUnknownKeys = true }

fun ensureInit(ctx: Context) {
    if (!L.ready) L.ctx = ctx.applicationContext
    if (!Store.ready) Store.init(ctx)
}

fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun channels(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_TRIP, L.s(R.string.channel_trip), NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
    nm.createNotificationChannel(NotificationChannel(CH_ALERTS, L.s(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CH_REMIND, L.s(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
}

private fun openApp(ctx: Context, trip: Boolean) = PendingIntent.getActivity(
    ctx, if (trip) 1 else 0,
    Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openTracked", trip),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

// ---------------------------------------------------------------------
// What to tell the user right now (same wording as the in-app status card)
// ---------------------------------------------------------------------

data class TripStatus(val title: String, val detail: String, val chip: String?, val done: Boolean)

private fun dep(l: Leg) = parseTime(l.from.departure ?: l.from.scheduledDeparture)
private fun arr(l: Leg) = parseTime(l.to.arrival ?: l.to.scheduledArrival)
private fun mins(now: ZonedDateTime, t: ZonedDateTime?) = t?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0) } ?: 0
private fun delay(l: Leg, departure: Boolean): Int? =
    if (departure) app.navi.ui.delayOf(l.from.departure, l.from.scheduledDeparture, l.realtime)
    else app.navi.ui.delayOf(l.to.arrival, l.to.scheduledArrival, l.realtime)

/**
 * The status-bar chip of the Live Update: only a few characters fit, so it says one thing, how long until the
 * next thing happens ("14 min"), or the clock time when that's more than an hour away. The line goes in the title.
 */
private fun chipIn(at: ZonedDateTime, m: Long) = if (m < 60) L.s(R.string.chip_min, m) else hhmm(at)

fun tripStatus(it: Itinerary, now: ZonedDateTime): TripStatus {
    val transit = it.legs.filterNot { l -> l.isWalk }
    val last = it.legs.last().to.name
    if (transit.isEmpty()) return TripStatus(L.s(R.string.status_arrived), last, null, true)
    if (transit.any { l -> l.cancelled || l.from.cancelled || l.to.cancelled }) {
        return TripStatus(L.s(R.string.part_cancelled), L.s(R.string.part_cancelled_hint), "!", false)
    }
    val f = transit[0]
    if (now.isBefore(dep(f) ?: now)) {
        val m = mins(now, dep(f))
        return TripStatus(
            if (m <= 120) L.s(R.string.status_departs_in, m) else L.s(R.string.status_departs_at, hhmm(dep(f)!!)),
            f.from.platform?.let { p -> L.s(R.string.status_board_platform, f.line.orEmpty(), f.from.name, p) } ?: L.s(R.string.status_board, f.line.orEmpty(), f.from.name),
            chipIn(dep(f)!!, m), false,
        )
    }
    for ((i, l) in transit.withIndex()) {
        val next = transit.getOrNull(i + 1)
        if (!now.isBefore(dep(l) ?: now) && now.isBefore(arr(l) ?: now)) {
            return TripStatus(
                L.s(R.string.status_riding, l.line.orEmpty(), l.headsign ?: l.to.name),
                L.s(R.string.status_get_off, l.to.name, hhmm(arr(l)!!), mins(now, arr(l))),
                chipIn(arr(l)!!, mins(now, arr(l))), false,
            )
        }
        if (next != null && !now.isBefore(arr(l) ?: now) && now.isBefore(dep(next) ?: now)) {
            return TripStatus(
                L.s(R.string.status_change, next.from.name, mins(now, dep(next))),
                listOfNotNull(next.line, next.from.platform?.let { p -> L.s(R.string.platform_n, p) }).joinToString(" · "),
                chipIn(dep(next)!!, mins(now, dep(next))), false,
            )
        }
    }
    return TripStatus(L.s(R.string.status_arrived), last, null, now.isAfter((parseTime(it.end) ?: now).plusMinutes(10)))
}

// ---------------------------------------------------------------------
// The service
// ---------------------------------------------------------------------

class TripService : Service() {
    companion object {
        /** The trip being followed, for the app's "following" state. */
        var tracked by mutableStateOf<Itinerary?>(null); private set

        fun start(ctx: Context, it: Itinerary) {
            tracked = it
            ctx.getSharedPreferences("navi", Context.MODE_PRIVATE).edit().putString("tracked", json.encodeToString(Itinerary.serializer(), it)).apply()
            ContextCompat.startForegroundService(ctx, Intent(ctx, TripService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TripService::class.java).setAction("stop"))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: kotlinx.coroutines.Job? = null
    private val fired = mutableSetOf<String>()
    private val lastDelay = mutableMapOf<Int, Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureInit(this)
        channels(this)
        val prefs = getSharedPreferences("navi", MODE_PRIVATE)
        if (intent?.action == "stop") { finish(); return START_NOT_STICKY }
        intent?.getStringExtra("savedKey")?.let { k -> Store.savedTrips.firstOrNull { t -> t.key == k }?.let { tracked = it.itinerary } }
        if (tracked == null) tracked = prefs.getString("tracked", null)?.let { s -> runCatching { json.decodeFromString(Itinerary.serializer(), s) }.getOrNull() }
        val it = tracked ?: run { finish(); return START_NOT_STICKY }
        ServiceCompat.startForeground(
            this, ID_TRIP, build(it, ZonedDateTime.now()),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        fired.clear(); lastDelay.clear()
        loop?.cancel()
        loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        var first = true
        while (scope.isActive) {
            val now = ZonedDateTime.now()
            var it = tracked ?: return finish()
            if (!first || it.legs.any { l -> l.realtime }) runCatching { Api.live(it) }.onSuccess { fresh ->
                if (fresh.legs.size == it.legs.size) { it = fresh; tracked = fresh }
            }
            val status = tripStatus(it, now)
            if (status.done) return finish()
            post(build(it, now, status))
            alerts(it, now, quiet = first)
            first = false
            delay(30_000)
        }
    }

    private fun finish() {
        tracked = null
        getSharedPreferences("navi", MODE_PRIVATE).edit().remove("tracked").apply()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    @SuppressLint("MissingPermission")
    private fun post(n: android.app.Notification) {
        if (canNotify(this)) NotificationManagerCompat.from(this).notify(ID_TRIP, n)
    }

    private fun build(it: Itinerary, now: ZonedDateTime, s: TripStatus = tripStatus(it, now)): android.app.Notification {
        val start = parseTime(it.legs.first().from.departure ?: it.start) ?: now
        val end = parseTime(it.legs.last().to.arrival ?: it.end) ?: now
        val total = Duration.between(start, end).seconds.coerceAtLeast(60).toInt()
        // one segment per part of the trip, rides in their line colour; a point at every change
        val segs = it.legs.map { l ->
            val a = parseTime(l.from.departure ?: l.from.scheduledDeparture)
            val b = parseTime(l.to.arrival ?: l.to.scheduledArrival)
            val len = if (a != null && b != null) Duration.between(a, b).seconds.toInt() else l.duration
            NotificationCompat.ProgressStyle.Segment(len.coerceAtLeast(1)).setColor(
                if (l.isWalk) 0xFF9AA0A6.toInt() else legColor(l).toArgb(),
            )
        }
        var at = 0
        val points = it.legs.dropLast(1).mapNotNull { l ->
            at += Duration.between(parseTime(l.from.departure ?: l.from.scheduledDeparture) ?: return@mapNotNull null, parseTime(l.to.arrival ?: l.to.scheduledArrival) ?: return@mapNotNull null).seconds.toInt()
            if (l.isWalk) null else NotificationCompat.ProgressStyle.Point(at.coerceIn(1, total - 1))
        }
        val progress = Duration.between(start, now).seconds.toInt().coerceIn(0, total)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(segs)
            .setProgressPoints(points)
            .setProgress(progress)
        val stop = PendingIntent.getService(this, 2, Intent(this, TripService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH_TRIP)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(s.title)
            .setContentText(s.detail)
            .setSubText("${it.legs.first().from.name} → ${it.legs.last().to.name}")
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setRequestPromotedOngoing(true)
            .apply { s.chip?.let { c -> setShortCriticalText(c) } }
            .setWhen(end.toInstant().toEpochMilli()).setShowWhen(false)
            .setContentIntent(openApp(this, true))
            .addAction(0, L.s(R.string.live_stop), stop)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun alert(key: String, title: String, text: String? = null) {
        if (!fired.add(key) || !canNotify(this)) return
        NotificationManagerCompat.from(this).notify(
            ID_ALERT,
            NotificationCompat.Builder(this, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title).setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true).setTimeoutAfter(10 * 60_000)
                .setContentIntent(openApp(this, true))
                .build(),
        )
    }

    /** Things worth a buzz. quiet: only remember the current state (first run), no alerts. */
    private fun alerts(it: Itinerary, now: ZonedDateTime, quiet: Boolean) {
        val transit = it.legs.withIndex().filter { (_, l) -> !l.isWalk }
        if (quiet) {
            transit.forEach { (i, l) -> (delay(l, !now.isAfter(dep(l) ?: now)))?.let { d -> lastDelay[i] = d } }
        }
        for ((n, iv) in transit.withIndex()) {
            val (i, l) = iv
            val d = dep(l) ?: continue
            val a = arr(l) ?: continue
            if (now.isAfter(a)) continue
            val line = l.line.orEmpty()
            if (l.cancelled || l.from.cancelled || l.to.cancelled) {
                if (!quiet) alert("x$i", L.s(R.string.risk_cancelled, line), L.s(R.string.part_cancelled_hint))
                continue
            }
            // delays: tell when they change by 3 min or more
            val delayNow = delay(l, !now.isAfter(d))
            if (delayNow != null && !quiet) {
                val before = lastDelay[i] ?: 0
                if (kotlin.math.abs(delayNow - before) >= 3) {
                    lastDelay[i] = delayNow
                    fired.remove("d$i")
                    alert("d$i", if (delayNow >= 3) L.s(R.string.alert_delay, line, delayNow) else L.s(R.string.alert_on_time, line), l.from.name)
                }
            }
            if (quiet) continue
            val m = Duration.between(now, d).toMinutes()
            if (n == 0 && m in 0..5) {
                alert("leave$i", L.s(R.string.alert_leave, line, m),
                    l.from.platform?.let { p -> L.s(R.string.status_board_platform, line, l.from.name, p) } ?: L.s(R.string.status_board, line, l.from.name))
            }
            if (n > 0 && m in 0..8) {
                val prev = transit[n - 1].value
                val buffer = Duration.between(arr(prev) ?: d, d).toMinutes()
                if (buffer < 0) alert("miss$i", L.s(R.string.risk_missed, prev.to.name, -buffer), L.s(R.string.alert_open_alternatives))
                else alert("chg$i", L.s(R.string.alert_change, l.from.name, line, m), l.from.platform?.let { p -> L.s(R.string.platform_n, p) })
            }
            // get off: once the vehicle left the stop before yours
            val beforeLast = l.stops.lastOrNull()?.let { s -> parseTime(s.departure ?: s.scheduledDeparture) } ?: a.minusMinutes(2)
            if (now.isAfter(d) && !now.isBefore(beforeLast)) alert("off$i", L.s(R.string.alert_get_off, l.to.name), l.to.platform?.let { p -> L.s(R.string.platform_n, p) })
        }
    }
}

// ---------------------------------------------------------------------
// Reminders for saved trips: 20 min before departure, with "follow live"
// ---------------------------------------------------------------------

object Reminders {
    private const val LEAD_MIN = 20L

    fun sync(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences("navi", Context.MODE_PRIVATE)
        prefs.getStringSet("reminders", emptySet())!!.forEach { k -> am.cancel(pi(ctx, k)) }
        val now = System.currentTimeMillis()
        val set = mutableSetOf<String>()
        Store.savedTrips.forEach { t ->
            val first = t.itinerary.legs.firstOrNull { l -> !l.isWalk } ?: return@forEach
            val at = (parseTime(t.itinerary.start) ?: return@forEach).minusMinutes(LEAD_MIN).toInstant().toEpochMilli()
            if (at <= now || parseTime(first.from.scheduledDeparture) == null) return@forEach
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, t.key))
            set += t.key
        }
        prefs.edit().putStringSet("reminders", set).apply()
    }

    private fun pi(ctx: Context, key: String) = PendingIntent.getBroadcast(
        ctx, key.hashCode(), Intent(ctx, ReminderReceiver::class.java).putExtra("key", key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class ReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        ensureInit(ctx)
        val key = intent.getStringExtra("key") ?: return
        val t = Store.savedTrips.firstOrNull { it.key == key } ?: return
        if (!canNotify(ctx)) return
        channels(ctx)
        val first = t.itinerary.legs.firstOrNull { !it.isWalk }
        val m = minutesUntil(t.itinerary.start) ?: return
        val follow = PendingIntent.getForegroundService(
            ctx, key.hashCode(), Intent(ctx, TripService::class.java).putExtra("savedKey", key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationManagerCompat.from(ctx).notify(
            key.hashCode(),
            NotificationCompat.Builder(ctx, CH_REMIND)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(L.s(R.string.reminder_title, t.to, hhmm(t.itinerary.start)))
                .setContentText(L.s(R.string.reminder_text, m, first?.line.orEmpty(), first?.from?.name ?: t.from))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, false))
                .addAction(0, L.s(R.string.live_start_short), follow)
                .build(),
        )
    }
}

// ---------------------------------------------------------------------
// Launcher shortcuts: long-press the icon → recent trips
// ---------------------------------------------------------------------

object Shortcuts {
    fun sync(ctx: Context) {
        val list = Store.recentJourneys.take(3).mapIndexed { i, j ->
            ShortcutInfoCompat.Builder(ctx, "journey$i")
                .setShortLabel(j.to.name.take(24))
                .setLongLabel("${j.from.name} → ${j.to.name}".take(40))
                .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .putExtra("journey", json.encodeToString(SavedJourney.serializer(), j)),
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, list) }
    }
}
