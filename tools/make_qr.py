"""Builds the Device Owner setup QR for a released APK.

Usage: python tools/make_qr.py <apk path> <download url> [wifi ssid] [wifi password]
Writes provisioning.json and setup-qr.png next to the current directory.
"""
import base64
import hashlib
import json
import sys

import qrcode

apk, url = sys.argv[1], sys.argv[2]
digest = hashlib.sha256(open(apk, "rb").read()).digest()
checksum = base64.urlsafe_b64encode(digest).decode().rstrip("=")

payload = {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
        "com.mobos.soundbox/com.mobos.soundbox.AdminReceiver",
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": url,
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": checksum,
    # Keep Play Store, Google TTS etc. so UPI apps and the Hindi voice still work.
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
    "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": True,
    "android.app.extra.PROVISIONING_TIME_ZONE": "Asia/Kolkata",
    "android.app.extra.PROVISIONING_LOCALE": "en_IN",
}
if len(sys.argv) >= 4:
    payload["android.app.extra.PROVISIONING_WIFI_SSID"] = sys.argv[3]
    payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = "WPA"
    if len(sys.argv) >= 5:
        payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = sys.argv[4]

text = json.dumps(payload, indent=2)
open("provisioning.json", "w").write(text)
qrcode.make(json.dumps(payload), box_size=8, border=4).save("setup-qr.png")
print(text)
