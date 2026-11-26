package com.mobos.soundbox

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Everything that needs Device Owner: lock the phone to this app, make it the home screen
 * (so it starts on boot), keep the screen on while charging, hide the status bar.
 * All calls are no-ops on a normal phone where we are not the owner.
 */
object Kiosk {

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    /** Called once right after QR setup, and again whenever the app starts as owner. */
    fun applyPolicies(ctx: Context) {
        if (!isOwner(ctx)) return
        try {
            applyPoliciesUnsafe(ctx)
        } catch (_: Exception) {
            // A failed policy must never crash the app, or the phone would boot-loop into a crash.
        }
    }

    private fun applyPoliciesUnsafe(ctx: Context) {
        val dpm = dpm(ctx)
        val admin = admin(ctx)

        dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName))

        // Become the home screen, so the phone boots straight into the soundbox.
        val alias = ComponentName(ctx, "${ctx.packageName}.HomeAlias")
        ctx.packageManager.setComponentEnabledSetting(
            alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, alias)

        // Screen stays on while plugged in (AC | USB | wireless).
        try {
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        } catch (_: SecurityException) { }

        dpm.setKeyguardDisabled(admin, true)
        setLocked(ctx, Store(ctx).kioskEnabled)
    }

    /** Kiosk on = status bar hidden and app pinned. Kiosk off = lets you open Settings / UPI apps. */
    fun setLocked(ctx: Context, locked: Boolean) {
        if (!isOwner(ctx)) return
        Store(ctx).kioskEnabled = locked
        try {
            dpm(ctx).setStatusBarDisabled(admin(ctx), locked)
        } catch (_: Exception) { }
    }

    /** Pin the app on screen if we are owner and kiosk is on. Call from onResume. */
    fun enterIfNeeded(activity: Activity) {
        if (!isOwner(activity) || !Store(activity).kioskEnabled) return
        if (dpm(activity).isLockTaskPermitted(activity.packageName)) {
            try { activity.startLockTask() } catch (_: Exception) { }
        }
    }

    fun exit(activity: Activity) {
        setLocked(activity, false)
        try { activity.stopLockTask() } catch (_: Exception) { }
    }

    /** For testing: give the phone back to normal without a factory reset. */
    @Suppress("DEPRECATION")
    fun removeOwner(activity: Activity) {
        if (!isOwner(activity)) return
        exit(activity)
        val dpm = dpm(activity)
        val admin = admin(activity)
        try {
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            dpm.setKeyguardDisabled(admin, false)
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity, "${activity.packageName}.HomeAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
            dpm.clearDeviceOwnerApp(activity.packageName)
        } catch (_: Exception) { }
    }
}
