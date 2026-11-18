package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CsuOutlet
import com.example.data.model.CsuPriority
import com.example.data.model.DailyShift
import com.example.data.model.Planogram
import com.example.data.model.VisitReport
import com.example.ui.components.GeotagMapCanvas
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.util.ImageUtils
import com.example.util.LocationFix
import com.example.util.LocationTracker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GeotagMapScreen(
    shift: DailyShift?,
    canReportVisit: Boolean,
    csuOutlets: List<CsuOutlet>,
    visitReports: List<VisitReport>,
    latestPlanogram: Planogram?,
    isGpsStreaming: Boolean,
    onCaptureSingleFix: ((LocationFix?) -> Unit) -> Unit,
    onSubmitVisitReport: (
        shift: DailyShift,
        csuCode: String,
        storeName: String,
        storeAddress: String,
        orderDone: Boolean,
        orderItemsPicked: String,
        orderPhotoBase64: String,
        planogramDone: Boolean,
        planogramVersionRef: String,
        planogramPhotoBase64: String,
        promoDone: Boolean,
        promoCycleName: String,
        posmPhotoBase64: String,
        latitude: Double,
        longitude: Double,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedOutlet by remember { mutableStateOf<CsuOutlet?>(null) }
    var subTab by remember { mutableStateOf("CSU") }
    var showVisitDialogFor by remember { mutableStateOf<CsuOutlet?>(null) }
    var showCustomVisitDialog by remember { mutableStateOf(false) }

    val activeOutlets = remember(csuOutlets) { csuOutlets.filter { it.active } }
    val currentLat = shift?.latitude ?: -6.2088
    val currentLng = shift?.longitude ?: 106.8456

    val visitedCodes = remember(visitReports) {
        visitReports.map { it.csuCode.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    }
    val visitedNames = remember(visitReports) {
        visitReports.map { it.storeName.trim().lowercase() }.toSet()
    }
    val visitedCsuCount = remember(activeOutlets, visitedCodes, visitedNames) {
        activeOutlets.count { csu ->
            visitedCodes.contains(csu.csuCode.trim().uppercase()) ||
                visitedNames.contains(csu.storeName.trim().lowercase())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Peta Geotag CSU & Kunjungan", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "$visitedCsuCount / ${activeOutlets.size} CSU aktif dikunjungi • ${visitReports.size} laporan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        text = if (isGpsStreaming) "GPS Live" else "GPS Siaga",
                        containerColor = if (isGpsStreaming) StatusSuccessBg else StatusInfoBg,
                        contentColor = if (isGpsStreaming) StatusSuccess else StatusInfo
                    )
                }
            }

            item {
                GeotagMapCanvas(
                    outlets = csuOutlets,
                    visits = visitReports,
                    currentLat = currentLat,
                    currentLng = currentLng,
                    trackingActive = isGpsStreaming || (shift?.trackingActive == true),
                    selectedOutletId = selectedOutlet?.id,
                    onSelectOutlet = { selectedOutlet = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .testTag("geotag_map_canvas")
                )
            }

            selectedOutlet?.let { csu ->
                val dist = LocationTracker.distanceMeters(currentLat, currentLng, csu.latitude, csu.longitude)
                val isVisited = visitedCodes.contains(csu.csuCode.trim().uppercase()) ||
                    visitedNames.contains(csu.storeName.trim().lowercase())
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${csu.csuCode} • ${csu.storeName}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${csu.address.ifBlank { "Alamat tidak dicantumkan" }} • Jarak ${LocationTracker.formatDistance(dist)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Geotag: ${LocationTracker.formatCoords(csu.latitude, csu.longitude)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                StatusPill(
                                    text = if (isVisited) "Sudah Dikunjungi" else CsuPriority.fromRaw(csu.priority).label,
                                    containerColor = if (isVisited) StatusSuccessBg else StatusWarningBg,
                                    contentColor = if (isVisited) StatusSuccess else StatusWarning
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        LocationTracker.openInGoogleMaps(context, csu.latitude, csu.longitude, csu.storeName)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Arahkan Maps")
                                }
                                if (canReportVisit) {
                                    Button(
                                        onClick = { showVisitDialogFor = csu },
                                        enabled = shift != null,
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Lapor & 3 Foto")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = subTab == "CSU",
                        onClick = { subTab = "CSU" },
                        label = { Text("CSU Wajib (${activeOutlets.size})") },
                        modifier = Modifier.testTag("tab_csu_list")
                    )
                    FilterChip(
                        selected = subTab == "VISITS",
                        onClick = { subTab = "VISITS" },
                        label = { Text("Laporan Kunjungan (${visitReports.size})") },
                        modifier = Modifier.testTag("tab_visit_reports")
                    )
                }
            }

            if (subTab == "CSU") {
                if (activeOutlets.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text("Belum ada CSU Wajib Kunjung", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Admin dapat mengunggah daftar CSU aktif beserta geotag di tab Admin & Tim.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(activeOutlets, key = { it.id }) { csu ->
                        val dist = LocationTracker.distanceMeters(currentLat, currentLng, csu.latitude, csu.longitude)
                        val isVisited = visitedCodes.contains(csu.csuCode.trim().uppercase()) ||
                            visitedNames.contains(csu.storeName.trim().lowercase())
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOutlet = csu }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(csu.csuCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        if (csu.assignedAssistantName.isNotBlank()) {
                                            Text("• ${csu.assignedAssistantName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(csu.storeName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${csu.address.ifBlank { "Geotag tersimpan" }} • ${LocationTracker.formatDistance(dist)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        text = if (isVisited) "Selesai" else CsuPriority.fromRaw(csu.priority).label,
                                        containerColor = if (isVisited) StatusSuccessBg else StatusWarningBg,
                                        contentColor = if (isVisited) StatusSuccess else StatusWarning
                                    )
                                    if (canReportVisit) {
                                        TextButton(
                                            onClick = { showVisitDialogFor = csu },
                                            enabled = shift != null,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Check-In")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (visitReports.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("Belum ada laporan kunjungan hari ini", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Ketuk tombol + di kanan bawah untuk melapor kunjungan toko + 3 foto tugas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(visitReports, key = { it.id }) { report ->
                        VisitReportCard(report)
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        if (canReportVisit && shift != null) {
            FloatingActionButton(
                onClick = { showCustomVisitDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_visit_fab")
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = "Lapor kunjungan baru")
            }
        }
    }

    val dialogShift = shift
    if (dialogShift != null && (showVisitDialogFor != null || showCustomVisitDialog)) {
        VisitReportDialog(
            shift = dialogShift,
            prefillCsu = showVisitDialogFor,
            latestPlanogram = latestPlanogram,
            onCaptureFix = onCaptureSingleFix,
            onDismiss = {
                showVisitDialogFor = null
                showCustomVisitDialog = false
            },
            onSubmit = { csuCode, store, addr, orderDone, orderPicked, orderPhoto, planoDone, planoVer, planoPhoto, promoDone, promoCycle, posmPhoto, lat, lng, notes ->
                onSubmitVisitReport(
                    dialogShift,
                    csuCode,
                    store,
                    addr,
                    orderDone,
                    orderPicked,
                    orderPhoto,
                    planoDone,
                    planoVer,
                    planoPhoto,
                    promoDone,
                    promoCycle,
                    posmPhoto,
                    lat,
                    lng,
                    notes
                )
                showVisitDialogFor = null
                showCustomVisitDialog = false
            }
        )
    }
}

@Composable
private fun VisitReportCard(report: VisitReport) {
    val timeText = remember(report.createdAt) {
        report.createdAt?.toDate()?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
        } ?: "--:--"
    }
    val orderBitmap = remember(report.orderPhotoBase64) {
        ImageUtils.decodeBase64ToImageBitmap(report.orderPhotoBase64)
    }
    val planogramBitmap = remember(report.planogramPhotoBase64) {
        ImageUtils.decodeBase64ToImageBitmap(report.planogramPhotoBase64)
    }
    val posmBitmap = remember(report.posmPhotoBase64) {
        ImageUtils.decodeBase64ToImageBitmap(report.posmPhotoBase64)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (report.csuCode.isNotBlank()) "${report.csuCode} • ${report.storeName}" else report.storeName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${report.storeAddress.ifBlank { "Toko Pelanggan" }} • $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (report.hasAllMandatoryPhotos()) "3 Foto Lengkap" else "Tercatat",
                    containerColor = StatusSuccessBg,
                    contentColor = StatusSuccess
                )
            }
            if (report.orderItemsPicked.isNotBlank()) {
                Text(
                    text = "Order Diambil: ${report.orderItemsPicked}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.orderDone) {
                    StatusPill("Ambil Order", StatusSuccessBg, StatusSuccess)
                }
                if (report.planogramDone) {
                    StatusPill(
                        text = "Planogram ${report.planogramVersionRef.ifBlank { "OK" }}",
                        containerColor = StatusInfoBg,
                        contentColor = StatusInfo
                    )
                }
                if (report.promoDone) {
                    StatusPill(
                        text = "POSM: ${report.promoCycleName.ifBlank { "Aktif" }}",
                        containerColor = StatusWarningBg,
                        contentColor = StatusWarning
                    )
                }
            }
            Text(
                text = "GPS: ${LocationTracker.formatCoords(report.latitude, report.longitude)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (report.notes.isNotBlank()) {
                Text(text = report.notes, style = MaterialTheme.typography.bodySmall)
            }

            if (orderBitmap != null || planogramBitmap != null || posmBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VisitPhotoThumb("Ambil Order", orderBitmap, Modifier.weight(1f))
                    VisitPhotoThumb("Planogram", planogramBitmap, Modifier.weight(1f))
                    VisitPhotoThumb("POSM / Promo", posmBitmap, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VisitPhotoThumb(
    label: String,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto $label",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VisitReportDialog(
    shift: DailyShift,
    prefillCsu: CsuOutlet?,
    latestPlanogram: Planogram?,
    onCaptureFix: ((LocationFix?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (
        csuCode: String,
        storeName: String,
        storeAddress: String,
        orderDone: Boolean,
        orderItemsPicked: String,
        orderPhotoBase64: String,
        planogramDone: Boolean,
        planogramVersionRef: String,
        planogramPhotoBase64: String,
        promoDone: Boolean,
        promoCycleName: String,
        posmPhotoBase64: String,
        latitude: Double,
        longitude: Double,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var csuCode by remember { mutableStateOf(prefillCsu?.csuCode ?: "") }
    var storeName by remember { mutableStateOf(prefillCsu?.storeName ?: "") }
    var storeAddress by remember { mutableStateOf(prefillCsu?.address ?: "") }

    var orderDone by remember { mutableStateOf(true) }
    var orderItemsPicked by remember { mutableStateOf("") }
    var orderPhotoBase64 by remember { mutableStateOf<String?>(null) }

    var planogramDone by remember { mutableStateOf(true) }
    var planogramPhotoBase64 by remember { mutableStateOf<String?>(null) }

    var promoDone by remember { mutableStateOf(true) }
    var posmPhotoBase64 by remember { mutableStateOf<String?>(null) }

    var lat by remember { mutableDoubleStateOf(prefillCsu?.latitude ?: shift.latitude) }
    var lng by remember { mutableDoubleStateOf(prefillCsu?.longitude ?: shift.longitude) }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val orderPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeUriToBase64(context, uri, maxDimension = 480, maxBytes = 90_000)
                if (encoded == null) {
                    errorText = "Gagal mengompres foto ambil barang. Coba foto lain."
                } else {
                    orderPhotoBase64 = encoded
                    errorText = null
                }
            }
        }
    }

    val planogramPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeUriToBase64(context, uri, maxDimension = 480, maxBytes = 90_000)
                if (encoded == null) {
                    errorText = "Gagal mengompres foto tata planogram. Coba foto lain."
                } else {
                    planogramPhotoBase64 = encoded
                    errorText = null
                }
            }
        }
    }

    val posmPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeUriToBase64(context, uri, maxDimension = 480, maxBytes = 90_000)
                if (encoded == null) {
                    errorText = "Gagal mengompres foto POSM. Coba foto lain."
                } else {
                    posmPhotoBase64 = encoded
                    errorText = null
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Laporan Kunjungan Toko", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Wajib unggah 3 foto tugas di toko ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nama Toko / Pelanggan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visit_store_name_input")
                )
                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    label = { Text("Alamat Toko") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Task 1: Ambil Barang Order Sales + Wajib Foto
                StoreTaskPhotoBox(
                    icon = Icons.Default.LocalShipping,
                    title = "1. Ambil Barang Order Sales di Kendaraan",
                    checked = orderDone,
                    onCheckedChange = { orderDone = it },
                    photoBase64 = orderPhotoBase64,
                    buttonLabel = "Upload Foto Ambil Barang (Wajib)",
                    testTag = "upload_order_photo_button",
                    onPickPhoto = {
                        orderPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    OutlinedTextField(
                        value = orderItemsPicked,
                        onValueChange = { orderItemsPicked = it },
                        label = { Text("Detail Barang Order yang Diambil") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Task 2: Tata Planogram Terbaru + Wajib Foto
                StoreTaskPhotoBox(
                    icon = Icons.Default.ViewQuilt,
                    title = "2. Tata Planogram (${latestPlanogram?.versionCode ?: "Terbaru"})",
                    checked = planogramDone,
                    onCheckedChange = { planogramDone = it },
                    photoBase64 = planogramPhotoBase64,
                    buttonLabel = "Upload Foto Planogram Rak (Wajib)",
                    testTag = "upload_planogram_photo_button",
                    onPickPhoto = {
                        planogramPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // Task 3: Pasang POSM / Promosi In-Store + Wajib Foto
                StoreTaskPhotoBox(
                    icon = Icons.Default.Campaign,
                    title = "3. Pasang POSM (${latestPlanogram?.cycleName ?: shift.activeCycleName})",
                    checked = promoDone,
                    onCheckedChange = { promoDone = it },
                    photoBase64 = posmPhotoBase64,
                    buttonLabel = "Upload Foto POSM / Promo (Wajib)",
                    testTag = "upload_posm_photo_button",
                    onPickPhoto = {
                        posmPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS: ${LocationTracker.formatCoords(lat, lng)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedButton(onClick = {
                        onCaptureFix { fix ->
                            if (fix != null) {
                                lat = fix.latitude
                                lng = fix.longitude
                            }
                        }
                    }) {
                        Text("Ambil GPS")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Kunjungan") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            if (storeName.trim().length < 2) {
                                errorText = "Nama toko minimal 2 karakter."
                                return@Button
                            }
                            val orderPhoto = orderPhotoBase64
                            val planoPhoto = planogramPhotoBase64
                            val posmPhoto = posmPhotoBase64
                            if (orderPhoto.isNullOrBlank() || planoPhoto.isNullOrBlank() || posmPhoto.isNullOrBlank()) {
                                errorText = "Ketiga foto (Ambil Barang, Tata Planogram, dan POSM) wajib diunggah."
                                return@Button
                            }
                            onSubmit(
                                csuCode,
                                storeName,
                                storeAddress,
                                orderDone,
                                orderItemsPicked,
                                orderPhoto,
                                planogramDone,
                                latestPlanogram?.versionCode ?: "Standard",
                                planoPhoto,
                                promoDone,
                                latestPlanogram?.cycleName ?: shift.activeCycleName,
                                posmPhoto,
                                lat,
                                lng,
                                notes
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_visit_button")
                    ) {
                        Text("Simpan Laporan")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTaskPhotoBox(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    photoBase64: String?,
    buttonLabel: String,
    testTag: String,
    onPickPhoto: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val previewBitmap = remember(photoBase64) {
        if (!photoBase64.isNullOrBlank()) ImageUtils.decodeBase64ToImageBitmap(photoBase64) else null
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (!photoBase64.isNullOrBlank()) StatusSuccessBg.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckedChange(!checked) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!photoBase64.isNullOrBlank()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Foto terunggah", tint = StatusSuccess, modifier = Modifier.size(18.dp))
                }
            }
            if (extraContent != null) {
                extraContent()
            }
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Preview $title",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            OutlinedButton(
                onClick = onPickPhoto,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (photoBase64 != null) "Ganti Foto (Tersimpan ✓)" else buttonLabel)
            }
        }
    }
}
