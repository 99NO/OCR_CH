package com.example.ocr_ch

data class OcrLineResult(
    val text: String,
    val angle: Float,
    val confidence: Float
)
