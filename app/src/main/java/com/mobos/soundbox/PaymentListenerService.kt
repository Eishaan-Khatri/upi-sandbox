package com.mobos.soundbox

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android calls this for every notification once the user grants
 * "Notification access". We only look at whitelisted UPI apps.
 *
 * Read-only by design: this class never calls cancelNotification, snoozeNotification,
 * notification actions/PendingIntents or anything call-related. Keep it that way.
 */
class PaymentListenerService : NotificationListenerService() {

    private lateinit var store: Store

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        Speaker.init(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Ask Android to connect us again (it sometimes drops listeners after updates).
        requestRebind(ComponentName(this, PaymentListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (_: Exception) {
            // Never let one odd notification crash the listener.
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!PaymentParser.isWatched(pkg)) return
        // Skip sticky notifications like "Today's collection ₹2,300": they are not new payments.
        if (sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val payment = PaymentParser.parse(pkg, title, text)
        store.logSeen(pkg, title, text, payment != null)
        if (payment == null) return

        if (!Dedupe.shared.shouldAnnounce(payment.amount, Dedupe.Channel.APP, payment.source, null)) return

        store.addPayment(payment, test = false)
        Speaker.announce(this, payment.amount, store.language)
    }
}
