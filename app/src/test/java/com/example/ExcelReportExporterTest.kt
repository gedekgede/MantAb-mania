package com.example

import android.util.Base64
import com.example.data.model.DailyShift
import com.example.data.model.ShiftStatus
import com.example.data.model.VisitReport
import com.example.util.ExcelReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class ExcelReportExporterTest {

    @Test
    fun buildsValidXlsxAndZipWithPhotos() {
        val sampleB64 = Base64.encodeToString(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()), Base64.NO_WRAP)

        val shifts = listOf(
            DailyShift(
                id = "s1",
                assistantName = "Budi Santoso",
                accompaniedSalesName = "Andi Wijaya",
                vehiclePlate = "B 9124 KWS",
                shiftDate = "2026-11-18",
                status = ShiftStatus.SIGNED_OFF.name,
                preStockChecked = true,
                postStockChecked = true,
                cleanlinessChecked = true,
                cleanlinessFileName = "bersih.jpg",
                cleanlinessFileBase64 = sampleB64,
                signerName = "Andi Wijaya"
            )
        )
        val visits = listOf(
            VisitReport(
                id = "v1",
                shiftId = "s1",
                csuCode = "CSU-01",
                assistantName = "Budi Santoso",
                storeName = "Toko Maju & Jaya",
                storeAddress = "Jl. Kemang",
                orderDone = true,
                orderItemsPicked = "3 Karton",
                orderPhotoBase64 = sampleB64,
                planogramDone = true,
                planogramVersionRef = "v2026.11",
                planogramPhotoBase64 = sampleB64,
                promoDone = true,
                promoCycleName = "Cycle 11",
                posmPhotoBase64 = sampleB64,
                latitude = -6.26,
                longitude = 106.81
            )
        )

        val summaries = ExcelReportExporter.buildMonthlySummaries("2026-11", shifts, visits)
        assertEquals(1, summaries.size)
        assertEquals(100, summaries.first().compliancePercent)
        assertEquals(1, summaries.first().outletsExecuted)

        val xlsxBytes = ExcelReportExporter.buildExcelWorkbookBytes("2026-11", shifts, visits)
        val xlsxEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                xlsxEntries += entry.name
            }
        }
        assertTrue("[Content_Types].xml" in xlsxEntries)
        assertTrue("xl/workbook.xml" in xlsxEntries)
        assertTrue("xl/worksheets/sheet1.xml" in xlsxEntries)
        assertTrue("xl/worksheets/sheet2.xml" in xlsxEntries)
        assertTrue("xl/worksheets/sheet3.xml" in xlsxEntries)

        val zipBytes = ExcelReportExporter.buildExcelAndPhotosZipBytes("2026-11", shifts, visits)
        val zipEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                zipEntries += entry.name
            }
        }
        assertTrue(zipEntries.any { it.endsWith(".xlsx") })
        assertTrue(zipEntries.any { it.startsWith("foto_kunjungan/") && it.endsWith("_order.jpg") })
        assertTrue(zipEntries.any { it.startsWith("foto_kunjungan/") && it.endsWith("_planogram.jpg") })
        assertTrue(zipEntries.any { it.startsWith("foto_kunjungan/") && it.endsWith("_posm.jpg") })
        assertTrue(zipEntries.any { it.startsWith("bukti_kebersihan/") })
    }
}
