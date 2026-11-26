package com.mobos.soundbox

/** A payment we believe was received. */
data class Payment(val amount: Double, val source: String, val payer: String?)

/** One saved payment. */
data class PaymentRecord(
    val id: Long,
    val time: Long,
    val amount: Double,
    val source: String,
    val payer: String?,
    val channel: String,   // "app", "sms" or "test"
    val ref: String?,
)
