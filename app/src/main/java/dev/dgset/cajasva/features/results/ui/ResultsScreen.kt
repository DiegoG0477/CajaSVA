package dev.dgset.cajasva.features.results.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import dev.dgset.cajasva.features.results.viewmodel.ResultsUiState
import dev.dgset.cajasva.features.results.viewmodel.ResultsViewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun ResultsScreen(
    imageUri: Uri,
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Procesar la imagen cuando se carga la pantalla
    LaunchedEffect(imageUri) {
        viewModel.processImageUri(imageUri)
    }

    ResultsContent(
        uiState = uiState,
        onScanAgain = onScanAgain,
        onBack = onBack,
        imageUri = imageUri
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsContent(
    uiState: ResultsUiState,
    imageUri: Uri,
    onScanAgain: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados del Escaneo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.error != null) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            } else {
                // Caja para mostrar la imagen con las detecciones
                var imageSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(uiState.imageAspectRatio) // Usar aspect ratio calculado
                        .onSizeChanged { imageSize = it }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = "Imagen Escaneada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        uiState.breakdown.forEach { detectedObject ->
                            // Escalar las coordenadas del bounding box al tamaño del Composable
                            val scaleX = size.width / uiState.originalImageWidth
                            val scaleY = size.height / uiState.originalImageHeight

                            drawRect(
                                color = Color.Red,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    x = detectedObject.boundingBox.left * scaleX,
                                    y = detectedObject.boundingBox.top * scaleY
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    width = detectedObject.boundingBox.width() * scaleX,
                                    height = detectedObject.boundingBox.height() * scaleY
                                ),
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Total Formateado ---
                Text(
                    "Total: $${"%.2f".format(uiState.totalAmount)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Desglose Detallado ---
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.breakdown) { detectedObject ->
                        // Formatear la confianza como porcentaje
                        val confidencePercent = detectedObject.confidence * 100
                        val confidenceText = "%.1f%%".format(confidencePercent)

                        Text(
                            text = "${detectedObject.label}: $${"%.2f".format(detectedObject.value)} (Conf: $confidenceText)",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onScanAgain) {
                    Text("Escanear de Nuevo")
                }
            }
        }
    }
}
