package com.skyscope.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- Colors & theme ----------

// Same high-tech palette as the iPhone version: near-black navy, glass cards, cyan accent.
object Sky {
    val Bg = Color(0xFF05080F)
    val Bg2 = Color(0xFF0A1222)
    val Card = Color(0xFF101C30)
    val CardHi = Color(0xFF16263F)
    val Line = Color(0x2978AAE6)
    val Text = Color(0xFFE6F1FF)
    val Muted = Color(0xFF7F93B2)
    val Accent = Color(0xFF22D3EE)
    val Accent2 = Color(0xFF818CF8)
    val Good = Color(0xFF34D399)
    val Fair = Color(0xFFFACC15)
    val Bad = Color(0xFFF87171)
    val Off = Color(0xFF3B4A66)
}

private val Mono = FontFamily.Monospace
private val ScreenBg = Brush.verticalGradient(listOf(Color(0xFF0B1A2C), Sky.Bg, Sky.Bg2))

fun cn0Color(cn0: Float): Color = when {
    cn0 <= 0f -> Sky.Off
    cn0 >= 35f -> Sky.Good
    cn0 >= 25f -> Sky.Fair
    else -> Sky.Bad
}

private fun severityColor(s: Severity): Color = when (s) {
    Severity.GOOD -> Sky.Good
    Severity.INFO -> Sky.Accent
    Severity.WARN -> Sky.Fair
    Severity.BAD -> Sky.Bad
}

private fun levelColor(l: Level): Color = when (l) {
    Level.EXCELLENT, Level.GOOD -> Sky.Good
    Level.FAIR -> Sky.Fair
    Level.POOR -> Sky.Bad
    Level.NONE -> Sky.Off
}

@Composable
fun SkyScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sky.Accent,
            background = Sky.Bg,
            surface = Sky.Card,
            onBackground = Sky.Text,
            onSurface = Sky.Text,
        ),
        content = content,
    )
}

// ---------- Screens ----------

@Composable
fun PermissionScreen(onGrant: () -> Unit, onOpenAppSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(32.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Measures how well your phone hears positioning satellites (GPS, Galileo, GLONASS, BeiDou) where you're standing, and tells you how to get a better signal.",
            color = Sky.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "It needs precise location permission to read satellite data. Nothing leaves your phone.",
            color = Sky.Text, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Sky.Accent, contentColor = Sky.Bg),
        ) { Text("Allow location", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onOpenAppSettings) { Text("Open app settings", color = Sky.Muted) }
    }
}

@Composable
fun MainScreen(snapshot: GnssSnapshot, trail: SignalTrail, onOpenLocationSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val a = remember(snapshot, now) { analyze(snapshot, now) }
    var showAll by remember { mutableStateOf(false) }
    var trailVersion by remember { mutableIntStateOf(0) }

    // Every new satellite fix adds a point to the map, colored by the satellite score at that moment.
    LaunchedEffect(snapshot.lastFixAt) {
        val lat = snapshot.lat
        val lon = snapshot.lon
        val acc = snapshot.accuracyM
        if (snapshot.lastFixAt != null && lat != null && lon != null && acc != null && acc < 100f) {
            val score = analyze(snapshot, System.currentTimeMillis()).score
            if (trail.add(TrailPoint(lat, lon, score, System.currentTimeMillis()))) trailVersion++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(a)
        ScoreCard(a)
        StatsRow(a)
        if (!snapshot.gpsEnabled) LocationOffCard(onOpenLocationSettings)
        SignalMapCard(
            trail = trail.points.toList(),
            trailVersion = trailVersion,
            lat = snapshot.lat,
            lon = snapshot.lon,
            accuracyM = snapshot.accuracyM,
            onClear = { trail.clear(); trailVersion++ },
        )
        RecommendationsCard(a.recommendations)
        SkyPlotCard(snapshot.sats)
        SatelliteListCard(snapshot.sats, showAll) { showAll = !showAll }
        Text(
            "Signal strength is C/N0 in dB-Hz. 35+ is strong, 25 to 35 is usable, under 25 is weak.",
            color = Sky.Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- Building blocks ----------

@Composable
internal fun Panel(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Sky.CardHi, Sky.Card)))
            .border(1.dp, Sky.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp).height(2.dp).background(Sky.Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title.uppercase(), color = Sky.Accent, fontSize = 11.sp, fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = Sky.Muted, fontSize = 13.sp)
                }
            }
        }
        content()
    }
}

@Composable
private fun Header(a: Assessment) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(20.sp)
        Spacer(Modifier.weight(1f))
        val c = severityColor(a.statusSeverity)
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(c.copy(alpha = 0.10f))
                .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(
                a.status.uppercase(), color = c, fontSize = 11.sp, fontFamily = Mono,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ScoreCard(a: Assessment) {
    val color = levelColor(a.level)
    val sweep by animateFloatAsState(targetValue = a.score / 100f, label = "score")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        Sky.Line, -90f, 360f, false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
                    )
                    if (sweep > 0f) {
                        drawArc(
                            color.copy(alpha = 0.25f), -90f, 360f * sweep, false,
                            topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(stroke * 2.2f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color, -90f, 360f * sweep, false,
                            topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${a.score}", color = Sky.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Text("SIGNAL", color = Sky.Muted, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.level.label.uppercase(), color = color, fontSize = 13.sp, fontFamily = Mono,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(a.headline, color = Sky.Text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StatsRow(a: Assessment) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Heard", a.tracked.toString(), "satellites", Modifier.weight(1f))
        StatTile("In fix", a.used.toString(), "satellites", Modifier.weight(1f))
        StatTile("Signal", a.top4?.roundToInt()?.toString() ?: "–", "dB-Hz", Modifier.weight(1f))
        StatTile("Accuracy", a.accuracy?.let { "±${it.roundToInt()}" } ?: "–", "meters", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Sky.Card)
            .border(1.dp, Sky.Line, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), color = Sky.Muted, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 1.sp, maxLines = 1)
        Text(value, color = Sky.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1)
        Text(unit.uppercase(), color = Sky.Muted, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun LocationOffCard(onOpenLocationSettings: () -> Unit) {
    Panel {
        Text("Location is off", color = Sky.Bad, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Turn it on so the phone can listen to satellites.", color = Sky.Muted, fontSize = 13.sp)
        Button(
            onClick = onOpenLocationSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Sky.Accent, contentColor = Sky.Bg),
        ) { Text("Open location settings") }
    }
}

@Composable
private fun RecommendationsCard(recs: List<Rec>) {
    Panel(title = "What to do") {
        recs.forEach { r ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(severityColor(r.severity))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(r.title, color = Sky.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.detail, color = Sky.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SkyPlotCard(sats: List<Sat>) {
    val measurer = rememberTextMeasurer()
    Panel(
        title = "Sky view",
        subtitle = "Top is north. The center is straight overhead, the edge is the horizon.",
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val margin = 18.dp.toPx()
            val r = size.minDimension / 2f - margin
            val c = center
            val thin = 1.dp.toPx()

            listOf(1f, 2f / 3f, 1f / 3f).forEach { f ->
                drawCircle(Sky.Line, radius = r * f, center = c, style = Stroke(thin))
            }
            drawLine(Sky.Line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = thin)
            drawLine(Sky.Line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = thin)

            val labelStyle = TextStyle(color = Sky.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            listOf(
                "N" to Offset(0f, -1f),
                "E" to Offset(1f, 0f),
                "S" to Offset(0f, 1f),
                "W" to Offset(-1f, 0f),
            ).forEach { (label, dir) ->
                val layout = measurer.measure(label, labelStyle)
                val reach = r + margin / 2f
                val p = Offset(c.x + dir.x * reach, c.y + dir.y * reach)
                drawText(
                    layout,
                    topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f),
                )
            }

            val dot = 7.dp.toPx()
            sats.filter { it.elevation > 0f }.sortedBy { it.cn0 }.forEach { s ->
                val d = r * (90f - s.elevation.coerceIn(0f, 90f)) / 90f
                val rad = Math.toRadians(s.azimuth.toDouble())
                val p = Offset(c.x + d * sin(rad).toFloat(), c.y - d * cos(rad).toFloat())
                val col = cn0Color(s.cn0)
                if (s.usedInFix) {
                    drawCircle(col, radius = dot, center = p)
                } else {
                    drawCircle(col, radius = dot - thin, center = p, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendItem(Sky.Good, "Strong")
            LegendItem(Sky.Fair, "Usable")
            LegendItem(Sky.Bad, "Weak")
            LegendItem(Sky.Off, "Not heard")
        }
        Text(
            "Filled dot: used for your position. Ring: heard but not used.",
            color = Sky.Muted, fontSize = 12.sp,
        )
    }
}

@Composable
internal fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Sky.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SatelliteListCard(sats: List<Sat>, showAll: Boolean, onToggle: () -> Unit) {
    val heard = sats.filter { it.cn0 > 0f }.sortedByDescending { it.cn0 }
    val shown = if (showAll) heard else heard.take(10)
    Panel(
        title = "Satellites",
        subtitle = "${heard.size} heard · ${sats.count { it.usedInFix }} used",
    ) {
        if (heard.isEmpty()) Text("None heard yet.", color = Sky.Muted, fontSize = 13.sp)
        shown.forEach { SatRow(it) }
        if (heard.size > 10) {
            TextButton(onClick = onToggle) {
                Text(if (showAll) "Show fewer" else "Show all ${heard.size}", color = Sky.Accent)
            }
        }
    }
}

@Composable
private fun SatRow(s: Sat) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${constellationShort(s.constellation)} ${s.svid}",
            color = Sky.Text, fontSize = 13.sp, modifier = Modifier.width(72.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Sky.CardHi)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((s.cn0 / 50f).coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(cn0Color(s.cn0))
            )
        }
        Text(
            "${s.cn0.roundToInt()}", color = Sky.Text, fontSize = 13.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
        )
        Text(
            if (s.usedInFix) "✓" else "", color = Sky.Good, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(20.dp),
        )
    }
}

@Composable
private fun Logo(size: androidx.compose.ui.unit.TextUnit) {
    Row {
        Text("SKY", color = Sky.Text, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text("SCOPE", color = Sky.Accent, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}
