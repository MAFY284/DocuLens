package com.doculens.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun rememberMlKitScanner(
    onPages: (List<Uri>) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnPages by rememberUpdatedState(onPages)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            }.getOrNull()?.let { scanningResult ->
                val pages = scanningResult.pages.orEmpty().mapNotNull { it.imageUri }
                if (pages.isNotEmpty()) {
                    currentOnPages(pages)
                } else {
                    currentOnError("El escáner no devolvió páginas")
                }
            } ?: currentOnError("No se pudo leer el resultado del escáner")
        }
    }
    return remember(context, launcher) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                currentOnError("No se pudo abrir el escáner")
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(30)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        currentOnError("ML Kit no está disponible. Usa la cámara rápida o instala Google Play Services.")
                    }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
