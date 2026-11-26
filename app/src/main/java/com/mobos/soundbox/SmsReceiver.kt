package com.mobos.soundbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Bank SMS backup. Does nothing unless the user switched it on. Only reads incoming SMS. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val store = Store(context)
            if (!store.smsEnabled) return
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            // A long SMS arrives in several parts; join them per sender.
            val bySender = LinkedHashMap<String, StringBuilder>()
            for (m in parts) {
                if (m == null) continue
                val sender = m.originatingAddress ?: continue
                bySender.getOrPut(sender) { StringBuilder() }.append(m.messageBody ?: "")
            }
            for ((sender, body) in bySender) {
                val text = body.toString()
                // Don't store personal SMS in the debug log: only bank-style senders.
                if (!SmsParser.isBankSender(sender)) continue
                val credit = SmsParser.parse(sender, text)
                // Log only credit-looking SMS, never OTPs or other bank messages.
                val creditLike = text.contains("credit", true) || text.contains("received", true)
                if (credit == null && (!creditLike || text.contains("otp", true))) continue
                store.logSeen("SMS $sender", "", text, credit != null)
                if (credit == null) continue
                val p = credit.payment
                if (!Dedupe.shared.shouldAnnounce(p.amount, Dedupe.Channel.SMS, p.source, credit.ref)) continue
                store.addPayment(p, "sms", credit.ref)
                Speaker.announce(context, p.amount, store.language)
            }
        } catch (_: Exception) {
            // Never crash on an odd SMS.
        }
    }
}
