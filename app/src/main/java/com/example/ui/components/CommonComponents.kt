package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CsuOutlet
import com.example.data.model.CsuPriority
import com.example.data.model.VisitReport
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Interactive Geotag Map rendered directly on Compose Canvas without external API key requirements.
 * Plots Admin-uploaded CSU outlets, live Assistant GPS position, visited check-in pins, and route links.
 */
@Composable
fun GeotagMapCanvas(
    outlets: List<CsuOutlet>,
    visits: List<VisitReport>,
    currentLat: Double,
    currentLng: Double,
    trackingActive: Boolean,
    selectedOutletId: String?,
    onSelectOutlet: (CsuOutlet) -> Unit,
    onMapCoordinateTap: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val visitedCodes = remember(visits) {
        visits.map { it.csuCode.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    }
    val visitedNames = remember(visits) {
        visits.map { it.storeName.trim().lowercase() }.toSet()
    }

    // Compute bounding box center & span across all points
    val allLats = remember(outlets, visits, currentLat) {
        buildList {
            add(currentLat)
            outlets.forEach { add(it.latitude) }
            visits.forEach { add(it.latitude) }
        }.filter { it != 0.0 }
    }
    val allLngs = remember(outlets, visits, currentLng) {
        buildList {
            add(currentLng)
            outlets.forEach { add(it.longitude) }
            visits.forEach { add(it.longitude) }
        }.filter { it != 0.0 }
    }

    val centerLat = if (allLats.isEmpty()) -6.2088 else (allLats.min() + allLats.max()) / 2.0
    val centerLng = if (allLngs.isEmpty()) 106.8456 else (allLngs.min() + allLngs.max()) / 2.0
    val latSpan = max(0.025, ((allLats.maxOrNull() ?: centerLat) - (allLats.minOrNull() ?: centerLat)) * 1.45)
    val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
    val lngSpan = max(0.025 / cosLat, ((allLngs.maxOrNull() ?: centerLng) - (allLngs.minOrNull() ?: centerLng)) * 1.45)

    val mapBg = Color(0xFFEAF2EF)
    val gridColor = Color(0xFFC8DCD4)
    val routeLineColor = Color(0xFF0A5A4D).copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(mapBg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.6f, 5f)
                        panOffset += pan
                    }
                }
                .pointerInput(outlets, centerLat, centerLng, latSpan, lngSpan, zoomScale, panOffset) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        fun project(lat: Double, lng: Double): Offset {
                            val nx = ((lng - centerLng) / lngSpan).toFloat()
                            val ny = (-(lat - centerLat) / latSpan).toFloat()
                            val baseScale = min(w, h) * 0.78f * zoomScale
                            return Offset(
                                x = w / 2f + nx * baseScale + panOffset.x,
                                y = h / 2f + ny * baseScale + panOffset.y
                            )
                        }

                        var nearest: CsuOutlet? = null
                        var nearestDist = 48f
                        for (csu in outlets) {
                            val p = project(csu.latitude, csu.longitude)
                            val d = hypot(tapOffset.x - p.x, tapOffset.y - p.y)
                            if (d < nearestDist) {
                                nearestDist = d
                                nearest = csu
                            }
                        }
                        if (nearest != null) {
                            onSelectOutlet(nearest)
                        } else if (onMapCoordinateTap != null) {
                            val baseScale = min(w, h) * 0.78f * zoomScale
                            val nx = (tapOffset.x - w / 2f - panOffset.x) / baseScale
                            val ny = (tapOffset.y - h / 2f - panOffset.y) / baseScale
                            val tappedLng = (centerLng + nx * lngSpan).coerceIn(-180.0, 180.0)
                            val tappedLat = (centerLat - ny * latSpan).coerceIn(-90.0, 90.0)
                            onMapCoordinateTap(tappedLat, tappedLng)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val baseScale = min(w, h) * 0.78f * zoomScale

            fun project(lat: Double, lng: Double): Offset {
                val nx = ((lng - centerLng) / lngSpan).toFloat()
                val ny = (-(lat - centerLat) / latSpan).toFloat()
                return Offset(
                    x = w / 2f + nx * baseScale + panOffset.x,
                    y = h / 2f + ny * baseScale + panOffset.y
                )
            }

            // Draw tactical coordinate grid
            val step = 48.dp.toPx()
            var gx = (panOffset.x % step)
            while (gx < w) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                gx += step
            }
            var gy = (panOffset.y % step)
            while (gy < h) {
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                gy += step
            }

            // Draw distance reference rings centered on current GPS
            val gpsPos = project(currentLat, currentLng)
            drawCircle(
                color = StatusInfo.copy(alpha = 0.08f),
                radius = 70.dp.toPx() * zoomScale.coerceAtMost(2f),
                center = gpsPos
            )
            drawCircle(
                color = StatusInfo.copy(alpha = 0.28f),
                radius = 70.dp.toPx() * zoomScale.coerceAtMost(2f),
                center = gpsPos,
                style = Stroke(width = 1.5f)
            )

            // Draw dashed-like route lines connecting active outlets in sequence from GPS
            val activeList = outlets.filter { it.active }
            if (activeList.isNotEmpty()) {
                val routePath = Path().apply {
                    moveTo(gpsPos.x, gpsPos.y)
                    activeList.forEach { csu ->
                        val p = project(csu.latitude, csu.longitude)
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = routePath,
                    color = routeLineColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw visit report pins (green diamond/dot if not already matched to CSU)
            visits.forEach { visit ->
                if (visit.latitude != 0.0 || visit.longitude != 0.0) {
                    val vp = project(visit.latitude, visit.longitude)
                    drawCircle(
                        color = StatusSuccess.copy(alpha = 0.25f),
                        radius = 12.dp.toPx(),
                        center = vp
                    )
                }
            }

            // Draw CSU pins
            outlets.forEach { csu ->
                val pt = project(csu.latitude, csu.longitude)
                val isVisited = visitedCodes.contains(csu.csuCode.trim().uppercase()) ||
                    visitedNames.contains(csu.storeName.trim().lowercase())
                val isSelected = csu.id == selectedOutletId

                val pinColor = when {
                    !csu.active -> Color(0xFF94A3B8)
                    isVisited -> StatusSuccess
                    csu.priority == CsuPriority.WAJIB.name -> AmberAccent
                    else -> EmeraldPrimary
                }

                if (isSelected) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.28f),
                        radius = 22.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 13.dp.toPx() else 10.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = pinColor,
                    radius = if (isSelected) 10.dp.toPx() else 7.5.dp.toPx(),
                    center = pt
                )

                val labelLayout = textMeasurer.measure(
                    text = csu.csuCode,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10201D)
                    )
                )
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(
                        x = pt.x - labelLayout.size.width / 2f,
                        y = pt.y + 12.dp.toPx()
                    )
                )
            }

            // Draw Assistant live GPS marker on top
            if (trackingActive) {
                drawCircle(
                    color = StatusInfo.copy(alpha = 0.22f),
                    radius = 20.dp.toPx(),
                    center = gpsPos
                )
            }
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = gpsPos
            )
            drawCircle(
                color = StatusInfo,
                radius = 7.dp.toPx(),
                center = gpsPos
            )
        }

        // Map controls overlay (top-right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                IconButton(
                    onClick = { zoomScale = (zoomScale * 1.35f).coerceAtMost(5f) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Perbesar peta", modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                IconButton(
                    onClick = { zoomScale = (zoomScale / 1.35f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Perkecil peta", modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                IconButton(
                    onClick = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Pusatkan peta", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Map legend overlay (top-left)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(StatusInfo, "GPS Anda")
                LegendDot(AmberAccent, "CSU Wajib")
                LegendDot(StatusSuccess, "Dikunjungi")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}

/**
 * Renders serialized stroke path string "M x,y L x,y;..." into a Compose Canvas.
 */
@Composable
fun SignaturePreviewCanvas(
    serializedPath: String,
    modifier: Modifier = Modifier
) {
    val strokeColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val strokes = serializedPath.split(";").filter { it.isNotBlank() }
        if (strokes.isEmpty()) return@Canvas

        // Parse normalized (0..1) coordinates
        for (stroke in strokes) {
            val tokens = stroke.trim().split(" ")
            val path = Path()
            var moved = false
            var i = 0
            while (i < tokens.size) {
                val cmd = tokens[i]
                if ((cmd == "M" || cmd == "L") && i + 1 < tokens.size) {
                    val coords = tokens[i + 1].split(",")
                    if (coords.size == 2) {
                        val nx = coords[0].toFloatOrNull() ?: 0f
                        val ny = coords[1].toFloatOrNull() ?: 0f
                        val px = nx * size.width
                        val py = ny * size.height
                        if (cmd == "M" || !moved) {
                            path.moveTo(px, py)
                            moved = true
                        } else {
                            path.lineTo(px, py)
                        }
                    }
                    i += 2
                } else {
                    i++
                }
            }
            if (moved) {
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

/**
 * End-of-day Digital Sign-Off Dialog for the accompanied Sales Representative.
 */
@Composable
fun SignaturePadDialog(
    defaultSignerName: String,
    onDismiss: () -> Unit,
    onConfirmSignOff: (signerName: String, note: String, serializedSignature: String) -> Unit
) {
    var signerName by remember { mutableStateOf(defaultSignerName) }
    var note by remember { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val activeStroke = remember { mutableStateListOf<Offset>() }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Sign-Off Salesman",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup dialog")
                    }
                }

                Text(
                    text = "Pengesahan akhir hari oleh Sales yang didampingi. Setelah disahkan, laporan shift hari ini terkunci secara permanen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = signerName,
                    onValueChange = { signerName = it },
                    label = { Text("Nama Sales yang Mendampingi") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signoff_signer_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan Evaluasi / Verifikasi (Opsional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Area Tanda Tangan Digital",
                            style = MaterialTheme.typography.labelLarge
                        )
                        OutlinedButton(
                            onClick = {
                                strokes.clear()
                                activeStroke.clear()
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus")
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    val inkColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        if (strokes.isEmpty() && activeStroke.isEmpty()) {
                            Text(
                                text = "Goreskan tanda tangan di sini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("signature_canvas")
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            activeStroke.clear()
                                            activeStroke.add(start)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStroke.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (activeStroke.isNotEmpty()) {
                                                strokes.add(activeStroke.toList())
                                                activeStroke.clear()
                                            }
                                        }
                                    )
                                }
                        ) {
                            canvasWidth = size.width.coerceAtLeast(1f)
                            canvasHeight = size.height.coerceAtLeast(1f)

                            fun drawPoints(pts: List<Offset>) {
                                if (pts.size == 1) {
                                    drawCircle(inkColor, radius = 3.dp.toPx(), center = pts[0])
                                    return
                                }
                                val path = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor,
                                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

                            strokes.forEach { drawPoints(it) }
                            if (activeStroke.isNotEmpty()) drawPoints(activeStroke)
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (signerName.trim().length < 2) {
                                validationError = "Nama Sales penandatangan wajib diisi."
                                return@Button
                            }
                            if (strokes.isEmpty()) {
                                validationError = "Silakan goreskan tanda tangan terlebih dahulu."
                                return@Button
                            }
                            val serialized = strokes.joinToString(";") { stroke ->
                                stroke.mapIndexed { idx, pt ->
                                    val nx = (pt.x / canvasWidth).coerceIn(0f, 1f)
                                    val ny = (pt.y / canvasHeight).coerceIn(0f, 1f)
                                    val prefix = if (idx == 0) "M" else "L"
                                    String.format(java.util.Locale.US, "%s %.3f,%.3f", prefix, nx, ny)
                                }.joinToString(" ")
                            }
                            onConfirmSignOff(signerName.trim(), note.trim(), serialized)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_signoff_button")
                    ) {
                        Text("Sahkan Shift")
                    }
                }
            }
        }
    }
}
