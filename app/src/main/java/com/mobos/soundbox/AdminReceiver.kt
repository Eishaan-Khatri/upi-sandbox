package com.mobos.soundbox

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** The "device admin" Android talks to during QR setup. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Kiosk.applyPolicies(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
