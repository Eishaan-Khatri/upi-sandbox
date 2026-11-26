package com.mobos.soundbox

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The one screen: shop QR, today's total, payments grouped by date, test button.
 * Tap the title 7 times for the setup panel. Built in code, no XML layouts.
 */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var accessButton: Button
    private lateinit var accessNote: TextView
    private lateinit var qrBox: LinearLayout
    private lateinit var qrName: TextView
    private lateinit var qrImage: ImageView
    private lateinit var qrVpa: TextView
    private lateinit var total: TextView
    private lateinit var langButton: Button
    private lateinit var historyBox: LinearLayout
    private lateinit var setupPanel: LinearLayout
    private lateinit var ownerStatus: TextView
    private lateinit var kioskButton: Button
    private lateinit var removeOwnerButton: Button
    private lateinit var smsButton: Button
    private lateinit var seenList: TextView
    private var titleTaps = 0
    private var shownQrFor: String? = null
    private var shownHistoryKey: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 2000) }
    }
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Speaker.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Kiosk.applyPolicies(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }

        col.addView(label("UPI Soundbox", 24f, bold = true).apply {
            setOnClickListener {
                titleTaps++
                if (titleTaps >= 7) {
                    titleTaps = 0
                    setupPanel.visibility =
                        if (setupPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    refresh()
                }
            }
        })

        setupPanel = buildSetupPanel()
        col.addView(setupPanel)

        status = label("", 15f)
        col.addView(status)
        accessButton = button("Give notification access") { openNotificationAccess() }
        col.addView(accessButton)
        accessNote = label(
            "Android shows one standard warning for every notification app. This app only reads " +
                "payment alerts from UPI apps. It never dismisses, taps or answers anything, and " +
                "nothing leaves the phone.", 13f
        ).apply { setTextColor(Color.DKGRAY) }
        col.addView(accessNote)

        // Shop QR (only when a UPI ID is set in the setup panel).
        qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        qrName = label("", 20f, bold = true).apply { gravity = Gravity.CENTER }
        qrImage = ImageView(this).apply { adjustViewBounds = true }
        qrVpa = label("", 14f).apply { gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) }
        qrBox.addView(qrName)
        qrBox.addView(qrImage)
        qrBox.addView(qrVpa)
        col.addView(qrBox)

        col.addView(label("Today", 16f).apply { setPadding(0, dp(16), 0, 0) })
        total = label("₹0", 44f, bold = true).apply { setTextColor(GREEN) }
        col.addView(total)

        col.addView(button("Test payment (fake amount)") {
            val amt = listOf(10, 20, 50, 120, 250, 499, 1500)[Random.nextInt(7)].toDouble()
            store.addPayment(Payment(amt, "Test", null), "test")
            Speaker.announce(this, amt, store.language)
            refresh()
        })
        langButton = button("") {
            store.language = Phrases.next(store.language).code
            refresh()
        }
        col.addView(langButton)

        historyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        col.addView(historyBox)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        Kiosk.enterIfNeeded(this)
        if (hasNotificationAccess()) {
            // Reconnects the listener if Android dropped it (happens after updates on some phones).
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(this, PaymentListenerService::class.java)
                )
            } catch (_: Exception) { }
        }
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    // ---------------- Setup panel ----------------

    private fun buildSetupPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(255, 243, 205))
        }
        panel.addView(label("Setup panel", 18f, bold = true))
        ownerStatus = label("", 14f)
        panel.addView(ownerStatus)
        kioskButton = button("") {
            if (store.kioskEnabled) Kiosk.exit(this)
            else { Kiosk.setLocked(this, true); Kiosk.enterIfNeeded(this) }
            refresh()
        }
        panel.addView(kioskButton)
        panel.addView(button("Set shop UPI ID (for the QR)") { editShopQr() })
        smsButton = button("") { toggleSms() }
        panel.addView(smsButton)
        panel.addView(button("Notification access settings") { openNotificationAccess() })
        panel.addView(button("Phone Settings") { open(Intent(Settings.ACTION_SETTINGS)) })
        panel.addView(button("Battery settings (keep app running)") {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        panel.addView(button("Open Play Store") { launch("com.android.vending") })
        for ((pkg, name) in PaymentParser.UPI_APPS) {
            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                panel.addView(button("Open $name") { launch(pkg) })
            }
        }
        panel.addView(button("Delete test payments") {
            val n = store.deleteTestPayments()
            Toast.makeText(this, "Deleted $n test payments", Toast.LENGTH_SHORT).show()
            refresh()
        })
        removeOwnerButton = button("Remove Device Owner (testing only)") {
            Kiosk.removeOwner(this)
            refresh()
        }
        panel.addView(removeOwnerButton)
        panel.addView(label("Payment notifications seen (✔ = announced)", 14f, bold = true)
            .apply { setPadding(0, dp(16), 0, dp(4)) })
        seenList = label("", 12f).apply { setTextColor(Color.DKGRAY) }
        panel.addView(seenList)
        return panel
    }

    private fun editShopQr() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val vpaInput = EditText(this).apply {
            hint = "UPI ID, e.g. shopname@ybl"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(store.shopVpa)
        }
        val nameInput = EditText(this).apply {
            hint = "Shop name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(store.shopName)
        }
        box.addView(vpaInput)
        box.addView(nameInput)
        AlertDialog.Builder(this)
            .setTitle("Shop UPI QR")
            .setMessage("Leave the UPI ID empty to hide the QR.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val vpa = vpaInput.text.toString().trim()
                if (vpa.isNotEmpty() && !UpiQr.isValidVpa(vpa)) {
                    Toast.makeText(this, "That doesn't look like a UPI ID (name@bank)", Toast.LENGTH_LONG).show()
                } else {
                    store.shopVpa = vpa
                    store.shopName = nameInput.text.toString()
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasSmsPermission() =
        checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun toggleSms() {
        if (store.smsEnabled) {
            store.smsEnabled = false
        } else {
            store.smsEnabled = true
            if (!hasSmsPermission() && !Kiosk.grantSmsPermission(this)) {
                requestPermissions(arrayOf(android.Manifest.permission.RECEIVE_SMS), REQ_SMS)
            }
        }
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS && !hasSmsPermission()) store.smsEnabled = false
        refresh()
    }

    private fun openNotificationAccess() =
        open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    /** Opening another screen needs the kiosk unlocked first. */
    private fun open(intent: Intent) {
        if (Kiosk.isOwner(this) && store.kioskEnabled) Kiosk.exit(this)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
        }
    }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { open(it) }
    }

    // ---------------- Screen refresh ----------------

    private fun refresh() {
        val granted = hasNotificationAccess()
        status.text = if (granted) "✅ Listening for UPI payments"
                      else "❌ Notification access is OFF. Tap the button below and turn on UPI Soundbox."
        status.setTextColor(if (granted) GREEN else Color.RED)
        accessButton.visibility = if (granted) View.GONE else View.VISIBLE
        accessNote.visibility = accessButton.visibility

        refreshQr()

        val now = System.currentTimeMillis()
        val records = store.paymentsSince(History.startOfDay(now) - 30L * 24 * 3600_000L)
        val groups = History.groupByDay(records, now)
        val today = groups.firstOrNull { it.label == "Today" }
        total.text = "₹" + money(today?.total ?: 0.0) + "   (${today?.items?.size ?: 0})"

        val lang = Phrases.byCode(store.language)
        val missing = Speaker.supports(this, lang.code) == false
        langButton.text = "Voice: " + lang.displayName + (if (missing) " (not installed, using English)" else "")

        val key = "${records.size}|${records.firstOrNull()?.id}|${History.startOfDay(now)}"
        if (key != shownHistoryKey) {
            shownHistoryKey = key
            drawHistory(groups)
        }

        if (setupPanel.visibility == View.VISIBLE) refreshSetupPanel()
    }

    private fun refreshQr() {
        val vpa = store.shopVpa
        if (vpa.isEmpty() || !UpiQr.isValidVpa(vpa)) {
            qrBox.visibility = View.GONE
            shownQrFor = null
            return
        }
        qrBox.visibility = View.VISIBLE
        val uri = UpiQr.uri(vpa, store.shopName)
        if (uri == shownQrFor) return
        val size = minOf(resources.displayMetrics.widthPixels - dp(48), dp(320))
        val bmp = qrBitmap(uri, size)
        if (bmp != null) {
            qrImage.setImageBitmap(bmp)
            shownQrFor = uri
        }
        qrName.text = store.shopName.ifEmpty { "Scan to pay" }
        qrVpa.text = vpa
    }

    private fun qrBitmap(text: String, size: Int): Bitmap? = try {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.RGB_565)
    } catch (_: Exception) {
        null
    }

    private fun drawHistory(groups: List<DayGroup>) {
        historyBox.removeAllViews()
        if (groups.isEmpty()) {
            historyBox.addView(label("No payments yet", 15f).apply { setTextColor(Color.DKGRAY) })
            return
        }
        for (g in groups) {
            historyBox.addView(row(g.label, "₹" + money(g.total), header = true))
            for (r in g.items) {
                val name = History.displayName(r) + (if (r.channel == "test") " (test)" else "")
                historyBox.addView(row(timeFmt.format(Date(r.time)) + "   " + name, "₹" + money(r.amount)))
            }
        }
    }

    /** One line: text on the left, amount on the right. */
    private fun row(left: String, right: String, header: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(4), if (header) dp(14) else dp(6), dp(4), dp(6))
        if (header) setBackgroundColor(Color.rgb(235, 245, 238))
        val size = if (header) 16f else 15f
        addView(label(left, size, bold = header).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(label(right, size, bold = true).apply {
            gravity = Gravity.END
            if (!header) setTextColor(GREEN)
        })
    }

    private fun refreshSetupPanel() {
        val owner = Kiosk.isOwner(this)
        ownerStatus.text = if (owner)
            "Device Owner: YES (dedicated soundbox). Kiosk is " + (if (store.kioskEnabled) "ON." else "OFF.")
        else "Device Owner: NO (normal app). Set up by QR after a factory reset to lock the phone."
        kioskButton.visibility = if (owner) View.VISIBLE else View.GONE
        kioskButton.text = if (store.kioskEnabled) "Unlock kiosk (to set up apps)" else "Lock kiosk again"
        removeOwnerButton.visibility = if (owner) View.VISIBLE else View.GONE
        smsButton.text = "Bank SMS backup: " + when {
            !store.smsEnabled -> "OFF"
            hasSmsPermission() -> "ON"
            else -> "ON, but SMS permission is missing (tap twice to ask again)"
        }

        val seen = store.seenLog()
        seenList.text = buildString {
            for (i in 0 until seen.length()) {
                val s = seen.getJSONObject(i)
                append(if (s.getBoolean("ok")) "✔ " else "✘ ")
                append(timeFmt.format(Date(s.getLong("t")))).append("  ").append(s.getString("pkg")).append('\n')
                append("   ").append(s.getString("title")).append(" | ").append(s.getString("text")).append("\n\n")
            }
            if (isEmpty()) append("Nothing seen yet")
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun money(v: Double): String {
        val paise = Math.round(v * 100)
        return if (paise % 100 == 0L) String.format(Locale.US, "%,d", paise / 100)
               else String.format(Locale.US, "%,.2f", paise / 100.0)
    }

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

    companion object {
        private const val REQ_SMS = 1
        private val GREEN = Color.rgb(0, 128, 60)
    }
}
