package com.doculens.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.doculens.app.domain.DocFilter
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.QuadPoint
import com.doculens.app.ui.components.ScreenHeader
import com.doculens.app.ui.components.SignaturePadDialog
import com.doculens.app.ui.theme.ScannerAccent
import com.doculens.app.ui.viewmodel.AppViewModel
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun EditScreen(vm: AppViewModel) {
    val editor = vm.editor
    var showDiscard by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var activeCorner by remember { mutableStateOf(-1) }
    var liveEraserStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var imageBoxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current

    fun closeEditor() {
        if (editor.dirty) showDiscard = true
        else {
            vm.closeEditor()
            vm.pop()
        }
    }

    BackHandler(enabled = true) { closeEditor() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Editar página",
            subtitle = if (editor.dirty) "Cambios sin guardar" else "Ajusta y mejora tu documento",
            onBack = { closeEditor() },
            actions = {
                IconButton(
                    onClick = { vm.saveEditor() },
                    enabled = editor.stage != null && !editor.processing && !editor.cropMode
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Guardar")
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            val preview = editor.preview
            if (preview == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando página…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val aspect = preview.width.toFloat() / preview.height.toFloat()
                    val availableW = maxWidth
                    val availableH = maxHeight
                    val surfaceWidth = if (availableW / availableH > aspect) availableH * aspect else availableW
                    val surfaceHeight = surfaceWidth / aspect
                    Box(
                        modifier = Modifier
                            .size(surfaceWidth, surfaceHeight)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .onSizeChanged { imageBoxSize = it }
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Página en edición",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        if (editor.cropMode) {
                            val quad = editor.cropQuad
                            Canvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { position ->
                                                val points = vm.editor.cropQuad.points()
                                                var nearest = -1
                                                var nearestDistance = 48.dp.toPx()
                                                points.forEachIndexed { index, point ->
                                                    val px = point.x * size.width
                                                    val py = point.y * size.height
                                                    val distance = hypot(position.x - px, position.y - py)
                                                    if (distance < nearestDistance) {
                                                        nearestDistance = distance
                                                        nearest = index
                                                    }
                                                }
                                                activeCorner = nearest
                                            },
                                            onDragEnd = { activeCorner = -1 },
                                            onDragCancel = { activeCorner = -1 },
                                            onDrag = { change, dragAmount ->
                                                if (activeCorner < 0) return@detectDragGestures
                                                change.consume()
                                                val current = vm.editor.cropQuad.points()[activeCorner]
                                                val updated = QuadPoint(
                                                    (current.x + dragAmount.x / size.width).coerceIn(0f, 1f),
                                                    (current.y + dragAmount.y / size.height).coerceIn(0f, 1f)
                                                )
                                                vm.editor.cropQuad = vm.editor.cropQuad.withPoint(activeCorner, updated)
                                            }
                                        )
                                    }
                            ) {
                                val points = quad.points().map {
                                    Offset(it.x * size.width, it.y * size.height)
                                }
                                val path = Path().apply {
                                    moveTo(points[0].x, points[0].y)
                                    lineTo(points[1].x, points[1].y)
                                    lineTo(points[2].x, points[2].y)
                                    lineTo(points[3].x, points[3].y)
                                    close()
                                }
                                val shade = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                                    moveTo(points[0].x, points[0].y)
                                    lineTo(points[1].x, points[1].y)
                                    lineTo(points[2].x, points[2].y)
                                    lineTo(points[3].x, points[3].y)
                                    close()
                                }
                                drawPath(shade, Color.Black.copy(alpha = 0.42f))
                                drawPath(path, ScannerAccent, style = Stroke(width = 2.dp.toPx()))
                                points.forEachIndexed { index, point ->
                                    drawCircle(
                                        color = if (index == activeCorner) Color.White else ScannerAccent,
                                        radius = 11.dp.toPx(),
                                        center = point
                                    )
                                    drawCircle(
                                        color = ScannerAccent,
                                        radius = 6.dp.toPx(),
                                        center = point
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(10.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape
                            ) {
                                Text(
                                    "Arrastra los vértices",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (editor.eraserMode) {
                            Canvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { liveEraserStroke = listOf(it) },
                                            onDragEnd = {
                                                if (liveEraserStroke.isNotEmpty()) {
                                                    vm.addEraserStroke(liveEraserStroke.map {
                                                        QuadPoint(it.x / size.width, it.y / size.height)
                                                    })
                                                }
                                                liveEraserStroke = emptyList()
                                            },
                                            onDragCancel = { liveEraserStroke = emptyList() },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                liveEraserStroke = liveEraserStroke + change.position
                                            }
                                        )
                                    }
                            ) {
                                val strokeWidth = max(size.width, size.height) * 0.035f
                                fun drawStroke(points: List<Offset>, alpha: Float) {
                                    if (points.size == 1) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = alpha),
                                            radius = strokeWidth / 2f,
                                            center = points[0]
                                        )
                                    } else if (points.size > 1) {
                                        val path = Path().apply {
                                            moveTo(points[0].x, points[0].y)
                                            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color.White.copy(alpha = alpha),
                                            style = Stroke(
                                                width = strokeWidth,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                editor.strokes.forEach { stroke ->
                                    drawStroke(
                                        stroke.map { Offset(it.x * size.width, it.y * size.height) },
                                        0.92f
                                    )
                                }
                                drawStroke(liveEraserStroke, 0.85f)
                            }
                        }

                        val signature = editor.signature
                        if (signature != null && !editor.cropMode) {
                            val sigWidth = imageBoxSize.width * editor.signatureWidth
                            val sigHeight = if (signature.width > 0) {
                                sigWidth * signature.height.toFloat() / signature.width.toFloat()
                            } else {
                                sigWidth
                            }
                            val x = (imageBoxSize.width * editor.signatureX)
                                .coerceIn(0f, max(0f, imageBoxSize.width - sigWidth))
                            val y = (imageBoxSize.height * editor.signatureY)
                                .coerceIn(0f, max(0f, imageBoxSize.height - sigHeight))
                            Image(
                                bitmap = signature.asImageBitmap(),
                                contentDescription = "Firma",
                                modifier = Modifier
                                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                                    .size(
                                        with(density) { sigWidth.toDp() },
                                        with(density) { sigHeight.toDp() }
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val widthFraction = signature.width.toFloat()
                                            vm.editor.signatureX =
                                                (vm.editor.signatureX + dragAmount.x / imageBoxSize.width)
                                                    .coerceIn(0f, 0.98f)
                                            vm.editor.signatureY =
                                                (vm.editor.signatureY + dragAmount.y / imageBoxSize.height)
                                                    .coerceIn(0f, 0.98f)
                                            vm.editor.dirty = true
                                        }
                                    }
                            )
                        }

                        if (editor.processing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                when {
                    editor.cropMode -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Recorte manual", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ajusta las 4 esquinas del documento.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                vm.editor.cropQuad = DocumentQuad.fullFrame(0.02f)
                                vm.editor.cropMode = false
                            }) { Text("Cancelar") }
                            Button(onClick = { vm.applyEditorCrop() }) { Text("Aplicar") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    editor.eraserMode -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Borrador", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Desliza para borrar manchas o sombras.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { vm.undoEraserStroke() },
                                enabled = editor.strokes.isNotEmpty()
                            ) {
                                Icon(Icons.Rounded.Undo, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Deshacer")
                            }
                            Button(onClick = { vm.editor.eraserMode = false }) { Text("Listo") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    editor.signatureMode && editor.signature != null -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Firma", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Arrastra la firma para colocarla.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { vm.clearSignature() }) { Text("Quitar") }
                                Button(onClick = { vm.editor.signatureMode = false }) { Text("Listo") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tamaño", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = editor.signatureWidth,
                                    onValueChange = {
                                        vm.editor.signatureWidth = it
                                        vm.editor.dirty = true
                                    },
                                    valueRange = 0.1f..0.8f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            EditorTool(Icons.Rounded.RotateRight, "Girar") { vm.rotateEditor() }
                            EditorTool(Icons.Rounded.Crop, "Recortar", active = editor.cropMode) {
                                editor.cropMode = true
                                editor.eraserMode = false
                                editor.signatureMode = false
                            }
                            EditorTool(Icons.Rounded.Brush, "Borrar", active = editor.eraserMode) {
                                editor.eraserMode = true
                                editor.cropMode = false
                                editor.signatureMode = false
                            }
                            EditorTool(Icons.Rounded.Draw, "Firma") {
                                if (editor.signature == null) showSignaturePad = true
                                else {
                                    editor.signatureMode = true
                                    editor.cropMode = false
                                    editor.eraserMode = false
                                }
                            }
                            EditorTool(Icons.Rounded.TextFields, "OCR") { vm.runOcr() }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DocFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = editor.filter == filter,
                                    onClick = { vm.setEditorFilter(filter) },
                                    label = { Text(filter.label) }
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AdjustmentSlider(
                                label = "Brillo",
                                value = editor.brightness,
                                range = -1f..1f,
                                onChange = { vm.setEditorBrightness(it) }
                            )
                            AdjustmentSlider(
                                label = "Contraste",
                                value = editor.contrast,
                                range = -1f..1f,
                                onChange = { vm.setEditorContrast(it) }
                            )
                            AdjustmentSlider(
                                label = "Nitidez",
                                value = editor.sharpness,
                                range = 0f..1f,
                                onChange = { vm.setEditorSharpness(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onConfirm = { bitmap ->
                showSignaturePad = false
                vm.setSignature(bitmap)
            }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Descartar cambios") },
            text = { Text("Los ajustes que no has guardado se perderán.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    vm.closeEditor()
                    vm.pop()
                }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Seguir editando") }
            }
        )
    }

    if (editor.ocrBusy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Reconociendo texto") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Procesando OCR en el dispositivo…")
                }
            }
        )
    }

    editor.ocrText?.let { text ->
        AlertDialog(
            onDismissRequest = { vm.dismissOcr() },
            title = { Text("Texto reconocido") },
            text = {
                Box(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text.ifBlank { "No se detectó texto en esta página." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                if (text.isNotBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        vm.dismissOcr()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissOcr() }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun EditorTool(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(64.dp),
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (range.start < 0f) "${(value * 100).roundToInt()}" else "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )
    }
}
