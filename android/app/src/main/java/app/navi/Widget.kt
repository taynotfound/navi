package app.navi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime

/**
 * Home-screen widget: the next departures at a favourite stop. Refreshes every 30 min (Android's floor),
 * on tap of ↻, and when the app's favourites change. ⇄ steps through the favourite stops.
 */
class DeparturesWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) = refresh(ctx, ids)

    override fun onDeleted(ctx: Context, ids: IntArray) {
        prefs(ctx).edit().apply { ids.forEach { remove("stop$it") } }.apply()
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_REFRESH -> refresh(ctx, intArrayOf(id))
            ACTION_NEXT -> {
                ensureInit(ctx)
                val stops = stops()
                if (stops.isNotEmpty()) {
                    val i = stops.indexOfFirst { it.id == prefs(ctx).getString("stop$id", null) }
                    prefs(ctx).edit().putString("stop$id", stops[(i + 1) % stops.size].id).apply()
                }
                refresh(ctx, intArrayOf(id))
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.navi.widget.REFRESH"
        private const val ACTION_NEXT = "app.navi.widget.NEXT"
        private const val ROWS = 5
        private val json = Json { ignoreUnknownKeys = true }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        private fun stops() = Store.favorites.filter { it.type == "STOP" }

        /** Redraw every widget, e.g. after the favourites changed. */
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, DeparturesWidget::class.java))
            if (ids.isNotEmpty()) refresh(ctx, ids)
        }

        private fun refresh(ctx: Context, ids: IntArray) {
            ensureInit(ctx)
            val app = ctx.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val stops = stops()
                val stop = stops.firstOrNull { it.id == prefs(app).getString("stop$id", null) } ?: stops.firstOrNull()
                if (stop == null) {
                    manager.updateAppWidget(id, empty(app, id))
                    continue
                }
                manager.updateAppWidget(id, frame(app, id, stop, stops.size > 1).apply { setViewVisibility(R.id.widget_loading, View.VISIBLE) })
                scope.launch {
                    val board = runCatching { Api.board(stop.id) }
                    val views = frame(app, id, stop, stops.size > 1)
                    board.onSuccess { b ->
                        val now = ZonedDateTime.now().minusMinutes(1)
                        val rows = b.rows.filter { r -> !(parseTime(r.time)?.isBefore(now) ?: false) }.take(ROWS)
                        if (rows.isEmpty()) views.setTextViewText(R.id.widget_message, app.getString(R.string.widget_no_departures))
                        rows.forEach { r -> views.addView(R.id.widget_rows, row(app, r)) }
                        views.setViewVisibility(R.id.widget_message, if (rows.isEmpty()) View.VISIBLE else View.GONE)
                        views.setTextViewText(R.id.widget_updated, hhmm(ZonedDateTime.now()) + if (Api.staleSince != null) " · " + app.getString(R.string.widget_offline) else "")
                    }.onFailure { e ->
                        views.setTextViewText(R.id.widget_message, e.message ?: app.getString(R.string.err_offline))
                        views.setViewVisibility(R.id.widget_message, View.VISIBLE)
                    }
                    manager.updateAppWidget(id, views)
                }
            }
        }

        private fun row(ctx: Context, r: Row) = RemoteViews(ctx.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.row_time, hhmm(r.time))
            val late = (r.delay ?: 0) >= 1
            setTextViewText(R.id.row_delay, if (r.cancelled) "✕" else if (late) "+${r.delay}" else "")
            setViewVisibility(R.id.row_delay, if (r.cancelled || late) View.VISIBLE else View.GONE)
            setTextViewText(R.id.row_line, r.line)
            setTextViewText(R.id.row_dest, r.headsign.orEmpty())
            r.platform?.let { setTextViewText(R.id.row_platform, it) }
            setViewVisibility(R.id.row_platform, if (r.platform != null) View.VISIBLE else View.GONE)
            val what = if (r.cancelled) ctx.getString(R.string.cancelled) else ""
            setContentDescription(R.id.row_root, "${hhmm(r.time)} ${r.line} ${r.headsign.orEmpty()} $what".trim())
        }

        private fun frame(ctx: Context, id: Int, stop: Location, canCycle: Boolean) =
            RemoteViews(ctx.packageName, R.layout.widget_departures).apply {
                setTextViewText(R.id.widget_title, stop.name)
                removeAllViews(R.id.widget_rows)
                setViewVisibility(R.id.widget_message, View.GONE)
                setViewVisibility(R.id.widget_loading, View.GONE)
                setViewVisibility(R.id.widget_next, if (canCycle) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_refresh, broadcast(ctx, id, ACTION_REFRESH))
                setOnClickPendingIntent(R.id.widget_next, broadcast(ctx, id, ACTION_NEXT))
                val open = Intent(ctx, MainActivity::class.java).setAction("app.navi.widget.OPEN.$id")
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("board", json.encodeToString(Location.serializer(), stop))
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }

        private fun empty(ctx: Context, id: Int) = RemoteViews(ctx.packageName, R.layout.widget_departures).apply {
            setTextViewText(R.id.widget_title, ctx.getString(R.string.app_name))
            removeAllViews(R.id.widget_rows)
            setTextViewText(R.id.widget_message, ctx.getString(R.string.widget_empty))
            setViewVisibility(R.id.widget_message, View.VISIBLE)
            setViewVisibility(R.id.widget_loading, View.GONE)
            setViewVisibility(R.id.widget_next, View.GONE)
            setViewVisibility(R.id.widget_refresh, View.GONE)
            val open = Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE))
        }

        private fun broadcast(ctx: Context, id: Int, action: String) = PendingIntent.getBroadcast(
            ctx, id * 2 + if (action == ACTION_NEXT) 1 else 0,
            Intent(ctx, DeparturesWidget::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
