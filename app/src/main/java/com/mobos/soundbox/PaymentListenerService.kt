package com.mobos.soundbox

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android calls this for every notification once the user grants
 * "Notification access". We look only at payment apps and bank SMS.
 */
class PaymentListenerService : NotificationListenerService() {

    private lateinit var store: Store
    /** "package|amount" -> last time announced, to skip repeated/updated notifications. */
    private val recentlyAnnounced = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        Speaker.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!PaymentParser.isWatched(pkg)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val payment = PaymentParser.parse(pkg, title, text, store.smsEnabled)
        store.logSeen(pkg, title, text, payment != null)
        if (payment == null) return

        val key = "$pkg|${payment.amount}"
        val now = System.currentTimeMillis()
        synchronized(recentlyAnnounced) {
            val last = recentlyAnnounced[key]
            if (last != null && now - last < 15_000) return
            recentlyAnnounced[key] = now
        }

        store.addPayment(payment, test = false)
        Speaker.announce(this, payment.amount, store.language)
    }
}
