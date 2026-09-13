package com.example.androidkotlinapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UrlBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // The power menu is pushed straight back off the screen while an alarm is sounding OR a
        // block session is running -- the phone must not be switched off to escape either.
        //
        // An accessibility service can see the global-actions dialog appear and dismiss it,
        // which is how the apps that offer "can't turn the phone off" do it. Google narrowed
        // this on Android 12, so it is not guaranteed on every build -- but it works on this
        // one's vendor, which is what matters here.
        // Also held between face-check rounds: nothing is ringing in the gap, but the alarm is
        // not beaten until the third check, so the phone stays un-switch-off-able until then.
        if ((AlarmRingService.ringing || FocusService.isRunning ||
                FaceRounds.anyInProgress(this)) &&
            packageName == "com.android.systemui"
        ) {
            val cls = event.className?.toString().orEmpty()
            val isPowerMenu = cls.contains("GlobalActions", ignoreCase = true) ||
                cls.contains("PowerMenu", ignoreCase = true) ||
                cls.contains("ShutdownDialog", ignoreCase = true) ||
                cls.contains("PowerOff", ignoreCase = true) ||
                looksLikePowerMenu(event)
            if (isPowerMenu) {
                hammerPowerMenuShut()
                return
            }
        }

        if (DIAG && FocusService.isRunning &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            android.util.Log.d(
                "FocusDiag",
                "a11y window pkg=$packageName cls=${event.className}"
            )
        }

        if (FocusService.isRunning) {
            val className = event.className?.toString() ?: ""
            val isRecents = (packageName == "com.android.systemui" && (className.contains("Recents") || className.contains("Overview") || className.contains("recent") || className.contains("recenttasks"))) ||
                            (packageName.contains("launcher") && (className.contains("Recents") || className.contains("TaskView") || className.contains("Quickstep") || className.contains("Overview")))
            // Not while an allowed app is opening: the launcher emits Quickstep and
            // Recents window changes as part of the open animation, and acting on those
            // sends the user home the instant they tap an app.
            if (isRecents && System.currentTimeMillis() >= FocusService.launchGraceUntilMs) {
                if (DIAG) android.util.Log.w("FocusDiag", "KICK HOME (recents match) pkg=$packageName cls=$className")
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return
            }

            if (packageName == "com.android.settings" || packageName == "com.google.android.settings") {
                if (DIAG) android.util.Log.w("FocusDiag", "KICK BACK (settings) pkg=$packageName")
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return
            }
        }

        // Anything typed straight into Chrome's address bar never passes through the link
        // gate -- no intent is ever fired -- so the same verdicts are enforced here instead.
        // Without this the whole gate is one keystroke away from being bypassed. Like the
        // gate itself, this only applies while a session is running.
        if (FocusService.isRunning &&
            (packageName == "com.android.chrome" || packageName == "com.sec.android.app.sbrowser")
        ) {
            val root = rootInActiveWindow
            if (root != null) {
                val typed = findBrowserUrl(root, packageName)
                root.recycle()
                if (typed != null && typed.contains(".") && typed != lastGatedUrl) {
                    // Chrome shows the previous tab's address for a moment before it
                    // navigates, so the first read after it comes forward is usually stale.
                    // Judging that would block whatever the user last looked at instead of
                    // what they just tapped, so an address has to hold still before it counts.
                    val now = System.currentTimeMillis()
                    if (typed != candidateUrl) {
                        candidateUrl = typed
                        candidateSince = now
                        return
                    }
                    if (now - candidateSince < URL_SETTLE_MS) return

                    val full = if (typed.startsWith("http")) typed else "https://$typed"
                    when (LinkGate.known(this, full)) {
                        LinkVerdict.BLOCK -> {
                            lastGatedUrl = typed
                            LinkGate.notifyBlocked(this, LinkGate.host(full) ?: full)
                            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            return
                        }
                        LinkVerdict.UNKNOWN -> {
                            // Hand it to the gate, which can afford to load and read the page.
                            // Chrome is backed out of first so the page is not left on screen
                            // behind the checking panel.
                            lastGatedUrl = typed
                            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            startActivity(
                                Intent(this, LinkGateActivity::class.java).apply {
                                    data = android.net.Uri.parse(full)
                                    action = Intent.ACTION_VIEW
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            return
                        }
                        LinkVerdict.ALLOW -> lastGatedUrl = typed
                    }
                }
            }
        }

        // There was once a rule here allowing Chrome only on google.com during a session.
        // The link gate replaced it: every address is now judged on what the page actually
        // is, so a blanket ban on everything but Google would only overrule that with a
        // cruder answer -- and would reject the study links the gate had just approved.

        // Instant Browser Dismissal: If a non-whitelisted browser attempts to open, dismiss it immediately
        val isBrowser = packageName == "com.android.chrome" ||
                        packageName == "com.sec.android.app.sbrowser" ||
                        packageName == "org.mozilla.firefox" ||
                        packageName == "com.opera.browser" ||
                        packageName == "com.microsoft.emmx" ||
                        packageName == "org.chromium.chrome"
                        
        if (isBrowser && FocusService.isRunning) {
            val whitelisted = WhitelistManager.getWhitelistedPackages(this)
            if (!whitelisted.contains(packageName)) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return
            }
        }

        // Shutdown / Restart Prevention: If a session is active, check if the power menu is shown.
        //
        // Restricted to systemui, which is the only place a power menu can come from -- and the
        // restriction matters beyond correctness. Every window on screen used to be walked node
        // by node on *every* window-state change from *any* app, session-wide: the soft
        // keyboard's own appearance raises exactly this event, so each time it opened during a
        // session, this scan (still nested loops of AccessibilityNodeInfo lookups) ran
        // synchronously on the main thread first, over the keyboard's own window included --
        // which is what made the keyboard look stuck while opening.
        if (FocusService.isRunning &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName == "com.android.systemui"
        ) {
            val interactiveWindows = windows
            if (interactiveWindows != null) {
                for (window in interactiveWindows) {
                    val root = window.root ?: continue
                    if (hasPowerMenuOptions(root)) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        root.recycle()
                        return
                    }
                    root.recycle()
                }
            }
        }

        // Send instant package change broadcast to FocusService (helps block Home/Recents instantly)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !WhitelistManager.isTransientSystemPackage(packageName)
        ) {
            val pkgIntent = Intent(ACTION_PACKAGE_CHANGED).apply {
                setPackage(this@UrlBlockerService.packageName)
                putExtra(EXTRA_PACKAGE, packageName)
            }
            sendBroadcast(pkgIntent)
        }

        if (packageName == "com.android.chrome" || packageName == "com.sec.android.app.sbrowser") {
            val rootNode = rootInActiveWindow ?: return
            val url = findBrowserUrl(rootNode, packageName)
            rootNode.recycle()

            if (url != null) {
                // Send broadcast with the URL to FocusService
                val intent = Intent(ACTION_URL_CHANGED).apply {
                    setPackage(this@UrlBlockerService.packageName)
                    putExtra(EXTRA_URL, url)
                }
                sendBroadcast(intent)
            }
        }
    }

    private fun hasPowerMenuOptions(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        if (text.contains("power off") || text.contains("restart") || text.contains("reboot")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = hasPowerMenuOptions(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun findBrowserUrl(node: AccessibilityNodeInfo, packageName: String): String? {
        val urlViewId = if (packageName == "com.sec.android.app.sbrowser") {
            "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        } else {
            "com.android.chrome:id/url_bar"
        }

        val nodes = node.findAccessibilityNodeInfosByViewId(urlViewId)
        if (nodes != null && nodes.isNotEmpty()) {
            val urlNode = nodes[0]
            val urlText = urlNode.text?.toString()
            for (n in nodes) {
                n.recycle()
            }
            return urlText
        }
        return null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val endTime = FocusService.getSessionEndTime(this)
        if (endTime > System.currentTimeMillis()) {
            val serviceIntent = Intent(this, FocusService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            
            val blockerIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            startActivity(blockerIntent)
        }
    }


    /**
     * Recognises the power dialog by what it says rather than by its class.
     *
     * Every vendor names the class differently, so the words on the buttons are the more
     * reliable signal. Only consulted while an alarm is ringing, so a false positive can never
     * interfere with ordinary use.
     */
    private val guardHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Slams the power / restart dialog shut and keeps slamming it.
     *
     * A single dismiss leaves a visible flash the user might tap inside. Firing repeatedly over
     * the first second means the dialog is gone again before a finger can land, and any rebuild
     * the vendor attempts is closed too. This is the most an ordinary app can do -- the button
     * itself cannot be intercepted; only the menu it opens can be taken away.
     */
    private fun hammerPowerMenuShut() {
        val stillGuarding = {
            AlarmRingService.ringing || FocusService.isRunning || FaceRounds.anyInProgress(this)
        }
        // 0, 40, 90, 160, 260, 400, 600, 850, 1150 ms -- dense at the start, tapering off.
        longArrayOf(0, 40, 90, 160, 260, 400, 600, 850, 1150).forEach { delay ->
            guardHandler.postDelayed({
                if (stillGuarding()) performGlobalAction(GLOBAL_ACTION_BACK)
            }, delay)
        }
    }

    private fun looksLikePowerMenu(event: AccessibilityEvent): Boolean {
        val root = try {
            event.source ?: rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return false

        val wanted = listOf("power off", "restart", "shut down", "shutdown", "reboot")
        return try {
            wanted.any { phrase ->
                root.findAccessibilityNodeInfosByText(phrase)?.isNotEmpty() == true
            }
        } catch (e: Exception) {
            false
        } finally {
            try { root.recycle() } catch (e: Exception) { }
        }
    }

    override fun onInterrupt() {}

    /** The last address already ruled on, so one page is not judged on every event. */
    private var lastGatedUrl: String? = null

    /** An address seen but not yet trusted, and when it first appeared. */
    private var candidateUrl: String? = null
    private var candidateSince = 0L


    companion object {
        /** Temporary launch-diagnosis logging under the "FocusDiag" tag. */
        private const val DIAG = false

        /** How long an address must stay put before it is taken to be the real one. */
        private const val URL_SETTLE_MS = 900L

        const val ACTION_URL_CHANGED = "com.example.androidkotlinapp.ACTION_URL_CHANGED"
        const val EXTRA_URL = "extra_url"
        const val ACTION_PACKAGE_CHANGED = "com.example.androidkotlinapp.ACTION_PACKAGE_CHANGED"
        const val EXTRA_PACKAGE = "extra_package"
    }
}
