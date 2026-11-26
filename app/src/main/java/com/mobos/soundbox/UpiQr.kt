package com.mobos.soundbox

import java.net.URLEncoder

/** Builds the standard UPI payment link that any UPI app can scan. */
object UpiQr {
    private val VPA = Regex("""^[A-Za-z0-9._\-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$""")

    fun isValidVpa(vpa: String) = VPA.matches(vpa.trim())

    fun uri(vpa: String, name: String): String {
        val pn = name.trim().ifEmpty { "Shop" }
        return "upi://pay?pa=" + enc(vpa.trim()) + "&pn=" + enc(pn) + "&cu=INR"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
