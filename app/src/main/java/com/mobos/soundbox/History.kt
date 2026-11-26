package com.mobos.soundbox

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Payments of one day, for the grouped list on screen. */
data class DayGroup(val label: String, val total: Double, val items: List<PaymentRecord>)

object History {

    /** Midnight (start of the day) for the given time. */
    fun startOfDay(time: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Records must be newest first. Groups them as Today / Yesterday / "Mon, 24 Nov 2026". */
    fun groupByDay(records: List<PaymentRecord>, now: Long, tz: TimeZone = TimeZone.getDefault()): List<DayGroup> {
        val today = startOfDay(now, tz)
        val yesterday = startOfDay(today - 12 * 3600_000L, tz)
        val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).apply { timeZone = tz }
        val groups = LinkedHashMap<Long, MutableList<PaymentRecord>>()
        for (r in records) groups.getOrPut(startOfDay(r.time, tz)) { ArrayList() }.add(r)
        return groups.map { (day, items) ->
            val label = when (day) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> fmt.format(Date(day))
            }
            DayGroup(label, items.sumOf { it.amount }, items)
        }
    }

    /** Name shown in the list: the payer if we know it, otherwise the app/bank it came from. */
    fun displayName(r: PaymentRecord): String = r.payer?.takeIf { it.isNotBlank() } ?: r.source
}
