package com.doculens.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.doculens.app.data.model.ScannedDocument
import com.doculens.app.utils.DocumentTypeDetector
import com.doculens.app.utils.ImageProcessor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "documents").apply { mkdirs() }
    private val lock = Any()

    fun createDocument(name: String = defaultName()): ScannedDocument {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        dir.mkdirs()
        val now = System.currentTimeMillis()
        val doc = ScannedDocument(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
            docType = "Documento",
            pages = emptyList(),
            pdf = null
        )
        writeMeta(doc)
        return doc
    }

    fun listDocuments(): List<ScannedDocument> {
        val files = root.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isDirectory }
            .mapNotNull { readMeta(it.name) }
            .sortedByDescending { it.updatedAt }
            .toList()
    }

    fun getDocument(id: String): ScannedDocument? = readMeta(id)

    fun pageFile(docId: String, pageName: String): File = File(File(root, docId), pageName)

    fun addPage(docId: String, bitmap: Bitmap): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val file = File(File(root, docId), "page_${UUID.randomUUID()}.jpg")
        if (!ImageProcessor.saveJpeg(bitmap, file)) return null
        val type = if (current.pages.isEmpty()) {
            DocumentTypeDetector.detect(bitmap.width, bitmap.height)
        } else {
            current.docType
        }
        val updated = current.copy(
            pages = current.pages + file,
            docType = type,
            updatedAt = System.currentTimeMillis()
        ).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun updatePageBitmap(docId: String, page: File, bitmap: Bitmap): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        if (current.pages.none { it.name == page.name }) return null
        if (!ImageProcessor.saveJpeg(bitmap, page)) return null
        val updated = current.copy(updatedAt = System.currentTimeMillis()).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun updatePageOrder(docId: String, ordered: List<File>): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val validNames = current.pages.map { it.name }.toSet()
        val files = ordered.filter { it.name in validNames }.map { File(File(root, docId), it.name) }
        if (files.isEmpty()) return current
        val updated = current.copy(pages = files, updatedAt = System.currentTimeMillis()).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun deletePage(docId: String, page: File): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val remaining = current.pages.filterNot { it.name == page.name }
        File(File(root, docId), page.name).delete()
        val updated = current.copy(pages = remaining, updatedAt = System.currentTimeMillis()).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun duplicatePage(docId: String, page: File): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val index = current.pages.indexOfFirst { it.name == page.name }
        if (index < 0) return null
        val source = File(File(root, docId), page.name)
        val copy = File(File(root, docId), "page_${UUID.randomUUID()}.jpg")
        source.copyTo(copy, overwrite = true)
        val mutable = current.pages.toMutableList()
        mutable.add(index + 1, copy)
        val updated = current.copy(pages = mutable, updatedAt = System.currentTimeMillis()).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun updateName(docId: String, name: String): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val clean = name.trim().ifBlank { current.name }
        val updated = current.copy(name = clean, updatedAt = System.currentTimeMillis()).clearedPdf()
        writeMeta(updated)
        return updated
    }

    fun updateType(docId: String, type: String): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val updated = current.copy(docType = type, updatedAt = System.currentTimeMillis())
        writeMeta(updated)
        return updated
    }

    fun attachPdf(docId: String, pdf: File): ScannedDocument? {
        val current = readMeta(docId) ?: return null
        val updated = current.copy(pdf = pdf, updatedAt = System.currentTimeMillis())
        writeMeta(updated)
        return updated
    }

    fun deleteDocument(id: String) {
        synchronized(lock) {
            File(root, id).deleteRecursively()
        }
    }

    fun deleteIfEmpty(id: String): Boolean {
        val doc = readMeta(id) ?: return true
        if (doc.pages.isEmpty()) {
            deleteDocument(id)
            return true
        }
        return false
    }

    private fun ScannedDocument.clearedPdf(): ScannedDocument {
        pdf?.delete()
        return copy(pdf = null)
    }

    private fun defaultName(): String {
        val formatter = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
        return "Escaneo ${formatter.format(Date())}"
    }

    private fun writeMeta(doc: ScannedDocument) {
        synchronized(lock) {
            val dir = File(root, doc.id)
            if (!dir.exists()) dir.mkdirs()
            val json = JSONObject().apply {
                put("id", doc.id)
                put("name", doc.name)
                put("createdAt", doc.createdAt)
                put("updatedAt", doc.updatedAt)
                put("docType", doc.docType)
                put("pages", JSONArray(doc.pages.map { it.name }))
                doc.pdf?.let { put("pdf", it.name) }
            }
            File(dir, "meta.json").writeText(json.toString())
        }
    }

    private fun readMeta(id: String): ScannedDocument? {
        synchronized(lock) {
            val dir = File(root, id)
            val meta = File(dir, "meta.json")
            if (!meta.exists()) return null
            return try {
                val json = JSONObject(meta.readText())
                val pagesArray = json.optJSONArray("pages") ?: JSONArray()
                val pages = buildList {
                    for (i in 0 until pagesArray.length()) {
                        val name = pagesArray.optString(i)
                        val file = File(dir, name)
                        if (file.exists()) add(file)
                    }
                }
                val pdfName = json.optString("pdf")
                val pdf = if (pdfName.isNotBlank()) File(dir, pdfName).takeIf { it.exists() } else null
                ScannedDocument(
                    id = json.optString("id", id),
                    name = json.optString("name", "Documento"),
                    createdAt = json.optLong("createdAt", meta.lastModified()),
                    updatedAt = json.optLong("updatedAt", meta.lastModified()),
                    docType = json.optString("docType", "Documento"),
                    pages = pages,
                    pdf = pdf
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
