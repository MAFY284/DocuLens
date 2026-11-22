package com.doculens.app.ui

sealed interface Screen {
    data object Home : Screen
    data object History : Screen
    data object Settings : Screen
    data class Scanner(val docId: String) : Screen
    data class Document(val docId: String) : Screen
    data class Editor(val docId: String, val pageName: String) : Screen
    data class PdfPreview(val docId: String) : Screen
}
