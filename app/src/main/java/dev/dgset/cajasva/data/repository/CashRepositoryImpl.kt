package dev.dgset.cajasva.data.repository

import android.graphics.Bitmap
import android.util.Log
import dev.dgset.cajasva.data.local.ml.MoneyClassifier
import dev.dgset.cajasva.data.local.ml.ObjectDetector
import dev.dgset.cajasva.domain.model.DetectedObject
import dev.dgset.cajasva.domain.repository.CashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CashRepositoryImpl @Inject constructor(
    private val detector: ObjectDetector,
    private val classifier: MoneyClassifier
) : CashRepository {
    private val TAG = "CashRepositoryImpl"

    override suspend fun detectAndClassify(image: Bitmap): Result<List<DetectedObject>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando pipeline de detección y clasificación...")

            val detectionResults = detector.detect(image)
            Log.d(TAG, "Detector encontró ${detectionResults.size} objetos.")

            val finalObjects = mutableListOf<DetectedObject>()

            for (detection in detectionResults) {
                val box = detection.boundingBox
                val croppedBitmap = Bitmap.createBitmap(
                    image,
                    box.left.toInt(),
                    box.top.toInt(),
                    box.width().toInt(),
                    box.height().toInt()
                )

                val (label, confidence) = classifier.classifyWithRetry(croppedBitmap, minConfidence = 0.85f)

                finalObjects.add(
                    DetectedObject(
                        boundingBox = box,
                        label = label,
                        confidence = confidence,
                        value = 0.0
                    )
                )
            }
            Log.i(TAG, "Pipeline completado. Se procesaron ${finalObjects.size} objetos.")
            Result.success(finalObjects)
        } catch (e: Exception) {
            Log.e(TAG, "Error en el pipeline de detectAndClassify", e)
            Result.failure(e)
        }
    }
}
