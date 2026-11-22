package com.doculens.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doculens.app.data.model.ScannedDocument
import com.doculens.app.di.ServiceLocator
import com.doculens.app.domain.DocFilter
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.PdfQuality
import com.doculens.app.domain.QuadPoint
import com.doculens.app.ui.Screen
import com.doculens.app.ui.theme.ThemeMode
import com.doculens.app.utils.FileHelper
import com.doculens.app.utils.ImageProcessor
import com.doculens.app.utils.OcrProcessor
import com.doculens.app.utils.PdfGenerator
import com.doculens.app.utils.QuadDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfPreviewState(
    val docId: String,
    val quality: PdfQuality,
    val file: File?,
    val pages: List<Bitmap>,
    val generating: Boolean
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val repository = ServiceLocator.repository(application)
    private val prefs = application.getSharedPreferences("doculens_settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(loadTheme())
        private set
    var defaultQuality by mutableStateOf(loadQuality())
        private set
    var documents by mutableStateOf<List<ScannedDocument>>(emptyList())
        private set
    var currentDocument by mutableStateOf<ScannedDocument?>(null)
        private set
    var historyQuery by mutableStateOf("")
    var busyMessage by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pdfState by mutableStateOf<PdfPreviewState?>(null)
        private set

    val navigation = mutableStateListOf<Screen>(Screen.Home)
    val editor = EditorState()

    private var previewJob: Job? = null
    private var renameJob: Job? = null
    private var pdfJob: Job? = null

    init {
        refreshDocuments()
    }

    private fun loadTheme(): ThemeMode {
        val stored = prefs.getString("theme", ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(stored ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun loadQuality(): PdfQuality {
        val stored = prefs.getString("quality", PdfQuality.MEDIUM.name)
        return runCatching { PdfQuality.valueOf(stored ?: "") }.getOrDefault(PdfQuality.MEDIUM)
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun updateDefaultQuality(quality: PdfQuality) {
        defaultQuality = quality
        prefs.edit().putString("quality", quality.name).apply()
    }

    fun consumeMessage() {
        message = null
    }

    fun notify(text: String) {
        message = text
    }

    fun navigate(screen: Screen) {
        if (navigation.lastOrNull() != screen) navigation.add(screen)
    }

    fun replaceLast(screen: Screen) {
        if (navigation.isNotEmpty()) navigation.removeAt(navigation.lastIndex)
        navigation.add(screen)
    }

    fun pop() {
        if (navigation.size > 1) navigation.removeAt(navigation.lastIndex)
    }

    fun popToHome() {
        while (navigation.size > 1) navigation.removeAt(navigation.lastIndex)
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.listDocuments() }
            documents = list
        }
    }

    fun startScan() {
        viewModelScope.launch {
            busyMessage = "Preparando escáner…"
            val doc = withContext(Dispatchers.IO) { repository.createDocument() }
            currentDocument = doc
            refreshDocuments()
            busyMessage = null
            navigate(Screen.Scanner(doc.id))
        }
    }

    fun importFromGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage = "Creando documento…"
            val doc = withContext(Dispatchers.IO) { repository.createDocument() }
            val added = importUrisInternal(doc.id, uris, autoCrop = true)
            val updated = withContext(Dispatchers.IO) { repository.getDocument(doc.id) }
            busyMessage = null
            currentDocument = updated
            refreshDocuments()
            if (added.isEmpty()) {
                withContext(Dispatchers.IO) { repository.deleteDocument(doc.id) }
                message = "No se pudieron importar las imágenes"
            } else {
                navigate(Screen.Document(doc.id))
            }
        }
    }

    fun importMlKitFromHome(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage = "Creando documento…"
            val doc = withContext(Dispatchers.IO) { repository.createDocument() }
            val added = importUrisInternal(doc.id, uris, autoCrop = false)
            val updated = withContext(Dispatchers.IO) { repository.getDocument(doc.id) }
            busyMessage = null
            currentDocument = updated
            refreshDocuments()
            if (added.isEmpty()) {
                withContext(Dispatchers.IO) { repository.deleteDocument(doc.id) }
                message = "No se pudieron añadir las páginas"
            } else {
                navigate(Screen.Document(doc.id))
            }
        }
    }

    fun addMlKitPages(docId: String, uris: List<Uri>, openDocument: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = importUrisInternal(docId, uris, autoCrop = false)
            val updated = withContext(Dispatchers.IO) { repository.getDocument(docId) }
            busyMessage = null
            currentDocument = updated
            refreshDocuments()
            if (added.isEmpty()) {
                message = "No se pudieron añadir las páginas"
            } else if (openDocument) {
                returnToDocument(docId)
            }
        }
    }

    fun importUrisToCurrent(uris: List<Uri>, autoCrop: Boolean = true) {
        val doc = currentDocument ?: return
        viewModelScope.launch {
            val added = importUrisInternal(doc.id, uris, autoCrop)
            val updated = withContext(Dispatchers.IO) { repository.getDocument(doc.id) }
            busyMessage = null
            currentDocument = updated
            refreshDocuments()
            if (added.isNotEmpty()) {
                message = "${added.size} página${if (added.size == 1) "" else "s"} añadida${if (added.size == 1) "" else "s"}"
            } else {
                message = "No se pudieron añadir las páginas"
            }
        }
    }

    private suspend fun importUrisInternal(docId: String, uris: List<Uri>, autoCrop: Boolean): List<String> {
        val added = mutableListOf<String>()
        try {
            uris.forEachIndexed { index, uri ->
                withContext(Dispatchers.Main) {
                    busyMessage = "Procesando página ${index + 1} de ${uris.size}…"
                }
                val bitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeUri(appContext, uri, 2600)
                } ?: return@forEachIndexed
                val processed = withContext(Dispatchers.Default) {
                    if (autoCrop) autoCrop(bitmap) else bitmap
                }
                val doc = withContext(Dispatchers.IO) {
                    val result = repository.addPage(docId, processed)
                    if (processed !== bitmap) processed.recycle()
                    bitmap.recycle()
                    result
                }
                if (doc != null) added.add(doc.id)
            }
        } finally {
            withContext(Dispatchers.Main) { busyMessage = null }
        }
        return added
    }

    private fun autoCrop(bitmap: Bitmap): Bitmap {
        return try {
            val quad = QuadDetector.detect(bitmap) ?: return bitmap
            ImageProcessor.warp(bitmap, quad)
        } catch (_: Throwable) {
            bitmap
        }
    }

    suspend fun addCapturedPage(
        docId: String,
        bitmap: Bitmap,
        cropQuad: DocumentQuad? = null,
        autoCrop: Boolean = true
    ): Boolean {
        val processed = withContext(Dispatchers.Default) {
            when {
                cropQuad != null -> ImageProcessor.warp(bitmap, cropQuad)
                autoCrop -> autoCrop(bitmap)
                else -> bitmap
            }
        }
        val updated = withContext(Dispatchers.IO) {
            val result = repository.addPage(docId, processed)
            if (processed !== bitmap) processed.recycle()
            bitmap.recycle()
            result
        }
        return if (updated != null) {
            currentDocument = updated
            refreshDocuments()
            true
        } else {
            false
        }
    }

    fun finishScan(docId: String) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { repository.getDocument(docId) }
            if (doc == null || doc.pages.isEmpty()) {
                withContext(Dispatchers.IO) { repository.deleteDocument(docId) }
                currentDocument = null
                refreshDocuments()
                pop()
            } else {
                currentDocument = doc
                refreshDocuments()
                returnToDocument(docId)
            }
        }
    }

    private fun returnToDocument(docId: String) {
        val alreadyPresent = navigation.dropLast(1).any { it is Screen.Document && it.docId == docId }
        if (alreadyPresent) pop() else replaceLast(Screen.Document(docId))
    }

    fun cancelScan(docId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteIfEmpty(docId) }
            val doc = withContext(Dispatchers.IO) { repository.getDocument(docId) }
            currentDocument = doc
            refreshDocuments()
            pop()
        }
    }

    fun openDocument(docId: String) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { repository.getDocument(docId) }
            if (doc == null) {
                message = "Documento no encontrado"
                return@launch
            }
            currentDocument = doc
            navigate(Screen.Document(docId))
        }
    }

    fun openDocumentWithoutNavigation(docId: String) {
        viewModelScope.launch {
            currentDocument = withContext(Dispatchers.IO) { repository.getDocument(docId) }
        }
    }

    fun deleteDocument(doc: ScannedDocument) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteDocument(doc.id) }
            if (currentDocument?.id == doc.id) currentDocument = null
            refreshDocuments()
            message = "Documento eliminado"
            if (navigation.any { it is Screen.Document && it.docId == doc.id }) pop()
        }
    }

    fun renameDocument(name: String) {
        val doc = currentDocument ?: return
        renameJob?.cancel()
        renameJob = viewModelScope.launch {
            delay(350)
            val updated = withContext(Dispatchers.IO) { repository.updateName(doc.id, name) }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
            }
        }
    }

    fun movePage(docId: String, from: Int, to: Int) {
        val doc = currentDocument ?: return
        if (from !in doc.pages.indices || to !in doc.pages.indices || from == to) return
        val pages = doc.pages.toMutableList()
        val moved = pages.removeAt(from)
        pages.add(to, moved)
        currentDocument = doc.copy(pages = pages)
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.updatePageOrder(docId, pages) }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
            }
        }
    }

    fun deletePage(docId: String, page: File) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.deletePage(docId, page) }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
                message = "Página eliminada"
            }
        }
    }

    fun duplicatePage(docId: String, page: File) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.duplicatePage(docId, page) }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
                message = "Página duplicada"
            }
        }
    }

    fun rotatePage(docId: String, page: File) {
        viewModelScope.launch {
            busyMessage = "Rotando página…"
            val updated = try {
                withContext(Dispatchers.IO) {
                    val bitmap = ImageProcessor.decodeFile(page, 2600) ?: return@withContext null
                    val rotated = ImageProcessor.rotate(bitmap, 90)
                    if (rotated !== bitmap) bitmap.recycle()
                    val result = repository.updatePageBitmap(docId, page, rotated)
                    rotated.recycle()
                    result
                }
            } finally {
                busyMessage = null
            }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
            }
        }
    }

    fun openEditor(docId: String, pageName: String) {
        val file = repository.pageFile(docId, pageName)
        editor.reset()
        editor.docId = docId
        editor.pageName = pageName
        editor.pageFile = file
        navigate(Screen.Editor(docId, pageName))
        viewModelScope.launch {
            editor.processing = true
            val bitmap = withContext(Dispatchers.IO) { ImageProcessor.decodeFile(file, 2200) }
            if (bitmap == null) {
                editor.processing = false
                message = "No se pudo abrir la página"
                pop()
                return@launch
            }
            editor.stage = bitmap
            editor.processing = false
            refreshEditorPreview(immediate = true)
        }
    }

    fun refreshEditorPreview(immediate: Boolean = false) {
        val stage = editor.stage ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!immediate) delay(180)
            editor.processing = true
            val previewSource = withContext(Dispatchers.Default) {
                ImageProcessor.previewCopy(stage, 1400)
            }
            val filtered = withContext(Dispatchers.Default) {
                ImageProcessor.applyFilter(
                    previewSource,
                    editor.filter,
                    editor.brightness,
                    editor.contrast,
                    editor.sharpness
                )
            }
            if (previewSource !== stage) previewSource.recycle()
            editor.preview = filtered
            editor.processing = false
        }
    }

    fun setEditorFilter(filter: DocFilter) {
        editor.filter = filter
        editor.dirty = true
        refreshEditorPreview()
    }

    fun setEditorBrightness(value: Float) {
        editor.brightness = value
        editor.dirty = true
        refreshEditorPreview()
    }

    fun setEditorContrast(value: Float) {
        editor.contrast = value
        editor.dirty = true
        refreshEditorPreview()
    }

    fun setEditorSharpness(value: Float) {
        editor.sharpness = value
        editor.dirty = true
        refreshEditorPreview()
    }

    fun rotateEditor() {
        val stage = editor.stage ?: return
        editor.stage = ImageProcessor.rotate(stage, 90)
        editor.strokes = emptyList()
        editor.cropQuad = DocumentQuad.fullFrame(0.02f)
        editor.dirty = true
        refreshEditorPreview(immediate = true)
    }

    fun applyEditorCrop() {
        val stage = editor.stage ?: return
        editor.processing = true
        viewModelScope.launch {
            val warped = withContext(Dispatchers.Default) {
                ImageProcessor.warp(stage, editor.cropQuad)
            }
            if (warped !== stage) editor.stage = warped
            editor.strokes = emptyList()
            editor.cropQuad = DocumentQuad.fullFrame(0.02f)
            editor.cropMode = false
            editor.dirty = true
            refreshEditorPreview(immediate = true)
        }
    }

    fun addEraserStroke(points: List<QuadPoint>) {
        if (points.isEmpty()) return
        editor.strokes = editor.strokes + listOf(points)
        editor.dirty = true
    }

    fun undoEraserStroke() {
        if (editor.strokes.isNotEmpty()) {
            editor.strokes = editor.strokes.dropLast(1)
            editor.dirty = true
        }
    }

    fun setSignature(bitmap: Bitmap) {
        editor.signature = bitmap
        editor.signatureMode = true
        editor.dirty = true
    }

    fun clearSignature() {
        editor.signature = null
        editor.signatureMode = false
        editor.dirty = true
    }

    fun saveEditor() {
        val stage = editor.stage ?: return
        val file = editor.pageFile ?: return
        val docId = editor.docId
        editor.processing = true
        viewModelScope.launch {
            val finalBitmap = withContext(Dispatchers.Default) {
                ImageProcessor.renderFinal(
                    stage = stage,
                    filter = editor.filter,
                    brightness = editor.brightness,
                    contrast = editor.contrast,
                    sharpness = editor.sharpness,
                    eraserStrokes = editor.strokes,
                    signature = editor.signature,
                    signatureX = editor.signatureX,
                    signatureY = editor.signatureY,
                    signatureWidth = editor.signatureWidth
                )
            }
            val updated = withContext(Dispatchers.IO) {
                val result = repository.updatePageBitmap(docId, file, finalBitmap)
                finalBitmap.recycle()
                result
            }
            editor.processing = false
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
                message = "Página guardada"
                editor.reset()
                pop()
            } else {
                message = "No se pudo guardar la página"
            }
        }
    }

    fun closeEditor() {
        previewJob?.cancel()
        editor.reset()
    }

    fun runOcr() {
        val stage = editor.stage ?: return
        editor.ocrBusy = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = ImageProcessor.previewCopy(stage, 1800)
                    val text = OcrProcessor.recognize(bitmap)
                    if (bitmap !== stage) bitmap.recycle()
                    text
                }
            }
            editor.ocrText = result.getOrNull() ?: ""
            editor.ocrBusy = false
            if (result.isFailure) message = "No se pudo reconocer el texto"
        }
    }

    fun dismissOcr() {
        editor.ocrText = null
    }

    fun openPdfPreview(name: String? = null) {
        val doc = currentDocument ?: return
        viewModelScope.launch {
            var target = doc
            if (!name.isNullOrBlank() && name != doc.name) {
                withContext(Dispatchers.IO) { repository.updateName(doc.id, name) }?.let {
                    target = it
                    currentDocument = it
                    refreshDocuments()
                }
            }
            navigate(Screen.PdfPreview(target.id))
            generatePdf(target, defaultQuality)
        }
    }

    fun generatePdf(quality: PdfQuality) {
        val doc = currentDocument ?: return
        updateDefaultQuality(quality)
        generatePdf(doc, quality)
    }

    private fun generatePdf(doc: ScannedDocument, quality: PdfQuality) {
        pdfJob?.cancel()
        pdfState = PdfPreviewState(
            docId = doc.id,
            quality = quality,
            file = null,
            pages = emptyList(),
            generating = true
        )
        pdfJob = viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { PdfGenerator.generate(appContext, doc, quality) }.getOrNull()
            }
            if (file == null) {
                pdfState = PdfPreviewState(doc.id, quality, null, emptyList(), false)
                message = "No se pudo generar el PDF"
                return@launch
            }
            val pages = PdfGenerator.renderPreview(file, 1000)
            val updated = withContext(Dispatchers.IO) { repository.attachPdf(doc.id, file) }
            if (updated != null) {
                currentDocument = updated
                refreshDocuments()
            }
            pdfState = PdfPreviewState(doc.id, quality, file, pages, false)
        }
    }

    fun savePdfToDownloads() {
        val state = pdfState ?: return
        val file = state.file ?: return
        val name = currentDocument?.name ?: file.nameWithoutExtension
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileHelper.savePdfToDownloads(appContext, file, name)
            }
            message = if (ok) "PDF guardado en Descargas/DocuLens" else "No se pudo guardar el PDF"
        }
    }

    fun sharePdf() {
        val file = pdfState?.file ?: return
        runCatching { FileHelper.sharePdf(appContext, file) }
            .onFailure { message = "No se pudo compartir el PDF" }
    }

    fun openPdf() {
        val file = pdfState?.file ?: return
        runCatching { FileHelper.openPdf(appContext, file) }
            .onFailure { message = "No hay una app para abrir PDFs" }
    }

    fun renameAndRegenerate(name: String) {
        val doc = currentDocument ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.updateName(doc.id, name) } ?: return@launch
            currentDocument = updated
            refreshDocuments()
            generatePdf(updated, pdfState?.quality ?: defaultQuality)
        }
    }

    fun shareDocument(doc: ScannedDocument) {
        viewModelScope.launch {
            busyMessage = "Preparando PDF…"
            val pdf = withContext(Dispatchers.IO) {
                doc.pdf?.takeIf { it.exists() } ?: runCatching {
                    PdfGenerator.generate(appContext, doc, defaultQuality)
                }.getOrNull()
            }
            if (pdf != null) {
                withContext(Dispatchers.IO) { repository.attachPdf(doc.id, pdf) }
                refreshDocuments()
                runCatching { FileHelper.sharePdf(appContext, pdf) }
                    .onFailure { message = "No se pudo compartir el PDF" }
            } else {
                message = "No se pudo generar el PDF"
            }
            busyMessage = null
        }
    }

    fun updateHistoryQuery(query: String) {
        historyQuery = query
    }

    fun currentPageFile(): File? = editor.pageFile

    fun refreshDocument(docId: String) {
        viewModelScope.launch {
            currentDocument = withContext(Dispatchers.IO) { repository.getDocument(docId) }
        }
    }
}
