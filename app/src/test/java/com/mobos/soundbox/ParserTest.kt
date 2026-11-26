package com.mobos.soundbox

import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JVM tests for parsers, dedupe, phrases, UPI QR and history grouping. Run: ./gradlew testDebugUnitTest */
class ParserTest {
    private fun check(name: String, got: Any?, want: Any?) = assertEquals(name, want, got)
    private fun sms(sender: String, body: String) = SmsParser.parse(sender, body)?.payment?.amount

    @Test
    fun allCases() {
    // --- UPI app notifications ---
    val app = listOf(
        Triple(Triple("com.google.android.apps.nbu.paisa.user", "Ramesh Kumar paid you ₹500.00", ""), 500.0, "gpay paid you"),
        Triple(Triple("com.phonepe.app", "Received ₹1,250 from SURESH", "Money credited to your bank account"), 1250.0, "phonepe received"),
        Triple(Triple("com.paytm.business", "₹20 received", "Received ₹20 from Anil via UPI"), 20.0, "paytm business"),
        Triple(Triple("com.paytm.business", "Today's collection", "₹2,300 received today in 12 payments"), null, "collection summary"),
        Triple(Triple("net.one97.paytm", "Payment successful", "You paid ₹300 to Ramesh"), null, "you paid"),
        Triple(Triple("com.phonepe.app", "Money request", "Ramesh requested ₹500"), null, "request"),
        Triple(Triple("com.phonepe.app", "Cashback received", "You won ₹10 cashback"), null, "cashback"),
        Triple(Triple("com.fake.app", "Received ₹999", "from X"), null, "fake app"),
        Triple(Triple("in.org.npci.upiapp", "₹ 75 प्राप्त हुए", "रमेश से"), 75.0, "hindi"),
    )
    for ((c, want, name) in app) check("APP $name", PaymentParser.parse(c.first, c.second, c.third)?.amount, want)

    // --- Bank SMS: should be accepted ---
    check("SMS HDFC money received", sms("AX-HDFCBK", "Money Received - INR 500.00 in your HDFC Bank A/c xx1234 on 26-11-26 by A/c linked to VPA abc@ybl (UPI Ref No 432112345678)."), 500.0)
    check("SMS SBI credited by Rs", sms("JD-SBIUPI-S", "Dear UPI user A/C X1234 credited by Rs500.00 on date 26Nov26 trf from RAMESH Refno 432112345678. -SBI"), 500.0)
    check("SMS ICICI", sms("VM-ICICIT", "Dear Customer, Acct XX123 is credited with Rs 1,200.50 on 26-Nov-26 from abc@ybl. UPI:432112345678-ICICI Bank."), 1200.5)
    check("SMS Axis", sms("AD-AXISBK", "INR 250.00 credited to A/c no. XX1234 on 26-11-26 at 10:00:00 IST. Info- UPI/P2A/432112345678/RAMESH. Avl Bal INR 10,250.00"), 250.0)
    check("SMS Kotak", sms("BP-KOTAKB", "Received Rs.99.00 in your Kotak Bank a/c X1234 from abc@ybl on 26-11-26.UPI Ref:432112345678."), 99.0)
    check("SMS no prefix header", sms("HDFCBK", "Money Received - INR 40.00 in your HDFC Bank A/c xx1234 by VPA abc@ybl (UPI Ref No 432112345679)."), 40.0)
    check("SMS lowercase sender", sms("ax-hdfcbk", "Money Received - INR 41.00 in your HDFC Bank A/c xx1234 by VPA abc@ybl."), 41.0)

    // --- Bank SMS: should be rejected ---
    check("SMS from phone number", sms("+919812345678", "Rs.450.00 credited to a/c XX1234 by VPA abc@ybl"), null)
    check("SMS from 10 digits", sms("9812345678", "Rs.450.00 credited to a/c XX1234 by VPA abc@ybl"), null)
    check("SMS non-bank header", sms("AD-AMAZON", "Rs.450.00 credited to a/c XX1234 via UPI"), null)
    check("SMS debit", sms("JD-SBIUPI-S", "Dear UPI user A/C X1234 debited by 100.0 on date 26Nov26 trf to SHOP Refno 432112345678"), null)
    check("SMS HDFC sent", sms("AX-HDFCBK", "Sent Rs.300.00 From HDFC Bank A/C x1234 To RAMESH On 26/11/26 Ref 432112345678 Not You? Call 18002586161"), null)
    check("SMS OTP", sms("AX-HDFCBK", "OTP for UPI transaction of Rs 500 to be credited is 123456. Do not share."), null)
    check("SMS offer", sms("AX-HDFCBK", "Get Rs 5,00,000 pre-approved loan credited to your account instantly via UPI!"), null)
    check("SMS refund", sms("AX-HDFCBK", "Refund of Rs 200.00 credited to your A/c xx1234 via UPI"), null)
    check("SMS will be credited", sms("AX-HDFCBK", "Rs 200.00 will be credited to your A/c xx1234 via UPI"), null)
    check("SMS no amount", sms("AX-HDFCBK", "Your A/c xx1234 has been credited via UPI."), null)
    check("SMS balance only", sms("AX-HDFCBK", "Avl Bal in A/c xx1234 is INR 10,000.00 on 26-11-26"), null)
    check("SMS ref parsed", SmsParser.parse("AX-HDFCBK", "Money Received - INR 500.00 in your HDFC Bank A/c xx1234 by VPA abc@ybl (UPI Ref No 432112345678).")?.ref, "432112345678")

    // --- Dedupe ---
    var t = 0L
    val d = Dedupe { t }
    check("D first app 50", d.shouldAnnounce(50.0, Dedupe.Channel.APP, "PhonePe", null), true)
    t = 30_000
    check("D sms copy of 50", d.shouldAnnounce(50.0, Dedupe.Channel.SMS, "Bank SMS (HDFCBK)", "111111111111"), false)
    t = 40_000
    check("D second customer 50 app", d.shouldAnnounce(50.0, Dedupe.Channel.APP, "PhonePe", null), true)
    t = 50_000
    check("D its sms copy", d.shouldAnnounce(50.0, Dedupe.Channel.SMS, "Bank SMS (HDFCBK)", "222222222222"), false)
    t = 51_000
    check("D app repost within 15s", d.shouldAnnounce(50.0, Dedupe.Channel.APP, "PhonePe", null), false)
    t = 60_000
    check("D sms first, 80", d.shouldAnnounce(80.0, Dedupe.Channel.SMS, "Bank SMS (SBIUPI)", "333333333333"), true)
    t = 70_000
    check("D app copy of 80", d.shouldAnnounce(80.0, Dedupe.Channel.APP, "Google Pay", null), false)
    t = 75_000
    check("D same sms ref again", d.shouldAnnounce(80.0, Dedupe.Channel.SMS, "Bank SMS (SBIUPI)", "333333333333"), false)
    t = 300_000
    check("D 80 after 4 min is new", d.shouldAnnounce(80.0, Dedupe.Channel.APP, "Google Pay", null), true)
    t = 310_000
    check("D different amount", d.shouldAnnounce(81.0, Dedupe.Channel.SMS, "Bank SMS (SBIUPI)", null), true)


    // --- Phrases ---
    check("P hi whole", Phrases.sentence("hi", 500.0), "500 रुपये प्राप्त हुए")
    check("P hi paise", Phrases.sentence("hi", 120.5), "120 रुपये 50 पैसे प्राप्त हुए")
    check("P en", Phrases.sentence("en", 99.99), "Received 99 rupees and 99 paise")
    check("P float edge 0.29", Phrases.split(0.29), 0L to 29L)
    check("P rounding 99.995", Phrases.split(99.995), 100L to 0L)
    check("P unknown lang -> default", Phrases.byCode("xx").code, "hi")
    check("P next", Phrases.next("hi").code, "en")
    check("P next wraps", Phrases.next("en").code, "hi")

    // --- UPI QR ---
    check("Q valid", UpiQr.isValidVpa("shop.name@ybl"), true)
    check("Q valid digits", UpiQr.isValidVpa("9876543210@paytm"), true)
    check("Q invalid no at", UpiQr.isValidVpa("shopname"), false)
    check("Q invalid space", UpiQr.isValidVpa("shop name@ybl"), false)
    check("Q invalid empty handle", UpiQr.isValidVpa("shop@"), false)
    check("Q uri", UpiQr.uri("shop@ybl", "Sharma Store & Co"), "upi://pay?pa=shop%40ybl&pn=Sharma%20Store%20%26%20Co&cu=INR")
    check("Q uri default name", UpiQr.uri("shop@ybl", " "), "upi://pay?pa=shop%40ybl&pn=Shop&cu=INR")

    // --- History grouping (IST) ---
    val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    fun at(s: String) = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = tz }.parse(s).time
    val now2 = at("2026-11-26 23:30")
    fun rec(id: Long, t: String, amt: Double, payer: String?) = PaymentRecord(id, at(t), amt, "PhonePe", payer, "app", null)
    val recs = listOf(
        rec(5, "2026-11-26 22:10", 500.0, "Ramesh"),
        rec(4, "2026-11-26 00:05", 20.0, null),
        rec(3, "2026-11-25 23:59", 100.0, "Suresh"),
        rec(2, "2026-11-25 09:00", 50.5, ""),
        rec(1, "2026-11-20 12:00", 10.0, "Anil"),
    )
    val g = History.groupByDay(recs, now2, tz)
    check("H group count", g.size, 3)
    check("H labels", g.map { it.label }, listOf("Today", "Yesterday", "Fri, 20 Nov 2026"))
    check("H today total", g[0].total, 520.0)
    check("H yesterday items", g[1].items.map { it.id }, listOf(3L, 2L))
    check("H yesterday total", g[1].total, 150.5)
    check("H name payer", History.displayName(recs[0]), "Ramesh")
    check("H name fallback null", History.displayName(recs[1]), "PhonePe")
    check("H name fallback blank", History.displayName(recs[3]), "PhonePe")
    check("H empty", History.groupByDay(emptyList(), now2, tz).size, 0)

        }
}
