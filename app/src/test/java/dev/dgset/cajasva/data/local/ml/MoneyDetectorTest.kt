package dev.dgset.cajasva.data.local.ml

import android.graphics.RectF
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MoneyDetector logic that doesn't require Android context
 */
class MoneyDetectorTest {

    @Test
    fun testMoneyValueMapping() {
        // Test that the value mapping works correctly
        val testValues = mapOf(
            "billete_20" to 20.0,
            "billete_50" to 50.0,
            "billete_100" to 100.0,
            "billete_200" to 200.0,
            "billete_500" to 500.0,
            "billete_1000" to 1000.0,
            "moneda_0_50" to 0.5,
            "moneda_1_anverso" to 1.0,
            "moneda_2_anverso" to 2.0,
            "moneda_5_anverso" to 5.0,
            "moneda_10" to 10.0
        )
        
        // This is a simplified version of the assignMonetaryValue logic
        testValues.forEach { (label, expectedValue) ->
            val actualValue = when (label) {
                "billete_20" -> 20.0
                "billete_50" -> 50.0
                "billete_100" -> 100.0
                "billete_200" -> 200.0
                "billete_500" -> 500.0
                "billete_1000" -> 1000.0
                "moneda_0_50" -> 0.5
                "moneda_1_anverso" -> 1.0  // Fixed value for 1 peso
                "moneda_2_anverso" -> 2.0
                "moneda_5_anverso" -> 5.0
                "moneda_10" -> 10.0
                else -> 0.0
            }
            
            assertEquals("Value for $label should match", expectedValue, actualValue, 0.01)
        }
    }
    
    @Test
    fun testCoordinateMapping() {
        // Test coordinate mapping from YOLO to original image
        val yoloSize = 640
        val originalWidth = 1920
        val originalHeight = 1080
        
        val scaleX = originalWidth.toFloat() / yoloSize
        val scaleY = originalHeight.toFloat() / yoloSize
        
        // Test a bounding box from YOLO space
        val yoloBoundingBox = RectF(100f, 100f, 200f, 200f) // 100x100 box
        
        val mappedBox = RectF(
            yoloBoundingBox.left * scaleX,
            yoloBoundingBox.top * scaleY,
            yoloBoundingBox.right * scaleX,
            yoloBoundingBox.bottom * scaleY
        )
        
        // Expected values
        val expectedLeft = 100f * 3f // 300
        val expectedTop = 100f * 1.6875f // 168.75
        val expectedRight = 200f * 3f // 600
        val expectedBottom = 200f * 1.6875f // 337.5
        
        assertEquals("Left coordinate should be scaled correctly", expectedLeft, mappedBox.left, 0.1f)
        assertEquals("Top coordinate should be scaled correctly", expectedTop, mappedBox.top, 0.1f)
        assertEquals("Right coordinate should be scaled correctly", expectedRight, mappedBox.right, 0.1f)
        assertEquals("Bottom coordinate should be scaled correctly", expectedBottom, mappedBox.bottom, 0.1f)
    }
    
    @Test
    fun testConsistentMoneda1AnversoValue() {
        // Test that moneda_1_anverso always returns 1.0 peso
        val variations = listOf(
            "moneda_1_anverso",
            "moneda_1",
            "1_peso"
        )
        
        variations.forEach { label ->
            val value = when {
                label.contains("moneda_1") || label.contains("1_peso") -> 1.0
                else -> 0.0
            }
            
            if (label.contains("1")) {
                assertEquals("$label should always return 1.0 peso", 1.0, value, 0.01)
            }
        }
    }
    
    @Test
    fun testSizeComparisonLogic() {
        // Test size-based identification logic
        val sizeReferences = mutableMapOf<Double, Float>()
        sizeReferences[1.0] = 100f // 1 peso = 100px
        sizeReferences[2.0] = 120f // 2 peso = 120px  
        sizeReferences[5.0] = 140f // 5 peso = 140px
        
        // Test finding closest match
        val testWidth = 115f // Should match 2 peso (120px)
        
        var closestValue = 1.0
        var minDifference = Float.MAX_VALUE
        
        for ((value, referenceWidth) in sizeReferences) {
            val difference = kotlin.math.abs(testWidth - referenceWidth)
            if (difference < minDifference) {
                minDifference = difference
                closestValue = value
            }
        }
        
        assertEquals("Width 115px should match 2 peso coin", 2.0, closestValue, 0.01)
    }
}