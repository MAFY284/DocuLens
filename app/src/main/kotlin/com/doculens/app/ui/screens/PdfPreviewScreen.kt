package com.doculens.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doculens.app.domain.PdfQuality
import com.doculens.app.ui.components.ScreenHeader
import com.doculens.app.ui.viewmodel.AppViewModel
import com.doculens.app.utils.FileHelper

@Composable
fun PdfPreviewScreen(vm: AppViewModel, docId: String) {
    val context = LocalContext.current
    val state = vm.pdfState
    val doc = vm.currentDocument
    var fileName by remember(doc?.id) { mutableStateOf(doc?.name ?: "") }
    var pendingSave by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSave) vm.savePdfToDownloads()
        if (!granted) vm.notify("Permiso de almacenamiento necesario para guardar en Descargas")
        pendingSave = false
    }

    LaunchedEffect(docId, state?.docId, state?.generating) {
        if (state == null && doc != null) {
            vm.generatePdf(vm.defaultQuality)
        }
    }

    fun savePdf() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = true
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.savePdfToDownloads()
        }
    }

    BackHandler(enabled = true) { vm.pop() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Vista previa PDF",
            subtitle = state?.file?.let {
                "${state.pages.size} página${if (state.pages.size == 1) "" else "s"} · ${FileHelper.fileSizeLabel(it)}"
            } ?: "Generando documento…",
            onBack = { vm.pop() },
            actions = {
                IconButton(
                    onClick = { vm.sharePdf() },
                    enabled = state?.file != null && !state.generating
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Compartir")
                }
                IconButton(
                    onClick = { vm.openPdf() },
                    enabled = state?.file != null && !state.generating
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "Abrir")
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = (state?.quality ?: vm.defaultQuality) == quality,
                        onClick = { if (!state?.generating.orFalse()) vm.generatePdf(quality) },
                        label = { Text(quality.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nombre del documento") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (fileName.isNotBlank()) vm.renameAndRegenerate(fileName) },
                    enabled = fileName.isNotBlank() && !state?.generating.orFalse()
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Aplicar nombre")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state == null || state.generating) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text("Creando PDF…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Optimizando ${state?.quality?.description ?: vm.defaultQuality.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.file == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se pudo generar el PDF", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.generatePdf(state.quality) }) { Text("Reintentar") }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(state.pages) { index, bitmap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 3.dp,
                                color = androidx.compose.ui.graphics.Color.White
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Página ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Página ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.pages.isEmpty()) {
                        item {
                            Text(
                                "La vista previa no está disponible, pero puedes guardar o compartir el PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { savePdf() },
                    enabled = state?.file != null && !state.generating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar en Descargas", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.sharePdf() },
                        enabled = state?.file != null && !state.generating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                    OutlinedButton(
                        onClick = { vm.openPdf() },
                        enabled = state?.file != null && !state.generating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "El PDF se guarda en Descargas/DocuLens. También permanece disponible sin conexión.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true
