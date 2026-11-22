package com.doculens.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doculens.app.domain.DocFilter
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.QuadPoint
import java.io.File

class EditorState {

    var docId by mutableStateOf("")
    var pageName by mutableStateOf("")
    var pageFile: File? = null
    var stage by mutableStateOf<Bitmap?>(null)
    var preview by mutableStateOf<Bitmap?>(null)
    var filter by mutableStateOf(DocFilter.ORIGINAL)
    var brightness by mutableFloatStateOf(0f)
    var contrast by mutableFloatStateOf(0f)
    var sharpness by mutableFloatStateOf(0f)
    var processing by mutableStateOf(false)
    var cropMode by mutableStateOf(false)
    var cropQuad by mutableStateOf(DocumentQuad.fullFrame(0.02f))
    var eraserMode by mutableStateOf(false)
    var strokes by mutableStateOf<List<List<QuadPoint>>>(emptyList())
    var signature by mutableStateOf<Bitmap?>(null)
    var signatureMode by mutableStateOf(false)
    var signatureX by mutableFloatStateOf(0.64f)
    var signatureY by mutableFloatStateOf(0.8f)
    var signatureWidth by mutableFloatStateOf(0.3f)
    var ocrText by mutableStateOf<String?>(null)
    var ocrBusy by mutableStateOf(false)
    var dirty by mutableStateOf(false)

    fun reset() {
        docId = ""
        pageName = ""
        pageFile = null
        stage = null
        preview = null
        filter = DocFilter.ORIGINAL
        brightness = 0f
        contrast = 0f
        sharpness = 0f
        processing = false
        cropMode = false
        cropQuad = DocumentQuad.fullFrame(0.02f)
        eraserMode = false
        strokes = emptyList()
        signature = null
        signatureMode = false
        signatureX = 0.64f
        signatureY = 0.8f
        signatureWidth = 0.3f
        ocrText = null
        ocrBusy = false
        dirty = false
    }
}
