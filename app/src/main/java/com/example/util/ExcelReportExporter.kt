package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.model.DailyShift
import com.example.data.model.ShiftStatus
import com.example.data.model.VisitReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DriverMonthlySummary(
    val assistantName: String,
    val monthKey: String,
    val totalShifts: Int,
    val signedOffShifts: Int,
    val outletsExecuted: Int,
    val completePhotoVisits: Int,
    val preStockChecksDone: Int,
    val postStockChecksDone: Int,
    val cleanlinessCompleteDone: Int,
    val compliancePercent: Int
)

object ExcelReportExporter {

    fun currentMonthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun shiftMonthKey(shift: DailyShift): String {
        val raw = shift.shiftDate.trim()
        return if (raw.length >= 7) raw.substring(0, 7) else currentMonthKey()
    }

    fun visitMonthKey(visit: VisitReport, shiftById: Map<String, DailyShift>): String {
        val fromShift = shiftById[visit.shiftId]?.shiftDate?.takeIf { it.length >= 7 }?.substring(0, 7)
        if (!fromShift.isNullOrBlank()) return fromShift
        val ts = visit.createdAt?.toDate()
        return if (ts != null) SimpleDateFormat("yyyy-MM", Locale.US).format(ts) else currentMonthKey()
    }

    fun buildMonthlySummaries(
        monthFilter: String?,
        shifts: List<DailyShift>,
        visits: List<VisitReport>
    ): List<DriverMonthlySummary> {
        val shiftById = shifts.associateBy { it.id }
        val filteredShifts = if (monthFilter.isNullOrBlank()) shifts else shifts.filter { shiftMonthKey(it) == monthFilter }
        val filteredVisits = if (monthFilter.isNullOrBlank()) visits else visits.filter { visitMonthKey(it, shiftById) == monthFilter }

        val driverNames = (filteredShifts.map { it.assistantName } + filteredVisits.map { it.assistantName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        return driverNames.map { name ->
            val drvShifts = filteredShifts.filter { it.assistantName.trim() == name }
            val drvVisits = filteredVisits.filter { it.assistantName.trim() == name }

            val signedOff = drvShifts.count { it.status == ShiftStatus.SIGNED_OFF.name }
            val preOk = drvShifts.count { it.preStockChecked }
            val postOk = drvShifts.count { it.postStockChecked }
            val cleanOk = drvShifts.count { it.cleanlinessChecked && it.cleanlinessFileBase64.isNotBlank() }
            val completePhotos = drvVisits.count { it.hasAllMandatoryPhotos() }

            val totalChecksPossible = drvShifts.size * 3 + drvVisits.size
            val totalChecksAchieved = preOk + postOk + cleanOk + completePhotos
            val pct = if (totalChecksPossible > 0) {
                ((totalChecksAchieved * 100.0) / totalChecksPossible).toInt().coerceIn(0, 100)
            } else 0

            DriverMonthlySummary(
                assistantName = name,
                monthKey = monthFilter ?: "Semua",
                totalShifts = drvShifts.size,
                signedOffShifts = signedOff,
                outletsExecuted = drvVisits.size,
                completePhotoVisits = completePhotos,
                preStockChecksDone = preOk,
                postStockChecksDone = postOk,
                cleanlinessCompleteDone = cleanOk,
                compliancePercent = pct
            )
        }
    }

    fun buildExcelWorkbookBytes(
        monthFilter: String?,
        shifts: List<DailyShift>,
        visits: List<VisitReport>
    ): ByteArray {
        val shiftById = shifts.associateBy { it.id }
        val filteredShifts = if (monthFilter.isNullOrBlank()) shifts else shifts.filter { shiftMonthKey(it) == monthFilter }
        val filteredVisits = if (monthFilter.isNullOrBlank()) visits else visits.filter { visitMonthKey(it, shiftById) == monthFilter }
        val summaries = buildMonthlySummaries(monthFilter, shifts, visits)

        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        // Sheet 1: Ringkasan Bulanan Task Asistant
        val sheet1Rows = mutableListOf<List<Any>>(
            listOf(
                "Bulan Review", "Nama Task Asistant", "Total Shift", "Shift Disahkan Salesman",
                "Total Outlet Dieksekusi", "Kunjungan Lengkap 3 Foto",
                "Check Stock Pagi", "Check Stock Sore", "Kebersihan + File", "Skor Kepatuhan (%)"
            )
        )
        summaries.forEach { s ->
            sheet1Rows += listOf(
                s.monthKey,
                s.assistantName,
                s.totalShifts,
                s.signedOffShifts,
                s.outletsExecuted,
                s.completePhotoVisits,
                s.preStockChecksDone,
                s.postStockChecksDone,
                s.cleanlinessCompleteDone,
                s.compliancePercent
            )
        }

        // Sheet 2: Detail Kunjungan Outlet & Rekaman Foto
        val sheet2Rows = mutableListOf<List<Any>>(
            listOf(
                "ID Laporan", "Waktu Kunjungan", "Nama Task Asistant", "Kode CSU", "Nama Outlet / Toko",
                "Alamat", "Latitude", "Longitude", "Order Diambil",
                "Ambil Order Selesai", "File Foto Order",
                "Planogram Selesai", "Versi Planogram", "File Foto Planogram",
                "POSM / Promo Selesai", "Cycle Promo", "File Foto POSM",
                "Kelengkapan 3 Foto", "Catatan Kunjungan"
            )
        )
        filteredVisits.forEachIndexed { idx, v ->
            val slug = safeFileSlug("${idx + 1}_${v.csuCode.ifBlank { "OUTLET" }}_${v.storeName}")
            val tsText = v.createdAt?.toDate()?.let { timeFmt.format(it) }
                ?: shiftById[v.shiftId]?.shiftDate.orEmpty()
            sheet2Rows += listOf(
                v.id.ifBlank { "visit_${idx + 1}" },
                tsText,
                v.assistantName,
                v.csuCode.ifBlank { "-" },
                v.storeName,
                v.storeAddress.ifBlank { "-" },
                v.latitude,
                v.longitude,
                v.orderItemsPicked.ifBlank { "-" },
                if (v.orderDone) "YA" else "TIDAK",
                if (v.orderPhotoBase64.isNotBlank()) "foto_kunjungan/${slug}_order.jpg" else "TIDAK ADA",
                if (v.planogramDone) "YA" else "TIDAK",
                v.planogramVersionRef.ifBlank { "-" },
                if (v.planogramPhotoBase64.isNotBlank()) "foto_kunjungan/${slug}_planogram.jpg" else "TIDAK ADA",
                if (v.promoDone) "YA" else "TIDAK",
                v.promoCycleName.ifBlank { "-" },
                if (v.posmPhotoBase64.isNotBlank()) "foto_kunjungan/${slug}_posm.jpg" else "TIDAK ADA",
                if (v.hasAllMandatoryPhotos()) "LENGKAP (3/3)" else "BELUM LENGKAP",
                v.notes.ifBlank { "-" }
            )
        }

        // Sheet 3: Checklist Armada & Kebersihan Harian
        val sheet3Rows = mutableListOf<List<Any>>(
            listOf(
                "Tanggal Shift", "Nama Task Asistant", "Salesman Didampingi", "Plat Kendaraan",
                "Check Stock Pagi", "Check Stock Sore", "Kebersihan 4 Poin",
                "File Bukti Kebersihan", "Status Shift", "Penandatangan Salesman"
            )
        )
        filteredShifts.forEachIndexed { idx, s ->
            val cleanFileRef = if (s.cleanlinessFileBase64.isNotBlank()) {
                val safeName = safeFileSlug(s.cleanlinessFileName.ifBlank { "bukti_${s.shiftDate}_${idx + 1}.jpg" })
                "bukti_kebersihan/$safeName"
            } else "TIDAK ADA"
            sheet3Rows += listOf(
                s.shiftDate,
                s.assistantName,
                s.accompaniedSalesName,
                s.vehiclePlate,
                if (s.preStockChecked) "YA" else "BELUM",
                if (s.postStockChecked) "YA" else "BELUM",
                if (s.cleanlinessChecked) "YA" else "BELUM",
                cleanFileRef,
                ShiftStatus.fromRaw(s.status).label,
                s.signerName.ifBlank { "-" }
            )
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipText(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeZipText(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipText(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeZipText(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipText(zip, "xl/styles.xml", STYLES_XML)
            writeZipText(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(sheet1Rows))
            writeZipText(zip, "xl/worksheets/sheet2.xml", buildWorksheetXml(sheet2Rows))
            writeZipText(zip, "xl/worksheets/sheet3.xml", buildWorksheetXml(sheet3Rows))
        }
        return baos.toByteArray()
    }

    fun buildExcelAndPhotosZipBytes(
        monthFilter: String?,
        shifts: List<DailyShift>,
        visits: List<VisitReport>
    ): ByteArray {
        val shiftById = shifts.associateBy { it.id }
        val filteredShifts = if (monthFilter.isNullOrBlank()) shifts else shifts.filter { shiftMonthKey(it) == monthFilter }
        val filteredVisits = if (monthFilter.isNullOrBlank()) visits else visits.filter { visitMonthKey(it, shiftById) == monthFilter }

        val excelBytes = buildExcelWorkbookBytes(monthFilter, shifts, visits)
        val periodTag = monthFilter ?: "Semua_Bulan"

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("Laporan_Bulanan_Task_Asistant_$periodTag.xlsx"))
            zip.write(excelBytes)
            zip.closeEntry()

            val usedPaths = mutableSetOf<String>()
            filteredVisits.forEachIndexed { idx, v ->
                val slug = safeFileSlug("${idx + 1}_${v.csuCode.ifBlank { "OUTLET" }}_${v.storeName}")
                writeBase64Entry(zip, "foto_kunjungan/${slug}_order.jpg", v.orderPhotoBase64, usedPaths)
                writeBase64Entry(zip, "foto_kunjungan/${slug}_planogram.jpg", v.planogramPhotoBase64, usedPaths)
                writeBase64Entry(zip, "foto_kunjungan/${slug}_posm.jpg", v.posmPhotoBase64, usedPaths)
            }

            filteredShifts.forEachIndexed { idx, s ->
                if (s.cleanlinessFileBase64.isNotBlank()) {
                    val rawName = s.cleanlinessFileName.ifBlank { "bukti_${s.shiftDate}_${idx + 1}.jpg" }
                    val ext = if (rawName.lowercase(Locale.US).endsWith(".pdf")) "pdf" else "jpg"
                    val base = safeFileSlug("${s.shiftDate}_${s.assistantName}_$rawName")
                    val finalName = if (base.lowercase(Locale.US).endsWith(".$ext")) base else "$base.$ext"
                    writeBase64Entry(zip, "bukti_kebersihan/$finalName", s.cleanlinessFileBase64, usedPaths)
                }
            }
        }
        return baos.toByteArray()
    }

    suspend fun writeBytesToUri(
        context: Context,
        uri: Uri,
        bytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out: OutputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Tidak dapat membuka tujuan penyimpanan.")
            out.use { it.write(bytes) }
        }
    }

    private fun writeBase64Entry(
        zip: ZipOutputStream,
        entryName: String,
        base64: String,
        usedPaths: MutableSet<String>
    ) {
        if (base64.isBlank()) return
        val bytes = runCatching {
            val clean = base64.substringAfter("base64,", base64)
            Base64.decode(clean, Base64.DEFAULT)
        }.getOrNull() ?: return
        if (bytes.isEmpty()) return

        var uniqueName = entryName
        var counter = 2
        while (!usedPaths.add(uniqueName)) {
            val dot = entryName.lastIndexOf('.')
            uniqueName = if (dot > 0) {
                "${entryName.substring(0, dot)}_$counter${entryName.substring(dot)}"
            } else {
                "${entryName}_$counter"
            }
            counter++
        }
        zip.putNextEntry(ZipEntry(uniqueName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeZipText(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildWorksheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, cols ->
            val rowNum = rIdx + 1
            sb.append("""<row r="$rowNum">""")
            cols.forEachIndexed { cIdx, value ->
                val ref = "${colIndexToName(cIdx)}$rowNum"
                val styleAttr = if (rIdx == 0) """ s="1"""" else ""
                when (value) {
                    is Number -> {
                        val d = value.toDouble()
                        if (d.isFinite()) {
                            sb.append("""<c r="$ref"$styleAttr t="n"><v>$value</v></c>""")
                        } else {
                            sb.append("""<c r="$ref"$styleAttr t="inlineStr"><is><t>0</t></is></c>""")
                        }
                    }
                    else -> {
                        val escaped = xmlEscape(value.toString())
                        sb.append("""<c r="$ref"$styleAttr t="inlineStr"><is><t>$escaped</t></is></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colIndexToName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun xmlEscape(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    if (code == 0x9 || code == 0xA || code == 0xD || code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun safeFileSlug(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(56)
            .ifBlank { "item" }

    private const val CONTENT_TYPES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Review Bulanan Task Asistant" sheetId="1" r:id="rId1"/>
    <sheet name="Detail Kunjungan Foto" sheetId="2" r:id="rId2"/>
    <sheet name="Checklist Armada" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0A5A4D"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
}
