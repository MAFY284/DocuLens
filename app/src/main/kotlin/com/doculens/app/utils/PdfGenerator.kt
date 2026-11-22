package com.doculens.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.doculens.app.data.model.ScannedDocument
import com.doculens.app.domain.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfGenerator {

    suspend fun generate(context: Context, document: ScannedDocument, quality: PdfQuality): File =
        withContext(Dispatchers.IO) {
            val folder = document.pages.firstOrNull()?.parentFile
                ?: File(context.filesDir, "documents/${document.id}")
            folder.mkdirs()
            val baseName = FileHelper.sanitizeFileName(document.name).ifBlank { "documento" }
            val output = File(folder, "$baseName.pdf")
            val pdf = PdfDocument()
            val maxDim = if (quality.maxDimension > 0) quality.maxDimension else 3000
            try {
                document.pages.forEachIndexed { index, pageFile ->
                    val bitmap = ImageProcessor.decodeFile(pageFile, maxDim) ?: return@forEachIndexed
                    val landscape = bitmap.width > bitmap.height
                    val pageWidth = if (landscape) 842 else 595
                    val pageHeight = if (landscape) 595 else 842
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val scale = min(
                        pageWidth.toFloat() / bitmap.width.toFloat(),
                        pageHeight.toFloat() / bitmap.height.toFloat()
                    )
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val left = (pageWidth - drawWidth) / 2f
                    val top = (pageHeight - drawHeight) / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, left + drawWidth, top + drawHeight),
                        paint
                    )
                    pdf.finishPage(page)
                    bitmap.recycle()
                }
                FileOutputStream(output).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
            output
        }

    suspend fun renderPreview(file: File, maxWidth: Int = 900): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    val width = min(maxWidth, page.width * 2)
                    val height = (page.height.toFloat() * width / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                } finally {
                    page.close()
                }
            }
        } catch (_: Throwable) {
        } finally {
            renderer?.close()
            descriptor?.close()
        }
        bitmaps
    }
}
