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
- `Speaker` uses the phone's built-in text-to-speech (Hindi if installed, otherwise English).
- `Store` keeps today's total, the last 20 payments and the last 15 raw payment-app
  notifications (shown on screen so we can fix the parser when an app changes its wording).
- Bank SMS alerts are optional (off by default). When on, only SMS from bank-style sender IDs
  such as `AX-HDFCBK` that mention UPI/VPA/account are counted.

## Install and test

1. Download `app-debug.apk` from the latest "Build APK" run in GitHub Actions (Artifacts).
2. Copy it to the phone and open it. Allow "Install unknown apps" when asked.
3. Open **UPI Soundbox** and tap **Give notification access**, then turn it on.
4. Tap **Test payment** to hear the voice. Turn the media volume up.
5. Install a UPI app on the same phone (or log in to your business app) and receive ₹1.
6. On Xiaomi/Oppo/Vivo/Realme, also tap **Keep running** and allow autostart / no battery
   restriction, or the phone may kill the listener.

For Hindi voice: Settings → Text-to-speech → Google TTS → Install voice data → Hindi.

## Build locally

Open the folder in Android Studio, or run `./gradlew assembleDebug`
(needs the Android SDK, platform 34).
