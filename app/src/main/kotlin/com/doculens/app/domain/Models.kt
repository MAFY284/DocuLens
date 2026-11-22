package com.doculens.app.domain

enum class DocFilter(val label: String) {
    ORIGINAL("Original"),
    BLACK_WHITE("Blanco y negro"),
    GRAYSCALE("Escala de grises"),
    ENHANCE("Mejorar"),
    HIGH_CONTRAST("Alto contraste"),
    CLEAN("Documento limpio")
}

enum class PdfQuality(val label: String, val description: String, val maxDimension: Int) {
    HIGH("Alta", "Máxima calidad", 0),
    MEDIUM("Media", "Equilibrado", 2200),
    LOW("Compacto", "Archivo ligero", 1500)
}

enum class ScanMode(val label: String) {
    FAST("Rápida"),
    PRECISION("Precisión")
}

data class QuadPoint(val x: Float, val y: Float) {
    fun clamped(): QuadPoint = QuadPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

data class DocumentQuad(
    val topLeft: QuadPoint,
    val topRight: QuadPoint,
    val bottomRight: QuadPoint,
    val bottomLeft: QuadPoint
) {
    fun points(): List<QuadPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withPoint(index: Int, point: QuadPoint): DocumentQuad {
        val p = point.clamped()
        return when (index) {
            0 -> copy(topLeft = p)
            1 -> copy(topRight = p)
            2 -> copy(bottomRight = p)
            3 -> copy(bottomLeft = p)
            else -> this
        }
    }

    companion object {
        fun fullFrame(inset: Float = 0f): DocumentQuad = DocumentQuad(
            topLeft = QuadPoint(inset, inset),
            topRight = QuadPoint(1f - inset, inset),
            bottomRight = QuadPoint(1f - inset, 1f - inset),
            bottomLeft = QuadPoint(inset, 1f - inset)
        )
    }
}
