package com.doculens.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.Surface as AndroidSurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.QuadPoint
import com.doculens.app.domain.ScanMode
import com.doculens.app.ui.components.FileThumbnail
import com.doculens.app.ui.components.rememberMlKitScanner
import com.doculens.app.ui.theme.ScannerAccent
import com.doculens.app.ui.viewmodel.AppViewModel
import com.doculens.app.utils.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(vm: AppViewModel, docId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionAsked = true
        if (!granted) vm.notify("Se necesita permiso de cámara para escanear")
    }

    var mode by rememberSaveable { mutableStateOf(ScanMode.FAST) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var frameQuad by rememberSaveable(stateSaver = QuadSaver) { mutableStateOf(defaultFrameQuad()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewAspect by remember { mutableStateOf(3f / 4f) }
    var capturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) vm.importUrisToCurrent(uris, autoCrop = true)
    }
    val mlKit = rememberMlKitScanner(
        onPages = { vm.addMlKitPages(docId, it, openDocument = true) },
        onError = { vm.notify(it) }
    )

    val doc = vm.currentDocument?.takeIf { it.id == docId }
    val pages = doc?.pages.orEmpty()

    LaunchedEffect(docId) {
        if (vm.currentDocument?.id != docId) vm.openDocumentWithoutNavigation(docId)
    }

    fun closeScanner() {
        vm.finishScan(docId)
    }

    fun capture() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        val cropQuad = frameQuad
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        runCatching {
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val bitmap = withContext(Dispatchers.IO) {
                                ImageProcessor.decodeFile(file, 2600)
                            }
                            file.delete()
                            if (bitmap != null) {
                                val ok = vm.addCapturedPage(docId, bitmap, cropQuad = cropQuad)
                                if (ok) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    vm.notify("No se pudo guardar la página")
                                }
                            } else {
                                vm.notify("No se pudo procesar la captura")
                            }
                            capturing = false
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        capturing = false
                        vm.notify("Error al capturar: ${exception.message}")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            )
        }.onFailure {
            capturing = false
            vm.notify("La cámara no está lista")
        }
    }

    if (!hasPermission) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = { closeScanner() },
            permanentlyDenied = permissionAsked
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(targetState = mode, label = "scanner_mode") { currentMode ->
            if (currentMode == ScanMode.FAST) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        flashMode = flashMode,
                        onCaptureReady = { imageCapture = it },
                        onAspect = { aspect ->
                            if (aspect > 0f) previewAspect = aspect
                        },
                        onError = { cameraError = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    CameraFrameOverlay(
                        quad = frameQuad,
                        imageAspect = previewAspect,
                        onQuadChange = { frameQuad = it }
                    )
                    if (cameraError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No se pudo iniciar la cámara",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    cameraError ?: "",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(18.dp))
                                Button(onClick = { mlKit() }) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Usar ML Kit")
                                }
                            }
                        }
                    }
                }
            } else {
                PrecisionModePanel(
                    pageCount = pages.size,
                    onLaunchMlKit = { mlKit() },
                    onGallery = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onContinue = { closeScanner() }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundDarkButton(onClick = { closeScanner() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                ModeToggle(mode = mode, onModeChange = { mode = it })
                Spacer(Modifier.weight(1f))
                if (mode == ScanMode.FAST) {
                    IconButton(onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                                else -> Icons.Rounded.FlashOff
                            },
                            contentDescription = "Flash",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            if (mode == ScanMode.FAST) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ajusta las esquinas del marco a la hoja",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = CircleShape,
                        modifier = Modifier.clickable { frameQuad = defaultFrameQuad() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = ScannerAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Marco",
                                color = ScannerAccent,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (pages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pages, key = { it.absolutePath }) { page ->
                            Box {
                                FileThumbnail(
                                    file = page,
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 58.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    corner = 8.dp,
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundDarkButton(onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Galería", tint = Color.White)
                    }
                    ShutterButton(
                        capturing = capturing,
                        enabled = imageCapture != null && !capturing,
                        onClick = { capture() }
                    )
                    RoundDarkButton(onClick = { closeScanner() }, highlight = pages.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Terminar",
                            tint = if (pages.isNotEmpty()) ScannerAccent else Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundDarkButton(onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Galería", tint = Color.White)
                    }
                    RoundDarkButton(onClick = { mlKit() }) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "ML Kit", tint = Color.White)
                    }
                    RoundDarkButton(onClick = { closeScanner() }, highlight = pages.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Terminar",
                            tint = if (pages.isNotEmpty()) ScannerAccent else Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(capturing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (capturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(ScannerAccent.copy(alpha = 0.22f))
                )
            }
        }
    }
}

@Composable
private fun RoundDarkButton(
    onClick: () -> Unit,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
                if (highlight) ScannerAccent.copy(alpha = 0.2f)
                else Color.Black.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ModeToggle(mode: ScanMode, onModeChange: (ScanMode) -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            ScanMode.entries.forEach { option ->
                val selected = option == mode
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onModeChange(option) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = option.label,
                        color = if (selected) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecisionModePanel(
    pageCount: Int,
    onLaunchMlKit: () -> Unit,
    onGallery: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C132E), Color(0xFF1C2F71), Color(0xFF0C132E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ScannerAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = ScannerAccent,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Escaneo inteligente",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Detección de bordes y corrección de perspectiva automáticas con ML Kit Document Scanner.",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            FeatureRow(Icons.Rounded.Tune, "Auto-recorte de alta precisión")
            FeatureRow(Icons.Rounded.Speed, "Varias páginas en una sesión")
            FeatureRow(Icons.Rounded.Security, "Procesamiento en el dispositivo")
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onLaunchMlKit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScannerAccent,
                    contentColor = Color(0xFF00382C)
                )
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir escáner ML Kit", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onGallery,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Galería")
                }
                if (pageCount > 0) {
                    OutlinedButton(
                        onClick = onContinue,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Continuar ($pageCount)")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Si ML Kit no está disponible, usa el modo Rápida.",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ScannerAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun defaultFrameQuad(): DocumentQuad = DocumentQuad(
    topLeft = QuadPoint(0.07f, 0.10f),
    topRight = QuadPoint(0.93f, 0.10f),
    bottomRight = QuadPoint(0.93f, 0.90f),
    bottomLeft = QuadPoint(0.07f, 0.90f)
)

private val QuadSaver = listSaver<DocumentQuad, Float>(
    save = { quad ->
        listOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )
    },
    restore = { values ->
        DocumentQuad(
            topLeft = QuadPoint(values[0], values[1]),
            topRight = QuadPoint(values[2], values[3]),
            bottomRight = QuadPoint(values[4], values[5]),
            bottomLeft = QuadPoint(values[6], values[7])
        )
    }
)

@Composable
private fun CameraFrameOverlay(
    quad: DocumentQuad,
    imageAspect: Float,
    onQuadChange: (DocumentQuad) -> Unit
) {
    var activeCorner by remember { mutableStateOf(-1) }
    val latestQuad by rememberUpdatedState(quad)
    val pulseTransition = rememberInfiniteTransition(label = "frame")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "framePulse"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        val points = latestQuad.points().map {
                            mapToView(it, size.width.toFloat(), size.height.toFloat(), imageAspect)
                        }
                        var nearest = -1
                        var nearestDistance = 56.dp.toPx()
                        points.forEachIndexed { index, point ->
                            val distance = (point - position).getDistance()
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
                        val delta = viewDeltaToImage(
                            dragAmount,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            imageAspect
                        )
                        val current = latestQuad.points()[activeCorner]
                        val updated = QuadPoint(
                            (current.x + delta.x).coerceIn(0f, 1f),
                            (current.y + delta.y).coerceIn(0f, 1f)
                        )
                        onQuadChange(latestQuad.withPoint(activeCorner, updated))
                    }
                )
            }
    ) {
        val points = quad.points().map {
            mapToView(it, size.width, size.height, imageAspect)
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
            addRect(Rect(0f, 0f, size.width, size.height))
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(shade, Color.Black.copy(alpha = 0.28f))
        drawPath(
            path = path,
            color = ScannerAccent.copy(alpha = 0.9f * pulse),
            style = Stroke(width = 2.5.dp.toPx())
        )
        points.forEachIndexed { index, point ->
            val radius = if (index == activeCorner) 13.dp.toPx() else 10.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = radius,
                center = point
            )
            drawCircle(
                color = if (index == activeCorner) Color.White else ScannerAccent,
                radius = radius - 3.dp.toPx(),
                center = point
            )
        }
        val centerX = (points[0].x + points[1].x + points[2].x + points[3].x) / 4f
        val centerY = (points[0].y + points[1].y + points[2].y + points[3].y) / 4f
        drawCircle(
            color = ScannerAccent.copy(alpha = 0.7f * pulse),
            radius = 2.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

private fun viewDeltaToImage(
    delta: Offset,
    viewW: Float,
    viewH: Float,
    imageAspect: Float
): Offset {
    if (viewW <= 0f || viewH <= 0f) return Offset.Zero
    if (imageAspect <= 0f) return Offset(delta.x / viewW, delta.y / viewH)
    val viewAspect = viewW / viewH
    return if (viewAspect >= imageAspect) {
        val displayWidth = viewH * imageAspect
        Offset(delta.x / displayWidth, delta.y / viewH)
    } else {
        val displayHeight = viewW / imageAspect
        Offset(delta.x / viewW, delta.y / displayHeight)
    }
}

private fun mapToView(point: QuadPoint, viewW: Float, viewH: Float, imageAspect: Float): Offset {
    if (imageAspect <= 0f) return Offset(point.x * viewW, point.y * viewH)
    val viewAspect = viewW / viewH
    return if (viewAspect >= imageAspect) {
        val displayWidth = viewH * imageAspect
        val offsetX = (viewW - displayWidth) / 2f
        Offset(offsetX + point.x * displayWidth, point.y * viewH)
    } else {
        val displayHeight = viewW / imageAspect
        val offsetY = (viewH - displayHeight) / 2f
        Offset(point.x * viewW, offsetY + point.y * displayHeight)
    }
}

@Composable
private fun CameraPreview(
    flashMode: Int,
    onCaptureReady: (ImageCapture) -> Unit,
    onAspect: (Float) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        val listener = Runnable {
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val rotation = previewView.display?.rotation ?: AndroidSurface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val newCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .setFlashMode(flashMode)
                    .build()
                capture = newCapture
                onCaptureReady(newCapture)
                val newAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build()
                analysis = newAnalysis
                var reportedAspect = false
                newAnalysis.setAnalyzer(executor) { proxy ->
                    try {
                        if (!reportedAspect) {
                            reportedAspect = true
                            val rotationDegrees = proxy.imageInfo.rotationDegrees
                            val aspect = if (rotationDegrees == 90 || rotationDegrees == 270) {
                                proxy.height.toFloat() / proxy.width.toFloat()
                            } else {
                                proxy.width.toFloat() / proxy.height.toFloat()
                            }
                            mainHandler.post {
                                onAspect(aspect)
                                runCatching { newAnalysis.clearAnalyzer() }
                            }
                        }
                    } finally {
                        proxy.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    newCapture,
                    newAnalysis
                )
            } catch (throwable: Throwable) {
                mainHandler.post { onError(throwable.message ?: "Error de cámara") }
            }
        }
        future.addListener(listener, mainExecutor)
        onDispose {
            analysis?.clearAnalyzer()
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(flashMode) {
        capture?.flashMode = flashMode
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun PermissionScreen(
    onRequest: () -> Unit,
    onClose: () -> Unit,
    permanentlyDenied: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C132E), Color(0xFF17275E), Color(0xFF0C132E))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ScannerAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    tint = ScannerAccent,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Permiso de cámara",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (permanentlyDenied) {
                    "Activa el permiso de cámara en los ajustes del sistema para escanear documentos."
                } else {
                    "DocuLens necesita la cámara para capturar tus documentos. Nada se sube a internet."
                },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScannerAccent,
                    contentColor = Color(0xFF00382C)
                )
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Permitir cámara", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
