package com.doculens.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doculens.app.data.model.ScannedDocument
import com.doculens.app.ui.Screen
import com.doculens.app.ui.components.DocumentCard
import com.doculens.app.ui.components.EmptyDocuments
import com.doculens.app.ui.components.SearchField
import com.doculens.app.ui.components.SectionHeader
import com.doculens.app.ui.components.rememberMlKitScanner
import com.doculens.app.ui.theme.BrandBlue
import com.doculens.app.ui.viewmodel.AppViewModel

@Composable
fun HomeScreen(vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ScannedDocument?>(null) }
    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) vm.importFromGallery(uris)
    }
    val mlKit = rememberMlKitScanner(
        onPages = { vm.importMlKitFromHome(it) },
        onError = { vm.notify(it) }
    )
    val filtered = remember(vm.documents, search) {
        if (search.isBlank()) vm.documents
        else vm.documents.filter { it.name.contains(search, ignoreCase = true) }
    }
    val shown = if (search.isBlank()) filtered.take(6) else filtered

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(listOf(BrandBlue, Color(0xFF7444FF)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DocuLens",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Escáner de documentos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.navigate(Screen.History) }) {
                Icon(Icons.Rounded.History, contentDescription = "Historial")
            }
            IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF2F5BFF),
                                        Color(0xFF5B3DF5),
                                        Color(0xFF7A3BFF)
                                    )
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    "100% offline  ·  sin anuncios",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Escanea. Mejora.\nComparte.",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Convierte cualquier documento en un PDF nítido y profesional en segundos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { vm.startScan() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = BrandBlue
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(
                                        Icons.Rounded.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Escanear")
                                }
                                OutlinedButton(
                                    onClick = { mlKit() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.55f)
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("ML Kit")
                                }
                                IconButton(
                                    onClick = {
                                        gallery.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.16f))
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoLibrary,
                                        contentDescription = "Importar de galería",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Buscar documentos"
                )
            }
            item {
                SectionHeader(
                    title = if (search.isBlank()) "Recientes" else "Resultados",
                    actionLabel = "Ver todo",
                    onAction = { vm.navigate(Screen.History) }
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyDocuments(onScan = { vm.startScan() })
                }
            } else {
                items(shown, key = { it.id }) { doc ->
                    DocumentCard(
                        document = doc,
                        onClick = { vm.openDocument(doc.id) },
                        onShare = { vm.shareDocument(doc) },
                        onDelete = { deleteTarget = doc }
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Consejo: usa Escaneo inteligente (ML Kit) para detección de bordes y corrección de perspectiva automática.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar documento") },
            text = { Text("Se eliminarán ${doc.pageCount} página(s) de este documento. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDocument(doc)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}
