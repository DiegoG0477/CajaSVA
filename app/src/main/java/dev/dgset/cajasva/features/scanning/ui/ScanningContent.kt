package dev.dgset.cajasva.features.scanning.ui

import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dgset.cajasva.features.scanning.viewmodel.ScanningUiState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun ScanningContent(
    uiState: ScanningUiState,
    onTakePicture: () -> Unit,
    onCameraReady: (ImageCapture) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    val camera: MutableState<androidx.camera.core.Camera?> = remember { mutableStateOf(null) }
    val isFlashEnabled: MutableState<Boolean> = remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Inicialización de cámara directa y simple
    LaunchedEffect(previewView, uiState.hasCameraPermission, uiState.isCameraReady) {
        Log.d("ScanningContent", "LaunchedEffect triggered - previewView: $previewView, hasCameraPermission: ${uiState.hasCameraPermission}, isCameraReady: ${uiState.isCameraReady}")

        if (previewView != null && uiState.hasCameraPermission && !uiState.isCameraReady) {
            Log.d("ScanningContent", "Starting camera initialization...")

            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                Log.d("ScanningContent", "Got camera provider future")

                // Usar el executor de la cámara para no bloquear el hilo principal
                cameraProviderFuture.addListener({
                    try {
                        Log.d("ScanningContent", "Camera provider listener called")
                        val cameraProvider = cameraProviderFuture.get()
                        Log.d("ScanningContent", "Got camera provider: $cameraProvider")

                        // Configurar preview
                        val cameraPreview = Preview.Builder().build()
                        Log.d("ScanningContent", "Created preview")

                        // Configurar captura de imagen con flash
                        val imageCaptureUseCase = ImageCapture.Builder()
                            .setFlashMode(if (isFlashEnabled.value) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                            .build()
                        Log.d("ScanningContent", "Created image capture")

                        // Selector de cámara (cámara trasera)
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        Log.d("ScanningContent", "Created camera selector")

                        // Desenlazar todos los casos de uso anteriores
                        cameraProvider.unbindAll()
                        Log.d("ScanningContent", "Unbound all use cases")

                        // Conectar preview al PreviewView
                        cameraPreview.setSurfaceProvider(previewView!!.surfaceProvider)
                        Log.d("ScanningContent", "Set surface provider")

                        // Vincular al ciclo de vida
                        camera.value = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            cameraPreview,
                            imageCaptureUseCase
                        )
                        Log.d("ScanningContent", "Bound to lifecycle, camera: ${camera.value}")

                        // Notificar que la cámara está lista
                        onCameraReady(imageCaptureUseCase)
                        Log.d("ScanningContent", "Camera ready callback called")

                    } catch (e: Exception) {
                        Log.e("ScanningContent", "Camera binding error: ${e.message}", e)
                        val imageCaptureUseCase = ImageCapture.Builder().build()
                        onCameraReady(imageCaptureUseCase)
                        Log.d("ScanningContent", "Fallback camera ready callback called")
                    }
                }, ContextCompat.getMainExecutor(context))

                Log.d("ScanningContent", "Camera provider listener added")

            } catch (e: Exception) {
                Log.e("ScanningContent", "Failed to get camera provider: ${e.message}", e)
                val imageCaptureUseCase = ImageCapture.Builder().build()
                onCameraReady(imageCaptureUseCase)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !uiState.hasCameraPermission -> {
                // Pantalla de solicitud de permisos
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📷",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Esta app necesita acceso a la cámara para escanear billetes y monedas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            !uiState.isCameraReady -> {
                // Pantalla de carga de cámara - MOSTRAR SIEMPRE EL PREVIEW VIEW
                Box(modifier = Modifier.fillMaxSize()) {
                    // Crear el PreviewView inmediatamente, incluso durante la carga
                    AndroidView(
                        factory = { ctx ->
                            Log.d("ScanningContent", "Creating PreviewView in factory")
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }.also { preview ->
                                Log.d("ScanningContent", "PreviewView created: $preview")
                                previewView = preview
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay de carga
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Iniciando cámara...")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "PreviewView: ${if (previewView != null) "✓" else "✗"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Permiso: ${if (uiState.hasCameraPermission) "✓" else "✗"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Estado: ${if (uiState.isCameraReady) "Listo" else "Inicializando"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // Vista de cámara lista para escanear
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Vista real de la cámara con controles táctiles
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageCapture = ImageCapture.Builder().build()
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                    try {
                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            lifecycleOwner, cameraSelector, preview, imageCapture
                                        )
                                        camera.value = cam
                                        onCameraReady(imageCapture)
                                    } catch (exc: Exception) {
                                        Log.e("ScanningContent", "Error al vincular casos de uso", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView.setOnTouchListener { view, event ->
                                    if (event.action == MotionEvent.ACTION_DOWN) {
                                        val factory = previewView.meteringPointFactory
                                        val point = factory.createPoint(event.x, event.y)
                                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                            .build()
                                        camera.value?.cameraControl?.startFocusAndMetering(action)
                                        view.performClick()
                                    }
                                    true
                                }
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlay con instrucciones y controles
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Instrucciones en la parte superior
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                            ) {
                                Text(
                                    text = "Toca para enfocar • Coloca billetes y monedas visibles",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Controles en la parte inferior derecha
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                // Botón de flash
                                IconButton(
                                    onClick = {
                                        isFlashEnabled.value = !isFlashEnabled.value
                                        camera.value?.cameraControl?.enableTorch(isFlashEnabled.value)
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            color = if (isFlashEnabled.value)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            else
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isFlashEnabled.value) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Flash",
                                        tint = if (isFlashEnabled.value) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Controles inferiores
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de volver
                        IconButton(onClick = onBackClick) {
                            Text("←", fontSize = 24.sp)
                        }

                        // Botón de captura
                        Button(
                            onClick = onTakePicture,
                            enabled = !uiState.isTakingPicture,
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            if (uiState.isTakingPicture) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            } else {
                                Text("📸", fontSize = 32.sp)
                            }
                        }

                        // Indicador de estado del flash
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isFlashEnabled.value) "Flash ON" else "Flash OFF",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFlashEnabled.value)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
