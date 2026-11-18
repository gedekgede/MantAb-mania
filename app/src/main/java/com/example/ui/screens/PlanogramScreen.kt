package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.Planogram
import com.example.data.model.Team
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.util.ImageUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PlanogramScreen(
    team: Team?,
    canEditPlanogram: Boolean,
    planograms: List<Planogram>,
    onPublishPlanogram: (
        team: Team,
        title: String,
        versionCode: String,
        cycleName: String,
        category: String,
        shelfInstructions: String,
        promoHighlight: String,
        imageBase64: String?,
        setAsActive: Boolean
    ) -> Unit,
    onActivatePlanogram: (Planogram) -> Unit,
    onDeletePlanogram: (String) -> Unit
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    var zoomedPlanogram by remember { mutableStateOf<Planogram?>(null) }

    val activePlanogram = remember(planograms) {
        planograms.firstOrNull { it.active } ?: planograms.firstOrNull()
    }
    val archivedPlanograms = remember(planograms, activePlanogram) {
        planograms.filter { it.id != activePlanogram?.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Admin Upload CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Planogram Terbaru", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (canEditPlanogram)
                            "Kelola & unggah planogram terbaru untuk tim"
                        else
                            "Mode Lihat • Sinkronisasi real-time dari Admin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEditPlanogram && team != null) {
                    Button(
                        onClick = { showUploadDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("upload_planogram_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unggah")
                    }
                }
            }
        }

        // Active Latest Planogram Hero
        if (activePlanogram != null) {
            item {
                ActivePlanogramCard(
                    planogram = activePlanogram,
                    onZoomImage = { zoomedPlanogram = activePlanogram }
                )
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_planogram_guide),
                            contentDescription = "Contoh panduan planogram rak supermarket",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text("Belum Ada Planogram Diunggah Admin", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Admin dapat mengunggah foto & panduan planogram terbaru agar seluruh Task Asistant melihat versi paling update di perangkat mereka.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (canEditPlanogram && team != null) {
                            Button(onClick = { showUploadDialog = true }) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unggah Planogram Pertama")
                            }
                        }
                    }
                }
            }
        }

        // Archived / Older Planogram Versions
        if (archivedPlanograms.isNotEmpty()) {
            item {
                Text(
                    text = "Riwayat Versi Planogram (${archivedPlanograms.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(archivedPlanograms, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
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
                                StatusPill(item.versionCode, StatusInfoBg, StatusInfo)
                                Text("Rev #${item.revision}", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${item.category} • ${item.cycleName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEditPlanogram) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onActivatePlanogram(item) }) {
                                    Text("Aktifkan")
                                }
                                IconButton(onClick = { onDeletePlanogram(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus planogram", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showUploadDialog && team != null) {
        AdminPlanogramUploadDialog(
            nextRevision = (planograms.maxOfOrNull { it.revision } ?: 0L) + 1L,
            onDismiss = { showUploadDialog = false },
            onPublish = { title, version, cycle, category, instr, promo, imageB64, active ->
                onPublishPlanogram(team, title, version, cycle, category, instr, promo, imageB64, active)
                showUploadDialog = false
            }
        )
    }

    zoomedPlanogram?.let { plano ->
        PlanogramFullscreenDialog(
            planogram = plano,
            onDismiss = { zoomedPlanogram = null }
        )
    }
}

@Composable
private fun ActivePlanogramCard(
    planogram: Planogram,
    onZoomImage: () -> Unit
) {
    val customBitmap = remember(planogram.imageBase64) {
        ImageUtils.decodeBase64ToImageBitmap(planogram.imageBase64)
    }
    val updatedText = remember(planogram.updatedAt) {
        planogram.updatedAt?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
        } ?: "Baru diperbarui"
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = if (planogram.active) "PALING UPDATE • ${planogram.versionCode}" else planogram.versionCode,
                        containerColor = StatusSuccessBg,
                        contentColor = StatusSuccess
                    )
                    StatusPill(
                        text = "Rev #${planogram.revision}",
                        containerColor = StatusInfoBg,
                        contentColor = StatusInfo
                    )
                }
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
            }

            Text(planogram.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Kategori: ${planogram.category} • Cycle: ${planogram.cycleName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onZoomImage() }
            ) {
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = "Gambar planogram ${planogram.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_planogram_guide),
                        contentDescription = "Diagram planogram standar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Perbesar", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (planogram.promoHighlight.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusWarningBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = StatusWarning)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Fokus Promosi In-Store Cycle Ini", style = MaterialTheme.typography.labelMedium, color = StatusWarning, fontWeight = FontWeight.Bold)
                            Text(planogram.promoHighlight, style = MaterialTheme.typography.bodySmall, color = Color(0xFF78350F))
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Panduan Penataan Rak", style = MaterialTheme.typography.titleSmall)
                    Text(planogram.shelfInstructions, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                text = "Diunggah oleh ${planogram.createdByName} • $updatedText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanogramFullscreenDialog(
    planogram: Planogram,
    onDismiss: () -> Unit
) {
    val customBitmap = remember(planogram.imageBase64) {
        ImageUtils.decodeBase64ToImageBitmap(planogram.imageBase64)
    }
    var scale by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = planogram.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_planogram_guide),
                        contentDescription = planogram.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${planogram.versionCode} • ${planogram.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup tampilan penuh", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AdminPlanogramUploadDialog(
    nextRevision: Long,
    onDismiss: () -> Unit,
    onPublish: (
        title: String,
        versionCode: String,
        cycleName: String,
        category: String,
        shelfInstructions: String,
        promoHighlight: String,
        imageBase64: String?,
        setAsActive: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("Planogram Rak Utama") }
    var versionCode by remember { mutableStateOf("v2026.11-R$nextRevision") }
    var cycleName by remember { mutableStateOf("Cycle 11 - Promo Akhir Tahun") }
    var category by remember { mutableStateOf("Minuman & Snack") }
    var shelfInstructions by remember {
        mutableStateOf("Rak 1 (Atas): Best Seller & Kemasan Besar\nRak 2 (Eye-Level): Produk Promo Cycle Aktif\nRak 3 (Tengah): Varian Reguler\nRak 4 (Bawah): Multipack & Karton")
    }
    var promoHighlight by remember { mutableStateOf("Pasang wobble tag & shelf talker promo Beli 2 Gratis 1") }
    var setAsActive by remember { mutableStateOf(true) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeUriToBase64(context, uri, maxDimension = 720, maxBytes = 240_000)
                if (encoded == null) {
                    errorMsg = "Ukuran gambar terlalu besar setelah dikompres. Pilih gambar lain."
                } else {
                    imageBase64 = encoded
                    errorMsg = null
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
                .padding(horizontal = 18.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ViewQuilt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Unggah Planogram Terbaru", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Planogram") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planogram_title_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text("Versi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Kategori") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = cycleName,
                    onValueChange = { cycleName = it },
                    label = { Text("Cycle Promosi Berjalan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = promoHighlight,
                    onValueChange = { promoHighlight = it },
                    label = { Text("Instruksi Promosi In-Store") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shelfInstructions,
                    onValueChange = { shelfInstructions = it },
                    label = { Text("Panduan Penataan Tier Rak") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageBase64 != null) "Gambar Planogram Siap (Ganti)" else "Pilih Gambar Planogram dari Galeri")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jadikan Planogram Aktif Terbaru", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = setAsActive, onCheckedChange = { setAsActive = it })
                }

                if (errorMsg != null) {
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            if (title.trim().length < 2 || versionCode.isBlank() || shelfInstructions.isBlank()) {
                                errorMsg = "Judul, Versi, dan Panduan Rak wajib diisi."
                                return@Button
                            }
                            onPublish(title, versionCode, cycleName, category, shelfInstructions, promoHighlight, imageBase64, setAsActive)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("publish_planogram_button")
                    ) {
                        Text("Publikasikan")
                    }
                }
            }
        }
    }
}
