package com.scanpro.scanner.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

object OcrHelper {

    fun extractTextFromImage(
        context: Context,
        imageFile: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extracted = visionText.text.trim()
                    if (extracted.isEmpty()) {
                        onError("No readable text found on this page.")
                    } else {
                        onSuccess(extracted)
                    }
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Failed to recognize text.")
                    recognizer.close()
                }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Unable to read image for OCR.")
        }
    }
}
