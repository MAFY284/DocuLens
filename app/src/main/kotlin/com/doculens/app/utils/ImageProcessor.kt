package com.doculens.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.doculens.app.domain.DocFilter
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.QuadPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageProcessor {

    fun decodeUri(context: Context, uri: Uri, maxDim: Int = 2600): Bitmap? {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use { input ->
                runCatching { ExifInterface(input) }.getOrNull()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val oriented = applyExif(decoded, exif)
            scaleToMax(oriented, maxDim)
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeFile(file: File, maxDim: Int = 2600): Bitmap? {
        return try {
            val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val oriented = applyExif(decoded, exif)
            scaleToMax(oriented, maxDim)
        } catch (_: Throwable) {
            null
        }
    }

    fun scaleToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (maxDim <= 0) return bitmap
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun previewCopy(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (maxDim <= 0 || longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (maxDim <= 0) return 1
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun applyExif(bitmap: Bitmap, exif: ExifInterface?): Bitmap {
        if (exif == null) return bitmap
        val rotation = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (rotation == 0 && !flipped) return bitmap
        val matrix = Matrix()
        if (flipped) matrix.postScale(-1f, 1f)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        return try {
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (result !== bitmap) bitmap.recycle()
            result
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun warp(bitmap: Bitmap, quad: DocumentQuad, maxOutput: Int = 2600): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val p = quad.points().map { QuadPoint(it.x * w, it.y * h) }
        if (polygonArea(p) < w * h * 0.01f) return bitmap
        val top = distance(p[0], p[1])
        val bottom = distance(p[3], p[2])
        val left = distance(p[0], p[3])
        val right = distance(p[1], p[2])
        var outW = max(top, bottom).roundToInt().coerceAtLeast(16)
        var outH = max(left, right).roundToInt().coerceAtLeast(16)
        val longest = max(outW, outH)
        if (maxOutput > 0 && longest > maxOutput) {
            val scale = maxOutput.toFloat() / longest
            outW = (outW * scale).roundToInt().coerceAtLeast(16)
            outH = (outH * scale).roundToInt().coerceAtLeast(16)
        }
        val src = floatArrayOf(
            p[0].x, p[0].y,
            p[1].x, p[1].y,
            p[2].x, p[2].y,
            p[3].x, p[3].y
        )
        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        return try {
            val matrix = Matrix()
            if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return bitmap
            val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, matrix, paint)
            result
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun applyFilter(
        source: Bitmap,
        filter: DocFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float
    ): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val bright = brightness.coerceIn(-1f, 1f) * 80f
        val factor = (1f + contrast.coerceIn(-1f, 1f) * 0.9f).coerceIn(0.15f, 2.2f)
        val out = IntArray(total)

        when (filter) {
            DocFilter.BLACK_WHITE -> adaptiveBlackWhite(pixels, out, w, h, bright, factor)
            DocFilter.GRAYSCALE -> {
                val lut = buildLut(factor, bright)
                for (i in 0 until total) {
                    val c = pixels[i]
                    val gray = (((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8)
                    val v = lut[gray.coerceIn(0, 255)]
                    out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
            DocFilter.ENHANCE -> enhanced(pixels, out, w, h, bright, factor)
            DocFilter.CLEAN -> cleaned(source, pixels, out, w, h, bright, factor)
            DocFilter.HIGH_CONTRAST -> {
                val strong = (factor * 1.75f).coerceIn(0.2f, 4f)
                val lut = buildLut(strong, bright)
                for (i in 0 until total) {
                    val c = pixels[i]
                    val r = lut[(c shr 16 and 0xFF)]
                    val g = lut[(c shr 8 and 0xFF)]
                    val b = lut[(c and 0xFF)]
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            DocFilter.ORIGINAL -> {
                val lut = buildLut(factor, bright)
                for (i in 0 until total) {
                    val c = pixels[i]
                    val r = lut[(c shr 16 and 0xFF)]
                    val g = lut[(c shr 8 and 0xFF)]
                    val b = lut[(c and 0xFF)]
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        if (sharpness > 0.02f) {
            unsharpMask(out, w, h, sharpness.coerceIn(0f, 1f) * 1.5f)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun enhanced(pixels: IntArray, out: IntArray, w: Int, h: Int, bright: Float, factor: Float) {
        val histR = IntArray(256)
        val histG = IntArray(256)
        val histB = IntArray(256)
        for (p in pixels) {
            histR[(p shr 16) and 0xFF]++
            histG[(p shr 8) and 0xFF]++
            histB[p and 0xFF]++
        }
        val lutR = stretchLut(histR, bright, factor)
        val lutG = stretchLut(histG, bright, factor)
        val lutB = stretchLut(histB, bright, factor)
        for (i in 0 until w * h) {
            val c = pixels[i]
            val r = lutR[(c shr 16) and 0xFF]
            val g = lutG[(c shr 8) and 0xFF]
            val b = lutB[c and 0xFF]
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun stretchLut(hist: IntArray, bright: Float, factor: Float): IntArray {
        val total = hist.sum().coerceAtLeast(1)
        val lowTarget = (total * 0.015f).toInt()
        val highTarget = (total * 0.985f).toInt()
        var low = 0
        var high = 255
        var cumulative = 0
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= lowTarget) {
                low = i
                break
            }
        }
        cumulative = 0
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= highTarget) {
                high = i
                break
            }
        }
        if (high - low < 24) {
            low = 0
            high = 255
        }
        val span = (high - low).coerceAtLeast(1).toFloat()
        return IntArray(256) { v ->
            val stretched = ((v - low) * 255f / span).coerceIn(0f, 255f)
            val adjusted = 128f + (stretched - 128f) * factor + bright
            adjusted.roundToInt().coerceIn(0, 255)
        }
    }

    private fun buildLut(factor: Float, bright: Float): IntArray {
        return IntArray(256) { v ->
            val value = 128f + (v - 128f) * factor + bright
            value.roundToInt().coerceIn(0, 255)
        }
    }

    private fun cleaned(
        source: Bitmap,
        pixels: IntArray,
        out: IntArray,
        w: Int,
        h: Int,
        bright: Float,
        factor: Float
    ) {
        val smallW = max(4, w / 16)
        val smallH = max(4, h / 16)
        val small = try {
            Bitmap.createScaledBitmap(source, smallW, smallH, true)
        } catch (_: Throwable) {
            null
        }
        val background = if (small != null) {
            try {
                Bitmap.createScaledBitmap(small, w, h, true)
            } catch (_: Throwable) {
                null
            } finally {
                if (small !== source) small.recycle()
            }
        } else {
            null
        }
        val bgPixels = if (background != null) {
            IntArray(w * h).also { background.getPixels(it, 0, w, 0, 0, w, h) }
        } else {
            null
        }
        if (background != null && background !== source) background.recycle()
        val lut = buildLut(factor, bright)
        for (i in 0 until w * h) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (bgPixels != null) {
                val bg = bgPixels[i]
                val bgLum = ((bg shr 16 and 0xFF) * 77 + (bg shr 8 and 0xFF) * 150 + (bg and 0xFF) * 29) shr 8
                val scale = (238f / bgLum.coerceAtLeast(8).toFloat()).coerceIn(0.65f, 3.2f)
                val nr = lut[(r * scale).roundToInt().coerceIn(0, 255)]
                val ng = lut[(g * scale).roundToInt().coerceIn(0, 255)]
                val nb = lut[(b * scale).roundToInt().coerceIn(0, 255)]
                out[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            } else {
                val nr = lut[r]
                val ng = lut[g]
                val nb = lut[b]
                out[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    private fun adaptiveBlackWhite(
        pixels: IntArray,
        out: IntArray,
        w: Int,
        h: Int,
        bright: Float,
        factor: Float
    ) {
        val lut = buildLut(factor, bright)
        val gray = IntArray(w * h)
        for (i in gray.indices) {
            val c = pixels[i]
            val lum = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            gray[i] = lut[lum.coerceIn(0, 255)]
        }
        val stride = w + 1
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val base = y * w
            val integralRow = (y + 1) * stride
            val integralPrev = y * stride
            for (x in 0 until w) {
                rowSum += gray[base + x].toLong()
                integral[integralRow + x + 1] = integral[integralPrev + x + 1] + rowSum
            }
        }
        val radius = max(6, min(w, h) / 48)
        for (y in 0 until h) {
            val y1 = max(0, y - radius)
            val y2 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x1 = max(0, x - radius)
                val x2 = min(w - 1, x + radius)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + x2 + 1] -
                    integral[y1 * stride + x2 + 1] -
                    integral[(y2 + 1) * stride + x1] +
                    integral[y1 * stride + x1]
                val mean = sum.toFloat() / area
                val threshold = mean * 0.88f
                val v = if (gray[y * w + x] < threshold) 0 else 255
                out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
    }

    private fun unsharpMask(pixels: IntArray, w: Int, h: Int, amount: Float) {
        if (amount <= 0f) return
        val source = pixels.copyOf()
        for (y in 0 until h) {
            val row = y * w
            val up = if (y > 0) row - w else row
            val down = if (y < h - 1) row + w else row
            for (x in 0 until w) {
                val idx = row + x
                val left = if (x > 0) idx - 1 else idx
                val right = if (x < w - 1) idx + 1 else idx
                val c = source[idx]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val leftColor = source[left]
                val rightColor = source[right]
                val upColor = source[up + x]
                val downColor = source[down + x]
                val ar = ((leftColor shr 16 and 0xFF) + (rightColor shr 16 and 0xFF) +
                    (upColor shr 16 and 0xFF) + (downColor shr 16 and 0xFF)) / 4f
                val ag = ((leftColor shr 8 and 0xFF) + (rightColor shr 8 and 0xFF) +
                    (upColor shr 8 and 0xFF) + (downColor shr 8 and 0xFF)) / 4f
                val ab = ((leftColor and 0xFF) + (rightColor and 0xFF) +
                    (upColor and 0xFF) + (downColor and 0xFF)) / 4f
                val nr = (cr + amount * (cr - ar)).roundToInt().coerceIn(0, 255)
                val ng = (cg + amount * (cg - ag)).roundToInt().coerceIn(0, 255)
                val nb = (cb + amount * (cb - ab)).roundToInt().coerceIn(0, 255)
                pixels[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    fun applyEraser(bitmap: Bitmap, strokes: List<List<QuadPoint>>, brushFraction: Float = 0.035f): Bitmap {
        if (strokes.isEmpty()) return bitmap
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val canvas = Canvas(mutable)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = brushFraction * max(mutable.width, mutable.height)
        }
        strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            if (stroke.size == 1) {
                canvas.drawCircle(
                    stroke[0].x * mutable.width,
                    stroke[0].y * mutable.height,
                    paint.strokeWidth / 2f,
                    Paint(paint).apply { style = Paint.Style.FILL }
                )
            } else {
                val path = Path()
                path.moveTo(stroke[0].x * mutable.width, stroke[0].y * mutable.height)
                for (i in 1 until stroke.size) {
                    path.lineTo(stroke[i].x * mutable.width, stroke[i].y * mutable.height)
                }
                canvas.drawPath(path, paint)
            }
        }
        return mutable
    }

    fun drawSignature(
        bitmap: Bitmap,
        signature: Bitmap,
        xFraction: Float,
        yFraction: Float,
        widthFraction: Float
    ): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val width = (mutable.width * widthFraction.coerceIn(0.08f, 0.9f)).roundToInt()
        val height = (width * signature.height.toFloat() / signature.width.toFloat()).roundToInt()
        val x = (mutable.width * xFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, max(0, mutable.width - width))
        val y = (mutable.height * yFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, max(0, mutable.height - height))
        val canvas = Canvas(mutable)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val dest = android.graphics.Rect(x, y, x + width, y + height)
        canvas.drawBitmap(signature, null, dest, paint)
        return mutable
    }

    fun renderFinal(
        stage: Bitmap,
        filter: DocFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        eraserStrokes: List<List<QuadPoint>>,
        signature: Bitmap?,
        signatureX: Float,
        signatureY: Float,
        signatureWidth: Float
    ): Bitmap {
        var result = applyFilter(stage, filter, brightness, contrast, sharpness)
        if (eraserStrokes.isNotEmpty()) {
            result = applyEraser(result, eraserStrokes)
        }
        if (signature != null) {
            result = drawSignature(result, signature, signatureX, signatureY, signatureWidth)
        }
        return result
    }

    fun trimTransparent(bitmap: Bitmap, padding: Int = 6): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) > 12) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return bitmap
        val left = (minX - padding).coerceAtLeast(0)
        val top = (minY - padding).coerceAtLeast(0)
        val right = (maxX + padding + 1).coerceAtMost(w)
        val bottom = (maxY + padding + 1).coerceAtMost(h)
        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean {
        file.parentFile?.mkdirs()
        return try {
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), stream)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun distance(a: QuadPoint, b: QuadPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun polygonArea(points: List<QuadPoint>): Float {
        if (points.size < 3) return 0f
        var area = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(area) / 2f
    }
}
