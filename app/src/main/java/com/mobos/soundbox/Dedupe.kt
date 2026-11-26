package com.mobos.soundbox

/**
 * Decides whether a payment should be announced, so one payment is spoken once even when
 * both the UPI app notification and the bank SMS arrive (in either order).
 *
 * - Same UTR/ref seen before -> duplicate.
 * - Same amount from the OTHER channel within 2 minutes, not already paired -> duplicate
 *   (each announced payment can absorb only one copy from the other channel, so two
 *   different customers paying the same amount are both still announced).
 * - Same amount from the same source within 15 seconds -> duplicate (app re-posted its alert).
 */
class Dedupe(private val now: () -> Long = System::currentTimeMillis) {

    enum class Channel { APP, SMS }

    private class Entry(val amount: Double, val channel: Channel, val source: String,
                        val time: Long, var ref: String?, var paired: Boolean = false)

    private val entries = ArrayList<Entry>()

    @Synchronized
    fun shouldAnnounce(amount: Double, channel: Channel, source: String, ref: String?): Boolean {
        val t = now()
        entries.removeAll { t - it.time > CROSS_WINDOW_MS }

        if (ref != null && entries.any { it.ref == ref }) return false

        entries.firstOrNull {
            it.amount == amount && it.channel != channel && !it.paired && t - it.time <= CROSS_WINDOW_MS
        }?.let {
            it.paired = true
            if (it.ref == null) it.ref = ref
            return false
        }

        if (entries.any { it.amount == amount && it.source == source && t - it.time <= REPEAT_WINDOW_MS }) {
            return false
        }

        entries.add(Entry(amount, channel, source, t, ref))
        return true
    }

    companion object {
        const val CROSS_WINDOW_MS = 120_000L
        const val REPEAT_WINDOW_MS = 15_000L
        /** One shared instance for the notification listener and the SMS receiver. */
        val shared = Dedupe()
    }
}
