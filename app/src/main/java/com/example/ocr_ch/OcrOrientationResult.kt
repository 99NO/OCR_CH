package com.example.ocr_ch

data class OcrOrientationResult(
    val text: String,
    val orientation: DocumentOrientation,
    /** 이미지를 180° 회전 후 재OCR한 경우 true */
    val wasRotated: Boolean = false,
    val validLineCount: Int,
    val uprightCount: Int,
    val upsideDownCount: Int,
    val sidewaysCount: Int,
    val uprightRatio: Float,
    val upsideDownRatio: Float,
    /** 1차 OCR의 라인별 각도 정보 (방향 판별 근거) */
    val lineResults: List<OcrLineResult>
)
