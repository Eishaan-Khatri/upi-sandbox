package com.mobos.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech

/**
 * A way of saying "₹X received" out loud. Today there is one: the phone's text-to-speech.
 * Later: recorded clips per language, or the shop owner's own recorded voice,
 * each as another VoiceEngine selected by Store.voiceEngine.
 */
interface VoiceEngine {
    val id: String
    fun init(context: Context)
    /** Can this engine speak this language right now? null = not known yet. */
    fun supports(language: Phrases.Language): Boolean?
    fun speak(context: Context, amount: Double, language: Phrases.Language)
}

/** Entry point used by the rest of the app. Plays a chime, then the selected voice. */
object Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val engines: Map<String, VoiceEngine> = listOf<VoiceEngine>(TtsVoice).associateBy { it.id }

    private fun engine(context: Context): VoiceEngine =
        engines[Store(context).voiceEngine] ?: TtsVoice

    fun init(context: Context) = engine(context).init(context)

    fun supports(context: Context, languageCode: String): Boolean? =
        engine(context).supports(Phrases.byCode(languageCode))

    fun announce(context: Context, amount: Double, languageCode: String) {
        val app = context.applicationContext
        main.post {
            chime()
            val voice = engine(app)
            // Wait for the chime to finish, then speak.
            main.postDelayed({ voice.speak(app, amount, Phrases.byCode(languageCode)) }, 400)
        }
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

/** The phone's built-in text-to-speech. Falls back to English if the language isn't installed. */
object TtsVoice : VoiceEngine {
    override val id = "tts"
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<Double, Phrases.Language>>()
    private val available = HashMap<String, Boolean>()

    @Synchronized
    override fun init(context: Context) {
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
        for (lang in Phrases.all) {
            available[lang.code] = engine.isLanguageAvailable(lang.locale) >= TextToSpeech.LANG_AVAILABLE
        }
        ready = true
        val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
        queued.forEach { (amt, lang) -> speakNow(amt, lang) }
    }

    override fun supports(language: Phrases.Language): Boolean? = available[language.code]

    override fun speak(context: Context, amount: Double, language: Phrases.Language) {
        init(context)
        if (!ready) {
            synchronized(pending) { pending.add(amount to language) }
        } else {
            speakNow(amount, language)
        }
    }

    private fun speakNow(amount: Double, language: Phrases.Language) {
        val engine = tts ?: return
        val lang = if (available[language.code] == true) language else Phrases.byCode("en")
        engine.setLanguage(lang.locale)
        engine.speak(Phrases.sentence(lang.code, amount), TextToSpeech.QUEUE_ADD, null, "pay-" + System.nanoTime())
    }
}
