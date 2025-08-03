package dev.dgset.cajasva.data.local.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import dev.dgset.cajasva.domain.model.DetectedObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MoneyDetector integrates YOLO detection with TensorFlow Lite classification
 * to provide complete money detection and value assignment.
 * 
 * Key features:
 * - Consistent value mapping using moneyValueMap
 * - Size reference storage for moneda_comun_reverso
 * - Coordinate mapping from YOLO 640x640 to original image resolution
 * - Persistent storage of coin size references
 */
@Singleton
class MoneyDetector @Inject constructor(
    private val context: Context,
    private val objectDetector: ObjectDetector,
    private val moneyClassifier: MoneyClassifier
) {
    private val TAG = "MoneyDetector"
    
    // Consistent value mapping for detected classes
    private val moneyValueMap = mapOf(
        "billete_20" to 20.0,
        "billete_50" to 50.0,
        "billete_100" to 100.0,
        "billete_200" to 200.0,
        "billete_500" to 500.0,
        "billete_1000" to 1000.0,
        "moneda_0_50" to 0.5,
        "moneda_1_anverso" to 1.0,  // Fixed value for 1 peso
        "moneda_2_anverso" to 2.0,
        "moneda_5_anverso" to 5.0,
        "moneda_10" to 10.0
    )
    
    // Size reference storage for moneda_comun_reverso identification
    private val sizeReferencesFile = File(context.filesDir, "coin_size_references.txt")
    private val sizeReferences = mutableMapOf<Double, Float>() // value -> average width
    
    init {
        loadSizeReferences()
    }
    
    /**
     * Detects and classifies money in the given image.
     * 
     * @param image Original high-resolution image
     * @return List of DetectedObject with assigned values
     */
    suspend fun detectAndClassifyMoney(image: Bitmap): Result<List<DetectedObject>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting money detection and classification...")
            Log.d(TAG, "Original image size: ${image.width}x${image.height}")
            
            // Step 1: Run YOLO detection on 640x640 image
            val detectionResults = objectDetector.detect(image)
            Log.d(TAG, "YOLO detected ${detectionResults.size} objects")
            
            val finalObjects = mutableListOf<DetectedObject>()
            
            // Step 2: Process each detection
            for (detection in detectionResults) {
                Log.d(TAG, "Processing detection: ${detection.label} with confidence ${detection.confidence}")
                
                // Step 3: Map coordinates from YOLO 640x640 to original image resolution
                val mappedBoundingBox = mapCoordinatesToOriginal(detection.boundingBox, image)
                Log.d(TAG, "Mapped coordinates: ${mappedBoundingBox}")
                
                // Step 4: Crop from original high-resolution image
                val croppedBitmap = cropFromOriginalImage(image, mappedBoundingBox)
                if (croppedBitmap == null) {
                    Log.w(TAG, "Failed to crop image, skipping detection")
                    continue
                }
                
                // Step 5: Classify the cropped region
                val (classificationLabel, confidence) = moneyClassifier.classify(croppedBitmap)
                Log.d(TAG, "Classification result: $classificationLabel with confidence $confidence")
                
                // Step 6: Assign monetary value
                val monetaryValue = assignMonetaryValue(classificationLabel, mappedBoundingBox)
                Log.d(TAG, "Assigned value: $$monetaryValue for $classificationLabel")
                
                // Step 7: Update size references if it's a known value
                updateSizeReferences(classificationLabel, monetaryValue, mappedBoundingBox.width())
                
                finalObjects.add(
                    DetectedObject(
                        boundingBox = mappedBoundingBox,
                        label = classificationLabel,
                        confidence = confidence,
                        value = monetaryValue
                    )
                )
            }
            
            Log.i(TAG, "Detection complete. Processed ${finalObjects.size} objects with total value: $${finalObjects.sumOf { it.value }}")
            saveSizeReferences()
            Result.success(finalObjects)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in money detection pipeline", e)
            Result.failure(e)
        }
    }
    
    /**
     * Maps coordinates from YOLO's 640x640 space to original image coordinates
     */
    private fun mapCoordinatesToOriginal(yoloBoundingBox: RectF, originalImage: Bitmap): RectF {
        val scaleX = originalImage.width.toFloat() / ObjectDetector.INPUT_SIZE
        val scaleY = originalImage.height.toFloat() / ObjectDetector.INPUT_SIZE
        
        return RectF(
            yoloBoundingBox.left * scaleX,
            yoloBoundingBox.top * scaleY,
            yoloBoundingBox.right * scaleX,
            yoloBoundingBox.bottom * scaleY
        )
    }
    
    /**
     * Crops region from original high-resolution image
     */
    private fun cropFromOriginalImage(originalImage: Bitmap, boundingBox: RectF): Bitmap? {
        return try {
            // Ensure coordinates are within image bounds
            val left = boundingBox.left.toInt().coerceAtLeast(0)
            val top = boundingBox.top.toInt().coerceAtLeast(0)
            val right = boundingBox.right.toInt().coerceAtMost(originalImage.width)
            val bottom = boundingBox.bottom.toInt().coerceAtMost(originalImage.height)
            
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)
            
            if (width > 0 && height > 0) {
                Bitmap.createBitmap(originalImage, left, top, width, height)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping image: ${e.message}", e)
            null
        }
    }
    
    /**
     * Assigns monetary value based on classification and size comparison
     */
    private fun assignMonetaryValue(label: String, boundingBox: RectF): Double {
        // First check for direct mapping
        moneyValueMap[label]?.let { return it }
        
        // Handle moneda_comun_reverso case with size comparison
        if (label == "moneda_comun_reverso" || label == "moneda_reverso_comun") {
            return identifyBySize(boundingBox.width())
        }
        
        // Handle any unmapped moneda_1_anverso cases
        if (label.contains("moneda_1") || label.contains("1_peso")) {
            return 1.0  // Force consistent value for 1 peso coins
        }
        
        Log.w(TAG, "Unknown label: $label, assigning value 0.0")
        return 0.0
    }
    
    /**
     * Identifies coin value by comparing size with stored references
     */
    private fun identifyBySize(width: Float): Double {
        if (sizeReferences.isEmpty()) {
            Log.w(TAG, "No size references available, defaulting to 1.0 peso")
            return 1.0
        }
        
        // Find closest size reference
        var closestValue = 1.0
        var minDifference = Float.MAX_VALUE
        
        for ((value, referenceWidth) in sizeReferences) {
            val difference = kotlin.math.abs(width - referenceWidth)
            if (difference < minDifference) {
                minDifference = difference
                closestValue = value
            }
        }
        
        Log.d(TAG, "Size-based identification: width=$width -> closest value=$closestValue (diff=$minDifference)")
        return closestValue
    }
    
    /**
     * Updates size references for future comparisons
     */
    private fun updateSizeReferences(label: String, value: Double, width: Float) {
        // Only update for known values (not moneda_comun_reverso)
        if (value in setOf(1.0, 2.0, 5.0, 10.0) && !label.contains("comun") && !label.contains("reverso")) {
            val currentAverage = sizeReferences[value]
            if (currentAverage == null) {
                sizeReferences[value] = width
                Log.d(TAG, "Added new size reference: $value peso = ${width}px")
            } else {
                // Update with weighted average (favor new measurements slightly)
                sizeReferences[value] = (currentAverage * 0.8f + width * 0.2f)
                Log.d(TAG, "Updated size reference: $value peso = ${sizeReferences[value]}px (from ${width}px)")
            }
        }
    }
    
    /**
     * Loads size references from local storage
     */
    private fun loadSizeReferences() {
        try {
            if (sizeReferencesFile.exists()) {
                sizeReferencesFile.readLines().forEach { line ->
                    val parts = line.split("=")
                    if (parts.size == 2) {
                        val value = parts[0].toDoubleOrNull()
                        val width = parts[1].toFloatOrNull()
                        if (value != null && width != null) {
                            sizeReferences[value] = width
                        }
                    }
                }
                Log.d(TAG, "Loaded ${sizeReferences.size} size references")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not load size references: ${e.message}")
        }
    }
    
    /**
     * Saves size references to local storage
     */
    private fun saveSizeReferences() {
        try {
            val content = sizeReferences.map { "${it.key}=${it.value}" }.joinToString("\n")
            sizeReferencesFile.writeText(content)
            Log.d(TAG, "Saved ${sizeReferences.size} size references")
        } catch (e: IOException) {
            Log.w(TAG, "Could not save size references: ${e.message}")
        }
    }
    
    /**
     * Debug function to get current size references
     */
    fun getSizeReferences(): Map<Double, Float> = sizeReferences.toMap()
    
    /**
     * Debug function to clear size references
     */
    fun clearSizeReferences() {
        sizeReferences.clear()
        if (sizeReferencesFile.exists()) {
            sizeReferencesFile.delete()
        }
        Log.d(TAG, "Cleared all size references")
    }
    
    companion object {
        const val INPUT_SIZE = ObjectDetector.INPUT_SIZE // YOLO input size
    }
}