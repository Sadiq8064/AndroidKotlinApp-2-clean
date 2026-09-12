package com.example.androidkotlinapp

import android.content.Context
import android.telecom.TelecomManager
import android.provider.Telephony
import android.content.Intent

object WhitelistManager {
    private const val PREFS_NAME = "whitelist_prefs"
    private const val KEY_WHITELISTED_PACKAGES = "whitelisted_packages"

    /** Maximum number of custom apps the user may allow during a focus session. */

    /**
     * Helper packages that briefly own the foreground on behalf of another app: Play Services,
     * ad SDKs, WebView shells, vendor game-overlay services. A launching game flickers through
     * these, and reading that flicker as "the user opened a blocked app" is what got
     * whitelisted games blocked and force-stopped a second after launch.
     *
     * These are treated as "no opinion" rather than "allowed" -- the blocker holds whatever
     * state it was already in instead of dismissing itself. Only packages with no browsable
     * UI of their own belong here; the Play Store and vendor security apps deliberately do
     * not, since allowing those would open a real hole in the block.
     */
    private val TRANSIENT_SYSTEM_PACKAGES = setOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.webview",
        "com.android.webview",
        "com.google.android.trichromelibrary",
        "com.samsung.android.game.gametools",        // Samsung Game Tools overlay
        "com.samsung.android.game.gos",              // Samsung Game Optimizing Service
        "com.oplus.games",                           // OPPO / OnePlus Game Space overlay
        "com.oplus.gamespace",
        "com.coloros.gamespace",
        "com.coloros.gamespaceui",
        "com.nearme.gamecenter",                     // OPPO / OnePlus game launch shim
        "com.xiaomi.gamecenter.sdk.service",         // Xiaomi game SDK overlay
        "com.miui.gallery.gamecenter",
        "com.vivo.gamewatch",                        // vivo Game Assistant
        "com.vivo.gamecube",

        // Screenshot preview and its crop/edit tool, which float briefly after a capture. A
        // session used to treat them as a blocked app and shove them off the screen mid-crop.
        "com.oplus.screenshot",
        "com.coloros.screenshot",
        "com.oplus.screenrecorder",
        "com.coloros.screenrecorder",
        "com.oplus.smartsidebar",
        "com.coloros.smartsidebar"
    )


    /**
     * Keyboards, pickers and share sheets, asked of the system rather than listed.
     *
     * A hard-coded list cannot keep up with what each vendor ships -- this phone's file picker
     * and gallery are ColorOS packages nobody would guess. These are looked up once and kept,
     * because the answer only changes when an app is installed or removed.
     *
     * Getting this wrong is not a small annoyance: a system sheet holds the foreground while
     * something is being chosen, so blocking one closed whatever whitelisted app the user was
     * part-way through using.
     */
    private var systemHelperCache: Set<String>? = null

    fun systemHelperPackages(context: Context): Set<String> {
        systemHelperCache?.let { return it }

        val found = mutableSetOf<String>()
        try {
            // Every keyboard the user could be typing with, whichever is switched on.
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.enabledInputMethodList?.forEach { found.add(it.packageName) }
            imm?.inputMethodList?.forEach { found.add(it.packageName) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Whatever answers the pick-a-thing intents, but only where it ships with the phone.
        // The system flag is what keeps an ordinary app from whitelisting itself just by
        // saying it can open images.
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" },
            Intent(Intent.ACTION_PICK).apply { type = "image/*" },
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" },
            Intent(Intent.ACTION_CHOOSER)
        )
        for (probe in probes) {
            try {
                pm.queryIntentActivities(probe, 0).forEach { info ->
                    val app = info.activityInfo?.applicationInfo ?: return@forEach
                    val isSystem =
                        (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    if (isSystem) found.add(app.packageName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        systemHelperCache = found
        return found
    }

    /** Clears the lookup, for when apps are installed or removed. */
    fun clearSystemHelperCache() {
        systemHelperCache = null
    }

    fun isTransientSystemPackage(packageName: String): Boolean {
        if (TRANSIENT_SYSTEM_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase()
        // Deliberately narrow: a plain "game" match would catch real games, which is the
        // very thing these overlays are stopping from running.
        return lower.startsWith("com.google.android.gms") ||
               lower.startsWith("com.google.android.webview") ||
               lower.endsWith(".gameservice") ||
               lower.contains("game.gos") ||
               lower.contains("gamespace") ||
               lower.contains("gametools") ||
               lower.contains("gameassistant") ||
               lower.contains("screenshot") ||
               lower.contains("screenrecord") ||
               lower.contains("smartsidebar")
    }

    val CORE_SYSTEM_PACKAGES = setOf(
        "com.example.androidkotlinapp",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard", // Samsung Keyboard
        "com.android.incallui", // Call screen overlay always allowed for safety
        "com.google.android.gms", // Google Play Services (accounts/sign-in flows)
        "com.android.documentsui", // System Document/File Picker
        "com.google.android.documentsui",
        "com.android.externalstorage", // External storage provider
        "com.google.android.providers.media.documents", // Google Media Documents Provider
        "com.android.providers.downloads.ui", // Download Picker UI
        "com.android.providers.downloads",
        "com.google.android.providers.downloads",
        "com.android.providers.media",
        "com.google.android.providers.media",
        "com.google.android.apps.photos", // Google Photos picker
        "com.google.android.apps.nbu.files", // Files by Google picker
        "com.sec.android.app.myfiles", // Samsung My Files
        "com.sec.android.gallery3d", // Samsung Gallery Picker
        "android", // Android System core / app chooser resolver dialog
        "com.android.intentresolver", // Android 13+ system app chooser / resolver dialog
        "com.google.android.providers.media.module", // Android Photo Picker (Android 13+)
        "com.android.permissioncontroller", // Runtime permission dialogs
        "com.google.android.permissioncontroller", // Runtime permission dialogs (Google variant)
        "com.google.android.gm", // Gmail (Always Allowed)
        "com.google.android.apps.docs", // Google Drive (Always Allowed - Hidden)
        "com.google.android.apps.docs.editors.docs", // Google Docs
        "com.google.android.apps.docs.editors.slides", // Google Slides
        "com.microsoft.office.excel", // Excel Sheet (Always Allowed - Hidden)
        "com.google.android.apps.docs.editors.sheets", // Google Sheets (Always Allowed - Hidden)
        "com.android.chrome", // Google Chrome Browser (Always Allowed - Hidden with custom URL restriction)
        "com.google.android.apps.meetings", // Google Meet (Always Allowed - Hidden)
        "com.google.android.apps.tachyon", // Google Duo / Google Meet (Always Allowed - Hidden)

        // Pickers and sheets this phone actually uses. A system sheet takes the foreground for
        // a moment while something is chosen -- an emoji, a photo, a file, a share target --
        // and blocking it closed the whitelisted app the user was in the middle of using.
        "com.google.android.photopicker",     // Android photo picker (newer builds)
        "com.coloros.filemanager",            // ColorOS file picker
        "com.coloros.gallery3d",              // ColorOS gallery picker
        "com.oplus.filemanager",
        "com.oplus.gallery3d",
        "com.coloros.oshare",                 // ColorOS share sheet
        "com.oplus.oshare",
        "com.oplus.securitykeyboard",         // ColorOS secure keyboard
        "com.android.avatarpicker",
        "com.android.wallpaper.livepicker",
        "com.android.sharedstoragebackup",
        "com.google.android.tts",             // Voice input method
        "com.google.android.overlay.modules.documentsui"
    )

    val PRODUCTIVITY_DEFAULT_PACKAGES = setOf(
        "com.google.android.apps.docs", // Google Drive
        "com.google.android.apps.docs.editors.docs", // Google Docs
        "com.google.android.apps.docs.editors.sheets", // Google Sheets
        "com.microsoft.office.excel", // Microsoft Excel
        "com.google.android.googlequicksearchbox" // Google (search) -- looking something up
                                                  // mid-session is work, not a distraction
    )

    fun isSystemPickerOrChooser(packageName: String): Boolean {
        val lower = packageName.lowercase()
        // Checked before anything else: several of the loose matches below would otherwise
        // let a settings package through on a word in its name.
        if (SETTINGS_PACKAGES.contains(lower)) return false
        // The Docs, Sheets and Slides editors all live under this prefix, so one match keeps
        // the whole suite open -- including any editor added to it later.
        if (lower.startsWith("com.google.android.apps.docs")) return true
        return lower.contains("picker") || 
               lower.contains("resolver") || 
               lower.contains("chooser") ||
               lower.contains("documentsui") ||
               lower.contains("document") ||
               lower.contains("media.providers") ||
               lower.contains("providers.media") ||
               lower.contains("externalstorage") ||
               lower.contains("intentresolver") ||
               lower.contains("gallery") ||
               lower.contains("photos") ||
               lower.contains("files") ||
               lower.contains("gsf") ||
               lower.contains("setupwizard") ||
               lower.contains("login")
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = android.content.ComponentName(context, UrlBlockerService::class.java)
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    /** Every door back into the phone's own settings. */
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.oplus.settings",
        "com.coloros.settings",
        "com.android.settings.intelligence"
    )

    fun getWhitelistedPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = if (!prefs.contains(KEY_WHITELISTED_PACKAGES)) {
            val defaults = getDynamicDefaults(context)
            prefs.edit().putStringSet(KEY_WHITELISTED_PACKAGES, defaults).apply()
            defaults
        } else {
            prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()) ?: emptySet()
        }
        val dynamicDefaults = getDynamicDefaults(context)
        // Keyboards and system pickers join the list every time, because they are how a
        // whitelisted app gets anything chosen -- an emoji, a photo, a file, a share target.
        val baseList = saved + dynamicDefaults + CORE_SYSTEM_PACKAGES +
            systemHelperPackages(context)
        
        return if (FocusService.isRunning) {
            // Not merely absent from the list: Settings is where accessibility, device admin
            // and the launcher role all live, so a session that let it open would be one the
            // user could switch off from inside. Held shut whether or not the accessibility
            // service happens to be running at this moment.
            baseList - SETTINGS_PACKAGES
        } else {
            baseList + SETTINGS_PACKAGES
        }
    }

    fun getCustomWhitelistedPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()) ?: emptySet()
        val dynamicDefaults = getDynamicDefaults(context)
        return (saved - dynamicDefaults - CORE_SYSTEM_PACKAGES).filter { it.isNotEmpty() }.toSet()
    }

    fun saveWhitelistedPackages(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_WHITELISTED_PACKAGES, packages).apply()
    }

    fun getDefaultPackagesInOrder(context: Context): List<String> {
        val list = mutableListOf<String>()
        val pm = context.packageManager

        // Query launcher activities to search for system apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)

        // 1. Default Dialer
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.defaultDialerPackage?.let { list.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Default SMS (Messages)
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { list.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Clock app (before calculator)
        val clockPkg = apps.firstOrNull {
            val pkg = it.activityInfo.packageName.lowercase()
            val label = it.loadLabel(pm).toString().lowercase()
            pkg.contains("clock") || label.equals("clock", ignoreCase = true)
        }?.activityInfo?.packageName
        clockPkg?.let { list.add(it) }

        // 4. Calculator
        val calculatorPkg = apps.firstOrNull {
            val pkg = it.activityInfo.packageName.lowercase()
            val label = it.loadLabel(pm).toString().lowercase()
            pkg.contains("calculator") || label.contains("calculator")
        }?.activityInfo?.packageName
        calculatorPkg?.let { list.add(it) }

        // 5. Gallery
        val galleryPkg = apps.firstOrNull {
            val pkg = it.activityInfo.packageName.lowercase()
            val label = it.loadLabel(pm).toString().lowercase()
            pkg.contains("gallery") || label.contains("gallery") || pkg.contains("photos") || label.contains("photos")
        }?.activityInfo?.packageName
        galleryPkg?.let { list.add(it) }

        // 6. Camera
        val cameraPkg = apps.firstOrNull {
            val pkg = it.activityInfo.packageName.lowercase()
            val label = it.loadLabel(pm).toString().lowercase()
            pkg.contains("camera") || label.contains("camera")
        }?.activityInfo?.packageName
        cameraPkg?.let { list.add(it) }

        return list.distinct()
    }

    fun saveShortcutOrder(context: Context, orderList: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = orderList.joinToString(",")
        prefs.edit().putString("shortcut_order", serialized).apply()
    }

    fun getShortcutOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString("shortcut_order", "") ?: ""
        if (serialized.isEmpty()) return emptyList()
        return serialized.split(",")
    }

    private const val PREFS_LIMITS = "app_usage_limits"
    private const val KEY_PREFIX_LIMIT = "limit_"
    private const val KEY_PREFIX_USED = "used_"
    private const val KEY_LAST_RESET = "last_reset_date"

    fun getAppUsageLimitMinutes(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PREFIX_LIMIT + packageName, -1)
    }

    fun setAppUsageLimitMinutes(context: Context, packageName: String, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PREFIX_LIMIT + packageName, minutes).apply()
    }

    fun deleteAppUsageLimit(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PREFIX_LIMIT + packageName)
            .remove(KEY_PREFIX_USED + packageName)
            .apply()
    }

    fun getAppUsedSeconds(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PREFIX_USED + packageName, 0)
    }

    fun setAppUsedSeconds(context: Context, packageName: String, seconds: Int) {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PREFIX_USED + packageName, seconds).apply()
    }

    fun resetAllUsedSeconds(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX_USED) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }

    fun checkAndResetDailyLimits(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastReset = prefs.getString(KEY_LAST_RESET, "") ?: ""
        if (todayStr != lastReset) {
            resetAllUsedSeconds(context)
            prefs.edit().putString(KEY_LAST_RESET, todayStr).apply()
        }
    }

    private fun getDynamicDefaults(context: Context): Set<String> {
        return getDefaultPackagesInOrder(context).toSet() + PRODUCTIVITY_DEFAULT_PACKAGES
    }
}
