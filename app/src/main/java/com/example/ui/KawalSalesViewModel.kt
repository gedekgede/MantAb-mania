package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.KawalSalesRepository
import com.example.data.model.CsuOutlet
import com.example.data.model.CsuPriority
import com.example.data.model.DailyShift
import com.example.data.model.Planogram
import com.example.data.model.SalesAssistant
import com.example.data.model.ShiftStatus
import com.example.data.model.Team
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VehicleStockItem
import com.example.data.model.VisitReport
import com.example.util.LocationFix
import com.example.util.LocationTracker
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KawalSalesViewModel(
    private val repository: KawalSalesRepository,
    val currentUserId: String
) : ViewModel() {

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    private val _bootstrapError = MutableStateFlow<String?>(null)
    val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

    private val _liveFix = MutableStateFlow<LocationFix?>(null)
    val liveFix: StateFlow<LocationFix?> = _liveFix.asStateFlow()

    private val _isGpsStreaming = MutableStateFlow(false)
    val isGpsStreaming: StateFlow<Boolean> = _isGpsStreaming.asStateFlow()

    private var locationJob: Job? = null

    init {
        bootstrapWorkspace()
    }

    fun bootstrapWorkspace() {
        viewModelScope.launch {
            _bootstrapError.value = null
            repository.ensureWorkspaceAndTodayShift()
                .onFailure { err ->
                    _bootstrapError.value = err.localizedMessage ?: "Gagal menyiapkan ruang kerja"
                }
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Gagal memuat $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiState("profil")

    val teamsState: StateFlow<UiState<List<Team>>> =
        repository.observeTeams(currentUserId).toUiState("tim")

    val planogramsState: StateFlow<UiState<List<Planogram>>> =
        repository.observePlanograms(currentUserId).toUiState("planogram")

    val csuOutletsState: StateFlow<UiState<List<CsuOutlet>>> =
        repository.observeCsuOutlets(currentUserId).toUiState("CSU")

    val salesAssistantsState: StateFlow<UiState<List<SalesAssistant>>> =
        repository.observeSalesAssistants(currentUserId).toUiState("daftar Task Asistant")

    val shiftsState: StateFlow<UiState<List<DailyShift>>> =
        repository.observeDailyShifts(currentUserId).toUiState("shift harian")

    val visitReportsState: StateFlow<UiState<List<VisitReport>>> =
        repository.observeVisitReports(currentUserId).toUiState("laporan kunjungan")

    val vehicleStocksState: StateFlow<UiState<List<VehicleStockItem>>> =
        repository.observeVehicleStocks(currentUserId).toUiState("stok kendaraan")

    // --- Real-Time GPS Tracking ---

    fun startLocationTracking(context: Context, shift: DailyShift?) {
        if (!LocationTracker.hasLocationPermission(context)) {
            _feedback.tryEmit("Izin lokasi belum diberikan.")
            return
        }
        locationJob?.cancel()
        _isGpsStreaming.value = true
        locationJob = viewModelScope.launch {
            // Immediate one-shot fix first
            val immediate = LocationTracker.getCurrentFix(context)
            if (immediate != null) {
                _liveFix.value = immediate
                if (shift != null && shift.status != ShiftStatus.SIGNED_OFF.name) {
                    repository.updateShiftLocation(
                        shiftId = shift.id,
                        trackingActive = true,
                        latitude = immediate.latitude,
                        longitude = immediate.longitude,
                        accuracyMeters = immediate.accuracyMeters.toDouble(),
                        locationLabel = "GPS Aktif (${LocationTracker.formatCoords(immediate.latitude, immediate.longitude)})"
                    )
                }
            }
            LocationTracker.locationUpdatesFlow(context, intervalMs = 8000L)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _isGpsStreaming.value = false
                    _feedback.tryEmit("Pelacakan GPS berhenti: ${e.localizedMessage}")
                }
                .collect { fix ->
                    _liveFix.value = fix
                    val latestShift = (shiftsState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.assistantUid == currentUserId }
                        ?: shift
                    if (latestShift != null && latestShift.status != ShiftStatus.SIGNED_OFF.name) {
                        repository.updateShiftLocation(
                            shiftId = latestShift.id,
                            trackingActive = true,
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            accuracyMeters = fix.accuracyMeters.toDouble(),
                            locationLabel = "GPS Aktif (${LocationTracker.formatCoords(fix.latitude, fix.longitude)})"
                        )
                    }
                }
        }
    }

    fun stopLocationTracking(shift: DailyShift?) {
        locationJob?.cancel()
        locationJob = null
        _isGpsStreaming.value = false
        if (shift != null && shift.status != ShiftStatus.SIGNED_OFF.name) {
            viewModelScope.launch {
                repository.updateShiftLocation(
                    shiftId = shift.id,
                    trackingActive = false,
                    latitude = shift.latitude,
                    longitude = shift.longitude,
                    accuracyMeters = shift.accuracyMeters,
                    locationLabel = "GPS Dijeda"
                )
            }
        }
    }

    fun captureSingleFix(context: Context, onResult: (LocationFix?) -> Unit) {
        viewModelScope.launch {
            val fix = LocationTracker.getCurrentFix(context)
            if (fix != null) _liveFix.value = fix
            onResult(fix)
        }
    }

    // --- Daily Shift Tasks & Vehicle Checks ---

    fun updateShiftTask(
        shift: DailyShift,
        fields: Map<String, Any>,
        successMsg: String? = null
    ) {
        if (shift.status == ShiftStatus.SIGNED_OFF.name) {
            _feedback.tryEmit("Shift sudah disahkan Sales, tidak dapat diubah.")
            return
        }
        viewModelScope.launch {
            repository.updateShiftFields(shift.id, fields)
                .onSuccess { if (successMsg != null) _feedback.tryEmit(successMsg) }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal memperbarui tugas") }
        }
    }

    fun requestSignOff(shift: DailyShift) {
        updateShiftTask(
            shift,
            mapOf("status" to ShiftStatus.PENDING_SIGNOFF.name),
            "Permintaan Sign-Off dikirim ke Salesman"
        )
    }

    fun submitSalesSignOff(
        shift: DailyShift,
        signerName: String,
        signOffNote: String,
        signatureSvgPath: String
    ) {
        viewModelScope.launch {
            repository.submitSalesSignOff(
                shiftId = shift.id,
                signerName = signerName,
                signOffNote = signOffNote,
                signatureSvgPath = signatureSvgPath
            ).onSuccess {
                _feedback.tryEmit("Shift hari ini resmi disahkan oleh $signerName")
            }.onFailure {
                _feedback.tryEmit(it.localizedMessage ?: "Gagal menyimpan tanda tangan")
            }
        }
    }

    // --- Vehicle Stock Check (Before & After Visits) ---

    fun addVehicleStockItem(
        shift: DailyShift,
        skuCode: String,
        productName: String,
        unit: String,
        qtyBefore: Long,
        qtyAfter: Long,
        conditionOk: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            repository.addVehicleStockItem(
                shift, skuCode, productName, unit, qtyBefore, qtyAfter, conditionOk, notes
            ).onSuccess {
                _feedback.tryEmit("Stok kendaraan $skuCode ditambahkan")
            }.onFailure {
                _feedback.tryEmit(it.localizedMessage ?: "Gagal menambah stok")
            }
        }
    }

    fun updateVehicleStockItem(
        stockId: String,
        qtyBefore: Long,
        qtyAfter: Long,
        conditionOk: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            repository.updateVehicleStockItem(stockId, qtyBefore, qtyAfter, conditionOk, notes)
                .onSuccess { _feedback.tryEmit("Stok kendaraan diperbarui") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal memperbarui stok") }
        }
    }

    fun deleteVehicleStockItem(stockId: String) {
        viewModelScope.launch {
            repository.deleteVehicleStockItem(stockId)
                .onSuccess { _feedback.tryEmit("Item stok dihapus") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal menghapus item") }
        }
    }

    // --- Customer Visit Reporting ---

    fun submitVisitReport(
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
    ) {
        viewModelScope.launch {
            repository.addVisitReport(
                shift = shift,
                csuCode = csuCode,
                storeName = storeName,
                storeAddress = storeAddress,
                orderDone = orderDone,
                orderItemsPicked = orderItemsPicked,
                orderPhotoBase64 = orderPhotoBase64,
                planogramDone = planogramDone,
                planogramVersionRef = planogramVersionRef,
                planogramPhotoBase64 = planogramPhotoBase64,
                promoDone = promoDone,
                promoCycleName = promoCycleName,
                posmPhotoBase64 = posmPhotoBase64,
                latitude = latitude,
                longitude = longitude,
                notes = notes
            ).onSuccess {
                _feedback.tryEmit("Laporan kunjungan $storeName + 3 foto tugas berhasil disimpan")
            }.onFailure {
                _feedback.tryEmit(it.localizedMessage ?: "Gagal menyimpan laporan kunjungan")
            }
        }
    }

    // --- Admin Planogram Upload & Activation ---

    fun publishPlanogram(
        team: Team,
        title: String,
        versionCode: String,
        cycleName: String,
        category: String,
        shelfInstructions: String,
        promoHighlight: String,
        imageBase64: String?,
        setAsActive: Boolean,
        existingPlanograms: List<Planogram>
    ) {
        viewModelScope.launch {
            repository.publishPlanogram(
                team = team,
                title = title,
                versionCode = versionCode,
                cycleName = cycleName,
                category = category,
                shelfInstructions = shelfInstructions,
                promoHighlight = promoHighlight,
                imageBase64 = imageBase64,
                setAsActive = setAsActive,
                existingActivePlanograms = existingPlanograms
            ).onSuccess {
                _feedback.tryEmit("Planogram $versionCode berhasil dipublikasikan")
            }.onFailure {
                _feedback.tryEmit(it.localizedMessage ?: "Gagal mengunggah planogram")
            }
        }
    }

    fun activatePlanogram(planogram: Planogram, allPlanograms: List<Planogram>) {
        viewModelScope.launch {
            repository.setPlanogramActive(planogram, allPlanograms)
                .onSuccess { _feedback.tryEmit("${planogram.versionCode} dijadikan planogram terbaru") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal mengaktifkan planogram") }
        }
    }

    fun deletePlanogram(planogramId: String) {
        viewModelScope.launch {
            repository.deletePlanogram(planogramId)
                .onSuccess { _feedback.tryEmit("Planogram dihapus") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal menghapus planogram") }
        }
    }

    // --- Admin CSU Outlets Upload & Geotag ---

    fun addCsuOutlet(
        team: Team,
        csuCode: String,
        storeName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        assignedAssistantName: String,
        priority: CsuPriority
    ) {
        viewModelScope.launch {
            repository.addCsuOutlet(
                team, csuCode, storeName, address, latitude, longitude, assignedAssistantName, priority
            ).onSuccess {
                _feedback.tryEmit("CSU $csuCode ($storeName) berhasil ditambahkan")
            }.onFailure {
                _feedback.tryEmit(it.localizedMessage ?: "Gagal menambah CSU")
            }
        }
    }

    fun bulkImportCsuOutlets(team: Team, csvText: String) {
        viewModelScope.launch {
            val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                _feedback.tryEmit("Masukkan minimal 1 baris data CSU.")
                return@launch
            }
            var count = 0
            var lastErr: String? = null
            for ((idx, line) in lines.withIndex()) {
                val parts = line.split(",", ";", "|").map { it.trim() }
                if (parts.size < 2) continue
                val code = parts[0].ifBlank { "CSU-${idx + 1}" }
                val name = parts[1]
                val address = parts.getOrNull(2).orEmpty()
                val lat = parts.getOrNull(3)?.toDoubleOrNull() ?: (-6.2088 + idx * 0.006)
                val lng = parts.getOrNull(4)?.toDoubleOrNull() ?: (106.8456 + idx * 0.005)
                val priority = CsuPriority.fromRaw(parts.getOrNull(5)?.uppercase())
                val assistantName = parts.getOrNull(6).orEmpty().ifBlank { "Semua Task Asistant" }
                repository.addCsuOutlet(team, code, name, address, lat, lng, assistantName, priority)
                    .onSuccess { count++ }
                    .onFailure { lastErr = it.localizedMessage }
            }
            if (count > 0) {
                _feedback.tryEmit("$count CSU aktif beserta geotag berhasil diunggah")
            } else {
                _feedback.tryEmit(lastErr ?: "Format baris CSV CSU tidak valid")
            }
        }
    }

    fun toggleCsuActive(csuId: String, active: Boolean) {
        viewModelScope.launch {
            repository.toggleCsuActive(csuId, active)
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal mengubah status CSU") }
        }
    }

    fun deleteCsuOutlet(csuId: String) {
        viewModelScope.launch {
            repository.deleteCsuOutlet(csuId)
                .onSuccess { _feedback.tryEmit("CSU dihapus") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal menghapus CSU") }
        }
    }

    // --- Admin Sales Assistant Roster Upload ---

    fun addSalesAssistant(
        team: Team,
        employeeCode: String,
        fullName: String,
        vehiclePlate: String,
        pairedSalesName: String,
        region: String
    ) {
        viewModelScope.launch {
            repository.addSalesAssistant(team, employeeCode, fullName, vehiclePlate, pairedSalesName, region)
                .onSuccess { _feedback.tryEmit("Task Asistant $fullName berhasil ditambahkan") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal menambah Task Asistant") }
        }
    }

    fun bulkImportSalesAssistants(team: Team, rawLines: String, existingCount: Int) {
        viewModelScope.launch {
            val lines = rawLines.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                _feedback.tryEmit("Masukkan minimal 1 nama Task Asistant.")
                return@launch
            }
            var count = 0
            var lastErr: String? = null
            for ((idx, line) in lines.withIndex()) {
                val parts = line.split(",", ";", "|").map { it.trim() }
                val name = if (parts.size == 1) parts[0] else parts[0]
                if (name.length < 2) continue
                val code = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: "AS-${(existingCount + idx + 1).toString().padStart(3, '0')}"
                val plate = parts.getOrNull(2).orEmpty().ifBlank { "B 900${idx + 1} KWS" }
                val pairedSales = parts.getOrNull(3).orEmpty().ifBlank { "Salesman" }
                val region = parts.getOrNull(4).orEmpty().ifBlank { "Area Utama" }
                repository.addSalesAssistant(team, code, name, plate, pairedSales, region)
                    .onSuccess { count++ }
                    .onFailure { lastErr = it.localizedMessage }
            }
            if (count > 0) {
                _feedback.tryEmit("$count nama Task Asistant berhasil diunggah")
            } else {
                _feedback.tryEmit(lastErr ?: "Format daftar Task Asistant tidak valid")
            }
        }
    }

    fun toggleAssistantActive(assistantId: String, active: Boolean) {
        viewModelScope.launch {
            repository.toggleAssistantActive(assistantId, active)
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal memperbarui status Task Asistant") }
        }
    }

    fun deleteSalesAssistant(assistantId: String) {
        viewModelScope.launch {
            repository.deleteSalesAssistant(assistantId)
                .onSuccess { _feedback.tryEmit("Task Asistant dihapus dari daftar") }
                .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Gagal menghapus Task Asistant") }
        }
    }

    fun selectAssistantForShift(shift: DailyShift, assistant: SalesAssistant) {
        updateShiftTask(
            shift,
            mapOf(
                "assistantName" to assistant.fullName,
                "vehiclePlate" to assistant.vehiclePlate.ifBlank { shift.vehiclePlate },
                "accompaniedSalesName" to assistant.pairedSalesName.ifBlank { shift.accompaniedSalesName }
            ),
            "Shift disesuaikan untuk ${assistant.fullName}"
        )
    }

    // --- Profile & Team Sync ---

    fun updateProfileSettings(
        displayName: String,
        role: UserRole,
        vehiclePlate: String,
        accompaniedSalesName: String,
        activeShift: DailyShift?
    ) {
        viewModelScope.launch {
            repository.updateUserProfileSettings(displayName, role, vehiclePlate, accompaniedSalesName)
                .onSuccess {
                    if (activeShift != null && activeShift.status != ShiftStatus.SIGNED_OFF.name) {
                        repository.updateShiftFields(
                            activeShift.id,
                            mapOf(
                                "assistantName" to displayName.trim().take(80).ifBlank { activeShift.assistantName },
                                "vehiclePlate" to vehiclePlate.trim().take(24).ifBlank { activeShift.vehiclePlate },
                                "accompaniedSalesName" to accompaniedSalesName.trim().take(80).ifBlank { activeShift.accompaniedSalesName }
                            )
                        )
                    }
                    _feedback.tryEmit("Pengaturan profil & peran disimpan")
                }
                .onFailure {
                    _feedback.tryEmit(it.localizedMessage ?: "Gagal menyimpan pengaturan")
                }
        }
    }

    fun joinTeamByCode(joinCode: String, joinAsRole: UserRole) {
        viewModelScope.launch {
            repository.joinTeamByCode(joinCode, joinAsRole)
                .onSuccess { code ->
                    _feedback.tryEmit("Terhubung ke tim $code sebagai ${joinAsRole.label}")
                }
                .onFailure {
                    _feedback.tryEmit("Kode tim tidak ditemukan atau gagal bergabung: ${it.localizedMessage}")
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

    private companion object {
        const val TAG = "KawalSalesVM"
    }
}
