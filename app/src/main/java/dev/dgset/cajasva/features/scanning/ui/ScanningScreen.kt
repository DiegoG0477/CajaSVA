package dev.dgset.cajasva.features.scanning.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dev.dgset.cajasva.core.navigation.NavigationEvent
import dev.dgset.cajasva.core.ui.utils.PermissionUtils
import dev.dgset.cajasva.features.scanning.viewmodel.ScanningViewModel

@Composable
fun ScanningScreen(
    onNavigateToResults: (Uri) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ScanningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.onPermissionResult(isGranted)
        }
    )

    LaunchedEffect(key1 = Unit) {
        if (!PermissionUtils.hasCameraPermission(context)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.onPermissionResult(true)
        }
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToResults -> onNavigateToResults(event.imageUri)
                is NavigationEvent.NavigateBack -> onNavigateBack()
                else -> {}
            }
        }
    }

    ScanningContent(
        uiState = uiState,
        onTakePicture = viewModel::onTakePicture,
        onCameraReady = viewModel::onCameraReady,
        onBackClick = viewModel::onBackClick
    )
}
