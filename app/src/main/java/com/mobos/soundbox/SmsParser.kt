package com.mobos.soundbox

/** A bank SMS that says money was credited. ref = 12-digit UPI reference (UTR) if present. */
data class SmsCredit(val payment: Payment, val ref: String?)

/**
 * Reads bank credit SMS, as a backup for when the UPI app's notification doesn't come.
 *
 * Trust rules:
 * - Sender must be a bank's registered header like "AX-HDFCBK" or "JD-SBIUPI-S".
 *   Normal phone numbers are always rejected (anyone can send an SMS from a number).
 * - The header must contain a known bank code.
 * - The text must say credited/received and mention UPI/IMPS/VPA/account,
 *   and must not look like a debit, OTP, offer, loan, refund or "will be credited".
 */
object SmsParser {

    /** TRAI header: 2-letter operator prefix, 3-8 letter/digit sender, optional -S/-T/-P/-G suffix. */
    private val HEADER = Regex("""^(?:[A-Z]{2}-)?([A-Z0-9]{3,8})(?:-[A-Z])?$""")

    private val BANK_CODES = listOf(
        "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "KMB", "PNB", "BOB", "BARODA", "CANARA", "CNRB",
        "UNION", "UBOI", "IDFC", "YES", "INDUS", "IDBI", "FEDERAL", "FEDBNK", "BOI", "CBOI", "CENTBK",
        "IOB", "INDBNK", "UCO", "PSB", "AUBANK", "AUSFB", "RBL", "DBS", "CITI", "HSBC", "SCB",
        "PAYTM", "PPBL", "AIRBNK", "AIRTEL", "JIOPBK", "JIOPAY", "FINO", "EQUITAS", "UJJIVN", "KVB",
        "CUB", "SIB", "TMB", "DCB", "BANDHN", "JKB", "KARB", "UPI",
    )

    private val AMOUNT = Regex(
        """(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT = Regex("""credited|received|deposited|\bcr\b""", RegexOption.IGNORE_CASE)
    private val CHANNEL = Regex("""upi|imps|vpa|a/c|acct|account""", RegexOption.IGNORE_CASE)
    private val NOT_A_CREDIT = Regex(
        """debited|\bdr\b|withdrawn|spent|\bpaid to|sent to|\botp\b|one time password|verification code|""" +
            """offer|cashback|reward|loan|\bemi\b|pre-?approved|refund|revers|will be credited|""" +
            """request|due\b|declined|failed""",
        RegexOption.IGNORE_CASE
    )
    /** 12-digit UPI reference number (UTR). */
    private val REF = Regex("""(?<!\d)(\d{12})(?!\d)""")
    private val PAYER = Regex(
        """\b(?:from|by|trf from)\s+([A-Za-z][A-Za-z .]{1,39}?)(?:\s+(?:on|via|ref|refno|upi)\b|[.,\n(]|$)""",
        RegexOption.IGNORE_CASE
    )

    fun isBankSender(sender: String): Boolean {
        val m = HEADER.matchEntire(sender.trim().uppercase()) ?: return false
        val id = m.groupValues[1]
        if (id.none { it.isLetter() }) return false
        return BANK_CODES.any { id.contains(it) }
    }

    fun parse(sender: String, body: String): SmsCredit? {
        if (!isBankSender(sender)) return null
        if (!CREDIT.containsMatchIn(body)) return null
        if (!CHANNEL.containsMatchIn(body)) return null
        if (NOT_A_CREDIT.containsMatchIn(body)) return null

        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0 || amount > 500_000.0) return null

        val payer = PAYER.find(body)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("VPA", true) && !it.equals("A", true) }
        val ref = REF.find(body)?.groupValues?.get(1)
        val bank = sender.trim().uppercase().substringAfter("-").substringBefore("-")
        return SmsCredit(Payment(amount, "Bank SMS ($bank)", payer), ref)
    }
}
