package app.navi.ui

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.navi.hasLocationPermission
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class MapLine(val points: List<Pair<Double, Double>>, val color: Color, val dashed: Boolean = false)
data class MapPoint(val id: String, val lat: Double, val lon: Double, val color: Color, val label: String? = null, val radius: Float = 6f)

/** What the camera should show; a new value moves the camera. One point → zoom in on it, several → fit all. */
data class MapCamera(val points: List<Pair<Double, Double>>, val zoom: Double = 15.0, val maxZoom: Double = 17.0)

// OpenFreeMap: free OSM vector tiles, no API key, no tracking.
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private fun Color.hex() = String.format("#%06X", 0xFFFFFF and toArgb())

private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var lines: List<MapLine> = emptyList()
    var points: List<MapPoint> = emptyList()
    var camera: MapCamera? = null
    var paddingPx = 0

    fun applyData() {
        val style = style ?: return
        style.getSourceAs<GeoJsonSource>("navi-lines")?.setGeoJson(
            FeatureCollection.fromFeatures(lines.filter { it.points.size >= 2 }.map { l ->
                Feature.fromGeometry(LineString.fromLngLats(l.points.map { Point.fromLngLat(it.second, it.first) })).apply {
                    addStringProperty("color", l.color.hex())
                    addBooleanProperty("dashed", l.dashed)
                }
            }),
        )
        style.getSourceAs<GeoJsonSource>("navi-points")?.setGeoJson(
            FeatureCollection.fromFeatures(points.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addStringProperty("id", p.id)
                    addStringProperty("color", p.color.hex())
                    addStringProperty("label", p.label.orEmpty())
                    addNumberProperty("radius", p.radius)
                }
            }),
        )
    }

    fun applyCamera(animate: Boolean) {
        val map = map ?: return
        val cam = camera?.takeIf { it.points.isNotEmpty() } ?: return
        val update = if (cam.points.size == 1 || cam.points.distinct().size == 1) {
            CameraUpdateFactory.newLatLngZoom(LatLng(cam.points[0].first, cam.points[0].second), cam.zoom)
        } else {
            val b = LatLngBounds.Builder()
            cam.points.forEach { b.include(LatLng(it.first, it.second)) }
            // fit all points, but never closer than maxZoom (tiny extents would zoom to street-level noise)
            val fit = map.getCameraForLatLngBounds(b.build(), intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx))
                ?: return
            CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder(fit).zoom(minOf(fit.zoom, cam.maxZoom)).build(),
            )
        }
        if (animate) map.easeCamera(update, 600) else map.moveCamera(update)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun NaviMap(
    modifier: Modifier = Modifier,
    lines: List<MapLine> = emptyList(),
    points: List<MapPoint> = emptyList(),
    camera: MapCamera? = null,
    showMyLocation: Boolean = false,
    onPointClick: ((String) -> Unit)? = null,
    onCameraIdle: ((lat: Double, lon: Double, zoom: Double) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val dark = darkScheme
    val holder = remember { MapHolder() }
    holder.paddingPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val clickCb by rememberUpdatedState(onPointClick)
    val idleCb by rememberUpdatedState(onCameraIdle)

    val mapView = remember {
        MapView(ctx).apply {
            onCreate(null)
            getMapAsync { map ->
                holder.map = map
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(51.16, 10.45)).zoom(5.0).build() // Germany
                map.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { style ->
                    holder.style = style
                    style.addSource(GeoJsonSource("navi-lines"))
                    style.addSource(GeoJsonSource("navi-points"))
                    val isDashed = Expression.eq(Expression.get("dashed"), Expression.literal(true))
                    style.addLayer(
                        LineLayer("navi-lines-casing", "navi-lines").withProperties(
                            PropertyFactory.lineColor(if (dark) "#000000" else "#FFFFFF"),
                            PropertyFactory.lineWidth(9f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ).withFilter(Expression.not(isDashed)),
                    )
                    style.addLayer(
                        LineLayer("navi-lines-solid", "navi-lines").withProperties(
                            PropertyFactory.lineColor(Expression.get("color")),
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ).withFilter(Expression.not(isDashed)),
                    )
                    style.addLayer(
                        LineLayer("navi-lines-dashed", "navi-lines").withProperties(
                            PropertyFactory.lineColor(Expression.get("color")),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineDasharray(arrayOf(0.5f, 2f)),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        ).withFilter(isDashed),
                    )
                    style.addLayer(
                        CircleLayer("navi-points", "navi-points").withProperties(
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleRadius(Expression.toNumber(Expression.get("radius"))),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2f),
                        ),
                    )
                    style.addLayer(
                        SymbolLayer("navi-labels", "navi-points").withProperties(
                            PropertyFactory.textField(Expression.get("label")),
                            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
                            PropertyFactory.textColor(if (dark) "#FFFFFF" else "#1A1C1C"),
                            PropertyFactory.textHaloColor(if (dark) "#000000" else "#FFFFFF"),
                            PropertyFactory.textHaloWidth(1.5f),
                            PropertyFactory.textOptional(true),
                        ),
                    )
                    holder.applyData()
                    holder.applyCamera(animate = false)
                    if (showMyLocation && hasLocationPermission(ctx)) {
                        runCatching {
                            map.locationComponent.activateLocationComponent(
                                LocationComponentActivationOptions.builder(ctx, style).useDefaultLocationEngine(true).build(),
                            )
                            map.locationComponent.isLocationComponentEnabled = true
                            map.locationComponent.renderMode = RenderMode.COMPASS
                        }
                    }
                }
                map.addOnMapClickListener { ll ->
                    val cb = clickCb ?: return@addOnMapClickListener false
                    val p = map.projection.toScreenLocation(ll)
                    val r = 24f
                    val f = map.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), "navi-points").firstOrNull()
                    val id = f?.getStringProperty("id") ?: return@addOnMapClickListener false
                    cb(id)
                    true
                }
                map.addOnCameraIdleListener {
                    val c = map.cameraPosition
                    c.target?.let { idleCb?.invoke(it.latitude, it.longitude, c.zoom) }
                }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(lines, points) {
        holder.lines = lines
        holder.points = points
        holder.applyData()
    }
    LaunchedEffect(camera) {
        holder.camera = camera
        holder.applyCamera(animate = true)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
