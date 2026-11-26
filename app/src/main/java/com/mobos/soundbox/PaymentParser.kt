package com.mobos.soundbox

/**
 * Turns a notification (package + title + text) into a Payment, or null.
 * Only whitelisted packages are trusted: any app can post a fake-looking
 * "₹500 received" notification, so the package name is the first check.
 */
object PaymentParser {

    /** UPI apps whose "received" notifications we trust. Package -> display name. */
    val UPI_APPS = mapOf(
        "com.phonepe.app" to "PhonePe",
        "com.phonepe.app.business" to "PhonePe Business",
        "com.phonepe.merchant" to "PhonePe Business",
        "net.one97.paytm" to "Paytm",
        "com.paytm.business" to "Paytm Business",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.google.android.apps.nbu.paisa.merchant" to "Google Pay Business",
        "in.org.npci.upiapp" to "BHIM",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.dreamplug.androidapp" to "CRED",
        "com.mobikwik_new" to "MobiKwik",
        "com.freecharge.android" to "Freecharge",
        "money.super.payments" to "super.money",
        "com.sbi.upi" to "SBI Pay",
        "com.csam.icici.bank.imobile" to "iMobile",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.axis.mobile" to "Axis Mobile",
        "com.kotak.mobile.banking" to "Kotak",
        "com.bharatpe.app" to "BharatPe",
    )

    private val AMOUNT = Regex(
        """(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT = Regex(
        """received|credited|paid you|sent you|has paid|प्राप्त|जमा""",
        RegexOption.IGNORE_CASE
    )
    private val NOT_A_CREDIT = Regex(
        """debited|you paid|paid to|sent to|payment to|request|cashback|reward|offer|""" +
            """failed|declined|pending|due\b|bill|recharge|expir|refund|scratch|won\b|loan|emi\b|""" +
            """collection|summary|settle""",
        RegexOption.IGNORE_CASE
    )
    private val PAYER_PAID_YOU = Regex("""^(.{2,40}?)\s+(?:has\s+)?(?:paid|sent)\s+you""", RegexOption.IGNORE_CASE)
    private val PAYER_FROM = Regex("""\bfrom\s+([A-Za-z][A-Za-z .]{1,39}?)(?:\s+(?:on|via|to|in|at|ref|upi)\b|[.,\n]|$)""", RegexOption.IGNORE_CASE)

    fun isWatched(pkg: String) = pkg in UPI_APPS

    fun parse(pkg: String, title: String, text: String): Payment? {
        val appName = UPI_APPS[pkg] ?: return null

        val full = "$title\n$text"
        if (!CREDIT.containsMatchIn(full)) return null
        if (NOT_A_CREDIT.containsMatchIn(full)) return null

        val amountText = AMOUNT.find(full)?.groupValues?.get(1) ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0 || amount > 500_000.0) return null

        val payer = (PAYER_PAID_YOU.find(full.trim()) ?: PAYER_FROM.find(full))
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && !it.contains("₹") }

        return Payment(amount, appName, payer)
    }
}
