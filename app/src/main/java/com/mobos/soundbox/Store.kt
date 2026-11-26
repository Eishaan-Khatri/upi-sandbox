package com.mobos.soundbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Settings and the debug log (SharedPreferences), plus payments (SQLite via PaymentDb). */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("soundbox", Context.MODE_PRIVATE)
    private val db = PaymentDb.get(context)

    init {
        migrateOldPayments()
    }

    // ---- Settings ----

    /** Language code from Phrases ("hi", "en", ...). */
    var language: String
        get() = prefs.getString("lang", Phrases.default.code) ?: Phrases.default.code
        set(v) = prefs.edit().putString("lang", v).apply()

    /** Which VoiceEngine speaks. Only "tts" today. */
    var voiceEngine: String
        get() = prefs.getString("voice_engine", "tts") ?: "tts"
        set(v) = prefs.edit().putString("voice_engine", v).apply()

    /** Bank SMS backup. Off by default. */
    var smsEnabled: Boolean
        get() = prefs.getBoolean("sms", false)
        set(v) = prefs.edit().putBoolean("sms", v).apply()

    var kioskEnabled: Boolean
        get() = prefs.getBoolean("kiosk", true)
        set(v) = prefs.edit().putBoolean("kiosk", v).apply()

    /** Shop's own UPI ID and name, shown as a QR on screen. Empty = no QR. */
    var shopVpa: String
        get() = prefs.getString("shop_vpa", "") ?: ""
        set(v) = prefs.edit().putString("shop_vpa", v.trim()).apply()

    var shopName: String
        get() = prefs.getString("shop_name", "") ?: ""
        set(v) = prefs.edit().putString("shop_name", v.trim()).apply()

    // ---- Payments ----

    fun addPayment(p: Payment, channel: String, ref: String? = null) {
        db.insert(p, channel, ref)
    }

    /** Newest first. */
    fun paymentsSince(fromTime: Long): List<PaymentRecord> = db.since(fromTime)

    fun deleteTestPayments() = db.deleteTestPayments()

    // ---- Debug log of payment-app notifications ----

    @Synchronized
    fun logSeen(pkg: String, title: String, text: String, matched: Boolean) {
        val log = JSONArray(prefs.getString("log", "[]"))
        val item = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("pkg", pkg)
            .put("title", title.take(80))
            .put("text", text.take(200))
            .put("ok", matched)
        val out = JSONArray().put(item)
        for (i in 0 until minOf(log.length(), 14)) out.put(log.get(i))
        prefs.edit().putString("log", out.toString()).apply()
    }

    fun seenLog(): JSONArray = JSONArray(prefs.getString("log", "[]"))

    /** Earlier builds kept the last 20 payments in prefs; move them into the database once. */
    private fun migrateOldPayments() {
        val old = prefs.getString("recent", null) ?: return
        try {
            val arr = JSONArray(old)
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                val channel = if (o.optBoolean("test")) "test" else "app"
                val payer = o.optString("payer").takeIf { it.isNotEmpty() }
                db.insert(Payment(o.getDouble("amt"), o.getString("src"), payer), channel, null, o.getLong("t"))
            }
        } catch (_: Exception) { }
        prefs.edit().remove("recent").remove("total").remove("count").remove("day").apply()
    }
}
