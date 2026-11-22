package com.doculens.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doculens.app.ui.Screen
import com.doculens.app.ui.components.FileThumbnail
import com.doculens.app.ui.components.ReorderableColumn
import com.doculens.app.ui.components.ScreenHeader
import com.doculens.app.ui.components.SmallTag
import com.doculens.app.ui.components.rememberMlKitScanner
import com.doculens.app.ui.theme.BrandBlue
import com.doculens.app.ui.viewmodel.AppViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(vm: AppViewModel, docId: String) {
    val doc = vm.currentDocument?.takeIf { it.id == docId }
    var showAddSheet by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(docId) {
        if (vm.currentDocument?.id != docId) vm.openDocumentWithoutNavigation(docId)
    }

    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) vm.importUrisToCurrent(uris, autoCrop = true)
    }
    val mlKit = rememberMlKitScanner(
        onPages = { vm.addMlKitPages(docId, it, openDocument = false) },
        onError = { vm.notify(it) }
    )

    BackHandler(enabled = true) { vm.pop() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = doc?.name ?: "Documento",
            subtitle = if (doc != null) "${doc.pageCount} página${if (doc.pageCount == 1) "" else "s"} · ${doc.docType}" else "Cargando…",
            onBack = { vm.pop() },
            actions = {
                IconButton(onClick = { showRename = true }, enabled = doc != null) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Renombrar")
                }
                IconButton(
                    onClick = { doc?.let { vm.shareDocument(it) } },
                    enabled = doc != null && doc.pages.isNotEmpty()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Compartir")
                }
            }
        )

        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando documento…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (doc.pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Añade tu primera página", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Escanea con la cámara, usa ML Kit o importa desde la galería.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.navigate(Screen.Scanner(docId)) }) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cámara")
                        }
                        Button(onClick = { mlKit() }) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("ML Kit")
                        }
                    }
                    TextButton(onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Importar de galería")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Páginas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Mantén pulsado para reordenar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Añadir páginas")
                }
            }

            ReorderableColumn(
                items = doc.pages,
                onMove = { from, to -> vm.movePage(docId, from, to) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                itemHeight = 112.dp,
                itemSpacing = 10.dp,
                key = { it.absolutePath }
            ) { index, page, dragging, dragHandle ->
                PageRow(
                    page = page,
                    index = index,
                    total = doc.pages.size,
                    dragging = dragging,
                    dragHandle = dragHandle,
                    canMoveUp = index > 0,
                    canMoveDown = index < doc.pages.lastIndex,
                    onMoveUp = { vm.movePage(docId, index, index - 1) },
                    onMoveDown = { vm.movePage(docId, index, index + 1) },
                    onOpen = { vm.openEditor(docId, page.name) },
                    onDuplicate = { vm.duplicatePage(docId, page) },
                    onRotate = { vm.rotatePage(docId, page) },
                    onDelete = { deleteTarget = page }
                )
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
                        onClick = { vm.openPdfPreview(doc.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Vista previa y PDF", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Calidad ${vm.defaultQuality.label} · las páginas se guardan en tu dispositivo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    "Añadir páginas",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                AddOption(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Cámara rápida",
                    subtitle = "Captura con guía y auto-recorte",
                    onClick = {
                        showAddSheet = false
                        vm.navigate(Screen.Scanner(docId))
                    }
                )
                AddOption(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Escaneo inteligente",
                    subtitle = "ML Kit: bordes y perspectiva automáticos",
                    onClick = {
                        showAddSheet = false
                        mlKit()
                    }
                )
                AddOption(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Galería",
                    subtitle = "Importa una o varias imágenes",
                    onClick = {
                        showAddSheet = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
        }
    }

    if (showRename) {
        var name by remember(doc?.id) { mutableStateOf(doc?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar documento") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) vm.renameDocument(name)
                        showRename = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancelar") }
            }
        )
    }

    deleteTarget?.let { page ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar página") },
            text = { Text("Esta página se eliminará del documento.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePage(docId, page)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PageRow(
    page: File,
    index: Int,
    total: Int,
    dragging: Boolean,
    dragHandle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(102.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (dragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (dragging) 10.dp else 1.dp,
        shadowElevation = if (dragging) 10.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FileThumbnail(
                    file = page,
                    modifier = Modifier
                        .width(62.dp)
                        .height(84.dp),
                    corner = 10.dp
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Página ${index + 1} de $total",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toca para editar · arrastra para mover",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                SmallTag("Editable")
            }
            Box(
                modifier = dragHandle.padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = "Reordenar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Acciones", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Girar 90°") },
                        leadingIcon = { Icon(Icons.Rounded.RotateRight, contentDescription = null) },
                        onClick = { menuOpen = false; onRotate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover arriba") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; onMoveUp() }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover abajo") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; onMoveDown() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(BrandBlue.copy(alpha = 0.16f), BrandBlue.copy(alpha = 0.06f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BrandBlue)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
