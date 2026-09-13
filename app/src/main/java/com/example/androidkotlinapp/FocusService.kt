package com.example.androidkotlinapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Looper
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val timerIntervalMs = 1000L
    private val blockCheckIntervalMs = 200L

    private val motivationalQuotes = listOf(
        "Secure that dream placement! Keep grinding! 💼🚀",
        "Your future self will thank you for this effort. Prep hard! 🎓🔥",
        "Distractions cost placements. Stay sharp, stay focused! 🎯💪",
        "Stay focused, you're doing amazing! 💪🔥",
        "Focus is your superpower. Eliminate distractions! 🚀✨",
        "One step closer to your goals. Keep pushing! 🏆🌟"
    )

    /** Title and body pairs for the one notification the user is meant to notice. */
    private val sessionCompleteMessages = listOf(
        "Focus session complete 🎉" to
            "Your block is lifted -- every app is yours again. That time is banked.",
        "Time's up. You made it 🏆" to
            "The session ran its course and the blocker is off. Go take a break, you earned it.",
        "Block lifted 🔓" to
            "Focus session finished. Nothing is held back any more -- well done for sitting it out.",
        "Session done ✅" to
            "The lock is off and your phone is back to normal. That was time well spent."
    )

    private var currentSessionQuote = motivationalQuotes.random()
    private var totalSeconds = 25 * 60
    private var isOverlayAdded = false
    private var activePackageName: String? = null
    private var isBlockedUrlActive = false

    /**
     * How many polls in a row have reported a blocked app. The overlay only appears once
     * this reaches [BLOCKED_CHECKS_BEFORE_BLOCK], so a single bad foreground reading -- which
     * happens constantly while a heavy app is starting up -- can no longer kill that app.
     */
    private var consecutiveBlockedChecks = 0

    /** Whitelist lookups hit SharedPreferences and PackageManager, far too costly to redo every poll. */
    private var cachedWhitelist: Set<String> = emptySet()
    private var cachedWhitelistAtMs = 0L

    /** Last usage-stats reading, and when it was taken. */
    private var cachedForeground: String? = null
    private var cachedForegroundAtMs = 0L

    /** Calendar day the habit checker last settled, so a day rollover is only handled once. */
    private var lastHabitCheckDate = ""

    /** The floating pill that follows the focus session across every app. */
    private var islandView: View? = null
    private var islandLabel: TextView? = null
    private var islandGlyph: TextView? = null
    /** Counts ticks so the glyph turns now and then rather than forever. */
    private var islandSpinTick = 0

    /** Counts ticks between checks that the standing countdown is still on screen. */
    private var noticeCheckTick = 0

    /** Focused seconds not yet written down, and the day they belong to. */
    private var accrualDay = ""
    private var accrualSeconds = 0

    private val showOverlayRunnable = Runnable {
        showOverlay()
    }

    private val blockerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UrlBlockerService.ACTION_URL_CHANGED -> {
                    val url = intent.getStringExtra(UrlBlockerService.EXTRA_URL)
                    if (url != null) {
                        val isBlocked = url.contains("youtube.com") ||
                                url.contains("facebook.com") ||
                                url.contains("instagram.com") ||
                                url.contains("tiktok.com")
                        if (isBlocked != isBlockedUrlActive) {
                            isBlockedUrlActive = isBlocked
                            manageOverlayState()
                        }
                    }
                }
                UrlBlockerService.ACTION_PACKAGE_CHANGED -> {
                    val pkg = intent.getStringExtra(UrlBlockerService.EXTRA_PACKAGE)
                    // A floating overlay is not an app switch, so it must not become the
                    // sticky hint -- it never clears on its own once it does.
                    if (pkg != null && pkg != activePackageName &&
                        !WhitelistManager.isTransientSystemPackage(pkg)
                    ) {
                        activePackageName = pkg
                        if (pkg != "com.android.chrome" && pkg != "com.sec.android.app.sbrowser") {
                            isBlockedUrlActive = false
                        }
                        manageOverlayState()
                    }
                }
            }
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            val savedEndTime = getSessionEndTime(this@FocusService)
            val now = System.currentTimeMillis()
            if (savedEndTime > now) {
                totalSeconds = ((savedEndTime - now) / 1000).toInt()
            } else {
                totalSeconds = 0
            }

            // A habit is owed every calendar day. If the session ran past midnight, settle
            // the day that just ended before moving on.
            val today = HabitStats.today()
            if (lastHabitCheckDate.isNotEmpty() && lastHabitCheckDate != today) {
                settleMissedDay(lastHabitCheckDate)
            }
            lastHabitCheckDate = today

            if (totalSeconds <= 0) {
                // The clock ran out, but the session is not over until any day that closed
                // while it ran has been paid for.
                if (collectDebtIntoSession() > 0) {
                    handler.postDelayed(this, timerIntervalMs)
                    return
                }

                try {
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    val r = android.media.RingtoneManager.getRingtone(this@FocusService, uri)
                    r?.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                BlockerActivity.isSessionCompleted = true
                showSessionCompleteNotification()
                stopSelf()
                return
            }

            remainingSeconds = totalSeconds

            // Focus sessions live inside the block session, so this is the clock that moves
            // them along -- it is the only thing certain to be awake for the whole stretch.
            // Counted before the phase is allowed to move on, so the last second of a focus
            // stretch is credited to focus rather than to the break that follows it.
            accrueFocusSecond()

            Pomodoro.tick(this@FocusService)
            updateIsland()

            // A swiped-away countdown is put back, but only every few seconds -- checking on
            // every tick would trade one cheap notification for a constant one.
            noticeCheckTick++
            if (noticeCheckTick % NOTICE_CHECK_EVERY_TICKS == 0) {
                Pomodoro.keepNoticeAlive(this@FocusService)
            }

            // Track app usage limits
            val foregroundPkg = currentForegroundPackage()
            if (foregroundPkg != null) {
                // Checked every tick, whatever the session length. This used to be gated on
                // the session being 24 hours or longer, which is a length nobody runs -- so
                // the reset never fired and yesterday's minutes were still counted against
                // today, until an app used once was permanently out of time.
                WhitelistManager.checkAndResetDailyLimits(this@FocusService)

                val limitMinutes = WhitelistManager.getAppUsageLimitMinutes(this@FocusService, foregroundPkg)
                if (limitMinutes > 0) {
                    val usedSeconds = WhitelistManager.getAppUsedSeconds(this@FocusService, foregroundPkg)
                    val newUsedSeconds = usedSeconds + 1
                    WhitelistManager.setAppUsedSeconds(this@FocusService, foregroundPkg, newUsedSeconds)

                    val total = limitMinutes * 60
                    val left = total - newUsedSeconds

                    // Warned before it happens rather than after. Being thrown out of an app
                    // mid-sentence with no notice reads as a crash, not as a limit.
                    if (left == 300 || left == 60) {
                        AppLimitNotifier.warn(this@FocusService, foregroundPkg, left)
                    }

                    if (newUsedSeconds >= total) {
                        AppLimitNotifier.spent(this@FocusService, foregroundPkg, limitMinutes)
                        manageOverlayState()
                    }
                }
            }

            
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val tickIntent = Intent(BlockerActivity.ACTION_TIMER_TICK).apply {
                setPackage(packageName)
                putExtra("minutes", minutes)
                putExtra("seconds", seconds)
            }
            sendBroadcast(tickIntent)

            handler.postDelayed(this, timerIntervalMs)
        }
    }

    private val blockCheckRunnable = object : Runnable {
        override fun run() {
            manageOverlayState()
            handler.postDelayed(this, blockCheckIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(UrlBlockerService.ACTION_URL_CHANGED)
            addAction(UrlBlockerService.ACTION_PACKAGE_CHANGED)
        }
        ContextCompat.registerReceiver(this, blockerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentSessionQuote = motivationalQuotes.random()
        val savedEndTime = getSessionEndTime(this)
        val now = System.currentTimeMillis()
        
        if (savedEndTime > now) {
            totalSeconds = ((savedEndTime - now) / 1000).toInt()
            FocusService.initialSeconds = getInitialSeconds(this)
        } else {
            val durationMinutes = intent?.getIntExtra("duration_minutes", 25) ?: 25

            // Days missed while no session was running have been sitting on the books. This
            // is where they are collected: the debt is added to the session about to start,
            // and marked served so it is never charged twice.
            HabitDebt.sweep(this)
            val owed = HabitDebt.collect(this)

            totalSeconds = durationMinutes * 60 + owed
            saveSessionEndTime(this, now + (totalSeconds * 1000L))
            saveInitialSeconds(this, totalSeconds)
            FocusService.initialSeconds = totalSeconds
        }
        
        updateTotalDurationString()
        BlockerActivity.isSessionCompleted = false

        lastHabitCheckDate = HabitStats.today()

        remainingSeconds = totalSeconds
        isRunning = true
        SessionLockdown.setUninstallBlocked(this, true)

        startForeground(NOTIFICATION_ID, buildNotification())

        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(blockCheckRunnable)
        handler.post(timerRunnable)
        handler.post(blockCheckRunnable)

        return START_STICKY
    }

    /**
     * Adds one second to today's total, if a focus stretch is what is running.
     *
     * The seconds are held in memory and written in batches. A record of how long someone
     * concentrated is not worth a disk write every second of it, and the batch is flushed the
     * moment the day changes so a session running over midnight still splits correctly.
     */
    private fun accrueFocusSecond() {
        val pomo = Pomodoro.state(this)
        if (!pomo.running || pomo.phase != PomoPhase.FOCUS) {
            flushAccrual()
            return
        }

        val today = FocusStats.dayKey(System.currentTimeMillis())
        if (today != accrualDay) {
            flushAccrual()
            accrualDay = today
        }
        accrualSeconds++
        if (accrualSeconds >= ACCRUAL_FLUSH_SECONDS) flushAccrual()
    }

    private fun flushAccrual() {
        if (accrualSeconds > 0 && accrualDay.isNotEmpty()) {
            FocusStats.addSeconds(this, accrualDay, accrualSeconds)
        }
        accrualSeconds = 0
    }

    /**
     * Keeps the floating countdown in step with the focus session.
     *
     * The pill is a window rather than a notification because the point of it is to be there
     * while the user is inside another app -- a notification would be behind a swipe, and by
     * then they have already lost the thread.
     */
    private fun updateIsland() {
        val pomo = Pomodoro.state(this)
        if (!pomo.onScreen) {
            hideIsland()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) return

        if (islandView == null) showIsland()
        islandLabel?.text = Pomodoro.clock(pomo.remainingSeconds)
        islandLabel?.alpha = if (pomo.paused) 0.45f else 1f
        // The colour says which half of the cycle is running without spelling it out, and
        // matches the ring on the home screen so the two never disagree.
        islandLabel?.setTextColor(
            android.graphics.Color.parseColor(
                when {
                    pomo.paused -> ISLAND_PAUSED_COLOUR
                    pomo.phase == PomoPhase.BREAK -> ISLAND_BREAK_COLOUR
                    else -> ISLAND_FOCUS_COLOUR
                }
            )
        )

        // A single turn every so often, then stillness. A glyph spinning without pause is
        // movement in the corner of the eye all session long, which is the opposite of what
        // this is for.
        if (pomo.running) {
            islandSpinTick++
            if (islandSpinTick % ISLAND_SPIN_EVERY_TICKS == 0) {
                islandGlyph?.animate()?.rotationBy(360f)?.setDuration(900)?.start()
            }
        }
    }

    private fun showIsland() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(android.graphics.Color.BLACK)
                    setStroke(dp(1), android.graphics.Color.parseColor("#332196F3"))
                }
            }

            islandGlyph = TextView(this).apply {
                text = "\u231B"
                textSize = 12f
            }
            row.addView(islandGlyph)

            // The camera is punched through the middle of the status bar, so the clock is
            // held this far off the glyph and the lens falls in the space between them.
            row.addView(View(this), LinearLayout.LayoutParams(dp(ISLAND_GAP_DP), dp(1)))

            islandLabel = TextView(this).apply {
                setTextColor(android.graphics.Color.parseColor(ISLAND_FOCUS_COLOUR))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(islandLabel)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                // Neither focusable nor touchable. It sits in the status bar strip, which
                // SystemUI owns and which swallows every touch anyway, so it is something to
                // read rather than press -- and it must never take a tap meant for the app
                // underneath it.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                // Sits in the status bar strip, where the phone puts its own pill.
                y = dp(6)
            }

            wm.addView(row, params)
            islandView = row
        } catch (e: Exception) {
            e.printStackTrace()
            islandView = null
            islandLabel = null
        }
    }

    private fun hideIsland() {
        islandSpinTick = 0
        val view = islandView ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        islandView = null
        islandLabel = null
        islandGlyph = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Whatever was focused in the last few seconds is kept, not lost with the service.
        flushAccrual()
        hideIsland()
        // Focus sessions run inside a block session and end with it. Left alone the phase
        // would stay FOCUS for ever, because the tick that retires it dies with this service.
        Pomodoro.stop(this)
        isRunning = false
        remainingSeconds = 0
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(blockCheckRunnable)
        unregisterReceiver(blockerReceiver)
        clearSessionEndTime(this)
        removeOverlay()
        // Only lifted if the alarm isn't separately ringing right now -- that still needs the
        // app to stay unremovable on its own.
        if (!AlarmRingService.ringing) {
            SessionLockdown.setUninstallBlocked(this, false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==========================================
    // Habit punishment
    // ==========================================

    private fun updateTotalDurationString() {
        val initialMins = FocusService.initialSeconds / 60
        val hrs = initialMins / 60
        val remainingMins = initialMins % 60

        BlockerActivity.totalFocusDurationString = if (hrs > 0) {
            if (remainingMins > 0) "$hrs hr $remainingMins min" else "$hrs hr"
        } else {
            "$initialMins min"
        }
    }

    /**
     * Books [dateString] as a missed day if any habit went undone in it, and charges it to
     * the running session straight away.
     *
     * A day costs a day, once, however many habits were missed in it. The unique key on the
     * debt table is what makes this safe to call repeatedly: a day already booked is not
     * booked again, and nothing in the app ever gives a served day back.
     */
    private fun settleMissedDay(dateString: String): Int {
        return try {
            val db = FocusDatabaseHelper(this)
            val habits = db.getActiveHabits()
            if (habits.isEmpty()) return 0

            // A habit cannot be missed on a day before it existed.
            val owed = habits.filter { it.createdDate.isNotBlank() && it.createdDate <= dateString }
            if (owed.isEmpty()) return 0

            val done = db.getHabitIdsDoneOn(dateString)
            val missed = owed.filter { !done.contains(it.id) }
            if (missed.isEmpty()) return 0

            if (!db.recordMissedDay(dateString, missed.map { it.text })) return 0
            collectDebtIntoSession()
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /** Adds whatever is owed to the running session and marks it served. */
    private fun collectDebtIntoSession(): Int {
        val owed = HabitDebt.collect(this)
        if (owed > 0) {
            extendSession(owed)
            Log.d("FocusService", "Missed-day debt: +${owed}s added to the session")
        }
        return owed
    }

    /** Pushes the session end time out and keeps the timer/progress bookkeeping consistent. */
    private fun extendSession(extraSeconds: Int) {
        val now = System.currentTimeMillis()
        val currentEnd = getSessionEndTime(this)
        val base = if (currentEnd > now) currentEnd else now
        val newEnd = base + extraSeconds * 1000L
        saveSessionEndTime(this, newEnd)

        // Grow the session length too, otherwise the progress ring would read as
        // over-complete for the rest of the session.
        val newInitial = getInitialSeconds(this) + extraSeconds
        saveInitialSeconds(this, newInitial)
        FocusService.initialSeconds = newInitial

        totalSeconds = ((newEnd - now) / 1000).toInt()
        remainingSeconds = totalSeconds
        BlockerActivity.isSessionCompleted = false
        updateTotalDurationString()
    }

    private fun showPenaltyNotification(missedHabits: List<String>, addedSeconds: Int) {
        val hours = addedSeconds / 3600
        val title = if (missedHabits.size == 1) {
            "Habit missed — +1 hour added ⏳"
        } else {
            "${missedHabits.size} habits missed — +$hours hours added ⏳"
        }
        val body = missedHabits.joinToString(", ") + " — this cannot be undone."

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, PENALTY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setSound(NotificationSound.uri(this))
            .build()

        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // A distinct id per push so consecutive punishments do not overwrite each other.
            manager.notify(PENALTY_NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * The package the user is actually looking at.
     *
     * The accessibility hint lands first and is preferred because it is instant, but it is
     * only a hint: TYPE_WINDOW_STATE_CHANGED also fires for windows that float over the
     * current app -- vendor game bars, Play Services dialogs, toasts -- which are not an app
     * switch at all. When the hint names one of those, the usage-stats reading wins, since
     * that tracks the resumed activity and stays on the game.
     */
    private fun currentForegroundPackage(): String? {
        val hint = activePackageName
        if (hint == null || WhitelistManager.isTransientSystemPackage(hint)) {
            return sampledForegroundPackage() ?: hint
        }

        val sampled = sampledForegroundPackage()
        if (sampled == null || sampled == hint) return hint

        // The two disagree, so one of them is a window that never actually took the screen.
        // The Play Store licence check every game fires on launch is the worst offender: it
        // raises three window events in a row from com.android.vending while the game is the
        // resumed activity throughout, which is enough to reach the strike count and cover a
        // whitelisted game a second after it opens. Usage stats follow the resumed activity,
        // so they are the honest answer whenever they name an app the user may be in. If the
        // user really did open the Play Store, usage stats say so too and the hint stands.
        return if (isAllowed(sampled)) sampled else hint
    }

    /** Cached whitelist lookup; refreshed at most every [WHITELIST_CACHE_MS]. */
    private fun whitelistedPackages(): Set<String> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedWhitelist.isEmpty() || now - cachedWhitelistAtMs > WHITELIST_CACHE_MS) {
            cachedWhitelist = WhitelistManager.getWhitelistedPackages(this)
            cachedWhitelistAtMs = now
        }
        return cachedWhitelist
    }

    private fun isAllowed(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (whitelistedPackages().contains(packageName)) {
            return !WhitelistManager.isUsageLimitReached(this, packageName)
        }
        return WhitelistManager.isSystemPickerOrChooser(packageName)
    }

    private fun manageOverlayState() {
        if (isBlockedUrlActive) {
            handler.removeCallbacks(showOverlayRunnable)
            consecutiveBlockedChecks = BLOCKED_CHECKS_BEFORE_BLOCK
            showOverlay()
            return
        }

        // The user just tapped an allowed app. A heavy app -- a game especially -- spends
        // several seconds in splash screens, ad SDKs and loader activities before its own
        // package settles into the foreground. Sampling during that window used to report
        // some unrelated package, which got the game blocked and then force-stopped even
        // though it was whitelisted. Stand down until the launch has had time to land.
        if (System.currentTimeMillis() < launchGraceUntilMs) {
            handler.removeCallbacks(showOverlayRunnable)
            consecutiveBlockedChecks = 0
            removeOverlay()
            return
        }

        val foregroundPackage = currentForegroundPackage()

        if (DIAG) {
            Log.d(
                "FocusDiag",
                "verdict fg=$foregroundPackage (a11y=$activePackageName usage=${getForegroundPackage()}) " +
                    "allowed=${foregroundPackage?.let { isAllowed(it) }} " +
                    "transient=${foregroundPackage?.let { WhitelistManager.isTransientSystemPackage(it) }} " +
                    "strikes=$consecutiveBlockedChecks graceLeftMs=${launchGraceUntilMs - System.currentTimeMillis()}"
            )
        }

        if (foregroundPackage == this.packageName) {
            // Do nothing when our app is in the foreground.
            handler.removeCallbacks(showOverlayRunnable)
            consecutiveBlockedChecks = 0
            return
        }

        if (foregroundPackage != null && WhitelistManager.isTransientSystemPackage(foregroundPackage)) {
            // A helper package owning the foreground says nothing about what the user is
            // doing. Hold the current state rather than blocking or un-blocking on it.
            return
        }

        if (foregroundPackage != null && isAllowed(foregroundPackage)) {
            handler.removeCallbacks(showOverlayRunnable)
            consecutiveBlockedChecks = 0
            removeOverlay()
            return
        }

        // Require the same verdict several polls running before acting on it.
        consecutiveBlockedChecks++
        if (consecutiveBlockedChecks < BLOCKED_CHECKS_BEFORE_BLOCK) {
            return
        }

        // If the package is a launcher or home screen, delay overlay display to let transition animations settle
        val isLauncher = foregroundPackage != null && (
            foregroundPackage.contains("launcher") ||
            foregroundPackage.contains("trebuchet") ||
            foregroundPackage.contains("home") ||
            foregroundPackage.contains("carcustom")
        )
        handler.removeCallbacks(showOverlayRunnable)
        if (isLauncher) {
            handler.postDelayed(showOverlayRunnable, 200)
        } else {
            showOverlay()
        }
    }

    private fun showOverlay() {
        val foregroundPackage = currentForegroundPackage()

        // Already on top: nothing to raise, and relaunching every poll would fight the user's
        // own taps. This is the only guard worth having -- it asks what is actually in front.
        //
        // There used to be a second one here, skipping the launch whenever the blocker had
        // been raised before. That flag only recorded that it was once launched, never that it
        // was still showing, so the first time another app covered the blocker the flag stayed
        // true and every app after it opened unchallenged. It is gone.
        if (foregroundPackage == this.packageName) {
            return
        }

        val blockerIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        try {
            startActivity(blockerIntent)
            isOverlayAdded = true

            val isSystemOrLauncher = foregroundPackage != null && (
                foregroundPackage.contains("launcher") ||
                foregroundPackage.contains("systemui") ||
                foregroundPackage.contains("googlequicksearchbox") ||
                foregroundPackage.contains("trebuchet") ||
                foregroundPackage.contains("home") ||
                WhitelistManager.CORE_SYSTEM_PACKAGES.contains(foregroundPackage)
            )
            // Force-stopping is reserved for apps that are genuinely blocked. Killing an
            // allowed app -- or anything at all during a launch grace period -- is what made
            // whitelisted games close on their own, so both are excluded outright.
            val mayForceClose = foregroundPackage != null &&
                foregroundPackage != this.packageName &&
                !isSystemOrLauncher &&
                !isAllowed(foregroundPackage) &&
                !WhitelistManager.isTransientSystemPackage(foregroundPackage) &&
                System.currentTimeMillis() >= launchGraceUntilMs

            if (mayForceClose && foregroundPackage != null) {
                handler.postDelayed({
                    // Re-check on the delayed hop: the app may have become allowed, or the
                    // user may have launched something legitimate in the meantime.
                    if (!isAllowed(foregroundPackage) &&
                        System.currentTimeMillis() >= launchGraceUntilMs
                    ) {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        am.killBackgroundProcesses(foregroundPackage)
                        Log.d("FocusService", "Force closed background process: $foregroundPackage")
                    }
                }, 500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        if (!isOverlayAdded) return

        try {
            val intent = Intent(BlockerActivity.ACTION_CLOSE_BLOCKER).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            isOverlayAdded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns the package that most recently came to the foreground.
     *
     * This reads the usage *event* stream rather than [UsageStatsManager.queryUsageStats].
     * The old query asked for INTERVAL_DAILY buckets over a ten second window: those buckets
     * summarise the whole day, and their `lastTimeUsed` has nothing to do with the requested
     * window, so the "foreground" app it reported was frequently just whatever had been used
     * most recently that day. Acting on that reading is what blocked and killed apps the user
     * had actually allowed. Resume events carry an exact timestamp and cannot drift this way.
     */
    /**
     * The usage-stats reading, held briefly between calls.
     *
     * The block check runs five times a second, and each raw read asks the system for every
     * usage event in the last stretch of a minute -- the single most expensive thing this
     * service does, repeated three hundred times a minute for an answer that changes when the
     * user switches app and not otherwise. The accessibility hint already reports switches the
     * instant they happen, so this only has to be fresh enough to catch what that misses.
     */
    private fun sampledForegroundPackage(): String? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedForegroundAtMs > FOREGROUND_CACHE_MS) {
            cachedForeground = getForegroundPackage()
            cachedForegroundAtMs = now
        }
        return cachedForeground
    }

    private fun getForegroundPackage(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - FOREGROUND_EVENT_WINDOW_MS
            val events = usageStatsManager.queryEvents(startTime, endTime)

            var latestPackage: String? = null
            var latestTimestamp = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    event.timeStamp >= latestTimestamp
                ) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            latestPackage
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * The notification Android demands of every foreground service, kept as close to absent
     * as the platform permits: minimum importance, no status bar icon, no sound, no heads-up
     * and no ticking countdown. It cannot be dropped altogether -- without it the OS kills
     * the service and the blocker stops.
     *
     * The content is static, so it is built once at [startForeground] and never refreshed.
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus session running")
            .setContentText(currentSessionQuote)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Posted once, as the clock runs out. Unlike the ongoing service notification -- which is
     * kept as close to invisible as Android allows -- this one is meant to be seen: it is the
     * only signal that the block has lifted, and it has to land before [stopSelf] tears the
     * service down. It carries its own id so stopping the service does not take it with it.
     *
     * Ending a session early, by the bypass or the developer broadcast, is a deliberate act
     * that needs no announcement, so only the clock running out reaches here.
     */
    private fun showSessionCompleteNotification() {
        val (title, body) = sessionCompleteMessages.random()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setSound(NotificationSound.uri(this))
            .build()

        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(COMPLETE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // An existing channel's importance can never be lowered from code, so the quiet
            // channel is a new id and the old noisy one is deleted rather than reconfigured.
            try {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Focus Session",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
            )
            listOf(LEGACY_PENALTY_CHANNEL_ID, LEGACY_COMPLETE_CHANNEL_ID).forEach {
                try {
                    manager.deleteNotificationChannel(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    COMPLETE_CHANNEL_ID,
                    "Session Complete",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tells you the moment a focus session ends and the block lifts"
                    setSound(
                        NotificationSound.uri(this@FocusService),
                        NotificationSound.attributes()
                    )
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    PENALTY_CHANNEL_ID,
                    "Habit Penalties",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when extra focus time is added for a missed habit"
                    setSound(
                        NotificationSound.uri(this@FocusService),
                        NotificationSound.attributes()
                    )
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "FocusServiceSilentChannel"

        /** The old IMPORTANCE_LOW channel, deleted on start so it stops showing up. */
        private const val LEGACY_CHANNEL_ID = "FocusServiceChannel"
        private const val PENALTY_CHANNEL_ID = "FocusPenaltyChannelV2"
        private const val COMPLETE_CHANNEL_ID = "FocusCompleteChannelV2"

        /** Replaced by the V2 channels above, which carry the app's own alert sound. */
        private const val LEGACY_PENALTY_CHANNEL_ID = "FocusPenaltyChannel"
        private const val LEGACY_COMPLETE_CHANNEL_ID = "FocusCompleteChannel"
        private const val NOTIFICATION_ID = 999
        private const val PENALTY_NOTIFICATION_ID = 4000
        private const val COMPLETE_NOTIFICATION_ID = 5000
        private const val PREFS_NAME = "focus_session_prefs"
        private const val KEY_SESSION_END_TIME = "session_end_time"
        private const val KEY_INITIAL_SECONDS = "initial_seconds"

        /** Consecutive blocked readings required before the overlay is shown. */
        /** Temporary launch-diagnosis logging under the "FocusDiag" tag. */
        private const val DIAG = false

        /** Blue while focusing, green on a break, grey when held -- as on the ring. */
        private const val ISLAND_FOCUS_COLOUR = "#FF64B5F6"
        private const val ISLAND_BREAK_COLOUR = "#FF81C784"
        private const val ISLAND_PAUSED_COLOUR = "#FF8A8A8A"

        /** Space between the island's glyph and its clock, so the camera falls between. */
        private const val ISLAND_GAP_DP = 38

        /** One turn of the island's glyph every this many seconds. */
        private const val ISLAND_SPIN_EVERY_TICKS = 12

        /** How often to check the standing countdown has not been swiped away. */
        private const val NOTICE_CHECK_EVERY_TICKS = 5

        /** Focused seconds held in memory before being written down. */
        private const val ACCRUAL_FLUSH_SECONDS = 15

        /**
         * Verdicts in a row before a blocked app is covered. One: the block should land the
         * moment the app does.
         *
         * This used to be three, as insurance against a transient reading -- a vendor game bar
         * or a licence check flashing past mid-launch and being mistaken for the app itself.
         * That is no longer what guards against it: those packages are recognised as transient
         * and the usage-stats cross-check overrules a hint that disagrees with the resumed
         * activity. The strike count was buying a second of delay for protection already
         * provided elsewhere.
         */
        private const val BLOCKED_CHECKS_BEFORE_BLOCK = 1
        private const val WHITELIST_CACHE_MS = 2000L
        /**
         * How far back a usage-stats read looks.
         *
         * A minute, and it has to be. Usage stats report the moment an app was resumed, not
         * which app is open now, so a window shorter than the time someone spends sitting in
         * an app returns nothing at all -- and the blocker goes blind to exactly the case it
         * exists for. Shrinking this to save battery let a blocked app run unchallenged; the
         * saving belongs in how often the query runs, not in how far back it looks.
         */
        private const val FOREGROUND_EVENT_WINDOW_MS = 60_000L

        /** How long a usage-stats reading is reused before another is taken. */
        private const val FOREGROUND_CACHE_MS = 800L

        /**
         * Window after tapping an allowed app during which blocking is suspended. Games can
         * take this long to get past splash screens and ad SDKs before their own package is
         * reported as foreground.
         */
        private const val LAUNCH_GRACE_MS = 8000L

        var isRunning by mutableStateOf(false)

        /**
         * Whether a block session is still owed, judged by the clock rather than by memory.
         *
         * [isRunning] is a flag in this process and nothing more: it is false the instant the
         * process dies and stays false until the service is started again. After a reboot --
         * especially on a phone whose vendor delays or blocks BOOT_COMPLETED -- the launcher
         * can be on screen well before that happens, and asking the flag then gives the wrong
         * answer about a session that is very much still running.
         *
         * The end time is on disk and survives all of it, so it is what anything user-facing
         * should ask.
         */
        fun hasLiveSession(context: Context): Boolean =
            getSessionEndTime(context) > System.currentTimeMillis()
        var remainingSeconds by mutableStateOf(0)
        var initialSeconds by mutableStateOf(25 * 60)

        @Volatile
        var launchGraceUntilMs = 0L
            private set

        /**
         * Call immediately before starting an allowed app so the blocker leaves it alone
         * while it loads. Without this the service can sample a loader/ad package mid-launch,
         * decide the app is blocked and force-stop it.
         */
        fun noteAllowedAppLaunch() {
            launchGraceUntilMs = System.currentTimeMillis() + LAUNCH_GRACE_MS
        }

        fun getInitialSeconds(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_INITIAL_SECONDS, 25 * 60)
        }

        fun saveInitialSeconds(context: Context, seconds: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_INITIAL_SECONDS, seconds).apply()
        }

        fun getSessionEndTime(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_SESSION_END_TIME, 0L)
        }

        fun saveSessionEndTime(context: Context, endTimeMs: Long) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_SESSION_END_TIME, endTimeMs).apply()
        }

        fun clearSessionEndTime(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SESSION_END_TIME).apply()
        }
    }
}
