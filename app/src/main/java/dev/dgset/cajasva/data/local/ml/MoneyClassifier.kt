package dev.dgset.cajasva.data.local.ml

import android.content.Context
import android.graphics.Bitmap
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