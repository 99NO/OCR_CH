package com.example.ocr_ch

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrManager = ChineseOcrManager()

    // TTS: 초기화가 비동기이므로 콜백을 먼저 등록한 뒤 onReady에서 상태 업데이트
    private val ttsManager = TtsManager(application).also { mgr ->
        mgr.onReady = { supported ->
            _ttsReady.value = supported
            if (!supported) _ttsError.value = "기기에 중국어 TTS가 설치되어 있지 않습니다.\n설정 > 언어 > 텍스트 음성 변환에서 중국어 음성을 추가하세요."
        }
        mgr.onStart = { _isSpeaking.value = true }
        mgr.onDone  = { _isSpeaking.value = false }
        mgr.onError = { _isSpeaking.value = false }
    }

    private val _ttsReady   = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _ttsError   = MutableStateFlow<String?>(null)
    val ttsError: StateFlow<String?> = _ttsError

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.KOREAN)
            .build()
    )

    private val _ocrResult = MutableStateFlow<OcrOrientationResult?>(null)
    val ocrResult: StateFlow<OcrOrientationResult?> = _ocrResult

    private val _displayBitmap = MutableStateFlow<Bitmap?>(null)
    val displayBitmap: StateFlow<Bitmap?> = _displayBitmap

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating

    private val _translationError = MutableStateFlow<String?>(null)
    val translationError: StateFlow<String?> = _translationError

    fun processUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _errorMessage.value = null
            _ocrResult.value = null
            _displayBitmap.value = null
            _translatedText.value = null
            _translationError.value = null
            ttsManager.stop()
            _isSpeaking.value = false
            _ttsError.value = null

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

            // 1차 OCR
            val rawResult = ocrManager.processImage(InputImage.fromBitmap(bitmap, 0))
            logRawResult(rawResult, pass = 1)

            val firstResult = DocumentOrientationDetector.detect(
                fullText = rawResult.fullText,
                lineResults = rawResult.lineResults
            )

            val finalResult: OcrOrientationResult
            if (firstResult.orientation == DocumentOrientation.UPSIDE_DOWN) {
                Log.i(TAG, ">>> UPSIDE_DOWN 감지: 180° 회전 후 2차 OCR")
                val rotated = rotateBitmap(bitmap, 180f)
                _displayBitmap.value = rotated

                val rawResult2 = ocrManager.processImage(InputImage.fromBitmap(rotated, 0))
                logRawResult(rawResult2, pass = 2)

                finalResult = firstResult.copy(text = rawResult2.fullText, wasRotated = true)
            } else {
                finalResult = firstResult
            }

            _ocrResult.value = finalResult
            logOrientationResult(finalResult)
        } catch (e: Exception) {
            Log.e(TAG, "OCR 처리 실패", e)
            _errorMessage.value = "OCR 처리 실패: ${e.message}"
            return
        } finally {
            _isProcessing.value = false
        }

        // OCR 완료(_isProcessing = false) 후 번역 시작
        val textToTranslate = _ocrResult.value?.text
        if (!textToTranslate.isNullOrBlank()) {
            translateToKorean(textToTranslate)
        }
    }

    private suspend fun translateToKorean(text: String) {
        _isTranslating.value = true
        _translationError.value = null
        try {
            // 첫 실행 시 모델 다운로드 (~30 MB). 이후 오프라인 사용 가능.
            suspendCancellableCoroutine<Unit> { cont ->
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }

            val result = suspendCancellableCoroutine<String> { cont ->
                translator.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }

            Log.i(TAG, "번역 완료: ${result.take(80)}")
            _translatedText.value = result
        } catch (e: Exception) {
            Log.e(TAG, "번역 실패", e)
            _translationError.value = "번역 실패 (인터넷 연결 확인): ${e.message}"
        } finally {
            _isTranslating.value = false
        }
    }

    /**
     * JPEG EXIF 메타데이터를 읽어 회전 값을 적용한다.
     * BitmapFactory.decodeStream() 은 EXIF를 자동으로 반영하지 않으므로
     * 카메라 사진이 90°/180°/270° 돌아간 채로 표시되는 문제를 수동으로 보정한다.
     */
    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        val cr = getApplication<Application>().contentResolver

        val exifOrientation = try {
            cr.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "EXIF 읽기 실패, 기본값 사용", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        val bitmap = try {
            cr.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap 디코딩 실패", e)
            return null
        } ?: return null

        return applyExifRotation(bitmap, exifOrientation)
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        Log.d(TAG, "EXIF 회전 적용: ${degrees}°")
        return rotateBitmap(bitmap, degrees)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun logRawResult(result: ChineseOcrManager.OcrRawResult, pass: Int) {
        Log.i(TAG, "===== ML KIT OCR RESULT (${pass}차) =====")
        result.lineResults.forEachIndexed { i, line ->
            Log.i(TAG, "Line[$i]  text=${line.text}  angle=${line.angle}  conf=${line.confidence}")
        }
        Log.i(TAG, "=========================================")
    }

    private fun logOrientationResult(result: OcrOrientationResult) {
        Log.i(TAG, "===== ORIENTATION RESULT =====")
        Log.i(TAG, "validLines=${result.validLineCount}  upright=${result.uprightCount}  upsideDown=${result.upsideDownCount}  sideways=${result.sidewaysCount}")
        Log.i(TAG, "uprightRatio=${result.uprightRatio}  upsideDownRatio=${result.upsideDownRatio}")
        Log.i(TAG, "orientation=${result.orientation}  wasRotated=${result.wasRotated}")
        Log.i(TAG, "==============================")
    }

    fun speakChinese(text: String) {
        if (!_ttsReady.value) {
            _ttsError.value = "중국어 TTS를 사용할 수 없습니다. 기기 TTS 설정을 확인하세요."
            return
        }
        _ttsError.value = null
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        ocrManager.close()
        translator.close()
        ttsManager.close()
    }

    companion object {
        private const val TAG = "OcrViewModel"
    }
}
