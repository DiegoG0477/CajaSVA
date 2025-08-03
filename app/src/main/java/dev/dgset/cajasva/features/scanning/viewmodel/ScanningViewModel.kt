package dev.dgset.cajasva.features.scanning.viewmodel

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dgset.cajasva.core.navigation.NavigationEvent
import dev.dgset.cajasva.core.ui.utils.ComposeFileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanningUiState(
    val hasCameraPermission: Boolean = false,
    val isCameraReady: Boolean = false,
    val isTakingPicture: Boolean = false
)

@HiltViewModel
class ScanningViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanningUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var imageCapture: ImageCapture? = null

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = isGranted) }
    }

    fun onCameraReady(imageCapture: ImageCapture) {
        this.imageCapture = imageCapture
        _uiState.update { it.copy(isCameraReady = true) }
    }

    fun onTakePicture() {
        if (!_uiState.value.isCameraReady || imageCapture == null) return
        _uiState.update { it.copy(isTakingPicture = true) }

        val imageFileResult = ComposeFileProvider.createImageFile(context)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFileResult.file).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = imageFileResult.uri
                    viewModelScope.launch {
                        _navigationEvent.emit(NavigationEvent.NavigateToResults(savedUri))
                    }
                    _uiState.update { it.copy(isTakingPicture = false) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanningViewModel", "Image capture error: ${exception.message}", exception)
                    _uiState.update { it.copy(isTakingPicture = false) }
                }
            }
        )
    }

    fun onBackClick() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateBack)
        }
    }
}