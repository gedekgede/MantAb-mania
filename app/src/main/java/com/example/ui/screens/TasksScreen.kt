package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyShift
import com.example.data.model.Planogram
import com.example.data.model.SalesAssistant
import com.example.data.model.ShiftStatus
import com.example.data.model.VisitReport
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.util.ImageUtils
import com.example.util.LocationTracker
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(
    shift: DailyShift?,
    latestPlanogram: Planogram?,
    visitReports: List<VisitReport>,
    salesAssistants: List<SalesAssistant>,
    isGpsStreaming: Boolean,
    onToggleGps: () -> Unit,
    onOpenPlanogramTab: () -> Unit,
    onOpenMapTab: () -> Unit,
    onUpdateShiftFields: (DailyShift, Map<String, Any>, String?) -> Unit,
    onRequestSignOff: (DailyShift) -> Unit,
    onSelectAssistant: (DailyShift, SalesAssistant) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCleanlinessExpanded by remember { mutableStateOf(true) }

    if (shift == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Menyiapkan shift kerja hari ini...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val isLocked = shift.status == ShiftStatus.SIGNED_OFF.name
    val completedChecks = shift.completedDailyChecksCount()
    val shiftVisits = remember(visitReports, shift.id) {
        visitReports.filter { it.shiftId == shift.id }
    }
    val cleanlinessPreviewBitmap = remember(shift.cleanlinessFileBase64) {
        ImageUtils.decodeBase64ToImageBitmap(shift.cleanlinessFileBase64)
    }

    val cleanlinessFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeFileUriToBase64(context, uri, maxRawBytes = 180_000)
                if (encoded == null) {
                    onNotify("Gagal memproses file. Pastikan ukuran file/foto maksimal ~180KB.")
                } else {
                    val (fileName, base64) = encoded
                    onUpdateShiftFields(
                        shift,
                        mapOf(
                            "cleanlinessFileName" to fileName,
                            "cleanlinessFileBase64" to base64
                        ),
                        "File bukti kebersihan \"$fileName\" berhasil diunggah"
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Shift & Real-time GPS Status Hero Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TASK ASISTANT • ${shift.shiftDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = shift.assistantName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Salesman: ${shift.accompaniedSalesName} • ${shift.vehiclePlate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                            val statusEnum = ShiftStatus.fromRaw(shift.status)
                            StatusPill(
                                text = statusEnum.label,
                                containerColor = when (statusEnum) {
                                    ShiftStatus.SIGNED_OFF -> StatusSuccessBg
                                    ShiftStatus.PENDING_SIGNOFF -> StatusWarningBg
                                    ShiftStatus.ACTIVE -> Color.White.copy(alpha = 0.2f)
                                },
                                contentColor = when (statusEnum) {
                                    ShiftStatus.SIGNED_OFF -> StatusSuccess
                                    ShiftStatus.PENDING_SIGNOFF -> StatusWarning
                                    ShiftStatus.ACTIVE -> Color.White
                                }
                            )
                        }

                        // Real-time GPS Tracker Row
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isGpsStreaming || shift.trackingActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                        contentDescription = "Status pelacakan GPS",
                                        tint = if (isGpsStreaming || shift.trackingActive) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isGpsStreaming || shift.trackingActive) "Pelacakan Lokasi Real-Time Aktif" else "Pelacakan Lokasi Nonaktif",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${LocationTracker.formatCoords(shift.latitude, shift.longitude)} (±${shift.accuracyMeters.toInt()}m)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.82f)
                                        )
                                    }
                                }
                                Button(
                                    onClick = onToggleGps,
                                    enabled = !isLocked,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isGpsStreaming) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("toggle_gps_button")
                                ) {
                                    Text(
                                        text = if (isGpsStreaming) "Jeda GPS" else "Aktifkan",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Quick selector from Admin-uploaded roster
                        val activeRoster = remember(salesAssistants) { salesAssistants.filter { it.active } }
                        if (activeRoster.isNotEmpty() && !isLocked) {
                            var expandedRoster by remember { mutableStateOf(false) }
                            Column {
                                TextButton(
                                    onClick = { expandedRoster = !expandedRoster },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (expandedRoster) "Tutup Daftar Task Asistant (${activeRoster.size})" else "Pilih Nama Task Asistant dari Daftar Admin (${activeRoster.size})",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                AnimatedVisibility(visible = expandedRoster) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        activeRoster.take(3).forEach { asst ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (asst.fullName == shift.assistantName) Color.White else Color.White.copy(alpha = 0.18f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        onSelectAssistant(shift, asst)
                                                        expandedRoster = false
                                                    }
                                            ) {
                                                Text(
                                                    text = asst.fullName,
                                                    color = if (asst.fullName == shift.assistantName) MaterialTheme.colorScheme.primary else Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Locked seal warning if already signed off
        if (isLocked) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StatusSuccessBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = StatusSuccess)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Shift sudah disahkan oleh Salesman (${shift.signerName}). Data harian terkunci.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2. Latest Planogram Update Banner (synced from Admin)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlanogramTab() }
                    .testTag("latest_planogram_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ViewQuilt, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (latestPlanogram != null)
                                "Planogram Terbaru: ${latestPlanogram.versionCode}"
                            else
                                "Belum Ada Planogram Aktif",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = latestPlanogram?.let { "${it.title} • ${it.cycleName}" }
                                ?: "Ketuk untuk melihat atau unggah planogram di tab Planogram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Buka Planogram",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // 3. Daily Vehicle Checks Progress Overview
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Checklist Kendaraan Harian", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$completedChecks / 3",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { completedChecks / 3f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(100.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // 4. Vehicle Stock Check (Checkboxes Only - Twice Daily)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Check Stock Barang Kendaraan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Centang 2x sehari: sebelum & sesudah kunjungan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            text = "${listOf(shift.preStockChecked, shift.postStockChecked).count { it }}/2",
                            containerColor = if (shift.preStockChecked && shift.postStockChecked) StatusSuccessBg else StatusWarningBg,
                            contentColor = if (shift.preStockChecked && shift.postStockChecked) StatusSuccess else StatusWarning
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    CheckRowItem(
                        label = "1. Check Stock Sebelum Kunjungan (Pagi)",
                        checked = shift.preStockChecked,
                        enabled = !isLocked,
                        testTag = "pre_stock_checkbox"
                    ) { checked ->
                        onUpdateShiftFields(
                            shift,
                            mapOf(
                                "preStockChecked" to checked,
                                "preStockSummary" to if (checked) "Stok kendaraan sebelum kunjungan sudah dicek" else ""
                            ),
                            if (checked) "Check stock SEBELUM kunjungan dicentang" else null
                        )
                    }

                    CheckRowItem(
                        label = "2. Check Stock Sesudah Kunjungan (Sore)",
                        checked = shift.postStockChecked,
                        enabled = !isLocked,
                        testTag = "post_stock_checkbox"
                    ) { checked ->
                        onUpdateShiftFields(
                            shift,
                            mapOf(
                                "postStockChecked" to checked,
                                "postStockSummary" to if (checked) "Stok kendaraan sesudah kunjungan sudah dicek" else ""
                            ),
                            if (checked) "Check stock SESUDAH kunjungan dicentang" else null
                        )
                    }
                }
            }
        }

        // 5. Vehicle Cleanliness Inspection + Mandatory Proof File Upload
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCleanlinessExpanded = !showCleanlinessExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Kebersihan Kendaraan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (shift.cleanlinessChecked && shift.cleanlinessFileBase64.isNotBlank())
                                        "4 poin & file bukti terlampir"
                                    else
                                        "Centang 4 poin & unggah file/foto bukti",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (shift.cleanlinessChecked && shift.cleanlinessFileBase64.isNotBlank())
                                        StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            text = if (shift.cleanlinessChecked && shift.cleanlinessFileBase64.isNotBlank())
                                "Lengkap"
                            else if (shift.cleanlinessChecked)
                                "Perlu File"
                            else
                                "Belum Cek",
                            containerColor = if (shift.cleanlinessChecked && shift.cleanlinessFileBase64.isNotBlank())
                                StatusSuccessBg else StatusWarningBg,
                            contentColor = if (shift.cleanlinessChecked && shift.cleanlinessFileBase64.isNotBlank())
                                StatusSuccess else StatusWarning
                        )
                    }

                    AnimatedVisibility(visible = showCleanlinessExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CheckRowItem(
                                label = "Eksterior / Bodi Kendaraan Bersih",
                                checked = shift.cleanlinessExteriorOk,
                                enabled = !isLocked
                            ) { checked ->
                                val allOk = checked && shift.cleanlinessCabinOk && shift.cleanlinessGlassOk && shift.cleanlinessTrashFree
                                onUpdateShiftFields(
                                    shift,
                                    mapOf("cleanlinessExteriorOk" to checked, "cleanlinessChecked" to allOk),
                                    null
                                )
                            }
                            CheckRowItem(
                                label = "Bak / Kabin Barang Rapi & Kering",
                                checked = shift.cleanlinessCabinOk,
                                enabled = !isLocked
                            ) { checked ->
                                val allOk = shift.cleanlinessExteriorOk && checked && shift.cleanlinessGlassOk && shift.cleanlinessTrashFree
                                onUpdateShiftFields(
                                    shift,
                                    mapOf("cleanlinessCabinOk" to checked, "cleanlinessChecked" to allOk),
                                    null
                                )
                            }
                            CheckRowItem(
                                label = "Kaca Depan & Spion Jernih",
                                checked = shift.cleanlinessGlassOk,
                                enabled = !isLocked
                            ) { checked ->
                                val allOk = shift.cleanlinessExteriorOk && shift.cleanlinessCabinOk && checked && shift.cleanlinessTrashFree
                                onUpdateShiftFields(
                                    shift,
                                    mapOf("cleanlinessGlassOk" to checked, "cleanlinessChecked" to allOk),
                                    null
                                )
                            }
                            CheckRowItem(
                                label = "Bebas Sampah & Bau",
                                checked = shift.cleanlinessTrashFree,
                                enabled = !isLocked
                            ) { checked ->
                                val allOk = shift.cleanlinessExteriorOk && shift.cleanlinessCabinOk && shift.cleanlinessGlassOk && checked
                                onUpdateShiftFields(
                                    shift,
                                    mapOf("cleanlinessTrashFree" to checked, "cleanlinessChecked" to allOk),
                                    if (allOk) "4 poin kebersihan dicentang" else null
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            if (shift.cleanlinessFileBase64.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = StatusSuccessBg.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (cleanlinessPreviewBitmap != null) Icons.Default.PhotoCamera else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = StatusSuccess,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Terlampir: ${shift.cleanlinessFileName.ifBlank { "Bukti Kebersihan" }}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = StatusSuccess,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (cleanlinessPreviewBitmap != null) {
                                            Image(
                                                bitmap = cleanlinessPreviewBitmap,
                                                contentDescription = "Foto bukti kebersihan kendaraan",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    cleanlinessFilePicker.launch(arrayOf("image/*", "application/pdf"))
                                },
                                enabled = !isLocked,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upload_cleanliness_file_button")
                            ) {
                                Icon(
                                    imageVector = if (shift.cleanlinessFileBase64.isNotBlank()) Icons.Default.AttachFile else Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (shift.cleanlinessFileBase64.isNotBlank())
                                        "Ganti File / Foto Kebersihan"
                                    else
                                        "Upload File / Foto Kebersihan Kendaraan"
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Per-Store Field Duties Card (Executed at Every Visited Store with Mandatory Photo)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tugas di Setiap Toko Kunjungan", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Dilakukan oleh Task Asistant & wajib upload foto per toko",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "${shiftVisits.size} Toko",
                            containerColor = if (shiftVisits.isNotEmpty()) StatusSuccessBg else StatusInfoBg,
                            contentColor = if (shiftVisits.isNotEmpty()) StatusSuccess else StatusInfo
                        )
                    }

                    PerStoreTaskInfoRow(
                        icon = Icons.Default.LocalShipping,
                        title = "1. Ambil Barang Order Sales di Kendaraan",
                        subtitle = "Wajib lampirkan foto barang order yang diserahkan di toko."
                    )
                    PerStoreTaskInfoRow(
                        icon = Icons.Default.ViewQuilt,
                        title = "2. Tata Planogram Terbaru (${latestPlanogram?.versionCode ?: "Update"})",
                        subtitle = "Wajib lampirkan foto rak toko setelah ditata sesuai planogram."
                    )
                    PerStoreTaskInfoRow(
                        icon = Icons.Default.Campaign,
                        title = "3. Pasang POSM / Promosi In-Store (${latestPlanogram?.cycleName ?: shift.activeCycleName})",
                        subtitle = "Wajib lampirkan foto terpasangnya materi POSM/promo di toko."
                    )

                    Button(
                        onClick = onOpenMapTab,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("go_to_store_visit_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lapor Kunjungan Toko + Upload 3 Foto")
                    }
                }
            }
        }

        // 7. Request End-of-Day Sign-Off Button
        if (!isLocked) {
            item {
                Button(
                    onClick = { onRequestSignOff(shift) },
                    enabled = shift.status != ShiftStatus.PENDING_SIGNOFF.name,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("request_signoff_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (shift.status == ShiftStatus.PENDING_SIGNOFF.name)
                            "Menunggu Tanda Tangan Salesman di Tab Monitor"
                        else
                            "Ajukan Sign-Off Akhir Hari ke Salesman"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CheckRowItem(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PerStoreTaskInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
