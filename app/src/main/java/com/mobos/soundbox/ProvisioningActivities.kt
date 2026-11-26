package com.mobos.soundbox

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Android 10+ setup wizard asks: work profile or fully managed? We answer: fully managed device. */
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().putExtra("android.app.extra.PROVISIONING_MODE", 1) // FULLY_MANAGED_DEVICE
        setResult(RESULT_OK, result)
        finish()
    }
}

/** Android 10+ setup wizard: "apply your policies now", then continues setup. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Kiosk.applyPolicies(this)
        setResult(RESULT_OK)
        finish()
    }
}
