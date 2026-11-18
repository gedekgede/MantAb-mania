package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CsuOutlet
import com.example.data.model.CsuPriority
import com.example.data.model.DailyShift
import com.example.data.model.SalesAssistant
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.GeotagMapCanvas
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.util.LocationFix
import com.example.util.LocationTracker

@Composable
fun AdminCenterScreen(
    team: Team?,
    isTeamAdmin: Boolean,
    effectiveRole: UserRole,
    userProfile: UserProfile?,
    activeShift: DailyShift?,
    csuOutlets: List<CsuOutlet>,
    salesAssistants: List<SalesAssistant>,
    onCaptureSingleFix: ((LocationFix?) -> Unit) -> Unit,
    onAddCsuOutlet: (Team, String, String, String, Double, Double, String, CsuPriority) -> Unit,
    onBulkImportCsu: (Team, String) -> Unit,
    onToggleCsuActive: (String, Boolean) -> Unit,
    onDeleteCsu: (String) -> Unit,
    onAddAssistant: (Team, String, String, String, String, String) -> Unit,
    onBulkImportAssistants: (Team, String, Int) -> Unit,
    onToggleAssistantActive: (String, Boolean) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onUpdateProfileSettings: (String, UserRole, String, String, DailyShift?) -> Unit,
    onJoinTeam: (String, UserRole) -> Unit,
    onNotify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val canAdminEdit = isTeamAdmin && effectiveRole == UserRole.ADMIN

    var showAddCsuDialog by remember { mutableStateOf(false) }
    var showBulkCsuDialog by remember { mutableStateOf(false) }
    var showAddAssistantDialog by remember { mutableStateOf(false) }
    var showBulkAssistantDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var joinRoleInput by remember { mutableStateOf(UserRole.ASSISTANT) }

    var displayNameInput by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var vehiclePlateInput by remember(userProfile) { mutableStateOf(userProfile?.vehiclePlate ?: "B 9124 KWS") }
    var salesNameInput by remember(userProfile) { mutableStateOf(userProfile?.accompaniedSalesName ?: "Salesman") }
    var roleInput by remember(effectiveRole) { mutableStateOf(effectiveRole) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (canAdminEdit) "Pusat Admin, CSU & Tim" else "Akun, Peran & Gabung Tim",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (canAdminEdit)
                        "Kelola CSU wajib kunjung + geotag, daftar Task Asistant, dan kode tim"
                    else
                        "Hak akses dibedakan sesuai peran • Admin mengelola CSU, Task Asistant & Planogram",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Role Access Summary Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hak Akses Peran Aktif", style = MaterialTheme.typography.titleSmall)
                        StatusPill(effectiveRole.label, StatusInfoBg, StatusInfo)
                    }
                    Text(
                        text = "• Admin: Mengubah nama Task Asistant, daftar toko CSU + geotag, & planogram.\n" +
                            "• Salesman: Memantau aktivitas, verifikasi foto, & tanda tangan akhir hari.\n" +
                            "• Task Asistant: Check stock 2x, kebersihan + file, & foto 3 tugas tiap toko.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Admin Upload CSU Aktif + Peta Geotag (Admin-only)
        if (canAdminEdit) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CSU Aktif Wajib Kunjung", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${csuOutlets.count { it.active }} aktif dari ${csuOutlets.size} toko terdaftar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill("Geotag", StatusInfoBg, StatusInfo)
                    }

                    if (team != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAddCsuDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_csu_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah CSU")
                            }
                            OutlinedButton(
                                onClick = { showBulkCsuDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_csu_button")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Impor CSV")
                            }
                        }
                    }

                    if (csuOutlets.isEmpty()) {
                        Text(
                            text = "Belum ada CSU diunggah. Tambahkan CSU dengan geotag atau impor daftar CSV sekaligus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(csuOutlets, key = { "csu_${it.id}" }) { csu ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${csu.csuCode} • ${csu.storeName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${LocationTracker.formatCoords(csu.latitude, csu.longitude)} • ${CsuPriority.fromRaw(csu.priority).label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canAdminEdit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = csu.active,
                                onCheckedChange = { onToggleCsuActive(csu.id, it) }
                            )
                            IconButton(onClick = { onDeleteCsu(csu.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus CSU", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        }

        // 2. Admin Upload Daftar Nama Task Asistant (Admin-only)
        if (canAdminEdit) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daftar Task Asistant", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${salesAssistants.count { it.active }} Task Asistant aktif terdaftar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill("Roster", StatusSuccessBg, StatusSuccess)
                    }

                    if (team != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAddAssistantDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_assistant_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah Nama")
                            }
                            OutlinedButton(
                                onClick = { showBulkAssistantDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bulk_assistant_button")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Unggah Daftar")
                            }
                        }
                    }
                }
            }
        }

        items(salesAssistants, key = { "asst_${it.id}" }) { asst ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${asst.employeeCode} • ${asst.fullName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${asst.vehiclePlate} • Sales: ${asst.pairedSalesName} • ${asst.region}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canAdminEdit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = asst.active,
                                onCheckedChange = { onToggleAssistantActive(asst.id, it) }
                            )
                            IconButton(onClick = { onDeleteAssistant(asst.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Task Asistant", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        }

        // 3. Team Sync & Multi-Device Join Code
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sinkronisasi Tim Multi-Perangkat", style = MaterialTheme.typography.titleMedium)
                    if (team != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Kode Tim Anda (${team.memberUids.size} anggota)", style = MaterialTheme.typography.labelSmall)
                                    Text(team.joinCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Kode Tim KawalSales", team.joinCode))
                                    onNotify("Kode tim ${team.joinCode} disalin")
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin kode tim")
                                }
                            }
                        }
                    }

                    Text("Pilih Peran Saat Gabung Tim:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = joinRoleInput == UserRole.ASSISTANT,
                            onClick = { joinRoleInput = UserRole.ASSISTANT },
                            label = { Text("Task Asistant", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = joinRoleInput == UserRole.SALES_LEAD,
                            onClick = { joinRoleInput = UserRole.SALES_LEAD },
                            label = { Text("Salesman (Tanda Tangan)", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { joinCodeInput = it },
                            label = { Text("Gabung Kode Tim Admin") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (joinCodeInput.isNotBlank()) {
                                    onJoinTeam(joinCodeInput, joinRoleInput)
                                    joinCodeInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gabung")
                        }
                    }
                }
            }
        }

        // 4. User Profile, Vehicle Plate & Role Settings
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pengaturan Profil & Armada", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Nama Tampilan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vehiclePlateInput,
                            onValueChange = { vehiclePlateInput = it },
                            label = { Text("Plat Kendaraan") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = salesNameInput,
                            onValueChange = { salesNameInput = it },
                            label = { Text("Salesman yang Didampingi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val selectableRoles = remember(isTeamAdmin) {
                        if (isTeamAdmin) UserRole.entries
                        else listOf(UserRole.ASSISTANT, UserRole.SALES_LEAD)
                    }
                    Text(
                        text = if (isTeamAdmin) "Mode Uji / Peran Anda (Pemilik Tim):" else "Peran di Tim Ini:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectableRoles.forEach { r ->
                            FilterChip(
                                selected = roleInput == r,
                                onClick = { roleInput = r },
                                label = { Text(r.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onUpdateProfileSettings(
                                displayNameInput,
                                roleInput,
                                vehiclePlateInput,
                                salesNameInput,
                                activeShift
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Pengaturan")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Keluar Akun Google")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Dialogs
    if (showAddCsuDialog && team != null) {
        AddCsuGeotagDialog(
            initialLat = activeShift?.latitude ?: -6.2088,
            initialLng = activeShift?.longitude ?: 106.8456,
            existingOutlets = csuOutlets,
            assistants = salesAssistants,
            onCaptureFix = onCaptureSingleFix,
            onDismiss = { showAddCsuDialog = false },
            onSave = { code, name, addr, lat, lng, asstName, priority ->
                onAddCsuOutlet(team, code, name, addr, lat, lng, asstName, priority)
                showAddCsuDialog = false
            }
        )
    }

    if (showBulkCsuDialog && team != null) {
        BulkImportDialog(
            title = "Impor Massal CSU Aktif (CSV)",
            hint = "Format per baris:\nKodeCSU, Nama Toko, Alamat, Latitude, Longitude, Prioritas\n\nContoh:\nCSU-01, Toko Sumber Jaya, Jl. Kemang 12, -6.2607, 106.8135, WAJIB\nCSU-02, Minimarket Berkah, Jl. Fatmawati 88, -6.2850, 106.7970, PRIORITAS",
            defaultSample = "CSU-01, Toko Sumber Jaya, Jl. Kemang Raya 12, -6.2607, 106.8135, WAJIB\nCSU-02, Mart Berkah Utama, Jl. Fatmawati 88, -6.2850, 106.7970, PRIORITAS\nCSU-03, Warung Maju, Jl. Antasari 45, -6.2720, 106.8090, WAJIB",
            onDismiss = { showBulkCsuDialog = false },
            onConfirm = { csv ->
                onBulkImportCsu(team, csv)
                showBulkCsuDialog = false
            }
        )
    }

    if (showAddAssistantDialog && team != null) {
        AddSalesAssistantDialog(
            nextIndex = salesAssistants.size + 1,
            onDismiss = { showAddAssistantDialog = false },
            onSave = { code, name, plate, salesName, region ->
                onAddAssistant(team, code, name, plate, salesName, region)
                showAddAssistantDialog = false
            }
        )
    }

    if (showBulkAssistantDialog && team != null) {
        BulkImportDialog(
            title = "Unggah Daftar Nama Task Asistant",
            hint = "Masukkan satu nama per baris, atau format lengkap:\nNama Lengkap, Kode, Plat Kendaraan, Salesman yang Didampingi, Wilayah",
            defaultSample = "Budi Santoso, AS-001, B 9124 KWS, Andi Wijaya, Jakarta Selatan\nRina Marlina, AS-002, B 9301 KWS, Dedi Pratama, Jakarta Barat\nFajar Nugroho, AS-003, B 9552 KWS, Andi Wijaya, Depok",
            onDismiss = { showBulkAssistantDialog = false },
            onConfirm = { text ->
                onBulkImportAssistants(team, text, salesAssistants.size)
                showBulkAssistantDialog = false
            }
        )
    }
}

@Composable
private fun AddCsuGeotagDialog(
    initialLat: Double,
    initialLng: Double,
    existingOutlets: List<CsuOutlet>,
    assistants: List<SalesAssistant>,
    onCaptureFix: ((LocationFix?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, String, CsuPriority) -> Unit
) {
    var code by remember { mutableStateOf("CSU-${(existingOutlets.size + 1).toString().padStart(2, '0')}") }
    var storeName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var lat by remember { mutableDoubleStateOf(initialLat) }
    var lng by remember { mutableDoubleStateOf(initialLng) }
    var latText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.5f", initialLat)) }
    var lngText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.5f", initialLng)) }
    var assignedAssistant by remember { mutableStateOf(assistants.firstOrNull { it.active }?.fullName ?: "Semua Task Asistant") }
    var priority by remember { mutableStateOf(CsuPriority.WAJIB) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
                    Text("Tambah CSU Aktif + Geotag", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Kode CSU") },
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Nama Toko / CSU") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("csu_name_input")
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat Toko") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = assignedAssistant,
                    onValueChange = { assignedAssistant = it },
                    label = { Text("Ditugaskan ke Task Asistant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Ketuk Peta untuk Menentukan Titik Geotag CSU:", style = MaterialTheme.typography.labelMedium)
                GeotagMapCanvas(
                    outlets = existingOutlets,
                    visits = emptyList(),
                    currentLat = lat,
                    currentLng = lng,
                    trackingActive = true,
                    selectedOutletId = null,
                    onSelectOutlet = {},
                    onMapCoordinateTap = { tappedLat, tappedLng ->
                        lat = tappedLat
                        lng = tappedLng
                        latText = String.format(java.util.Locale.US, "%.5f", tappedLat)
                        lngText = String.format(java.util.Locale.US, "%.5f", tappedLng)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { v -> lat = v }
                        },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            it.toDoubleOrNull()?.let { v -> lng = v }
                        },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        onCaptureFix { fix ->
                            if (fix != null) {
                                lat = fix.latitude
                                lng = fix.longitude
                                latText = String.format(java.util.Locale.US, "%.5f", fix.latitude)
                                lngText = String.format(java.util.Locale.US, "%.5f", fix.longitude)
                            }
                        }
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CsuPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            val parsedLat = latText.toDoubleOrNull()
                            val parsedLng = lngText.toDoubleOrNull()
                            if (code.isBlank() || storeName.trim().length < 2 || parsedLat == null || parsedLng == null) {
                                errorMsg = "Lengkapi kode, nama toko, dan koordinat geotag yang valid."
                                return@Button
                            }
                            onSave(code, storeName, address, parsedLat, parsedLng, assignedAssistant, priority)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan CSU") }
                }
            }
        }
    }
}

@Composable
private fun AddSalesAssistantDialog(
    nextIndex: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var code by remember { mutableStateOf("AS-${nextIndex.toString().padStart(3, '0')}") }
    var fullName by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("B 9124 KWS") }
    var pairedSales by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Jakarta Selatan") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tambah Nama Task Asistant", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kode Task Asistant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nama Lengkap Task Asistant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vehiclePlate, onValueChange = { vehiclePlate = it }, label = { Text("Plat Kendaraan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pairedSales, onValueChange = { pairedSales = it }, label = { Text("Salesman yang Didampingi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Area / Wilayah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (errorMsg != null) {
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            if (code.isBlank() || fullName.trim().length < 2) {
                                errorMsg = "Kode dan nama wajib diisi."
                                return@Button
                            }
                            onSave(code, fullName, vehiclePlate, pairedSales, region)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

@Composable
private fun BulkImportDialog(
    title: String,
    hint: String,
    defaultSample: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { text = defaultSample }) {
                    Text("Isi Contoh Format")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Tempel Data di Sini") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(onClick = { onConfirm(text) }, modifier = Modifier.weight(1f)) { Text("Unggah Semua") }
                }
            }
        }
    }
}
