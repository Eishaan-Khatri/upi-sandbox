# UPI Soundbox (barebones prototype)

Turns any Android 7+ phone into a talking UPI soundbox. When PhonePe, Paytm, Google Pay,
BHIM (or their Business apps) show a "money received" notification, the phone plays a chime
and says the amount out loud ("500 रुपये प्राप्त हुए").

No server, no KYC, no bank tie-up. It only reads notifications that are already on the phone.

## How it works

- `PaymentListenerService` is an Android NotificationListener. It ignores everything except a
  whitelist of payment-app package names (so a random app can't fake a payment).
- `PaymentParser` pulls the ₹ amount out of "received / credited / paid you" notifications and
  skips "you paid", requests, cashback, offers, failures and debits.
- `PaymentDb` (SQLite on the phone) keeps every payment; the screen shows the last 30 days grouped
  by date (Today / Yesterday / date) with payer name, time, amount and a day total.
- `Store` keeps settings and the last 15 raw payment notifications (for fixing the parser).
- Optional shop QR: set the shop's UPI ID in the setup panel and a standard `upi://pay` QR is shown.
- Voices: `Phrases` holds the sentence per language (add a language = add one entry);
  `VoiceEngine` lets recorded or custom voices plug in later. Today: phone text-to-speech.
- The app has **no internet permission**: payment data cannot leave the phone.
- Bank SMS backup (setup panel, off by default): only SMS from bank headers like `AX-HDFCBK`
  (never phone numbers), credits only. One payment is spoken once even if both the app alert and
  the bank SMS arrive (matched by UPI ref, or same amount within 2 minutes).
- Sticky "today's collection" notifications are ignored, so totals are never announced as payments.
- Tap the "UPI Soundbox" title 7 times for the setup panel: kiosk on/off, settings, open UPI apps,
  and the raw notifications seen (to fix the parser when an app changes its wording).

## Dedicated soundbox (Device Owner, QR setup)

1. Factory reset a spare phone.
2. On the first welcome screen, tap the same spot 6 times. A QR scanner opens.
3. Scan `setup-qr.png` from the "latest" GitHub release. Connect Wi-Fi if asked.
4. The phone downloads the app, finishes setup and boots straight into the soundbox (kiosk).

## Install and test

1. Download `upi-soundbox.apk` from the "latest" GitHub release.
2. Copy it to the phone and open it. Allow "Install unknown apps" when asked.
3. Open **UPI Soundbox** and tap **Give notification access**, then turn it on.
   On Android 13+, if the switch is greyed out: Settings → Apps → UPI Soundbox → ⋮ →
   "Allow restricted settings", then try again.
4. Tap **Test payment** to hear the voice. Turn the media volume up.
5. Install a UPI app on the same phone (or log in to your business app) and receive ₹1.
6. On Xiaomi/Oppo/Vivo/Realme, allow autostart and "no battery restriction" for the app,
   or the phone may kill the listener.

For Hindi voice: Settings → Text-to-speech → Google TTS → Install voice data → Hindi.

## Build locally

Open the folder in Android Studio, or run `./gradlew assembleDebug`
(needs the Android SDK, platform 34).
