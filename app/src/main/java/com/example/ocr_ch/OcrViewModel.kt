package com.example.ocr_ch

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrManager = ChineseOcrManager()

    private val _ocrResult = MutableStateFlow<OcrOrientationResult?>(null)
    val ocrResult: StateFlow<OcrOrientationResult?> = _ocrResult

    private val _displayBitmap = MutableStateFlow<Bitmap?>(null)
    val displayBitmap: StateFlow<Bitmap?> = _displayBitmap

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun processUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _errorMessage.value = null
            _ocrResult.value = null
            _displayBitmap.value = null

            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap == null) {
                _errorMessage.value = "이미지를 불러올 수 없습니다."
                _isProcessing.value = false
                return@launch
            }

            processImageInternal(bitmap)
        }
    }

    private suspend fun processImageInternal(bitmap: Bitmap) {
        try {
            _displayBitmap.value = bitmap

            // 1차 OCR: 원본 이미지
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val rawResult = ocrManager.processImage(inputImage)

            logRawResult(rawResult, pass = 1)

            // 1차 OCR 결과로 방향 판별
            val firstResult = DocumentOrientationDetector.detect(
                fullText = rawResult.fullText,
                lineResults = rawResult.lineResults
            )

            if (firstResult.orientation == DocumentOrientation.UPSIDE_DOWN) {
                Log.i(TAG, ">>> UPSIDE_DOWN 감지: 이미지 180° 회전 후 2차 OCR 수행")

                // 거꾸로 된 경우: 180° 회전 후 재OCR로 올바른 reading order 확보
                val rotatedBitmap = rotateBitmap(bitmap, 180f)
                _displayBitmap.value = rotatedBitmap

                val inputImage2 = InputImage.fromBitmap(rotatedBitmap, 0)
                val rawResult2 = ocrManager.processImage(inputImage2)

                logRawResult(rawResult2, pass = 2)

                // text는 2차(올바른 방향) OCR 결과, 각도 디버그 정보는 1차 기준 유지
                val finalResult = firstResult.copy(
                    text = rawResult2.fullText,
                    wasRotated = true
                )
                _ocrResult.value = finalResult
                logOrientationResult(finalResult)
            } else {
                _ocrResult.value = firstResult
                logOrientationResult(firstResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR 처리 실패", e)
            _errorMessage.value = "OCR 처리 실패: ${e.message}"
        } finally {
            _isProcessing.value = false
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap 디코딩 실패", e)
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun logRawResult(result: ChineseOcrManager.OcrRawResult, pass: Int) {
        Log.i(TAG, "===== ML KIT OCR RESULT (${pass}차) =====")
        result.lineResults.forEachIndexed { i, line ->
            Log.i(TAG, "Line[$i]")
            Log.i(TAG, "  text       = ${line.text}")
            Log.i(TAG, "  angle      = ${line.angle}")
            Log.i(TAG, "  confidence = ${line.confidence}")
        }
        Log.i(TAG, "=========================================")
    }

    private fun logOrientationResult(result: OcrOrientationResult) {
        Log.i(TAG, "===== ORIENTATION RESULT =====")
        Log.i(TAG, "validLines     = ${result.validLineCount}")
        Log.i(TAG, "uprightCount   = ${result.uprightCount}")
        Log.i(TAG, "upsideDownCount= ${result.upsideDownCount}")
        Log.i(TAG, "sidewaysCount  = ${result.sidewaysCount}")
        Log.i(TAG, "uprightRatio   = ${result.uprightRatio}")
        Log.i(TAG, "upsideDownRatio= ${result.upsideDownRatio}")
        Log.i(TAG, "orientation    = ${result.orientation}")
        Log.i(TAG, "wasRotated     = ${result.wasRotated}")
        Log.i(TAG, "==============================")
    }

    override fun onCleared() {
        super.onCleared()
        ocrManager.close()
    }

    companion object {
        private const val TAG = "OcrViewModel"
    }
}
