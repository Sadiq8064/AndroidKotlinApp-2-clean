package com.example.androidkotlinapp

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Guards a focus session against the one developer setting that can defeat it.
 *
 * USB debugging hands anyone with a cable a shell that can force-stop the blocker, revoke
 * its permissions or uninstall it outright, so a session is not allowed to start while it is
 * on. Developer options being enabled is not itself a problem and is deliberately tolerated
 * -- only the ADB switch is blocking.
 */
object DeveloperOptionsGuard {

    /**
     * Testing escape hatch, kept for development and off in anything shipped.
     *
     * While this is true [isUsbDebuggingEnabled] always reports false, so a session starts
     * normally with a debugger cable attached. It exists so this app can be worked on at all;
     * it must never be true in a release, because it disarms the one check standing between a
     * session and a shell that can uninstall the blocker.
     */
    const val BYPASS_USB_DEBUGGING_CHECK = false

    fun isUsbDebuggingEnabled(context: Context): Boolean {
        if (BYPASS_USB_DEBUGGING_CHECK) return false
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Whether developer options are on at all. Informational only; never blocks a session. */
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Opens developer options so the user can switch USB debugging off. */
    fun openDeveloperSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
