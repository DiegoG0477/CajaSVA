package dev.dgset.cajasva.domain.repository

import android.graphics.Bitmap
import dev.dgset.cajasva.domain.model.DetectedObject

/**
 * Define el contrato para las operaciones de datos relacionadas con el escaneo de dinero.
 * La capa de dominio depende de esta abstracción, no de la implementación concreta.
 */
interface CashRepository {

    /**
     * Analiza una imagen para detectar y clasificar todas las piezas de dinero presentes.
     *
     * @param image El Bitmap de la imagen a analizar.
     * @return Un [Result] que contiene:
     *         - En caso de éxito: Una [List] de [DetectedObject].
     *         - En caso de fallo: Una [Exception] que describe el error.
     */
    suspend fun detectAndClassify(image: Bitmap): Result<List<DetectedObject>>

}
