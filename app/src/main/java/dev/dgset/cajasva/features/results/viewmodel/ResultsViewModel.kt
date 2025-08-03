package dev.dgset.cajasva.features.results.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dgset.cajasva.domain.model.DetectedObject
import dev.dgset.cajasva.domain.repository.CashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val cashRepository: CashRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState = _uiState.asStateFlow()

    fun processImageUri(imageUri: Uri) {
        Log.d("ResultsViewModel", "Processing image URI: $imageUri")
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { loadBitmapFromUri(imageUri) }

                Log.d("ResultsViewModel", "Bitmap loaded: ${bitmap.width}x${bitmap.height}")

                val originalWidth = bitmap.width
                val originalHeight = bitmap.height
                val aspectRatio = if (originalHeight > 0) originalWidth.toFloat() / originalHeight.toFloat() else 1f
                _uiState.update { it.copy(
                    originalImageWidth = originalWidth,
                    originalImageHeight = originalHeight,
                    imageAspectRatio = aspectRatio
                )}

                cashRepository.detectAndClassify(bitmap).fold(
                    onSuccess = { detectedObjects ->
                        Log.d("ResultsViewModel", "Detection successful: ${detectedObjects.size} objects detected before processing.")

                        // --- CAMBIO CLAVE: Llamar a la función de mapeo mejorada ---
                        val (processedDetections, totalAmount) = mapDetectionsToValues(detectedObjects)

                        Log.d("ResultsViewModel", "Processing complete. Total amount: $$totalAmount")
                        processedDetections.forEach { obj ->
                            Log.d("ResultsViewModel", "Processed: ${obj.label} with value $${obj.value}")
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                breakdown = processedDetections, // Usar la lista procesada
                                totalAmount = totalAmount        // Usar el total calculado
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e("ResultsViewModel", "Detection failed: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Error desconocido"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ResultsViewModel", "Error processing image: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al procesar imagen: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * FUNCIÓN MEJORADA: Mapea detecciones a valores con lógica corregida
     */
    private fun mapDetectionsToValues(detections: List<DetectedObject>): Pair<List<DetectedObject>, Double> {
        if (detections.isEmpty()) {
            return Pair(emptyList(), 0.0)
        }

        var total = 0.0
        val processedDetections = detections.map { detection ->
            val detectedValue = when (detection.label) {
                // Billetes - valores fijos
                "billete_20" -> 20.0
                "billete_50" -> 50.0
                "billete_100" -> 100.0
                "billete_200" -> 200.0
                "billete_500" -> 500.0
                "billete_1000" -> 1000.0

                // Monedas con denominación específica - valores fijos
                "moneda_0_50" -> 0.5
                "moneda_10" -> 10.0
                "moneda_1_anverso" -> 1.0  // CORREGIDO: siempre 1 peso
                "moneda_2_anverso" -> 2.0  // CORREGIDO: siempre 2 pesos
                "moneda_5_anverso" -> 5.0  // CORREGIDO: siempre 5 pesos

                // Moneda reverso común - requiere comparación con anversos
                "moneda_reverso_comun" -> {
                    Log.d("ResultsViewModel", "Determinando valor para moneda_reverso_comun")
                    determineReverseValue(detection, detections)
                }

                // Manejo de clasificaciones inciertas
                "moneda_comun_reverso" -> {
                    Log.d("ResultsViewModel", "Detectada moneda_comun_reverso, aplicando lógica de comparación")
                    determineReverseValue(detection, detections)
                }

                // Clasificaciones inciertas (del sistema de reintento)
                else -> {
                    if (detection.label.startsWith("incierto_")) {
                        Log.w("ResultsViewModel", "Clasificación incierta: ${detection.label}")
                        0.0 // No sumar valor incierto al total
                    } else {
                        Log.w("ResultsViewModel", "Etiqueta no reconocida: ${detection.label}")
                        0.0 // Valor por defecto si la etiqueta no se reconoce
                    }
                }
            }

            total += detectedValue
            Log.d("ResultsViewModel", "Asignado valor $detectedValue a ${detection.label}")

            // Creamos una nueva instancia de DetectedObject con el valor actualizado
            detection.copy(value = detectedValue)
        }

        return Pair(processedDetections, total)
    }

    /**
     * NUEVA FUNCIÓN: Determina el valor de una moneda reverso común
     * comparando su tamaño con monedas anverso detectadas en la misma imagen
     */
    private fun determineReverseValue(
        reverseDetection: DetectedObject,
        allDetections: List<DetectedObject>
    ): Double {
        // Buscar monedas anverso detectadas en la misma imagen
        val anversoCoins = allDetections.filter {
            it.label.contains("_anverso") && it.label.startsWith("moneda_")
        }

        if (anversoCoins.isEmpty()) {
            Log.d("ResultsViewModel", "No hay monedas anverso para comparar, asignando valor por defecto: 1.0")
            return 1.0 // Si no hay referencias, usar valor más común
        }

        // Calcular área de la moneda reverso
        val reverseArea = reverseDetection.boundingBox.width() * reverseDetection.boundingBox.height()
        Log.d("ResultsViewModel", "Área de moneda reverso: $reverseArea")

        // Encontrar la moneda anverso más cercana en tamaño
        val closestCoin = anversoCoins.minByOrNull { anversoCoin ->
            val anversoArea = anversoCoin.boundingBox.width() * anversoCoin.boundingBox.height()
            val difference = abs(reverseArea - anversoArea)
            Log.d("ResultsViewModel", "Comparando con ${anversoCoin.label}, área: $anversoArea, diferencia: $difference")
            difference
        }

        val assignedValue = when (closestCoin?.label) {
            "moneda_1_anverso" -> 1.0
            "moneda_2_anverso" -> 2.0
            "moneda_5_anverso" -> 5.0
            else -> 1.0 // Valor por defecto
        }

        Log.d("ResultsViewModel", "Moneda reverso asignada valor $assignedValue basado en ${closestCoin?.label}")
        return assignedValue
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return try {
            Log.d("ResultsViewModel", "Loading bitmap from URI: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo de imagen")

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val targetSize = 1024
            val scaleFactor = calculateInSampleSize(options, targetSize)

            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val inputStream2 = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo de imagen")

            val bitmap = BitmapFactory.decodeStream(inputStream2, null, finalOptions)
                ?: throw IOException("No se pudo decodificar la imagen")

            inputStream2.close()

            correctImageRotation(bitmap, uri)
        } catch (e: Exception) {
            Log.e("ResultsViewModel", "Error loading bitmap from URI: ${e.message}", e)
            Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqSize: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqSize || width > reqSize) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun correctImageRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees)
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.w("ResultsViewModel", "Could not read EXIF info, using original bitmap", e)
            bitmap
        }
    }
}