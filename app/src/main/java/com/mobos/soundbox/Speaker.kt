package com.mobos.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Plays a chime, then speaks "500 रुपये प्राप्त हुए" with the phone's built-in text-to-speech. */
object Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<Double, String>>()
    private val HINDI = Locale("hi", "IN")
    private val ENGLISH = Locale("en", "IN")

    /** null = not known yet. */
    var hindiAvailable: Boolean? = null
        private set

    @Synchronized
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status -> main.post { onReady(status) } }
    }

    private fun onReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            // No working TTS engine. Drop it so the next payment tries again.
            try { engine.shutdown() } catch (_: Exception) { }
            tts = null
            ready = false
            return
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        hindiAvailable = engine.isLanguageAvailable(HINDI) >= TextToSpeech.LANG_AVAILABLE
        ready = true
        val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
        queued.forEach { (amt, lang) -> speakNow(amt, lang) }
    }

    fun announce(context: Context, amount: Double, language: String) {
        main.post {
            init(context)
            if (!ready) {
                synchronized(pending) { pending.add(amount to language) }
            } else {
                speakNow(amount, language)
            }
        }
    }

    private fun speakNow(amount: Double, language: String) {
        val engine = tts ?: return
        chime()
        val useHindi = language == "hi" && hindiAvailable == true
        val rupees = amount.toLong()
        val paise = Math.round((amount - rupees) * 100)
        val sentence = if (useHindi) {
            if (paise > 0) "$rupees रुपये $paise पैसे प्राप्त हुए" else "$rupees रुपये प्राप्त हुए"
        } else {
            if (paise > 0) "Received $rupees rupees and $paise paise" else "Received $rupees rupees"
        }
        // Wait for the chime to finish, then speak.
        main.postDelayed({
            engine.setLanguage(if (useHindi) HINDI else ENGLISH)
            engine.speak(sentence, TextToSpeech.QUEUE_ADD, null, "pay-" + System.nanoTime())
        }, 400)
    }

    private fun chime() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            main.postDelayed({ tg.release() }, 500)
        } catch (_: RuntimeException) {
            // Some old phones refuse ToneGenerator; the voice still plays.
        }
    }
}
