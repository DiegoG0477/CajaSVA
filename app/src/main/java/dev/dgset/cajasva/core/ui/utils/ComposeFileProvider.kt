package dev.dgset.cajasva.core.ui.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ImageFileResult(
    val file: File,
    val uri: Uri
)

object ComposeFileProvider {
    fun createImageFile(context: Context): ImageFileResult {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(context.cacheDir, "images")

        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        return ImageFileResult(imageFile, imageUri)
    }
}
