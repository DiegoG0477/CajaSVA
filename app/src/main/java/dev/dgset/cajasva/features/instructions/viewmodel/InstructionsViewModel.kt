package dev.dgset.cajasva.features.instructions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dgset.cajasva.core.navigation.NavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstructionsViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    fun onScanNowClick() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToScanning)
        }
    }
}
