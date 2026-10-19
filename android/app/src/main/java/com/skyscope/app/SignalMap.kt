package com.skyscope.app

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Trail colors follow the satellite score: 60+ good, 35 to 59 medium, below 35 bad. */
fun scoreColor(score: Int) = when {
    score >= 60 -> Sky.Good
    score >= 35 -> Sky.Fair
    else -> Sky.Bad
}

// Free map styles that need no account or key.
private val Satellite: ITileSource = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Imagery © Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(index: Long): String =
        baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
}
private val Terrain: ITileSource = TileSourceFactory.OpenTopo

/** Draws the colored trail plus the "you are here" dot and its accuracy circle. */
private class TrailOverlay : Overlay() {
    var points: List<TrailPoint> = emptyList()
    var me: GeoPoint? = null
    var meAccuracyM: Float? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val px = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection
        val glowR = pj.metersToPixels(9f).coerceAtLeast(6f)
        val coreR = pj.metersToPixels(3.5f).coerceAtLeast(3f)
        points.forEach { p ->
            pj.toPixels(GeoPoint(p.lat, p.lon), px)
            val c = scoreColor(p.score).toArgb()
            fill.color = c; fill.alpha = 56
            canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), glowR, fill)
            fill.alpha = 220
            canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), coreR, fill)
        }
        val m = me ?: return
        pj.toPixels(m, px)
        meAccuracyM?.let { acc ->
            ring.color = Sky.Accent.toArgb(); ring.alpha = 160; ring.strokeWidth = 2f
            canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), pj.metersToPixels(acc.coerceAtMost(500f)), ring)
        }
        fill.color = android.graphics.Color.WHITE; fill.alpha = 255
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 22f, fill)
        fill.color = Sky.Accent.toArgb()
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 16f, fill)
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun SignalMapCard(
    trail: List<TrailPoint>,
    trailVersion: Int,
    lat: Double?,
    lon: Double?,
    accuracyM: Float?,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var satellite by remember { mutableStateOf(true) }
    var centered by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val overlay = remember { TrailOverlay() }
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(Satellite)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(3.0)
            overlays.add(overlay)
            setOnTouchListener { v, e ->
                // Let the map take drags instead of the page scrolling underneath it.
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (e.action == MotionEvent.ACTION_MOVE) follow = false
                false
            }
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    Panel(
        title = "Signal map",
        subtitle = "Walk around. Every place you pass is colored by how well the phone hears satellites there.",
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Sky.Line, RoundedCornerShape(12.dp)),
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxWidth().height(340.dp),
                update = { mv ->
                    mv.setTileSource(if (satellite) Satellite else Terrain)
                    overlay.points = trail
                    overlay.me = if (lat != null && lon != null) GeoPoint(lat, lon) else null
                    overlay.meAccuracyM = accuracyM
                    val me = overlay.me
                    if (me != null && !centered) {
                        mv.controller.setZoom(18.0); mv.controller.setCenter(me); centered = true
                    } else if (me != null && follow) {
                        mv.controller.animateTo(me)
                    }
                    mv.invalidate()
                    trailVersion // read so a new trail point redraws the map
                },
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, Sky.Line, RoundedCornerShape(10.dp)),
            ) {
                MapChip("Satellite", satellite) { satellite = true }
                MapChip("Terrain", !satellite) { satellite = false }
            }
            if (!follow) {
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                    MapChip("Center on me", false) { follow = true }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(Sky.Good, "Good")
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
            LegendItem(Sky.Fair, "Medium")
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
            LegendItem(Sky.Bad, "Bad")
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (trail.isNotEmpty()) {
                Text(
                    if (confirmClear) "TAP AGAIN" else "CLEAR MAP",
                    color = Sky.Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        if (confirmClear) { onClear(); confirmClear = false } else confirmClear = true
                    }.padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun MapChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        color = if (on) Sky.Bg else Sky.Text,
        fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .background(if (on) Sky.Accent else Sky.Bg.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
