package com.printer.zpl.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.roundToInt

object BitmapUtils {

    /**
     * Trims surrounding white borders from labels or documents.
     */
    fun trimWhitespace(source: Bitmap, threshold: Int = 245): Bitmap {
        val width = source.width
        val height = source.height
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        val pixels = IntArray(width)
        for (y in 0 until height) {
            source.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = pixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < threshold || g < threshold || b < threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source // Empty / solid white
        }

        val pad = 8
        val cropX = (minX - pad).coerceAtLeast(0)
        val cropY = (minY - pad).coerceAtLeast(0)
        val cropW = (maxX - minX + 1 + 2 * pad).coerceAtMost(width - cropX)
        val cropH = (maxY - minY + 1 + 2 * pad).coerceAtMost(height - cropY)

        return Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
    }

    /**
     * Rotates a bitmap by [degrees] clockwise.
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Prepares bitmap for direct thermal printing:
     * - Ensures ARGB_8888 config
     * - Optionally trims outer whitespace
     * - Auto-rotates landscape media to portrait label orientation if desired
     * - Scales to target dot dimensions with aspect ratio preservation
     * - Centers on white canvas
     */
    fun prepareBitmapForPrint(
        source: Bitmap,
        targetWidth: Int = 812,
        targetHeight: Int = 1218,
        autoRotate: Boolean = false,
        marginDots: Int = 0,
        trimMargins: Boolean = false
    ): Bitmap {
        var src = source
        if (src.config != Bitmap.Config.ARGB_8888) {
            src = src.copy(Bitmap.Config.ARGB_8888, true) ?: src
        }

        if (trimMargins) {
            src = trimWhitespace(src)
        }

        if (autoRotate && src.width > src.height && targetWidth < targetHeight) {
            src = rotateBitmap(src, 90f)
        }

        val availableW = (targetWidth - 2 * marginDots).coerceAtLeast(100)
        val availableH = (targetHeight - 2 * marginDots).coerceAtLeast(100)

        val scale = minOf(
            availableW.toFloat() / src.width.toFloat(),
            availableH.toFloat() / src.height.toFloat()
        )

        val scaledW = (src.width * scale).roundToInt().coerceAtLeast(1)
        val scaledH = (src.height * scale).roundToInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)

        val canvasBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.WHITE)

        val left = (targetWidth - scaledW) / 2f
        val top = (targetHeight - scaledH) / 2f
        canvas.drawBitmap(scaled, left, top, null)

        return canvasBitmap
    }
}
