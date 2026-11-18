package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole(val label: String) {
    ASSISTANT("Task Asistant"),
    SALES_LEAD("Salesman"),
    ADMIN("Admin / Supervisor");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: ASSISTANT
    }
}

enum class ShiftStatus(val label: String) {
    ACTIVE("Aktif Bertugas"),
    PENDING_SIGNOFF("Menunggu Sign-Off"),
    SIGNED_OFF("Disahkan Sales");

    companion object {
        fun fromRaw(raw: String?): ShiftStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class CsuPriority(val label: String) {
    WAJIB("Wajib Kunjung"),
    PRIORITAS("Prioritas"),
    REGULER("Reguler");

    companion object {
        fun fromRaw(raw: String?): CsuPriority =
            entries.firstOrNull { it.name == raw } ?: WAJIB
    }
}

enum class VisitStatus(val label: String) {
    CHECKED_IN("Check-In"),
    COMPLETED("Selesai");

    companion object {
        fun fromRaw(raw: String?): VisitStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = UserRole.ASSISTANT.name,
    val activeTeamId: String = "",
    val vehiclePlate: String = "",
    val accompaniedSalesName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Team(
    @DocumentId val id: String = "",
    val teamName: String = "",
    val ownerUid: String = "",
    val joinCode: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Planogram(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val memberUids: List<String> = emptyList(),
    val title: String = "",
    val versionCode: String = "",
    val cycleName: String = "",
    val category: String = "",
    val shelfInstructions: String = "",
    val promoHighlight: String = "",
    val imageBase64: String = "",
    val active: Boolean = true,
    val revision: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CsuOutlet(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    val csuCode: String = "",
    val storeName: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val assignedAssistantName: String = "",
    val priority: String = CsuPriority.WAJIB.name,
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SalesAssistant(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeCode: String = "",
    val fullName: String = "",
    val vehiclePlate: String = "",
    val pairedSalesName: String = "",
    val region: String = "",
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DailyShift(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val assistantUid: String = "",
    val assistantName: String = "",
    val accompaniedSalesName: String = "",
    val vehiclePlate: String = "",
    val shiftDate: String = "",
    val activeCycleName: String = "",
    val memberUids: List<String> = emptyList(),
    val status: String = ShiftStatus.ACTIVE.name,
    val trackingActive: Boolean = false,
    val latitude: Double = -6.2088,
    val longitude: Double = 106.8456,
    val accuracyMeters: Double = 0.0,
    val locationLabel: String = "",
    val taskOrderPicked: Boolean = false,
    val taskOrderNote: String = "",
    val taskPlanogramArranged: Boolean = false,
    val taskPlanogramNote: String = "",
    val taskPromoSetup: Boolean = false,
    val taskPromoNote: String = "",
    val preStockChecked: Boolean = false,
    val preStockSummary: String = "",
    val postStockChecked: Boolean = false,
    val postStockSummary: String = "",
    val cleanlinessChecked: Boolean = false,
    val cleanlinessExteriorOk: Boolean = false,
    val cleanlinessCabinOk: Boolean = false,
    val cleanlinessGlassOk: Boolean = false,
    val cleanlinessTrashFree: Boolean = false,
    val cleanlinessNote: String = "",
    val cleanlinessFileName: String = "",
    val cleanlinessFileBase64: String = "",
    val signerName: String = "",
    val signerUid: String = "",
    val signOffNote: String = "",
    val signatureSvgPath: String = "",
    val signedOffAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun completedDailyChecksCount(): Int = listOf(
        preStockChecked,
        cleanlinessChecked,
        postStockChecked
    ).count { it }
}

@Keep
@IgnoreExtraProperties
data class VisitReport(
    @DocumentId val id: String = "",
    val shiftId: String = "",
    val teamId: String = "",
    val csuCode: String = "",
    val assistantUid: String = "",
    val assistantName: String = "",
    val memberUids: List<String> = emptyList(),
    val storeName: String = "",
    val storeAddress: String = "",
    val visitStatus: String = VisitStatus.COMPLETED.name,
    val orderDone: Boolean = true,
    val orderItemsPicked: String = "",
    val orderPhotoBase64: String = "",
    val planogramDone: Boolean = false,
    val planogramVersionRef: String = "",
    val planogramPhotoBase64: String = "",
    val promoDone: Boolean = false,
    val promoCycleName: String = "",
    val posmPhotoBase64: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun hasAllMandatoryPhotos(): Boolean =
        orderPhotoBase64.isNotBlank() &&
            planogramPhotoBase64.isNotBlank() &&
            posmPhotoBase64.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class VehicleStockItem(
    @DocumentId val id: String = "",
    val shiftId: String = "",
    val teamId: String = "",
    val assistantUid: String = "",
    val memberUids: List<String> = emptyList(),
    val skuCode: String = "",
    val productName: String = "",
    val unit: String = "",
    val qtyBefore: Long = 0L,
    val qtyAfter: Long = 0L,
    val conditionOk: Boolean = true,
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
