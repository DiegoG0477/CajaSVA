package dev.dgset.cajasva.data.local.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class MoneyClassifier(
    private val context: Context,
    private val modelPath: String = "classifier_model_float32.tflite",
    private val labelPath: String = "classifier_labels.txt"
) {
    private val TAG = "MoneyClassifier"
    private var interpreter: Interpreter? = null
    private lateinit var labels: List<String>

    private val imageSizeX: Int = 224
    private val imageSizeY: Int = 224

    init {
        try {
            setupClassifier()
            loadLabels()
            Log.i(TAG, "Clasificador TensorFlow Lite inicializado. ${labels.size} etiquetas cargadas.")
        } catch (e: IOException) {
            Log.e(TAG, "Error al inicializar el clasificador TensorFlow Lite.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error general al inicializar el clasificador.", e)
        }
    }

    private fun setupClassifier() {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }

        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadLabels() {
        labels = context.assets.open(labelPath).bufferedReader().use { reader ->
            reader.readLines()
        }
    }

    fun classify(bitmap: Bitmap): Pair<String, Float> {
        if (interpreter == null) {
            Log.e(TAG, "Clasificador no inicializado.")
            return Pair("Error", 0.0f)
        }

        try {
            val inputBuffer = preprocessImage(bitmap)

            // --- CORRECCIÓN CLAVE: Shape Mismatch ---
            // El modelo devuelve [1, 12], así que necesitamos un buffer que coincida.
            val outputBuffer = Array(1) { FloatArray(labels.size) }

            interpreter!!.run(inputBuffer, outputBuffer)

            // Ahora accedemos al primer (y único) array de resultados.
            val probabilities = outputBuffer[0]

            var maxIndex = 0
            for (i in probabilities.indices) {
                if (probabilities[i] > probabilities[maxIndex]) {
                    maxIndex = i
                }
            }

            val className = labels[maxIndex]
            val maxProb = probabilities[maxIndex]

            Log.d(TAG, "Clasificación: $className ($maxProb)")
            return Pair(className, maxProb)

        } catch (e: Exception) {
            Log.e(TAG, "Error durante la clasificación: ${e.message}", e)
            return Pair("Error", 0.0f)
        }
    }

    /**
     * Clasificación con sistema de reintento hasta 5 veces para baja confianza
     */
    fun classifyWithRetry(bitmap: Bitmap, minConfidence: Float = 0.85f, maxRetries: Int = 5): Pair<String, Float> {
        var currentBitmap = bitmap
        var bestResult = classify(currentBitmap)

        if (bestResult.second >= minConfidence) {
            Log.d(TAG, "Clasificación exitosa en primer intento: ${bestResult.first} (${bestResult.second})")
            return bestResult
        }

        Log.d(TAG, "Baja confianza (${bestResult.second}), iniciando reintentos...")

        for (attempt in 1..maxRetries) {
            Log.d(TAG, "Reintento $attempt/$maxRetries")

            // Aplicar mejoras a la imagen con variaciones por intento
            val enhancedBitmap = enhanceImage(currentBitmap, attempt)
            val retryResult = classify(enhancedBitmap)

            Log.d(TAG, "Resultado intento $attempt: ${retryResult.first} (${retryResult.second})")

            // Mantener el mejor resultado hasta ahora
            if (retryResult.second > bestResult.second) {
                bestResult = retryResult
                Log.d(TAG, "Nuevo mejor resultado: ${bestResult.first} (${bestResult.second})")
            }

            // Si alcanzamos la confianza mínima, retornar inmediatamente
            if (retryResult.second >= minConfidence) {
                Log.d(TAG, "Confianza alcanzada en intento $attempt: ${retryResult.first} (${retryResult.second})")
                return retryResult
            }

            // Usar la imagen mejorada para el siguiente intento
            currentBitmap = enhancedBitmap
        }

        Log.w(TAG, "Todos los reintentos completados. Mejor resultado: ${bestResult.first} (${bestResult.second})")

        // Código comentado para implementación futura
        /*
        // Si después de todos los reintentos no se alcanza la confianza, marcar como incierto
        return if (bestResult.second < minConfidence) {
            Log.w(TAG, "Clasificación incierta después de $maxRetries reintentos")
            Pair("incierto_${bestResult.first}", bestResult.second)
        } else {
            bestResult
        }
        */

        // Por ahora, retornar el mejor resultado sin modificar la etiqueta
        return bestResult
    }

    /**
     * Mejora la imagen aplicando ajustes de contraste y brillo
     * Modificado para aplicar diferentes mejoras según el intento
     */
    private fun enhanceImage(bitmap: Bitmap, attempt: Int = 1): Bitmap {
        try {
            // Variar los parámetros de mejora según el intento
            val (contrastFactor, brightnessFactor) = when (attempt) {
                1 -> Pair(1.2f, 10f)    // Mejora moderada
                2 -> Pair(1.3f, 15f)    // Más contraste y brillo
                3 -> Pair(1.1f, 5f)     // Menos agresivo
                4 -> Pair(1.4f, 20f)    // Muy agresivo
                5 -> Pair(1.0f, 0f)     // Sin mejoras (imagen original)
                else -> Pair(1.2f, 10f) // Por defecto
            }

            Log.d(TAG, "Aplicando mejoras intento $attempt: contraste=$contrastFactor, brillo=$brightnessFactor")

            val matrix = ColorMatrix().apply {
                set(floatArrayOf(
                    contrastFactor, 0f, 0f, 0f, brightnessFactor,    // R
                    0f, contrastFactor, 0f, 0f, brightnessFactor,    // G
                    0f, 0f, contrastFactor, 0f, brightnessFactor,    // B
                    0f, 0f, 0f, 1f, 0f                               // Alpha sin cambios
                ))
            }

            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }

            // CORRECCIÓN: Usar ?: para manejar bitmap.config nullable
            val bitmapConfig = bitmap.config ?: Bitmap.Config.ARGB_8888
            val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmapConfig)
            val canvas = Canvas(enhancedBitmap)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            Log.d(TAG, "Imagen mejorada aplicada para intento $attempt")
            return enhancedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error al mejorar imagen en intento $attempt, usando original: ${e.message}")
            return bitmap
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Redimensionar la imagen
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true)

        // Crear buffer para los datos de entrada
        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSizeX * imageSizeY * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        // Extraer píxeles
        val intValues = IntArray(imageSizeX * imageSizeY)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        // Convertir píxeles a formato float normalizado
        var pixel = 0
        for (row in 0 until imageSizeY) {
            for (col in 0 until imageSizeX) {
                val value = intValues[pixel++]
                // Normalización [0, 255] -> [0, 1]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }

        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}