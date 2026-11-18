package com.example.data

import android.content.Context
import com.example.R
import com.example.data.model.CsuOutlet
import com.example.data.model.CsuPriority
import com.example.data.model.DailyShift
import com.example.data.model.Planogram
import com.example.data.model.SalesAssistant
import com.example.data.model.ShiftStatus
import com.example.data.model.Team
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VehicleStockItem
import com.example.data.model.VisitReport
import com.example.data.model.VisitStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KawalSalesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus login dengan Google sebelum mengakses Firestore.")
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun sanitizeId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(120).ifBlank { "id_${System.currentTimeMillis()}" }
    }

    private fun <T> Flow<T>.wrapFirestoreErrors(path: String, op: OperationType): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser != null) {
                handleFirestoreError(exceptionToLog, op, path)
            }
            throw error
        }

    // --- Observers (Real-time snapshots) ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(path, OperationType.GET)
        )
    }

    fun observeTeams(userId: String): Flow<List<Team>> = flow {
        val path = "teams"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Team::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observePlanograms(userId: String): Flow<List<Planogram>> = flow {
        val path = "planograms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Planogram::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Planogram> { it.active }
                                .thenByDescending { it.revision }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observeCsuOutlets(userId: String): Flow<List<CsuOutlet>> = flow {
        val path = "csu_outlets"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CsuOutlet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<CsuOutlet> { it.active }.thenBy { it.csuCode })
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observeSalesAssistants(userId: String): Flow<List<SalesAssistant>> = flow {
        val path = "sales_assistants"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SalesAssistant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<SalesAssistant> { it.active }.thenBy { it.fullName })
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observeDailyShifts(userId: String): Flow<List<DailyShift>> = flow {
        val path = "daily_shifts"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DailyShift::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observeVisitReports(userId: String): Flow<List<VisitReport>> = flow {
        val path = "visit_reports"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VisitReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    fun observeVehicleStocks(userId: String): Flow<List<VehicleStockItem>> = flow {
        val path = "vehicle_stocks"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VehicleStockItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.skuCode }
                }
                .wrapFirestoreErrors(path, OperationType.LIST)
        )
    }

    // --- Workspace Bootstrap & Team Management ---

    suspend fun ensureWorkspaceAndTodayShift(): Result<Pair<String, String>> {
        var currentPath = "users"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val safeName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Task Asistant").take(80)
            val safeEmail = (user?.email?.takeIf { it.isNotBlank() } ?: "user@kawalsales.local").take(120)
            val defaultTeamId = sanitizeId("TIM_${uid.take(8).uppercase(Locale.US)}")

            val userRef = db.collection("users").document(uid)
            currentPath = userRef.path
            val userSnap = userRef.get().await()

            val activeTeamId: String
            val vehiclePlate: String
            val salesName: String
            val assistantName: String

            if (!userSnap.exists()) {
                val teamRef = db.collection("teams").document(defaultTeamId)
                currentPath = teamRef.path
                val teamSnap = teamRef.get().await()
                if (!teamSnap.exists()) {
                    teamRef.set(
                        mapOf(
                            "teamName" to "Tim Operasional $safeName",
                            "ownerUid" to uid,
                            "joinCode" to defaultTeamId,
                            "memberUids" to listOf(uid),
                            "adminUids" to listOf(uid),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                activeTeamId = defaultTeamId
                vehiclePlate = "B 9124 KWS"
                salesName = "Salesman"
                assistantName = safeName

                currentPath = userRef.path
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "email" to safeEmail,
                        "role" to UserRole.ADMIN.name,
                        "activeTeamId" to activeTeamId,
                        "vehiclePlate" to vehiclePlate,
                        "accompaniedSalesName" to salesName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                val effectiveRole = UserRole.ADMIN.name
                ensureShiftIfAssistant(uid, effectiveRole, activeTeamId, assistantName, salesName, vehiclePlate) { p ->
                    currentPath = p
                }
            } else {
                val profile = userSnap.toObject(UserProfile::class.java)
                activeTeamId = profile?.activeTeamId?.takeIf { it.isNotBlank() } ?: defaultTeamId
                vehiclePlate = profile?.vehiclePlate?.takeIf { it.isNotBlank() } ?: "B 9124 KWS"
                salesName = profile?.accompaniedSalesName?.takeIf { it.isNotBlank() } ?: "Salesman"
                assistantName = profile?.displayName?.takeIf { it.isNotBlank() } ?: safeName
                val effectiveRole = profile?.role ?: UserRole.ASSISTANT.name

                val teamRef = db.collection("teams").document(activeTeamId)
                currentPath = teamRef.path
                val teamSnap = teamRef.get().await()
                if (!teamSnap.exists()) {
                    teamRef.set(
                        mapOf(
                            "teamName" to "Tim Operasional $assistantName",
                            "ownerUid" to uid,
                            "joinCode" to activeTeamId,
                            "memberUids" to listOf(uid),
                            "adminUids" to listOf(uid),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                ensureShiftIfAssistant(uid, effectiveRole, activeTeamId, assistantName, salesName, vehiclePlate) { p ->
                    currentPath = p
                }
            }

            val today = todayDateString()
            val shiftId = sanitizeId("shift_${today}_${activeTeamId.take(10)}_${uid.take(10)}")
            Result.success(activeTeamId to shiftId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    private suspend fun ensureShiftIfAssistant(
        uid: String,
        roleRaw: String,
        activeTeamId: String,
        assistantName: String,
        salesName: String,
        vehiclePlate: String,
        onPath: (String) -> Unit
    ) {
        if (roleRaw != UserRole.ASSISTANT.name) return
        val today = todayDateString()
        val shiftId = sanitizeId("shift_${today}_${activeTeamId.take(10)}_${uid.take(10)}")
        val shiftRef = db.collection("daily_shifts").document(shiftId)
        onPath(shiftRef.path)
        val shiftSnap = shiftRef.get().await()
        if (!shiftSnap.exists()) {
            val teamMembers = db.collection("teams").document(activeTeamId).get().await()
                .get("memberUids") as? List<*>
            val memberUids = teamMembers?.filterIsInstance<String>()?.take(20)
                ?.let { if (uid in it) it else (it + uid).take(20) }
                ?: listOf(uid)

            shiftRef.set(
                mapOf(
                    "teamId" to activeTeamId,
                    "assistantUid" to uid,
                    "assistantName" to assistantName,
                    "accompaniedSalesName" to salesName,
                    "vehiclePlate" to vehiclePlate,
                    "shiftDate" to today,
                    "activeCycleName" to "Cycle 11 - Promo Berjalan",
                    "memberUids" to memberUids,
                    "status" to ShiftStatus.ACTIVE.name,
                    "trackingActive" to false,
                    "latitude" to -6.2088,
                    "longitude" to 106.8456,
                    "accuracyMeters" to 0.0,
                    "locationLabel" to "Menunggu aktivasi GPS",
                    "taskOrderPicked" to false,
                    "taskOrderNote" to "",
                    "taskPlanogramArranged" to false,
                    "taskPlanogramNote" to "",
                    "taskPromoSetup" to false,
                    "taskPromoNote" to "",
                    "preStockChecked" to false,
                    "preStockSummary" to "",
                    "postStockChecked" to false,
                    "postStockSummary" to "",
                    "cleanlinessChecked" to false,
                    "cleanlinessExteriorOk" to false,
                    "cleanlinessCabinOk" to false,
                    "cleanlinessGlassOk" to false,
                    "cleanlinessTrashFree" to false,
                    "cleanlinessNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateUserProfileSettings(
        displayName: String,
        role: UserRole,
        vehiclePlate: String,
        accompaniedSalesName: String
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(80).ifBlank { "Task Asistant" }
            val cleanPlate = vehiclePlate.trim().take(24).ifBlank { "B 9124 KWS" }
            val cleanSales = accompaniedSalesName.trim().take(80).ifBlank { "Salesman" }
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val snap = userRef.get().await()
            val teamId = snap.getString("activeTeamId").orEmpty()
            userRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "role" to role.name,
                    "vehiclePlate" to cleanPlate,
                    "accompaniedSalesName" to cleanSales,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (role == UserRole.ASSISTANT && teamId.isNotBlank()) {
                ensureShiftIfAssistant(uid, role.name, teamId, cleanName, cleanSales, cleanPlate) { p ->
                    path = p
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinTeamByCode(rawJoinCode: String, joinAsRole: UserRole): Result<String> {
        var path = "teams"
        return try {
            val uid = requireUserId()
            val code = sanitizeId(rawJoinCode.trim())
            require(code.length >= 4) { "Kode tim minimal 4 karakter." }
            val safeRole = if (joinAsRole == UserRole.SALES_LEAD) UserRole.SALES_LEAD else UserRole.ASSISTANT

            val teamRef = db.collection("teams").document(code)
            path = teamRef.path
            teamRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val userSnap = userRef.get().await()
            val displayName = userSnap.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Task Asistant"
            val plate = userSnap.getString("vehiclePlate")?.takeIf { it.isNotBlank() } ?: "B 9124 KWS"
            val salesName = userSnap.getString("accompaniedSalesName")?.takeIf { it.isNotBlank() } ?: "Salesman"

            userRef.update(
                mapOf(
                    "activeTeamId" to code,
                    "role" to safeRole.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (safeRole == UserRole.ASSISTANT) {
                ensureShiftIfAssistant(uid, safeRole.name, code, displayName, salesName, plate) { p ->
                    path = p
                }
            }
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Planogram Management (Admin Upload & Assistant View) ---

    suspend fun publishPlanogram(
        team: Team,
        title: String,
        versionCode: String,
        cycleName: String,
        category: String,
        shelfInstructions: String,
        promoHighlight: String,
        imageBase64: String?,
        setAsActive: Boolean,
        existingActivePlanograms: List<Planogram>
    ): Result<Unit> {
        var path = "planograms"
        return try {
            val uid = requireUserId()
            val userName = (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Admin").take(80)
            val cleanTitle = title.trim().take(100)
            val cleanVersion = versionCode.trim().take(32)
            val cleanCycle = cycleName.trim().take(60)
            val cleanCategory = category.trim().take(50)
            val cleanInstr = shelfInstructions.trim().take(1500)
            require(cleanTitle.length >= 2) { "Judul planogram minimal 2 karakter." }
            require(cleanVersion.isNotBlank()) { "Versi planogram wajib diisi." }
            require(cleanInstr.isNotBlank()) { "Instruksi rak planogram wajib diisi." }

            val nextRevision = (existingActivePlanograms.maxOfOrNull { it.revision } ?: 0L) + 1L
            val memberUids = team.memberUids.distinct().take(20).ifEmpty { listOf(uid) }

            // Deactivate previously active planograms when publishing a new latest active version
            if (setAsActive) {
                for (prev in existingActivePlanograms.filter { it.active && it.teamId == team.id }) {
                    val prevRef = db.collection("planograms").document(prev.id)
                    path = prevRef.path
                    prevRef.update(
                        mapOf(
                            "active" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val newDoc = db.collection("planograms").document()
            path = newDoc.path
            val payload = mutableMapOf<String, Any>(
                "teamId" to team.id,
                "createdByUid" to uid,
                "createdByName" to userName,
                "memberUids" to memberUids,
                "title" to cleanTitle,
                "versionCode" to cleanVersion,
                "cycleName" to cleanCycle.ifBlank { "Cycle Aktif" },
                "category" to cleanCategory.ifBlank { "Umum" },
                "shelfInstructions" to cleanInstr,
                "promoHighlight" to promoHighlight.trim().take(300),
                "active" to setAsActive,
                "revision" to nextRevision,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!imageBase64.isNullOrBlank() && imageBase64.length <= 350_000) {
                payload["imageBase64"] = imageBase64
            }
            newDoc.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun setPlanogramActive(
        planogram: Planogram,
        allPlanograms: List<Planogram>
    ): Result<Unit> {
        var path = "planograms/${planogram.id}"
        return try {
            requireUserId()
            for (item in allPlanograms.filter { it.teamId == planogram.teamId && it.active && it.id != planogram.id }) {
                db.collection("planograms").document(item.id)
                    .update(mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
            db.collection("planograms").document(planogram.id)
                .update(mapOf("active" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlanogram(planogramId: String): Result<Unit> {
        val path = "planograms/$planogramId"
        return try {
            requireUserId()
            db.collection("planograms").document(planogramId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CSU Active Outlets & Geotag Management (Admin Upload) ---

    suspend fun addCsuOutlet(
        team: Team,
        csuCode: String,
        storeName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        assignedAssistantName: String,
        priority: CsuPriority,
        active: Boolean = true
    ): Result<Unit> {
        var path = "csu_outlets"
        return try {
            val uid = requireUserId()
            val cleanCode = csuCode.trim().take(32)
            val cleanStore = storeName.trim().take(100)
            require(cleanCode.isNotBlank()) { "Kode CSU wajib diisi." }
            require(cleanStore.length >= 2) { "Nama toko CSU minimal 2 karakter." }
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Koordinat geotag tidak valid." }

            val memberUids = team.memberUids.distinct().take(20).ifEmpty { listOf(uid) }
            val docRef = db.collection("csu_outlets").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "teamId" to team.id,
                    "createdByUid" to uid,
                    "memberUids" to memberUids,
                    "csuCode" to cleanCode,
                    "storeName" to cleanStore,
                    "address" to address.trim().take(200),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "assignedAssistantName" to assignedAssistantName.trim().take(80),
                    "priority" to priority.name,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleCsuActive(csuId: String, active: Boolean): Result<Unit> {
        val path = "csu_outlets/$csuId"
        return try {
            requireUserId()
            db.collection("csu_outlets").document(csuId)
                .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCsuOutlet(csuId: String): Result<Unit> {
        val path = "csu_outlets/$csuId"
        return try {
            requireUserId()
            db.collection("csu_outlets").document(csuId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Sales Assistant Roster Management (Admin Upload) ---

    suspend fun addSalesAssistant(
        team: Team,
        employeeCode: String,
        fullName: String,
        vehiclePlate: String,
        pairedSalesName: String,
        region: String,
        active: Boolean = true
    ): Result<Unit> {
        var path = "sales_assistants"
        return try {
            val uid = requireUserId()
            val cleanCode = employeeCode.trim().take(32)
            val cleanName = fullName.trim().take(80)
            require(cleanCode.isNotBlank()) { "Kode Task Asistant wajib diisi." }
            require(cleanName.length >= 2) { "Nama Task Asistant minimal 2 karakter." }

            val memberUids = team.memberUids.distinct().take(20).ifEmpty { listOf(uid) }
            val docRef = db.collection("sales_assistants").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "teamId" to team.id,
                    "createdByUid" to uid,
                    "memberUids" to memberUids,
                    "employeeCode" to cleanCode,
                    "fullName" to cleanName,
                    "vehiclePlate" to vehiclePlate.trim().take(24),
                    "pairedSalesName" to pairedSalesName.trim().take(80),
                    "region" to region.trim().take(60),
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleAssistantActive(assistantId: String, active: Boolean): Result<Unit> {
        val path = "sales_assistants/$assistantId"
        return try {
            requireUserId()
            db.collection("sales_assistants").document(assistantId)
                .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSalesAssistant(assistantId: String): Result<Unit> {
        val path = "sales_assistants/$assistantId"
        return try {
            requireUserId()
            db.collection("sales_assistants").document(assistantId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Daily Shift: Live GPS, Daily Tasks, Vehicle Checks, and Sign-Off ---

    suspend fun updateShiftLocation(
        shiftId: String,
        trackingActive: Boolean,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        locationLabel: String
    ): Result<Unit> {
        val path = "daily_shifts/$shiftId"
        return try {
            requireUserId()
            db.collection("daily_shifts").document(shiftId)
                .update(
                    mapOf(
                        "trackingActive" to trackingActive,
                        "latitude" to latitude.coerceIn(-90.0, 90.0),
                        "longitude" to longitude.coerceIn(-180.0, 180.0),
                        "accuracyMeters" to accuracyMeters.coerceIn(0.0, 100000.0),
                        "locationLabel" to locationLabel.take(160),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateShiftFields(shiftId: String, fields: Map<String, Any>): Result<Unit> {
        val path = "daily_shifts/$shiftId"
        return try {
            requireUserId()
            val payload = fields.toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("daily_shifts").document(shiftId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitSalesSignOff(
        shiftId: String,
        signerName: String,
        signOffNote: String,
        signatureSvgPath: String
    ): Result<Unit> {
        val path = "daily_shifts/$shiftId"
        return try {
            val uid = requireUserId()
            val cleanSigner = signerName.trim().take(80)
            require(cleanSigner.length >= 2) { "Nama sales penandatangan wajib diisi." }
            require(signatureSvgPath.length >= 5) { "Tanda tangan digital wajib digoreskan." }

            db.collection("daily_shifts").document(shiftId)
                .update(
                    mapOf(
                        "status" to ShiftStatus.SIGNED_OFF.name,
                        "signerName" to cleanSigner,
                        "signerUid" to uid,
                        "signOffNote" to signOffNote.trim().take(400),
                        "signatureSvgPath" to signatureSvgPath.take(25000),
                        "signedOffAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Customer Visit Reports ---

    suspend fun addVisitReport(
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
    ): Result<Unit> {
        var path = "visit_reports"
        return try {
            val uid = requireUserId()
            val cleanStore = storeName.trim().take(100)
            require(cleanStore.length >= 2) { "Nama toko wajib diisi." }
            require(orderPhotoBase64.length in 10..180_000) { "Foto ambil barang order wajib diunggah (maks 180KB Base64)." }
            require(planogramPhotoBase64.length in 10..180_000) { "Foto tata planogram wajib diunggah (maks 180KB Base64)." }
            require(posmPhotoBase64.length in 10..180_000) { "Foto promosi/POSM wajib diunggah (maks 180KB Base64)." }

            val docRef = db.collection("visit_reports").document()
            path = docRef.path
            val memberUids = shift.memberUids.distinct().take(20).ifEmpty { listOf(uid) }
            val payload = mapOf<String, Any>(
                "shiftId" to shift.id,
                "teamId" to shift.teamId,
                "csuCode" to csuCode.trim().take(32),
                "assistantUid" to uid,
                "assistantName" to shift.assistantName.take(80),
                "memberUids" to memberUids,
                "storeName" to cleanStore,
                "storeAddress" to storeAddress.trim().take(200),
                "visitStatus" to VisitStatus.COMPLETED.name,
                "orderDone" to orderDone,
                "orderItemsPicked" to orderItemsPicked.trim().take(400),
                "orderPhotoBase64" to orderPhotoBase64,
                "planogramDone" to planogramDone,
                "planogramVersionRef" to planogramVersionRef.trim().take(40),
                "planogramPhotoBase64" to planogramPhotoBase64,
                "promoDone" to promoDone,
                "promoCycleName" to promoCycleName.trim().take(60),
                "posmPhotoBase64" to posmPhotoBase64,
                "latitude" to latitude.coerceIn(-90.0, 90.0),
                "longitude" to longitude.coerceIn(-180.0, 180.0),
                "notes" to notes.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Vehicle Stock Check (Before & After Daily Visits) ---

    suspend fun addVehicleStockItem(
        shift: DailyShift,
        skuCode: String,
        productName: String,
        unit: String,
        qtyBefore: Long,
        qtyAfter: Long,
        conditionOk: Boolean,
        notes: String
    ): Result<Unit> {
        var path = "vehicle_stocks"
        return try {
            val uid = requireUserId()
            val cleanSku = skuCode.trim().take(32)
            val cleanName = productName.trim().take(80)
            require(cleanSku.isNotBlank()) { "Kode SKU wajib diisi." }
            require(cleanName.length >= 2) { "Nama barang wajib diisi." }

            val docRef = db.collection("vehicle_stocks").document()
            path = docRef.path
            val memberUids = shift.memberUids.distinct().take(20).ifEmpty { listOf(uid) }
            docRef.set(
                mapOf(
                    "shiftId" to shift.id,
                    "teamId" to shift.teamId,
                    "assistantUid" to uid,
                    "memberUids" to memberUids,
                    "skuCode" to cleanSku,
                    "productName" to cleanName,
                    "unit" to unit.trim().take(20).ifBlank { "Karton" },
                    "qtyBefore" to qtyBefore.coerceIn(0L, 100000L),
                    "qtyAfter" to qtyAfter.coerceIn(0L, 100000L),
                    "conditionOk" to conditionOk,
                    "notes" to notes.trim().take(200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehicleStockItem(
        stockId: String,
        qtyBefore: Long,
        qtyAfter: Long,
        conditionOk: Boolean,
        notes: String
    ): Result<Unit> {
        val path = "vehicle_stocks/$stockId"
        return try {
            requireUserId()
            db.collection("vehicle_stocks").document(stockId)
                .update(
                    mapOf(
                        "qtyBefore" to qtyBefore.coerceIn(0L, 100000L),
                        "qtyAfter" to qtyAfter.coerceIn(0L, 100000L),
                        "conditionOk" to conditionOk,
                        "notes" to notes.trim().take(200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVehicleStockItem(stockId: String): Result<Unit> {
        val path = "vehicle_stocks/$stockId"
        return try {
            requireUserId()
            db.collection("vehicle_stocks").document(stockId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
