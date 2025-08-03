package dev.dgset.cajasva.core.navigation

import android.net.Uri

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
    object NavigateToScanning : NavigationEvent()
    data class NavigateToResults(val imageUri: Uri) : NavigationEvent()
}
