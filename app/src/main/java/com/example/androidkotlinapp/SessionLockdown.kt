package com.example.androidkotlinapp

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * The device-owner-level lockdown that makes an active session actually unbreakable.
 *
 * [UrlBlockerService]'s power-menu hammer is reactive: the global actions dialog has to
 * actually appear, get noticed by an accessibility event, and then get dismissed a few
 * milliseconds later. That gap is a real window, however small. A device-owner app can
 * instead put the screen into LockTask mode with the global actions dialog left out of the
 * allowed feature set entirely -- Android then never shows it in the first place, the same
 * way a single-purpose kiosk device works. The same device-owner access also blocks the app
 * from being uninstalled outright.
 *
 * None of this is available to a plain Device Admin app (which is all [FocusDeviceAdminReceiver]
 * has ever been granted so far) -- it needs actual Device Owner status, granted once by hand
 * with `adb shell dpm set-device-owner com.example.androidkotlinapp/.FocusDeviceAdminReceiver`
 * on a device with no other accounts/admins set up. Every call here is a no-op (caught
 * SecurityException) until that is done, so the existing accessibility-based dismiss stays in
 * place as the fallback regardless.
 */
object SessionLockdown {

    private fun admin(context: Context) = ComponentName(context, FocusDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager? =
        try {
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        } catch (e: Exception) {
            null
        }

    fun isDeviceOwner(context: Context): Boolean =
        try {
            dpm(context)?.isDeviceOwnerApp(context.packageName) == true
        } catch (e: Exception) {
            false
        }

    /** Whatever is guarding the phone right now: an active block session, a ringing alarm, or a face check. */
    fun shouldBeLocked(context: Context): Boolean =
        FocusService.isRunning || AlarmRingService.ringing || FaceRounds.anyInProgress(context)

    /**
     * Blocks (or unblocks) uninstalling this app outright. Instant and needs no activity or
     * lock task state, so it is armed first -- on session start and on a post-reboot restore --
     * before anything else has had a chance to run.
     */
    fun setUninstallBlocked(context: Context, blocked: Boolean) {
        val dpm = dpm(context) ?: return
        if (!isDeviceOwner(context)) return
        try {
            dpm.setUninstallBlocked(admin(context), context.packageName, blocked)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Puts the current activity's task into LockTask mode with every optional feature left
     * off: no global actions (power/restart) dialog, no home button, no notification shade,
     * no recents, no status bar info. Call from the guarding activity's onResume.
     */
    fun engage(activity: Activity) {
        val dpm = dpm(activity) ?: return
        if (!isDeviceOwner(activity)) return
        try {
            val admin = admin(activity)
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            // LOCK_TASK_FEATURE_NONE: every optional door (global actions, home, recents,
            // notifications, keyguard, system info) stays shut. Nothing here is a workaround
            // to dismiss quickly -- Android simply never opens any of them while this holds.
            // The API itself only exists from P; below that, LockTask has no optional
            // features to begin with, so there is nothing to set.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                activity.startLockTask()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Call from the guarding activity's onPause/onDestroy once nothing needs guarding any more. */
    fun release(activity: Activity) {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                activity.stopLockTask()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
