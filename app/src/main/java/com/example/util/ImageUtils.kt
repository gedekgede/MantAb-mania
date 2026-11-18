package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    suspend fun encodeUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBytes: Int = 180_000
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return@runCatching null

            var sampleSize = 1
            while (max(origW / sampleSize, origH / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val scale = maxDimension.toFloat() / max(decoded.width, decoded.height).toFloat()
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            var quality = 75
            var bytes: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                bytes = baos.toByteArray()
                quality -= 15
            } while (bytes.size > maxBytes && quality >= 25)

            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            if (bytes.size > maxBytes) null
            else Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    suspend fun encodeFileUriToBase64(
        context: Context,
        uri: Uri,
        maxRawBytes: Int = 180_000
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri).orEmpty()
            val fileName = resolveDisplayName(context, uri)
            val base64 = if (mimeType.startsWith("image/")) {
                encodeUriToBase64(context, uri, maxDimension = 720, maxBytes = maxRawBytes)
            } else {
                val rawBytes = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        total += read
                        if (total > maxRawBytes) return@use null
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                } ?: return@runCatching null
                if (rawBytes.isEmpty()) return@runCatching null
                Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            }
            if (base64.isNullOrBlank()) null else fileName.take(120) to base64
        }.getOrNull()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "bukti_kebersihan_${System.currentTimeMillis()}"
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return runCatching {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}
