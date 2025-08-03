package dev.dgset.cajasva.data.local.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.PriorityQueue

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float
)

class ObjectDetector(
    private val context: Context,
    private val modelPath: String = "detector.tflite",
    private var confidenceThreshold: Float = 0.5f,
    private var iouThreshold: Float = 0.45f,
    private var numThreads: Int = 4
) {
    private val TAG = "ObjectDetector"
    private var interpreter: Interpreter? = null
    private lateinit var labels: List<String>
    private val labelsPath = "detector_labels.txt"

    companion object {
        const val INPUT_SIZE = 640
    }

    init {
        setupDetector()
    }

    private fun setupDetector() {
        try {
            val assetFileDescriptor = context.assets.openFd(modelPath)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply { setNumThreads(numThreads) }
            interpreter = Interpreter(modelBuffer, options)
            labels = context.assets.open(labelsPath).bufferedReader().readLines()
            Log.i(TAG, "Detector TFLite inicializado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el detector: ${e.message}", e)
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) return emptyList()

        val inputBuffer = preprocessBitmap(bitmap)
        val outputShape = interpreter!!.getOutputTensor(0).shape() // ej. [1, 6, 8400]
        val outputBuffer = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        interpreter?.run(inputBuffer, outputBuffer)

        val rawDetections = processYoloOutput(outputBuffer[0], bitmap.width, bitmap.height)
        Log.d(TAG, "Detecciones crudas (antes de NMS): ${rawDetections.size}")

        return nonMaxSuppression(rawDetections)
    }

    private fun processYoloOutput(output: Array<FloatArray>, bitmapWidth: Int, bitmapHeight: Int): List<DetectionResult> {
        val numPredictions = output[0].size // ej. 8400
        val numClasses = labels.size

        val predictions = Array(numPredictions) { FloatArray(output.size) }
        for (i in output.indices) {
            for (j in 0 until numPredictions) {
                predictions[j][i] = output[i][j]
            }
        }

        val results = mutableListOf<DetectionResult>()
        for (prediction in predictions) {
            val scores = prediction.sliceArray(4 until 4 + numClasses)
            val classId = scores.indices.maxByOrNull { scores[it] } ?: -1
            val confidence = if (classId != -1) scores[classId] else 0f

            if (confidence >= confidenceThreshold) {
                val xCenter = prediction[0] * bitmapWidth
                val yCenter = prediction[1] * bitmapHeight
                val width = prediction[2] * bitmapWidth
                val height = prediction[3] * bitmapHeight

                results.add(
                    DetectionResult(
                        boundingBox = RectF(xCenter - width / 2, yCenter - height / 2, xCenter + width / 2, yCenter + height / 2),
                        label = labels[classId],
                        confidence = confidence
                    )
                )
            }
        }
        return results
    }

    private fun nonMaxSuppression(detections: List<DetectionResult>): List<DetectionResult> {
        // CORRECCIÓN: Evitar crash si no hay detecciones.
        if (detections.isEmpty()) {
            return emptyList()
        }

        val nmsList = mutableListOf<DetectionResult>()

        // Usar una cola de prioridad para ordenar las detecciones por confianza
        // coerceAtLeast(1) asegura que la capacidad nunca sea menor que 1.
        val pq = PriorityQueue<DetectionResult>(detections.size.coerceAtLeast(1), compareByDescending { it.confidence })
        pq.addAll(detections)

        while (pq.isNotEmpty()) {
            val maxConfidenceDetection = pq.poll()
            if (maxConfidenceDetection != null) {
                var shouldAdd = true
                for (selected in nmsList) {
                    if (calculateIoU(maxConfidenceDetection.boundingBox, selected.boundingBox) > iouThreshold) {
                        shouldAdd = false
                        break
                    }
                }
                if (shouldAdd) {
                    nmsList.add(maxConfidenceDetection)
                }
            }
        }
        return nmsList
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        if (areaA <= 0 || areaB <= 0) return 0.0f
        val intersect = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left)) * maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val union = areaA + areaB - intersect
        return if (union > 0) intersect / union else 0.0f
    }
}