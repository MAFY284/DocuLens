package com.doculens.app.data.model

import java.io.File

data class ScannedDocument(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val docType: String,
    val pages: List<File>,
    val pdf: File?
) {
    val pageCount: Int get() = pages.size
}
