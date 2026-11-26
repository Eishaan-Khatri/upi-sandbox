package com.mobos.soundbox

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** The "device admin" Android talks to during QR setup. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Kiosk.applyPolicies(context)
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Newer Android may block this; the app still opens as the home screen.
        }
    }
}
