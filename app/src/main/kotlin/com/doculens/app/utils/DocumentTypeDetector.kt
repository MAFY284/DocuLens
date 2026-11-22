package com.doculens.app.utils

object DocumentTypeDetector {

    fun detect(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Documento"
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio < 0.45f -> "Recibo"
            ratio < 0.63f -> "Tarjeta"
            ratio in 0.63f..0.82f -> "Documento A4"
            ratio in 1.22f..1.65f -> "Documento A4"
            ratio > 1.25f -> "Foto"
            else -> "Documento"
        }
    }
}
