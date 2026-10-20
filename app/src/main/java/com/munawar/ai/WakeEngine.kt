package com.munawar.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * "Hey Munawar" detector. Android has no free always-on keyword API, so this restarts SpeechRecognizer in a loop and
 * looks for the name in each result. It uses battery and the recognizer may beep on some phones (see README).
 */
class WakeEngine(private val ctx: Context, private val prefs: Prefs, private val cb: Cb) {
    interface Cb {
        fun onWake(cmd: String)
        fun onLevel(l: Float)
        fun onActive(active: Boolean)
        fun onFatal(msg: String)
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var paused = false
    private var fails = 0

    @Volatile var on = false
        private set

    private val re = Regex(
        "(?:\\b(?:hey|hay|hi|hello|ok|okay)\\b\\s*|हे\\s*|हेय\\s*)?" +
            "(munawwar|munawar|munavar|munnawar|manawar|munawer|munnavar|मुनव्वर|मुनावर|मुनव्वार|मुनवर|मुनव्वर)"
    )

    fun start() {
        if (on) return
        on = true; paused = false; fails = 0
        loop()
    }

    fun stop() {
        on = false
        release(); h.removeCallbacksAndMessages(null)
        cb.onActive(false)
    }

    fun setPaused(p: Boolean) {
        if (!on) return
        paused = p
        h.removeCallbacksAndMessages(null)
        if (p) { release(); cb.onActive(false) } else loop()
    }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun loop() {
        if (!on || paused || rec != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            on = false; cb.onFatal("Speech recognition isn't available on this phone."); return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { if (rec === r) cb.onActive(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { if (rec === r) cb.onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onError(e: Int) {
                if (rec !== r) return
                release(); fails++
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    on = false; cb.onFatal("Microphone permission is missing."); return
                }
                val delay = when {
                    e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                    e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    fails > 8 -> 5000L
                    else -> 800L
                }
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) cb.onActive(false)
                h.postDelayed({ loop() }, delay)
            }
            override fun onResults(b: Bundle?) {
                if (rec !== r) return
                val text = first(b)
                release(); fails = 0
                val m = if (text != null) re.find(text.lowercase()) else null
                if (m != null && text != null) {
                    paused = true
                    cb.onActive(false)
                    cb.onWake(text.substring(m.range.last + 1).trim())
                } else h.postDelayed({ loop() }, 200)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.sttLocale())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        r.startListening(i)
    }

    private fun release() {
        val r = rec
        rec = null
        if (r != null) h.post {
            try { r.cancel() } catch (e: Exception) {}
            try { r.destroy() } catch (e: Exception) {}
        }
    }
}
