package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CsuOutlet
import com.example.data.model.DailyShift
import com.example.data.model.ShiftStatus
import com.example.data.model.VisitReport
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.SignaturePreviewCanvas
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.util.ExcelReportExporter
import com.example.util.ImageUtils
import com.example.util.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MonitoringScreen(
    shifts: List<DailyShift>,
    selectedShift: DailyShift?,
    isAdminView: Boolean,
    canSignOff: Boolean,
    csuOutlets: List<CsuOutlet>,
    visitReports: List<VisitReport>,
    onSubmitSignOff: (shift: DailyShift, signerName: String, note: String, signatureSvgPath: String) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeInspectionId by remember(selectedShift?.id) { mutableStateOf(selectedShift?.id) }
    var showSignOffDialogFor by remember { mutableStateOf<DailyShift?>(null) }
    var zoomedPhotoTitle by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }

    val availableMonths = remember(shifts, visitReports) {
        val shiftById = shifts.associateBy { it.id }
        val set = linkedSetOf(ExcelReportExporter.currentMonthKey())
        shifts.forEach { set += ExcelReportExporter.shiftMonthKey(it) }
        visitReports.forEach { set += ExcelReportExporter.visitMonthKey(it, shiftById) }
        set.sortedDescending()
    }
    var selectedMonth by remember(availableMonths) {
        mutableStateOf<String?>(ExcelReportExporter.currentMonthKey())
    }

    val excelSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildExcelWorkbookBytes(selectedMonth, shifts, visitReports)
                }
                ExcelReportExporter.writeBytesToUri(context, uri, bytes)
                    .onSuccess { onNotify("Laporan Excel (.xlsx) berhasil disimpan") }
                    .onFailure { onNotify("Gagal menyimpan Excel: ${it.localizedMessage}") }
            }
        }
    }

    val zipSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildExcelAndPhotosZipBytes(selectedMonth, shifts, visitReports)
                }
                ExcelReportExporter.writeBytesToUri(context, uri, bytes)
                    .onSuccess { onNotify("Paket Excel + Semua Foto (.zip) berhasil disimpan") }
                    .onFailure { onNotify("Gagal menyimpan paket ZIP: ${it.localizedMessage}") }
            }
        }
    }

    val inspectedShift = remember(shifts, activeInspectionId, selectedShift) {
        shifts.firstOrNull { it.id == activeInspectionId } ?: selectedShift ?: shifts.firstOrNull()
    }

    val monthlySummaries = remember(selectedMonth, shifts, visitReports) {
        ExcelReportExporter.buildMonthlySummaries(selectedMonth, shifts, visitReports)
    }
    val monthlyVisits = remember(selectedMonth, shifts, visitReports) {
        val shiftById = shifts.associateBy { it.id }
        if (selectedMonth == null) visitReports
        else visitReports.filter { ExcelReportExporter.visitMonthKey(it, shiftById) == selectedMonth }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isAdminView) "Monitoring, Rekap Bulanan & Ekspor" else "Monitoring Aktivitas & Sign-Off",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (isAdminView)
                        "Audit foto bukti, outlet tereksekusi, & unduh Excel review bulanan Task Asistant"
                    else
                        "Kontrol real-time Salesman yang didampingi & Supervisor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ADMIN BACK-END SECTION: Monthly Excel + Photo Export & Driver Review
        if (isAdminView) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.TableView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Rekap Bulanan & Ekspor Excel Admin", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Outlet dieksekusi + rekaman foto untuk review bulanan Task Asistant",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill("3 Sheet .xlsx", StatusSuccessBg, StatusSuccess)
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedMonth == null,
                                    onClick = { selectedMonth = null },
                                    label = { Text("Semua Bulan") }
                                )
                            }
                            items(availableMonths) { monthKey ->
                                FilterChip(
                                    selected = selectedMonth == monthKey,
                                    onClick = { selectedMonth = monthKey },
                                    label = { Text(monthKey) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricBox(
                                title = "Task Asistant Aktif",
                                value = "${monthlySummaries.size}",
                                subtitle = "Periode ${selectedMonth ?: "Semua"}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                title = "Outlet Dieksekusi",
                                value = "${monthlyVisits.size}",
                                subtitle = "${monthlyVisits.count { it.hasAllMandatoryPhotos() }} foto lengkap",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (monthlySummaries.isEmpty()) {
                            Text(
                                text = "Belum ada rekaman aktivitas Task Asistant pada periode ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            monthlySummaries.forEach { summary ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(summary.assistantName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "${summary.outletsExecuted} outlet • ${summary.completePhotoVisits} lengkap 3 foto • ${summary.signedOffShifts}/${summary.totalShifts} sign-off",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(
                                            text = "${summary.compliancePercent}%",
                                            containerColor = if (summary.compliancePercent >= 80) StatusSuccessBg else StatusWarningBg,
                                            contentColor = if (summary.compliancePercent >= 80) StatusSuccess else StatusWarning
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val tag = selectedMonth ?: "Semua"
                                    excelSaveLauncher.launch("Rekap_Kinerja_Task_Asistant_$tag.xlsx")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_excel_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Excel (.xlsx)")
                            }
                            OutlinedButton(
                                onClick = {
                                    val tag = selectedMonth ?: "Semua"
                                    zipSaveLauncher.launch("Rekap_Excel_Dan_Foto_$tag.zip")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_zip_photos_button")
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Excel + Foto (.zip)")
                            }
                        }
                    }
                }
            }
        }

        if (inspectedShift == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada shift aktif Task Asistant untuk dipantau hari ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            val shiftVisits = visitReports.filter { it.shiftId == inspectedShift.id }
            val activeCsus = csuOutlets.filter { it.active }
            val visitedCodes = shiftVisits.map { it.csuCode.trim().uppercase() }.toSet()
            val visitedNames = shiftVisits.map { it.storeName.trim().lowercase() }.toSet()
            val csuDoneCount = activeCsus.count { csu ->
                visitedCodes.contains(csu.csuCode.trim().uppercase()) ||
                    visitedNames.contains(csu.storeName.trim().lowercase())
            }
            val completePhotoVisitsCount = shiftVisits.count { it.hasAllMandatoryPhotos() }
            val cleanlinessPreview = ImageUtils.decodeBase64ToImageBitmap(inspectedShift.cleanlinessFileBase64)
            val statusEnum = ShiftStatus.fromRaw(inspectedShift.status)
            val updatedAtStr = inspectedShift.updatedAt?.toDate()?.let {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(it)
            } ?: "--:--"

            if (shifts.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shifts, key = { it.id }) { s ->
                            val selected = s.id == inspectedShift.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { activeInspectionId = s.id }
                            ) {
                                Text(
                                    text = "${s.assistantName} (${s.shiftDate})",
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Live GPS Telemetry Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (inspectedShift.trackingActive) StatusSuccessBg else StatusInfoBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        tint = if (inspectedShift.trackingActive) StatusSuccess else StatusInfo
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(inspectedShift.assistantName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${inspectedShift.vehiclePlate} • Update $updatedAtStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill(
                                text = if (inspectedShift.trackingActive) "Live GPS" else "Terakhir",
                                containerColor = if (inspectedShift.trackingActive) StatusSuccessBg else StatusWarningBg,
                                contentColor = if (inspectedShift.trackingActive) StatusSuccess else StatusWarning
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = LocationTracker.formatCoords(inspectedShift.latitude, inspectedShift.longitude),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "Akurasi ±${inspectedShift.accuracyMeters.toInt()} m • ${inspectedShift.locationLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = {
                                    LocationTracker.openInGoogleMaps(
                                        context,
                                        inspectedShift.latitude,
                                        inspectedShift.longitude,
                                        inspectedShift.assistantName
                                    )
                                }) {
                                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Maps")
                                }
                            }
                        }
                    }
                }
            }

            // 2. KPI Metrics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "Cek Armada",
                        value = "${inspectedShift.completedDailyChecksCount()}/3",
                        subtitle = "Stok 2x & bersih",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Kunjungan CSU",
                        value = if (activeCsus.isNotEmpty()) "$csuDoneCount/${activeCsus.size}" else "${shiftVisits.size}",
                        subtitle = "${shiftVisits.size} toko",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Foto 3 Tugas",
                        value = "$completePhotoVisitsCount/${shiftVisits.size}",
                        subtitle = "Order/Plano/POSM",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Detailed Compliance Matrix
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rincian Kepatuhan Task Asistant", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        AuditLine("Check Stock Sebelum Kunjungan", inspectedShift.preStockChecked)
                        AuditLine("Check Stock Sesudah Kunjungan", inspectedShift.postStockChecked)
                        AuditLine("Kebersihan Kendaraan (4 Poin)", inspectedShift.cleanlinessChecked)
                        AuditLine("Upload File Bukti Kebersihan", inspectedShift.cleanlinessFileBase64.isNotBlank())
                        AuditLine(
                            label = "Kunjungan Toko + 3 Foto Tugas",
                            done = shiftVisits.isNotEmpty() && shiftVisits.all { it.hasAllMandatoryPhotos() }
                        )

                        if (inspectedShift.cleanlinessFileBase64.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Bukti Kebersihan: ${inspectedShift.cleanlinessFileName.ifBlank { "Terlampir" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusSuccess
                                    )
                                    if (cleanlinessPreview != null) {
                                        Image(
                                            bitmap = cleanlinessPreview,
                                            contentDescription = "Preview kebersihan armada",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    zoomedPhotoTitle = "Bukti Kebersihan (${inspectedShift.assistantName})" to cleanlinessPreview
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. End-of-Day Salesman Digital Sign-Off
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusEnum == ShiftStatus.SIGNED_OFF)
                            StatusSuccessBg.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (statusEnum == ShiftStatus.SIGNED_OFF) Icons.Default.Verified else Icons.Default.Draw,
                                contentDescription = null,
                                tint = if (statusEnum == ShiftStatus.SIGNED_OFF) StatusSuccess else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Signment Akhir Hari (Salesman yang Didampingi)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (statusEnum == ShiftStatus.SIGNED_OFF) {
                            val signedDateStr = inspectedShift.signedOffAt?.toDate()?.let {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
                            } ?: "Disahkan"
                            Text(
                                text = "Disahkan oleh Salesman: ${inspectedShift.signerName} • $signedDateStr",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatusSuccess
                            )
                            if (inspectedShift.signOffNote.isNotBlank()) {
                                Text(
                                    text = "Catatan Salesman: ${inspectedShift.signOffNote}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(1.dp, StatusSuccess.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            ) {
                                SignaturePreviewCanvas(
                                    serializedPath = inspectedShift.signatureSvgPath,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            }
                        } else if (canSignOff) {
                            Text(
                                text = "Salesman yang didampingi (${inspectedShift.accompaniedSalesName}) memeriksa hasil kerja Task Asistant (check stock 2x, bukti kebersihan kendaraan, dan foto tugas di setiap toko) sebelum menandatangani pengesahan akhir hari.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showSignOffDialogFor = inspectedShift },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("open_signoff_dialog_button")
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tanda Tangani & Sahkan Shift")
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StatusWarningBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Menunggu verifikasi & tanda tangan digital dari Salesman yang didampingi (${inspectedShift.accompaniedSalesName}). Akun Task Asistant tidak dapat menandatangani sendiri.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusWarning,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Visit Logs + Zoomable Photo Evidence
            val displayedVisits = if (isAdminView) monthlyVisits else shiftVisits
            if (displayedVisits.isNotEmpty()) {
                item {
                    Text(
                        text = if (isAdminView)
                            "Rekaman Foto & Outlet Tereksekusi (${displayedVisits.size})"
                        else
                            "Verifikasi Kunjungan & Foto Tugas (${displayedVisits.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(displayedVisits, key = { it.id }) { visit ->
                    val orderBmp = remember(visit.orderPhotoBase64) { ImageUtils.decodeBase64ToImageBitmap(visit.orderPhotoBase64) }
                    val planoBmp = remember(visit.planogramPhotoBase64) { ImageUtils.decodeBase64ToImageBitmap(visit.planogramPhotoBase64) }
                    val posmBmp = remember(visit.posmPhotoBase64) { ImageUtils.decodeBase64ToImageBitmap(visit.posmPhotoBase64) }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (visit.csuCode.isNotBlank()) "${visit.csuCode} • ${visit.storeName}" else visit.storeName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Task Asistant: ${visit.assistantName} • Order: ${visit.orderItemsPicked.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (visit.hasAllMandatoryPhotos()) Icons.Default.CheckCircle else Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = if (visit.hasAllMandatoryPhotos()) StatusSuccess else StatusWarning
                                )
                            }
                            if (orderBmp != null || planoBmp != null || posmBmp != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AuditPhotoTile("Order", orderBmp, Modifier.weight(1f)) {
                                        zoomedPhotoTitle = "${visit.storeName} — Foto Ambil Order" to it
                                    }
                                    AuditPhotoTile("Planogram", planoBmp, Modifier.weight(1f)) {
                                        zoomedPhotoTitle = "${visit.storeName} — Foto Planogram" to it
                                    }
                                    AuditPhotoTile("POSM", posmBmp, Modifier.weight(1f)) {
                                        zoomedPhotoTitle = "${visit.storeName} — Foto POSM" to it
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    showSignOffDialogFor?.let { targetShift ->
        SignaturePadDialog(
            defaultSignerName = targetShift.accompaniedSalesName,
            onDismiss = { showSignOffDialogFor = null },
            onConfirmSignOff = { signer, note, svgPath ->
                onSubmitSignOff(targetShift, signer, note, svgPath)
                showSignOffDialogFor = null
            }
        )
    }

    zoomedPhotoTitle?.let { (title, bmp) ->
        Dialog(
            onDismissRequest = { zoomedPhotoTitle = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { zoomedPhotoTitle = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup foto", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditPhotoTile(
    label: String,
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    onZoom: (ImageBitmap) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto $label",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onZoom(bitmap) },
                contentScale = ContentScale.Crop
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuditLine(label: String, done: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (done) StatusSuccess else StatusWarning,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (done) "Selesai" else "Belum",
                style = MaterialTheme.typography.labelMedium,
                color = if (done) StatusSuccess else StatusWarning,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
