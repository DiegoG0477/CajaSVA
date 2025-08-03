package dev.dgset.cajasva.domain.model

import android.graphics.RectF

/**
 * Representa un único objeto (moneda o billete) detectado y clasificado en la imagen.
 * Esta es la clase de datos principal que se usará en toda la aplicación.
 *
 * @param boundingBox Las coordenadas [left, top, right, bottom] del objeto en la imagen original.
 *                    Es crucial para la lógica de diferenciación por tamaño.
 * @param label El nombre de la clase devuelto por el modelo clasificador (ej. "moneda_5_anverso").
 * @param confidence La puntuación de confianza del clasificador (de 0.0 a 1.0).
 * @param value El valor monetario deducido en Pesos (ej. 5.0, 20.0, 0.5).
 *              Este valor será calculado en el ViewModel.
 */
data class DetectedObject(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float,
    var value: Double = 0.0 // Se inicializa en 0 y se calcula en el ViewModel
) {
    /**
     * Helper para obtener el ancho de la caja delimitadora en píxeles.
     */
    fun getPixelWidth(): Float = boundingBox.width()
}
