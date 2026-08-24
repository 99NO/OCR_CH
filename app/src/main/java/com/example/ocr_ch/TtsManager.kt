package com.example.ocr_ch

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : AutoCloseable {

    private var tts: TextToSpeech? = null

    var onReady: (supported: Boolean) -> Unit = {}
    var onStart: () -> Unit = {}
    var onDone: () -> Unit = {}
    var onError: () -> Unit = {}

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS 초기화 실패 (status=$status)")
                onReady(false)
                return@TextToSpeech
            }

            val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            val supported = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            Log.i(TAG, "TTS 초기화 완료. 중국어 지원: $supported (result=$result)")
            onReady(supported)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS 시작")
                onStart()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS 완료")
                onDone()
            }

            @Deprecated("Deprecated in API 21; onError(String, Int) used on API 21+")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS 오류")
                onError()
            }
        })
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        if (tts?.isSpeaking == true) tts?.stop()
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_ID = "ocr_ch_utt"
    }
}
