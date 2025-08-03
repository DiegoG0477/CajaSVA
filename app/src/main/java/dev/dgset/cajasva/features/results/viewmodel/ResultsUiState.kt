package dev.dgset.cajasva.features.results.viewmodel

import dev.dgset.cajasva.domain.model.DetectedObject

data class ResultsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val breakdown: List<DetectedObject> = emptyList(),
    val totalAmount: Double = 0.0,
    val originalImageWidth: Int = 1,
    val originalImageHeight: Int = 1,
    val imageAspectRatio: Float = 1f
)
