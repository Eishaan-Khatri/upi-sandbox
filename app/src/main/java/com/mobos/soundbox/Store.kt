package com.mobos.soundbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny on-phone storage: today's total, recent payments, settings, debug log. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("soundbox", Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString("lang", "hi") ?: "hi"
        set(v) = prefs.edit().putString("lang", v).apply()

    var smsEnabled: Boolean
        get() = prefs.getBoolean("sms", false)
        set(v) = prefs.edit().putBoolean("sms", v).apply()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun rollDay() {
        if (prefs.getString("day", "") != today()) {
            prefs.edit()
                .putString("day", today())
                .putString("total", "0")
                .putInt("count", 0)
                .apply()
        }
    }

    val todayTotal: Double
        get() { rollDay(); return prefs.getString("total", "0")!!.toDouble() }

    val todayCount: Int
        get() { rollDay(); return prefs.getInt("count", 0) }

    @Synchronized
    fun addPayment(p: Payment, test: Boolean) {
        rollDay()
        val recent = JSONArray(prefs.getString("recent", "[]"))
        val item = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("amt", p.amount)
            .put("src", p.source)
            .put("payer", p.payer ?: "")
            .put("test", test)
        prefs.edit()
            .putString("total", (todayTotal + p.amount).toString())
            .putInt("count", todayCount + 1)
            .putString("recent", prepend(recent, item, 20).toString())
            .apply()
    }

    fun recent(): JSONArray = JSONArray(prefs.getString("recent", "[]"))

    fun resetToday() {
        prefs.edit().putString("day", today()).putString("total", "0").putInt("count", 0).apply()
    }

    /** Raw notifications we saw from payment apps, so we can fix the parser when formats change. */
    @Synchronized
    fun logSeen(pkg: String, title: String, text: String, matched: Boolean) {
        val log = JSONArray(prefs.getString("log", "[]"))
        val item = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("pkg", pkg)
            .put("title", title.take(80))
            .put("text", text.take(200))
            .put("ok", matched)
        prefs.edit().putString("log", prepend(log, item, 15).toString()).apply()
    }

    fun seenLog(): JSONArray = JSONArray(prefs.getString("log", "[]"))

    private fun prepend(arr: JSONArray, item: JSONObject, max: Int): JSONArray {
        val out = JSONArray().put(item)
        for (i in 0 until minOf(arr.length(), max - 1)) out.put(arr.get(i))
        return out
    }
}
