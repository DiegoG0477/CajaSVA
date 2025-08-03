# MoneyDetector Integration - Implementation Guide

## Overview

This implementation integrates the `MoneyDetector` class into the `ScanningViewModel` to solve the key issues with coin classification and detection while maintaining compatibility with existing code.

## Problems Solved

### 1. Inconsistent Classification of `moneda_1_anverso`
**Before:** Random values (5, 2, 1 pesos) due to hardcoded size-based logic
**After:** Fixed value mapping using `moneyValueMap` → always returns 1.0 peso

### 2. `moneda_comun_reverso` Identification
**Before:** No proper size-based identification system
**After:** Persistent size reference storage with comparison algorithm

### 3. Resolution Loss in Processing
**Before:** YOLO 640x640 → classifier 224x224 (quality loss)
**After:** YOLO detects → map to original resolution → crop high-res → classify

### 4. Integration with ScanningViewModel
**Before:** Only capture functionality
**After:** Complete detection + classification pipeline with immediate processing

## Key Components

### MoneyDetector.kt
- **Purpose:** Wraps ObjectDetector + MoneyClassifier with value assignment
- **Features:**
  - Consistent value mapping via `moneyValueMap`
  - Coordinate mapping from YOLO 640x640 to original image resolution
  - Size reference storage for `moneda_comun_reverso` identification
  - Persistent storage of coin size references in local file
- **Key Methods:**
  - `detectAndClassifyMoney()`: Main detection pipeline
  - `assignMonetaryValue()`: Consistent value assignment
  - `getSizeReferences()` / `clearSizeReferences()`: Debug functions

### Updated ScanningViewModel.kt
- **New Features:**
  - MoneyDetector injection via Hilt
  - Immediate image processing after capture
  - Processing state management (isProcessingImage)
  - Error handling and display
  - Pre-processed data storage for ResultsViewModel
- **Flow:** Capture → Process → Store Results → Navigate

### Updated UI Components

#### ScanningContent.kt
- Processing indicators during image analysis
- Error message display
- Disabled capture button during processing
- Status text updates (Capturing... / Processing...)

#### ResultsViewModel.kt
- Compatible with pre-processed data from ScanningViewModel
- Fallback to original flow for backward compatibility
- Uses `ScanningViewModel.getLastProcessedData()` when available

### Dependency Injection (RepositoryModule.kt)
- Added `provideMoneyDetector()` method
- Maintains existing CashRepository compatibility
- Singleton scope for efficiency

## Data Flow

### New Flow (Primary)
1. User captures image in `ScanningViewModel`
2. `MoneyDetector.detectAndClassifyMoney()` processes immediately
3. Results stored in ViewModel state and companion object
4. Navigate to ResultsScreen with pre-processed data
5. `ResultsViewModel` uses pre-processed data directly

### Fallback Flow (Compatibility)
1. If no pre-processed data available
2. `ResultsViewModel` falls back to original `CashRepository` flow
3. Ensures backward compatibility

## Value Mapping

```kotlin
private val moneyValueMap = mapOf(
    "billete_20" to 20.0,
    "billete_50" to 50.0,
    "billete_100" to 100.0,
    "billete_200" to 200.0,
    "billete_500" to 500.0,
    "billete_1000" to 1000.0,
    "moneda_0_50" to 0.5,
    "moneda_1_anverso" to 1.0,  // Fixed value
    "moneda_2_anverso" to 2.0,
    "moneda_5_anverso" to 5.0,
    "moneda_10" to 10.0
)
```

## Size Reference System

### Storage
- File: `coin_size_references.txt` in app's internal storage
- Format: `value=width` (e.g., `1.0=100.5`)
- Automatic loading on MoneyDetector initialization

### Algorithm
1. Store average width for each known coin value
2. For `moneda_comun_reverso`, find closest size match
3. Update references with weighted average (80% old, 20% new)

### Example
```
1.0=100.5   # 1 peso coin average width
2.0=120.2   # 2 peso coin average width  
5.0=140.8   # 5 peso coin average width
```

## Coordinate Mapping

### Problem
YOLO works on 640x640 images but original images are higher resolution

### Solution
```kotlin
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
```

### Benefits
- Preserves original image quality for classification
- Better crop accuracy
- Improved classification confidence

## Testing

### Unit Tests (MoneyDetectorTest.kt)
- Value mapping consistency
- Coordinate mapping accuracy
- Size comparison logic
- `moneda_1_anverso` fixed value validation

### Manual Testing
- Capture images with various coins
- Verify consistent 1 peso classification
- Check size reference building over time
- Test error handling and recovery

## Debug Functions

### Size Reference Management
```kotlin
// Get current size references
val references = viewModel.getSizeReferences()

// Clear all references (for testing)
viewModel.clearSizeReferences()
```

### Logging
- Comprehensive logging at DEBUG level
- Processing pipeline status
- Value assignment decisions
- Size reference updates

## Backward Compatibility

- Existing `CashRepository` flow preserved
- `ResultsViewModel` handles both new and old flows
- UI components work with both processing methods
- No breaking changes to public APIs

## Performance Considerations

- MoneyDetector is Singleton (single instance)
- Size references cached in memory
- File I/O only on initialization and updates
- Efficient coordinate mapping calculations

## Future Enhancements

1. **Machine Learning Size References:** Train model to predict coin size
2. **Confidence Thresholds:** Adjustable thresholds for size matching
3. **Multi-currency Support:** Extend value mapping for other currencies
4. **Batch Processing:** Process multiple images simultaneously
5. **Cloud Sync:** Sync size references across devices

## Troubleshooting

### Common Issues

1. **Build Errors:** Ensure AGP version compatibility
2. **Size References Not Saving:** Check file permissions
3. **Inconsistent Values:** Verify MoneyDetector injection
4. **Processing Stuck:** Check error logs for pipeline failures

### Debug Steps

1. Check logs for MoneyDetector processing
2. Verify size references file exists
3. Test with known good images
4. Clear size references and rebuild
5. Check coordinate mapping calculations

This implementation provides a robust, scalable solution for accurate money detection while maintaining clean code architecture and backward compatibility.