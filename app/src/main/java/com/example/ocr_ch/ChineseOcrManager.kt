package com.example.ocr_ch

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChineseOcrManager : AutoCloseable {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    data class OcrRawResult(
        val fullText: String,
        val lineResults: List<OcrLineResult>
    )

    suspend fun processImage(inputImage: InputImage): OcrRawResult =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val lines = mutableListOf<OcrLineResult>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            lines.add(
                                OcrLineResult(
                                    text = line.text,
                                    angle = line.angle,
                                    confidence = line.confidence
                                )
                            )
                        }
                    }
                    continuation.resume(OcrRawResult(visionText.text, lines))
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    override fun close() {
        recognizer.close()
    }
}
