package com.mobos.soundbox

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** One screen: permission status, today's total, last payments, test button. Built in code, no XML. */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var accessButton: Button
    private lateinit var total: TextView
    private lateinit var last: TextView
    private lateinit var langButton: Button
    private lateinit var smsButton: Button
    private lateinit var recentList: TextView
    private lateinit var seenList: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 2000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Speaker.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        col.addView(label("UPI Soundbox", 26f, bold = true))
        status = label("", 15f)
        col.addView(status)
        accessButton = button("Give notification access") {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        col.addView(accessButton)

        col.addView(label("Today", 16f).apply { setPadding(0, dp(20), 0, 0) })
        total = label("₹0", 48f, bold = true).apply { setTextColor(Color.rgb(0, 128, 60)) }
        col.addView(total)
        last = label("No payments yet", 16f)
        col.addView(last)

        col.addView(button("Test payment (fake ₹ amount)") {
            val amt = listOf(10, 20, 50, 120, 250, 499, 1500)[Random.nextInt(7)].toDouble()
            store.addPayment(Payment(amt, "Test button", null), test = true)
            Speaker.announce(this, amt, store.language)
            refresh()
        })
        langButton = button("") {
            store.language = if (store.language == "hi") "en" else "hi"
            refresh()
        }
        col.addView(langButton)
        smsButton = button("") {
            store.smsEnabled = !store.smsEnabled
            refresh()
        }
        col.addView(smsButton)
        col.addView(button("Keep running (battery settings)") {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        })
        col.addView(button("Reset today's total") { store.resetToday(); refresh() })

        col.addView(label("Recent payments", 18f, bold = true).apply { setPadding(0, dp(20), 0, dp(4)) })
        recentList = label("", 14f)
        col.addView(recentList)

        col.addView(label("Payment-app notifications seen (for fixing the parser)", 14f, bold = true)
            .apply { setPadding(0, dp(20), 0, dp(4)) })
        seenList = label("", 12f).apply { setTextColor(Color.DKGRAY) }
        col.addView(seenList)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() { super.onResume(); handler.post(refresher) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresher) }

    private fun refresh() {
        val granted = hasNotificationAccess()
        status.text = if (granted) "✅ Listening for UPI payments" else "❌ Notification access is OFF. Tap the button below and turn on UPI Soundbox."
        status.setTextColor(if (granted) Color.rgb(0, 128, 60) else Color.RED)
        accessButton.visibility = if (granted) View.GONE else View.VISIBLE

        total.text = "₹" + money(store.todayTotal)
        val hindiNote = if (Speaker.hindiAvailable == false) " (Hindi voice not installed, using English)" else ""
        langButton.text = "Voice: " + (if (store.language == "hi") "हिंदी" else "English") + hindiNote
        smsButton.text = "Bank SMS alerts: " + (if (store.smsEnabled) "ON" else "OFF")

        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        val recent = store.recent()
        if (recent.length() > 0) {
            val p = recent.getJSONObject(0)
            last.text = "Last: ₹${money(p.getDouble("amt"))} via ${p.getString("src")} at ${fmt.format(Date(p.getLong("t")))}" +
                "  •  ${store.todayCount} payments today"
        } else {
            last.text = "No payments yet"
        }
        recentList.text = buildString {
            for (i in 0 until recent.length()) {
                val p = recent.getJSONObject(i)
                append(fmt.format(Date(p.getLong("t"))))
                append("   ₹").append(money(p.getDouble("amt")))
                append("   ").append(p.getString("src"))
                val payer = p.optString("payer")
                if (payer.isNotEmpty()) append("   from ").append(payer)
                if (p.optBoolean("test")) append("   [test]")
                append('\n')
            }
            if (isEmpty()) append("Nothing yet")
        }
        val seen = store.seenLog()
        seenList.text = buildString {
            for (i in 0 until seen.length()) {
                val s = seen.getJSONObject(i)
                append(if (s.getBoolean("ok")) "✔ " else "✘ ")
                append(fmt.format(Date(s.getLong("t")))).append("  ").append(s.getString("pkg")).append('\n')
                append("   ").append(s.getString("title")).append(" | ").append(s.getString("text")).append("\n\n")
            }
            if (isEmpty()) append("Nothing seen yet")
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun money(v: Double): String =
        if (v == Math.floor(v)) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%,.2f", v)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.BLACK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }
}
