package com.mobos.soundbox

import java.util.Locale

/**
 * What gets spoken, per language. To add a language, add one entry here
 * (and, for recorded voices later, a folder of clips with the same language code).
 */
object Phrases {

    class Language(
        val code: String,
        val displayName: String,
        val locale: Locale,
        /** Sentence for "rupees received", given whole rupees and paise. */
        val received: (rupees: Long, paise: Long) -> String,
    )

    val all: List<Language> = listOf(
        Language("hi", "हिंदी", Locale("hi", "IN")) { r, p ->
            if (p > 0) "$r रुपये $p पैसे प्राप्त हुए" else "$r रुपये प्राप्त हुए"
        },
        Language("en", "English", Locale("en", "IN")) { r, p ->
            if (p > 0) "Received $r rupees and $p paise" else "Received $r rupees"
        },
    )

    val default: Language get() = all.first()

    fun byCode(code: String): Language = all.firstOrNull { it.code == code } ?: default

    /** Next language in the list, for the "Voice" button. */
    fun next(code: String): Language = all[(all.indexOf(byCode(code)) + 1) % all.size]

    fun split(amount: Double): Pair<Long, Long> {
        val totalPaise = Math.round(amount * 100)
        return totalPaise / 100 to totalPaise % 100
    }

    fun sentence(code: String, amount: Double): String {
        val (r, p) = split(amount)
        return byCode(code).received(r, p)
    }
}
