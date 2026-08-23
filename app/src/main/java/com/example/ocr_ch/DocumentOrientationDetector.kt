package com.example.ocr_ch

import kotlin.math.abs

/**
 * ML Kit Text.Line.angle 값을 voting 방식으로 분석해 문서 방향을 판별한다.
 *
 * 중요: angle 값은 -180°~+180° 범위이므로 179°와 -179°는 같은 180° 방향이다.
 * 따라서 abs(angle)로 개별 분류 후 voting 해야 하며,
 * 산술 평균(179 + (-179) = 0)을 쓰면 안 된다.
 */
object DocumentOrientationDetector {

    const val UPRIGHT_THRESHOLD = 45f
    const val UPSIDE_DOWN_THRESHOLD = 135f
    const val MIN_VALID_LINES = 3

    private enum class LineOrientation { UPRIGHT, UPSIDE_DOWN, SIDEWAYS }

    private fun classifyLine(angle: Float): LineOrientation {
        val absAngle = abs(angle)
        return when {
            absAngle <= UPRIGHT_THRESHOLD -> LineOrientation.UPRIGHT
            absAngle >= UPSIDE_DOWN_THRESHOLD -> LineOrientation.UPSIDE_DOWN
            else -> LineOrientation.SIDEWAYS
        }
    }

    fun detect(fullText: String, lineResults: List<OcrLineResult>): OcrOrientationResult {
        val validLines = lineResults.filter { it.text.isNotBlank() }
        val totalValidLines = validLines.size

        if (totalValidLines < MIN_VALID_LINES) {
            return OcrOrientationResult(
                text = fullText,
                orientation = DocumentOrientation.UNKNOWN,
                validLineCount = totalValidLines,
                uprightCount = 0,
                upsideDownCount = 0,
                sidewaysCount = 0,
                uprightRatio = 0f,
                upsideDownRatio = 0f,
                lineResults = validLines
            )
        }

        var uprightCount = 0
        var upsideDownCount = 0
        var sidewaysCount = 0

        for (line in validLines) {
            when (classifyLine(line.angle)) {
                LineOrientation.UPRIGHT -> uprightCount++
                LineOrientation.UPSIDE_DOWN -> upsideDownCount++
                LineOrientation.SIDEWAYS -> sidewaysCount++
            }
        }

        val uprightRatio = uprightCount.toFloat() / totalValidLines
        val upsideDownRatio = upsideDownCount.toFloat() / totalValidLines

        val orientation = when {
            uprightCount > upsideDownCount && uprightCount >= sidewaysCount ->
                DocumentOrientation.UPRIGHT
            upsideDownCount > uprightCount && upsideDownCount >= sidewaysCount ->
                DocumentOrientation.UPSIDE_DOWN
            sidewaysCount > uprightCount && sidewaysCount > upsideDownCount ->
                DocumentOrientation.SIDEWAYS
            else ->
                DocumentOrientation.UNKNOWN
        }

        return OcrOrientationResult(
            text = fullText,
            orientation = orientation,
            validLineCount = totalValidLines,
            uprightCount = uprightCount,
            upsideDownCount = upsideDownCount,
            sidewaysCount = sidewaysCount,
            uprightRatio = uprightRatio,
            upsideDownRatio = upsideDownRatio,
            lineResults = validLines
        )
    }

    /** 테스트용: angle 목록만 넘기면 방향 판별 결과 반환 */
    fun detectFromAngles(angles: List<Float>): DocumentOrientation {
        val lines = angles.mapIndexed { i, angle ->
            OcrLineResult(text = "line$i", angle = angle, confidence = 1f)
        }
        return detect("", lines).orientation
    }
}
