package com.example.ocr_ch

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentOrientationDetectorTest {

    @Test
    fun `정상 방향 각도들은 UPRIGHT 반환`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(1f, -2f, 3f, 0f, -1.5f)
        )
        assertEquals(DocumentOrientation.UPRIGHT, result)
    }

    @Test
    fun `거꾸로 된 각도들은 UPSIDE_DOWN 반환`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(178f, -179f, 175f, -176f, 180f)
        )
        assertEquals(DocumentOrientation.UPSIDE_DOWN, result)
    }

    /**
     * 핵심 테스트: 179 + (-179) = 0이 되어 UPRIGHT로 잘못 판별되지 않는지 확인.
     * 산술 평균을 쓰면 0°가 되지만, voting 방식이면 모두 UPSIDE_DOWN으로 분류된다.
     */
    @Test
    fun `179도와 -179도가 섞여도 UPSIDE_DOWN 반환 (산술평균 오류 방지)`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(179f, -179f, 178f, -178f, 176f)
        )
        assertEquals(DocumentOrientation.UPSIDE_DOWN, result)
    }

    @Test
    fun `라인 개수 부족(MIN 미만)이면 UNKNOWN 반환`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(1f, 178f) // 2개 < MIN_VALID_LINES(3)
        )
        assertEquals(DocumentOrientation.UNKNOWN, result)
    }

    @Test
    fun `MIN_VALID_LINES 경계 정확히 3개면 판별함`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(179f, -179f, 178f)
        )
        assertEquals(DocumentOrientation.UPSIDE_DOWN, result)
    }

    @Test
    fun `90도 부근 각도는 SIDEWAYS 반환`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(89f, 92f, 90f, 91f, 88f)
        )
        assertEquals(DocumentOrientation.SIDEWAYS, result)
    }

    @Test
    fun `작은 각도 편차가 있어도 UPRIGHT 판별`() {
        val result = DocumentOrientationDetector.detectFromAngles(
            listOf(0.5f, -1.2f, 2.1f, 3.5f, -0.8f)
        )
        assertEquals(DocumentOrientation.UPRIGHT, result)
    }

    @Test
    fun `빈 각도 목록이면 UNKNOWN 반환`() {
        val result = DocumentOrientationDetector.detectFromAngles(emptyList())
        assertEquals(DocumentOrientation.UNKNOWN, result)
    }

    @Test
    fun `UPRIGHT가 다수면 UPRIGHT 반환`() {
        // uprightCount=7, upsideDownCount=1, sidewaysCount=1
        val angles = listOf(1f, -2f, 3f, 0f, -1f, 2f, 1.5f, 179f, 90f)
        assertEquals(DocumentOrientation.UPRIGHT, DocumentOrientationDetector.detectFromAngles(angles))
    }

    @Test
    fun `UPSIDE_DOWN이 다수면 UPSIDE_DOWN 반환`() {
        // uprightCount=1, upsideDownCount=7
        val angles = listOf(1f, 179f, -178f, 176f, -175f, 177f, -179f, 178f)
        assertEquals(DocumentOrientation.UPSIDE_DOWN, DocumentOrientationDetector.detectFromAngles(angles))
    }
}
