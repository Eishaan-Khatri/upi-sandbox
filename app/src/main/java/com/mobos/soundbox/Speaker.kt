package com.mobos.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks "500 रुपये प्राप्त हुए" using the phone's built-in text-to-speech. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<Double, String>>()
    var hindiAvailable: Boolean? = null
        private set

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val r = tts?.setLanguage(Locale("hi", "IN"))
                hindiAvailable = r != null && r >= TextToSpeech.LANG_AVAILABLE
                synchronized(pending) {
                    pending.forEach { (amt, lang) -> speakNow(amt, lang) }
                    pending.clear()
                }
            }
        }
    }

    fun announce(context: Context, amount: Double, language: String) {
        init(context)
        if (!ready) {
            synchronized(pending) { pending.add(amount to language) }
            return
        }
        speakNow(amount, language)
    }

    private fun speakNow(amount: Double, language: String) {
        val t = tts ?: return
        chime()
        val useHindi = language == "hi" && hindiAvailable == true
        t.setLanguage(if (useHindi) Locale("hi", "IN") else Locale("en", "IN"))
        val rupees = amount.toLong()
        val paise = Math.round((amount - rupees) * 100)
        val sentence = if (useHindi) {
            if (paise > 0) "$rupees रुपये $paise पैसे प्राप्त हुए" else "$rupees रुपये प्राप्त हुए"
        } else {
            if (paise > 0) "Received $rupees rupees and $paise paise" else "Received $rupees rupees"
        }
        // Small delay so the chime finishes before the voice starts.
        Handler(Looper.getMainLooper()).postDelayed({
            t.speak(sentence, TextToSpeech.QUEUE_ADD, null, "pay-" + System.nanoTime())
        }, 400)
    }

    private fun chime() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 500)
        } catch (_: RuntimeException) {
            // Some old phones refuse ToneGenerator; the voice still plays.
        }
    }
}
