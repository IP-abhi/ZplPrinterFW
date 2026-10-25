package com.printer.zpl.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaLoader {

    /**
     * Loads a Bitmap from a content Uri (image or PDF page 1 at 203 DPI).
     */
    suspend fun loadFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
                    uri.path?.endsWith(".pdf", ignoreCase = true) == true

            if (isPdf) {
                renderPdfFirstPage(context, uri)
            } else {
                decodeImage(context, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun renderPdfFirstPage(context: Context, uri: Uri): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdf ->
                if (pdf.pageCount == 0) return null
                val page = pdf.openPage(0)
                page.use { pg ->
                    // Standard PDF page is 72 points/inch.
                    // Thermal label printers are commonly 203 DPI (8 dots/mm).
                    val scale = 203f / 72f
                    val w = (pg.width * scale).toInt().coerceAtLeast(1)
                    val h = (pg.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bmp
                }
            }
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    /**
     * Saves a bitmap as PNG to the device's Pictures gallery or app storage.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        return try {
            val filename = "${title}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ThermalStudio")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    true
                } else {
                    false
                }
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val appDir = java.io.File(dir, "ThermalStudio")
                if (!appDir.exists()) appDir.mkdirs()
                val file = java.io.File(appDir, filename)
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
