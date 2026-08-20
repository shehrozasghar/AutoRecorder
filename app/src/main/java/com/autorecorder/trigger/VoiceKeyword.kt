package com.autorecorder.trigger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.autorecorder.config.AppConfig

/**
 * On-demand voice keyword listener. The mic is only active while this
 * object is running (bounded by an armed window), not continuously.
 */
class VoiceKeyword(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    var onKeyword: (() -> Unit)? = null
    var active = false
        private set

    fun start() {
        active = true
        beginListening()
    }

    private fun beginListening() {
        if (!active) return
        try {
            recognizer?.cancel()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (!active) return
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_AUDIO ||
                        error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    ) {
                        active = false
                        recognizer = null
                        return
                    }
                    restart()
                }

                override fun onResults(results: Bundle?) {
                    checkTexts(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    restart()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    checkTexts(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            r.startListening(intent)
        } catch (e: Exception) {
            active = false
            recognizer = null
        }
    }

    private fun restart() {
        handler.postDelayed({
            if (active) beginListening()
        }, 250)
    }

    private fun checkTexts(texts: List<String>?) {
        if (texts.isNullOrEmpty()) return
        val keywords = AppConfig.load(context).voiceKeywords.map { it.lowercase() }
        val hit = texts.any { t ->
            val text = t.lowercase()
            keywords.any { text.contains(it) }
        }
        if (hit) onKeyword?.invoke()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        recognizer = null
    }
}