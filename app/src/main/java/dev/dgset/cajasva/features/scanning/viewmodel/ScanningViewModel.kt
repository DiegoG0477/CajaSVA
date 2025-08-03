package dev.dgset.cajasva.features.scanning.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dgset.cajasva.core.navigation.NavigationEvent
import dev.dgset.cajasva.core.ui.utils.ComposeFileProvider
import dev.dgset.cajasva.data.local.ml.MoneyDetector
import dev.dgset.cajasva.domain.model.DetectedObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class ScanningUiState(
    val hasCameraPermission: Boolean = false,
    val isCameraReady: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isProcessingImage: Boolean = false,
    val processedResults: List<DetectedObject>? = null,
    val totalAmount: Double = 0.0,
    val error: String? = null
)

@HiltViewModel
class ScanningViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moneyDetector: MoneyDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanningUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var imageCapture: ImageCapture? = null
    private var lastProcessedImageUri: Uri? = null

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = isGranted) }
    }

    fun onCameraReady(imageCapture: ImageCapture) {
        this.imageCapture = imageCapture
        _uiState.update { it.copy(isCameraReady = true) }
    }

    fun onTakePicture() {
        if (!_uiState.value.isCameraReady || imageCapture == null) return
        _uiState.update { it.copy(isTakingPicture = true, error = null) }

        val imageFileResult = ComposeFileProvider.createImageFile(context)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFileResult.file).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = imageFileResult.uri
                    lastProcessedImageUri = savedUri
                    _uiState.update { it.copy(isTakingPicture = false, isProcessingImage = true) }
                    
                    // Process the captured image immediately
                    processImageWithMoneyDetector(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanningViewModel", "Image capture error: ${exception.message}", exception)
                    _uiState.update { 
                        it.copy(
                            isTakingPicture = false, 
                            error = "Error al capturar imagen: ${exception.message}"
                        ) 
                    }
                }
            }
        )
    }

    /**
     * Processes the captured image using MoneyDetector for detection and classification
     */
    private fun processImageWithMoneyDetector(imageUri: Uri) {
        Log.d("ScanningViewModel", "Starting image processing with MoneyDetector")
        
        viewModelScope.launch {
            try {
                // Load bitmap from URI
                val bitmap = withContext(Dispatchers.IO) { 
                    loadBitmapFromUri(imageUri) 
                }
                
                Log.d("ScanningViewModel", "Loaded bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Process with MoneyDetector
                moneyDetector.detectAndClassifyMoney(bitmap).fold(
                    onSuccess = { detectedObjects ->
                        val totalAmount = detectedObjects.sumOf { it.value }
                        
                        Log.d("ScanningViewModel", "Processing successful: ${detectedObjects.size} objects, total: $$totalAmount")
                        detectedObjects.forEach { obj ->
                            Log.d("ScanningViewModel", "Detected: ${obj.label} = $${obj.value} (confidence: ${obj.confidence})")
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isProcessingImage = false,
                                processedResults = detectedObjects,
                                totalAmount = totalAmount,
                                error = null
                            ) 
                        }
                        
                        // Store processed data for ResultsViewModel
                        setLastProcessedData(detectedObjects, totalAmount)
                        
                        // Navigate to results with processed data
                        viewModelScope.launch {
                            _navigationEvent.emit(NavigationEvent.NavigateToResults(imageUri))
                        }
                    },
                    onFailure = { error ->
                        Log.e("ScanningViewModel", "Money detection failed: ${error.message}", error)
                        _uiState.update { 
                            it.copy(
                                isProcessingImage = false,
                                error = "Error al procesar imagen: ${error.message}"
                            ) 
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e("ScanningViewModel", "Error in image processing: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isProcessingImage = false,
                        error = "Error al cargar imagen: ${e.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Loads bitmap from URI with proper rotation correction
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return try {
            Log.d("ScanningViewModel", "Loading bitmap from URI: $uri")

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
            Log.e("ScanningViewModel", "Error loading bitmap from URI: ${e.message}", e)
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
            Log.w("ScanningViewModel", "Could not read EXIF info, using original bitmap", e)
            bitmap
        }
    }

    /**
     * Clears the error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Gets the processed results for the results screen
     */
    fun getProcessedResults(): List<DetectedObject>? = _uiState.value.processedResults

    /**
     * Gets the total amount from processed results
     */
    fun getTotalAmount(): Double = _uiState.value.totalAmount

    /**
     * Debug function to get size references from MoneyDetector
     */
    fun getSizeReferences(): Map<Double, Float> = moneyDetector.getSizeReferences()

    /**
     * Debug function to clear size references
     */
    fun clearSizeReferences() {
        moneyDetector.clearSizeReferences()
    }

    fun onBackClick() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateBack)
        }
    }
    
    companion object {
        // Temporary storage for processed results to pass to ResultsViewModel
        @Volatile
        private var lastProcessedResults: List<DetectedObject>? = null
        
        @Volatile
        private var lastTotalAmount: Double = 0.0
        
        fun setLastProcessedData(results: List<DetectedObject>, totalAmount: Double) {
            lastProcessedResults = results
            lastTotalAmount = totalAmount
        }
        
        fun getLastProcessedData(): Pair<List<DetectedObject>?, Double> {
            val results = lastProcessedResults
            val amount = lastTotalAmount
            // Clear after reading to avoid stale data
            lastProcessedResults = null
            lastTotalAmount = 0.0
            return Pair(results, amount)
        }
    }
}