package com.example.androidkotlinapp.ui.main

import com.example.androidkotlinapp.HabitDebt
import com.example.androidkotlinapp.SyncClient
import com.example.androidkotlinapp.HabitReminderScheduler
import com.example.androidkotlinapp.AppIconWithLimit
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.androidkotlinapp.AtomGlyph
import com.example.androidkotlinapp.AlarmGlyph
import com.example.androidkotlinapp.Alarms
import com.example.androidkotlinapp.AlarmScreen
import com.example.androidkotlinapp.BellGlyph
import com.example.androidkotlinapp.BulbGlyph
import com.example.androidkotlinapp.ReminderScreen
import com.example.androidkotlinapp.NextReminderChip
import com.example.androidkotlinapp.Tasks
import com.example.androidkotlinapp.AccountScreen
import com.example.androidkotlinapp.FocusStatsScreen
import com.example.androidkotlinapp.TaskReminderScheduler
import com.example.androidkotlinapp.TasksScreen
import com.example.androidkotlinapp.LocalAccount
import com.example.androidkotlinapp.Pomodoro
import com.example.androidkotlinapp.PomoPhase
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.example.androidkotlinapp.YouTubeScreen
import com.example.androidkotlinapp.MainActivity
import android.app.AppOpsManager
import androidx.activity.compose.BackHandler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.text.TextUtils
import android.widget.Toast
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Add
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import com.example.androidkotlinapp.FocusService
import com.example.androidkotlinapp.UrlBlockerService
import com.example.androidkotlinapp.WhitelistManager
import com.example.androidkotlinapp.BlockerActivity
import com.example.androidkotlinapp.FocusDatabaseHelper
import com.example.androidkotlinapp.DeveloperOptionsGuard
import com.example.androidkotlinapp.HabitRecord
import com.example.androidkotlinapp.HabitStats
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppInfoCache(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val componentName: ComponentName = ComponentName(packageName, ""),
    val user: UserHandle = Process.myUserHandle()
)

private var cachedAppList: List<AppInfoCache> = emptyList()

/** Dock entry that opens the habit tracker. */
/** Marks the one-time carry-over of the dock from preferences into the database. */
private const val SETTING_DOCK_MIGRATED = "dock_migrated_v1"

const val ACTION_HABITS = "custom.action.habits"
const val ACTION_YOUTUBE = "custom.action.youtube"
const val ACTION_TASKS = "custom.action.tasks"

/**
 * Which of the launcher's own screens sit on the home shelf, and in what order.
 *
 * The three (Streak, Target, Resource) start on the shelf but can be reordered or taken off it
 * entirely, exactly like a dock app. Anything removed is still reachable from the drawer, and
 * can be put back from there.
 */
object ShelfLayout {
    private const val PREFS = "home_screen_prefs"
    private const val KEY = "shelf_ids"
    val ALL = listOf(ACTION_HABITS, ACTION_TASKS, ACTION_YOUTUBE)

    fun get(context: android.content.Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return ALL
        return raw.split(",").filter { it in ALL }
    }

    fun set(context: android.content.Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, ids.joinToString(",")).apply()
    }
}

/** The Reminder app. Drawer only -- the home screen reaches it through the chip under FOCUS. */
const val ACTION_REMINDERS = "custom.action.reminders"

/** The Alarm app. Drawer only, like Reminder. */
const val ACTION_ALARM = "custom.action.alarm"

/** How many tiles the dock will hold. */
const val MAX_HOME_APPS = 5


/** True for the launcher's own screens, which are listed like apps but are not apps. */
private fun isOwnScreen(packageName: String) =
    packageName == ACTION_HABITS || packageName == ACTION_YOUTUBE ||
        packageName == ACTION_TASKS || packageName == ACTION_REMINDERS ||
        packageName == ACTION_ALARM

/** Dock key used by the removed Goals/task-manager feature, migrated to [ACTION_HABITS]. */
private const val LEGACY_ACTION_TASKS = "custom.action.tasks"

/** Dock key of the removed analytics screen. Stripped from saved docks, never re-added. */
private const val LEGACY_ACTION_ANALYTICS = "custom.action.analytics"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dbHelper = remember { FocusDatabaseHelper(context) }
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isAdminEnabled by remember { mutableStateOf(isAdminActive(context)) }
    var hasNotificationPermissionState by remember { mutableStateOf(hasNotificationPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    var showHabitsScreen by remember { mutableStateOf(false) }
    var showYouTubeScreen by remember { mutableStateOf(false) }
    var showTasksScreen by remember { mutableStateOf(false) }
    var showReminderScreen by remember { mutableStateOf(false) }
    var showAlarmScreen by remember { mutableStateOf(false) }
    var shelfIds by remember { mutableStateOf(ShelfLayout.get(context)) }
    var showExitSheet by remember { mutableStateOf(false) }

    // Bumped every time the app is opened. The launcher never really closes, so without this
    // the Reminder screen would come back exactly where it was left -- mid-edit, if that is
    // where the user last was, rather than at the list they just asked for.
    var reminderSession by remember { mutableStateOf(0) }

    // The soonest reminder that has not passed, or null. Recomputed when the Time app closes
    // and once a day is enough otherwise -- the text only changes at midnight.
    var reminderTick by remember { mutableStateOf(0) }
    val upcoming = remember(showReminderScreen, reminderTick) {
        Tasks.upcomingReminders(context)
    }

    // Which one has the slot this time round. Only one chip fits, so rather than picking a
    // winner and burying the rest, the home screen shows the next one each time it is come
    // back to -- leave the launcher, return, and a different reminder is waiting.
    // Counted by the activity on every return to the launcher, plus once for each of this
    // app's own screens closing -- those never pause the activity, so they would not otherwise
    // register as coming back.
    var innerReturns by remember { mutableStateOf(0) }
    LaunchedEffect(showTasksScreen, showReminderScreen, showHabitsScreen, showYouTubeScreen) {
        if (!showTasksScreen && !showReminderScreen && !showHabitsScreen && !showYouTubeScreen) {
            innerReturns++
        }
    }
    val rotation = MainActivity.homeReturnCount + innerReturns
    val nextReminder = upcoming.getOrNull(
        if (upcoming.isEmpty()) 0 else rotation % upcoming.size
    )
    val alsoWaiting = (upcoming.size - 1).coerceAtLeast(0)

    // Re-read when the day turns, so "Tomorrow" becomes "Today" on a phone left sitting on the
    // home screen. Sleeps until the next midnight rather than polling.
    LaunchedEffect(reminderTick) {
        val now = System.currentTimeMillis()
        val midnight = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 2)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        kotlinx.coroutines.delay((midnight - now).coerceAtLeast(1_000L))
        reminderTick++
    }

    // Booked here rather than when a goal is made: one alarm serves every goal and reminder,
    // and re-booking it on each launch means a reboot or a cleared alarm cannot end the series.
    LaunchedEffect(Unit) { TaskReminderScheduler.schedule(context) }
    // Both daily rounds are re-booked on every launch, and the sweep runs here too so a day
    // missed while the phone was off is on the books before the next session can start.
    LaunchedEffect(Unit) {
        HabitReminderScheduler.schedule(context)
        HabitDebt.sweep(context)

        // Also rolled over here, not only mid-session. Go a day without starting a session and
        // the counters would otherwise still be holding yesterday's minutes when you looked at
        // the dock.
        WhitelistManager.checkAndResetDailyLimits(context)

        // Alarms live in the database, but only AlarmManager can actually ring one. Re-booked
        // on every launch so a row that lost its system alarm -- a reboot, a force-stop, a
        // vendor cleaner -- is armed again rather than quietly never going off.
        Alarms.rescheduleAll(context)

        // A session that outlived the process gets its service back. BOOT_COMPLETED is the
        // intended route, but this phone's vendor is free to delay or withhold it, and the
        // launcher being on screen is proof enough that the device is up.
        // The mirror runs off the main thread and its failures are its own business: the app
        // is already working from the local database, and a copy a minute behind is not worth
        // interrupting anyone about.
        if (SyncClient.isLinked(context)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    SyncClient.pushIfDue(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (!FocusService.isRunning && FocusService.hasLiveSession(context)) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    android.content.Intent(context, FocusService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    var showPomodoroSheet by remember { mutableStateOf(false) }
    var showFocusStats by remember { mutableStateOf(false) }

    // The link gate parks YouTube links here rather than opening a browser on them.
    LaunchedEffect(MainActivity.pendingYouTubeLink) {
        if (MainActivity.pendingYouTubeLink != null) showYouTubeScreen = true
    }

    LaunchedEffect(MainActivity.openHabitsScreenAction) {
        if (MainActivity.openHabitsScreenAction) {
            MainActivity.openHabitsScreenAction = false
            showHabitsScreen = true
        }
    }

    // Where a tapped notification lands. Consumed once, so backing out returns to the home
    // screen rather than re-opening the same screen on the next recomposition.
    LaunchedEffect(MainActivity.pendingRoute) {
        when (MainActivity.pendingRoute) {
            MainActivity.ROUTE_HABITS -> showHabitsScreen = true
            MainActivity.ROUTE_TARGET -> showTasksScreen = true
            MainActivity.ROUTE_REMINDER -> {
                reminderSession++
                showReminderScreen = true
            }
            MainActivity.ROUTE_RESOURCE -> showYouTubeScreen = true
            MainActivity.ROUTE_ALARM -> showAlarmScreen = true
            else -> return@LaunchedEffect
        }
        MainActivity.pendingRoute = null
    }

    var selectedDuration by remember { mutableStateOf(0) } // Duration in minutes
    var showCustomTimeSheet by remember { mutableStateOf(false) }
    var showStartConfirmationSheet by remember { mutableStateOf(false) }
    var isEditingAllowedApps by remember { mutableStateOf(false) }

    var selectedDaysVal by remember { mutableStateOf(0) }
    var selectedHoursVal by remember { mutableStateOf(0) }
    var selectedMinutesVal by remember { mutableStateOf(0) }

    // Memory cached app list loaded in background
    var installedApps by remember { mutableStateOf<List<AppInfoCache>>(cachedAppList) }
    var isLoadingApps by remember { mutableStateOf(cachedAppList.isEmpty()) }

    // Launcher / App Drawer states
    var showFocusSetup by remember { mutableStateOf(false) }
    var showFocusBottomSheet by remember { mutableStateOf(false) }
    var focusSheetStep by remember { mutableStateOf(0) } // 0 = pick duration, 2 = options/whitelist

    // Every habit that exists is in force for the session -- there is nothing to select.
    // A session simply cannot start until at least one habit exists.
    var habitsList by remember { mutableStateOf(emptyList<HabitRecord>()) }
    var showHabitEditor by remember { mutableStateOf(false) }
    var habitBeingEdited by remember { mutableStateOf<HabitRecord?>(null) }
    var showUsbDebuggingBlocker by remember { mutableStateOf(false) }

    fun reloadHabits() {
        habitsList = dbHelper.getAllHabits()
    }

    var focusMessage by remember { mutableStateOf("") }
    var showAppWhitelistSelector by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("25") }
    var showAppDrawer by remember { mutableStateOf(false) }
    var drawerSearchQuery by remember { mutableStateOf("") }

    // Only meaningful mid-session: 0 is what the user allowed, 1 is what the app allows by
    // default. Reset whenever the drawer closes so it always opens on the chosen apps.
    var drawerTab by remember { mutableStateOf(0) }

    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now)
            kotlinx.coroutines.delay(1000)
        }
    }
    var isDefaultLauncher by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val currentDefaultHomePackage = resolveInfo?.activityInfo?.packageName
            isDefaultLauncher = (currentDefaultHomePackage == context.packageName)
            kotlinx.coroutines.delay(2000)
        }
    }
    var homeScreenPackages by remember { mutableStateOf<List<String>>(emptyList()) }

    fun saveHomeApps(packages: List<String>) {
        homeScreenPackages = packages
        FocusDatabaseHelper(context).setHomeApps(packages)
    }

    LaunchedEffect(installedApps) {
        if (installedApps.isNotEmpty()) {
            val db = FocusDatabaseHelper(context)
            val prefs = context.getSharedPreferences("home_screen_prefs", Context.MODE_PRIVATE)

            // The dock used to live in preferences. It is carried over once, keyed on a flag
            // in the database rather than on the table being empty -- an empty dock is a valid
            // dock, and treating it as "not migrated yet" would resurrect old tiles every
            // launch for anyone who had deliberately cleared theirs.
            if (db.getSetting(SETTING_DOCK_MIGRATED) == null) {
                val legacy = prefs.getString("home_apps", null)
                if (legacy != null) {
                    // Old installs stored a Goals key and an analytics key. The first is
                    // pointed at the habit tracker so the slot survives the feature swap; the
                    // second is dropped, along with the retired Block tile, since neither
                    // opens anything.
                    val migrated = legacy.replace(LEGACY_ACTION_TASKS, ACTION_HABITS)
                        .split(",")
                        .filter {
                            it.isNotEmpty() &&
                                it != LEGACY_ACTION_ANALYTICS &&
                                it != "custom.action.focus"
                        }
                    db.setHomeApps(migrated)
                }
                db.putSetting(SETTING_DOCK_MIGRATED, "1")
            }

            // A fresh install starts with nothing. Every screen this launcher owns is
            // reachable without a tile -- the ring starts a block session, the drawer holds
            // the rest -- so the dock holds only what the user puts there.
            homeScreenPackages = db.getHomeApps()
        }
    }

    // YouTube's own launcher icon, borrowed for the tile. Null if YouTube is not installed,
    // in which case the tile falls back to a drawn glyph.
    val youTubeIcon = remember(installedApps) {
        installedApps.firstOrNull { it.packageName == "com.google.android.youtube" }?.icon
    }

    val homeScreenApps = remember(homeScreenPackages, installedApps, FocusService.isRunning) {
        val list = mutableListOf<AppInfoCache>()
        homeScreenPackages.forEach { pkg ->
            if (pkg == "custom.action.focus") {
                // Nothing to add. Starting a block session is what the ring in the middle of
                // the screen is for, and a tile in the dock that opened the same sheet was a
                // second door onto one room.
            } else if (pkg == ACTION_HABITS) {
                list.add(AppInfoCache(name = "Streak", packageName = ACTION_HABITS, icon = null))
            } else if (pkg == ACTION_TASKS) {
                list.add(AppInfoCache(name = "Target", packageName = ACTION_TASKS, icon = null))
            } else if (pkg == ACTION_REMINDERS) {
                list.add(AppInfoCache(name = "Reminder", packageName = ACTION_REMINDERS, icon = null))
            } else if (pkg == ACTION_ALARM) {
                list.add(AppInfoCache(name = "Alarm", packageName = ACTION_ALARM, icon = null))
            } else if (pkg == ACTION_YOUTUBE) {
                // Wears YouTube's real launcher icon, borrowed from the installed app, so the
                // tile is recognisable at a glance. It opens this launcher's own screen -- the
                // YouTube app is never started from here.
                list.add(AppInfoCache(name = "Resource", packageName = ACTION_YOUTUBE, icon = null))
            } else {
                installedApps.firstOrNull { it.packageName == pkg }?.let { list.add(it) }
            }
        }
        list
    }

    /**
     * Moves a dock tile to where the tile currently sitting at [toVisualIndex] is.
     *
     * Reordering works in package names rather than positions because the two lists no
     * longer line up: the saved order keeps every tile, while the dock skips Focus during a
     * session and skips anything whose app has been uninstalled. Indexing one list with the
     * other's position would drag the wrong icon.
     */
    fun moveHomeApp(fromPackage: String, toVisualIndex: Int) {
        val visible = homeScreenApps.map { it.packageName }
        val fromVisualIndex = visible.indexOf(fromPackage)
        val targetPackage = visible.getOrNull(toVisualIndex) ?: return
        if (fromVisualIndex < 0 || targetPackage == fromPackage) return

        val saved = homeScreenPackages.toMutableList()
        if (!saved.remove(fromPackage)) return
        val anchor = saved.indexOf(targetPackage)
        if (anchor < 0) return
        saved.add(if (toVisualIndex > fromVisualIndex) anchor + 1 else anchor, fromPackage)
        saveHomeApps(saved)
    }

    // Periodically update permission statuses
    LaunchedEffect(Unit) {
        while (true) {
            hasUsagePermission = hasUsageStatsPermission(context)
            hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
            isAdminEnabled = isAdminActive(context)
            hasNotificationPermissionState = hasNotificationPermission(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(showFocusBottomSheet, focusSheetStep) {
        if (showFocusBottomSheet) {
            reloadHabits()
        }
    }

    var triggerAppReload by remember { mutableStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                triggerAppReload++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Load installed apps and convert their icons to ImageBitmaps on a background thread for lag-free rendering
    LaunchedEffect(triggerAppReload) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val list = resolved.map { info ->
                val pkg = info.activityInfo.packageName
                val name = info.loadLabel(pm).toString()
                val icon = try {
                    val drawable = info.loadIcon(pm)
                    drawable.toBitmap(width = 120, height = 120).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                AppInfoCache(name, pkg, icon, android.content.ComponentName(pkg, info.activityInfo.name), android.os.Process.myUserHandle())
            }.distinctBy { it.packageName }
             .filter { 
                 it.packageName != context.packageName
             }
             .sortedWith(compareBy({ it.name.lowercase() }))
            
            installedApps = list
            cachedAppList = list
            isLoadingApps = false
        }
    }

    if (isEditingAllowedApps) {
        // Full screen Whitelisted Apps Grid
        AllowedAppsScreen(
            installedApps = installedApps,
            initialSelected = WhitelistManager.getWhitelistedPackages(context),
            onBack = { isEditingAllowedApps = false },
            onSave = { updatedSelection ->
                WhitelistManager.saveWhitelistedPackages(context, updatedSelection)
                isEditingAllowedApps = false
            }
        )
    } else {
        // Back handler to close app drawer on back button press
        BackHandler(enabled = showAppDrawer) {
            showAppDrawer = false
            drawerSearchQuery = ""
            drawerTab = 0
        }

        // Back handler to go back from Focus Setup to Home view
        BackHandler(enabled = showFocusSetup && !showAppDrawer) {
            showFocusSetup = false
        }

        // Back handler to consume back button on Home Screen so it does nothing
        BackHandler(enabled = !showFocusSetup && !showAppDrawer) {
            // Consume back press, do nothing
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (false) {
                // Launcher Home View
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Black, Color.Black),
                                radius = 1400f
                            )
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (dragAmount.y < -12f) {
                                        showAppDrawer = true
                                    }
                                }
                            )
                        }
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Time & Date Display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 80.dp)
                    ) {
                        val timeParts = currentTimeString.split(" ")
                        val timeDigits = timeParts.getOrNull(0) ?: ""
                        val amPmSuffix = timeParts.getOrNull(1) ?: ""

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = timeDigits,
                                color = Color.White,
                                fontSize = 58.sp,
                                fontWeight = FontWeight.ExtraLight,
                                letterSpacing = (-1.5).sp
                            )
                            if (amPmSuffix.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = amPmSuffix,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentDateString,
                            color = Color(0xFF64B5F6),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        if (!isDefaultLauncher) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(homeIntent)
                                    } catch (e: Exception) {
                                        // Fallback to home picker chooser
                                        val intent = Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_HOME)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1B1B)),
                                border = BorderStroke(1.dp, Color(0xFFE57373)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = "⚠️ SET DEFAULT LAUNCHER",
                                    color = Color(0xFFE57373),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom section containing swipe up label & dock
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        // Dock Apps Row
                        val appCount = homeScreenApps.size
                        val cardSize = when {
                            appCount <= 4 -> 52.dp
                            appCount == 5 -> 48.dp
                            else -> 42.dp
                        }
                        val colWidth = when {
                            appCount <= 4 -> 64.dp
                            appCount == 5 -> 58.dp
                            else -> 50.dp
                        }
                        val iconSize = when {
                            appCount <= 4 -> 36.dp
                            appCount == 5 -> 32.dp
                            else -> 28.dp
                        }
                        val spacing = when {
                            appCount <= 4 -> 20.dp
                            appCount == 5 -> 12.dp
                            else -> 6.dp
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            homeScreenApps.forEachIndexed { index, app ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(colWidth)
                                ) {
                                    var isMenuForThisAppExpanded by remember { mutableStateOf(false) }
                                    var offsetX by remember { mutableStateOf(0f) }

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                                        modifier = Modifier
                                            .size(cardSize)
                                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                                            .pointerInput(homeScreenApps) {
                                                detectDragGesturesAfterLongPress(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        offsetX += dragAmount.x
                                                    },
                                                    onDragEnd = {
                                                        val itemWidthPx = (colWidth.toPx() + spacing.toPx())
                                                        val shift = (offsetX / itemWidthPx).roundToInt()
                                                        if (shift != 0) {
                                                            val newIndex = (index + shift).coerceIn(0, homeScreenApps.lastIndex)
                                                            if (newIndex != index) {
                                                                val mutable = homeScreenPackages.toMutableList()
                                                                val pkg = mutable.removeAt(index)
                                                                mutable.add(newIndex, pkg)
                                                                saveHomeApps(mutable)
                                                            }
                                                        }
                                                        offsetX = 0f
                                                    },
                                                    onDragCancel = {
                                                        offsetX = 0f
                                                    }
                                                )
                                            }
                                            .combinedClickable(
                                                onClick = {
                                                    if (app.packageName == "custom.action.focus") {
                                                        showFocusBottomSheet = true
                                                        focusSheetStep = 0
                                                    } else if (app.packageName == ACTION_HABITS) {
                                                        showHabitsScreen = true
                                                    } else {
                                                        val isFocusActive = FocusService.isRunning
                                                        val isWhitelisted = WhitelistManager.getWhitelistedPackages(context).contains(app.packageName)
                                                        if (isFocusActive && !isWhitelisted) {
                                                            Toast.makeText(context, "${app.name} is blocked during Block Session", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                            try {
                                                                BlockerActivity.isLaunchingWhitelistedApp = true
                                                                launcherApps.startMainActivity(app.componentName, app.user, null, null)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Could not open ${app.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    isMenuForThisAppExpanded = true
                                                }
                                            )
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (app.packageName == "custom.action.focus") {
                                                Icon(
                                                    imageVector = Icons.Default.HourglassFull,
                                                    contentDescription = "Block",
                                                    tint = Color(0xFF81C784),
                                                    modifier = Modifier.size(iconSize - 6.dp)
                                                )
                                            } else if (app.packageName == ACTION_HABITS) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Habits",
                                                    tint = Color(0xFFFF9800),
                                                    modifier = Modifier.size(iconSize - 6.dp)
                                                )
                                            } else {
                                                AppIconWithLimit(
                                                    context = context,
                                                    packageName = app.packageName,
                                                    bitmap = app.icon,
                                                    size = iconSize
                                                )
                                            }

                                            if (app.packageName != "custom.action.focus" && app.packageName != ACTION_HABITS && app.user != android.os.Process.myUserHandle()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .size(6.dp)
                                                        .background(Color(0xFFFBC02D), shape = androidx.compose.foundation.shape.CircleShape)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = isMenuForThisAppExpanded,
                                                onDismissRequest = { isMenuForThisAppExpanded = false },
                                                modifier = Modifier.background(Color(0xFF141414))
                                            ) {
                                                if (app.packageName != "custom.action.focus" && app.packageName != ACTION_HABITS) {
                                                    DropdownMenuItem(
                                                        text = { Text("App Info", color = Color.White, fontSize = 13.sp) },
                                                        onClick = {
                                                            isMenuForThisAppExpanded = false
                                                            if (app.user != android.os.Process.myUserHandle()) {
                                                                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                                try {
                                                                    launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null)
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } else {
                                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                    data = Uri.fromParts("package", app.packageName, null)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(intent)
                                                            }
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Remove from Home", color = Color(0xFFE57373), fontSize = 13.sp) },
                                                    onClick = {
                                                        isMenuForThisAppExpanded = false
                                                        val updated = homeScreenPackages.filter { it != app.packageName }
                                                        saveHomeApps(updated)
                                                        Toast.makeText(context, "Removed ${app.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                if (index > 0) {
                                                    DropdownMenuItem(
                                                        text = { Text("Move Left", color = Color.White, fontSize = 13.sp) },
                                                        onClick = {
                                                            isMenuForThisAppExpanded = false
                                                            val mutable = homeScreenPackages.toMutableList()
                                                            val pkg = mutable.removeAt(index)
                                                            mutable.add(index - 1, pkg)
                                                            saveHomeApps(mutable)
                                                            Toast.makeText(context, "Moved Left", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                                if (index < homeScreenApps.lastIndex) {
                                                    DropdownMenuItem(
                                                        text = { Text("Move Right", color = Color.White, fontSize = 13.sp) },
                                                        onClick = {
                                                            isMenuForThisAppExpanded = false
                                                            val mutable = homeScreenPackages.toMutableList()
                                                            val pkg = mutable.removeAt(index)
                                                            mutable.add(index + 1, pkg)
                                                            saveHomeApps(mutable)
                                                            Toast.makeText(context, "Moved Right", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = app.name,
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (index < homeScreenApps.lastIndex) {
                                    Spacer(modifier = Modifier.width(spacing))
                                }
                            }
                        }
                    }
                }
            } else {
        val allPermissionsGranted = hasUsagePermission && hasAccessibilityPermission &&
            isAdminEnabled && isDefaultLauncher && hasNotificationPermissionState &&
            hasOverlayPermission
        val onboardingPrefs = remember { context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE) }
        var onboardingCompleted by remember { mutableStateOf(onboardingPrefs.getBoolean("completed", false)) }
        var signedIn by remember { mutableStateOf(LocalAccount.isSignedIn(context)) }
        var onboardingStep by remember {
            mutableStateOf(
                when {
                    allPermissionsGranted && signedIn -> 3
                    signedIn -> 2
                    else -> 0
                }
            )
        }

        LaunchedEffect(allPermissionsGranted) {
            if (!allPermissionsGranted && onboardingStep == 3) {
                onboardingStep = 2
                return@LaunchedEffect
            }
            // And back in again once everything is granted.
            //
            // The step is decided on the very first composition, when a permission check can
            // still read false for a moment -- an accessibility service that has not finished
            // binding, a launcher role not yet resolved. Without this, that momentary false
            // stranded someone who had granted everything on the welcome screen, with no way
            // back but to walk through setup again.
            if (allPermissionsGranted && onboardingCompleted && signedIn && onboardingStep != 3) {
                onboardingStep = 3
            }
        }

        val defaultDockPackages = remember { WhitelistManager.getDefaultPackagesInOrder(context) }
        var selectedAllowedApps by remember(showFocusBottomSheet, showAppWhitelistSelector) {
            mutableStateOf(WhitelistManager.getCustomWhitelistedPackages(context))
        }
        var appToLimitDialog by remember { mutableStateOf<AppInfoCache?>(null) }

        // Back handler to close app drawer on back button press
        BackHandler(enabled = showAppDrawer) {
            showAppDrawer = false
            drawerSearchQuery = ""
            drawerTab = 0
        }

        // Back handler to go back from Focus setup bottom sheet to Home view or Step 0
        BackHandler(enabled = showFocusBottomSheet && !showAppDrawer && !showAppWhitelistSelector) {
            if (focusSheetStep > 0) {
                focusSheetStep = 0
                selectedDuration = 0
            } else {
                showFocusBottomSheet = false
            }
        }

        // Back handler to close the habit tracker
        BackHandler(enabled = showTasksScreen) {
            showTasksScreen = false
        }

        BackHandler(enabled = showReminderScreen) {
            showReminderScreen = false
        }

        BackHandler(enabled = showAlarmScreen) {
            showAlarmScreen = false
        }

        BackHandler(enabled = showFocusStats) {
            showFocusStats = false
        }

        BackHandler(enabled = showYouTubeScreen) {
            showYouTubeScreen = false
        }

        BackHandler(enabled = showHabitsScreen) {
            showHabitsScreen = false
        }

        // Back handler to close whitelist selector and return to Focus setup bottom sheet
        BackHandler(enabled = showAppWhitelistSelector) {
            showAppWhitelistSelector = false
        }

        // Back handler to consume back button on Home Screen so it does nothing
        BackHandler(enabled = onboardingStep == 3 && !showFocusBottomSheet && !showAppDrawer && !showAppWhitelistSelector && !showHabitsScreen && !showYouTubeScreen && !showFocusStats && !showTasksScreen && !showReminderScreen && !showAlarmScreen) {
            // Consume back press, do nothing
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (showTasksScreen) {
                TasksScreen(onBack = { showTasksScreen = false })
            } else if (showAlarmScreen) {
                AlarmScreen(onBack = { showAlarmScreen = false })
            } else if (showReminderScreen) {
                key(reminderSession) {
                    ReminderScreen(onBack = { showReminderScreen = false })
                }
            } else if (showFocusStats) {
                FocusStatsScreen(onBack = { showFocusStats = false })
            } else if (showYouTubeScreen) {
                YouTubeScreen(onBack = { showYouTubeScreen = false })
            } else if (showHabitsScreen) {
                HabitTrackerScreen(
                    onBack = {
                        showHabitsScreen = false
                        reloadHabits()
                    }
                )
            } else if (onboardingStep == 0) {
                WelcomeScreen(onStart = { onboardingStep = 1 })
            } else if (onboardingStep == 1) {
                AccountScreen(
                    onSignedIn = {
                        signedIn = true
                        onboardingStep = 2
                    }
                )
            } else if (onboardingStep == 2) {
                PermissionsSetupScreen(
                    context = context,
                    hasUsagePermission = hasUsagePermission,
                    hasAccessibilityPermission = hasAccessibilityPermission,
                    isAdminEnabled = isAdminEnabled,
                    isDefaultLauncher = isDefaultLauncher,
                    hasNotificationPermission = hasNotificationPermissionState,
                    hasOverlayPermission = hasOverlayPermission,
                    allPermissionsGranted = allPermissionsGranted,
                    onEnterLauncher = {
                        onboardingPrefs.edit().putBoolean("completed", true).apply()
                        onboardingStep = 3
                    }
                )
            } else {
                    val pomo = Pomodoro.state(context)

                    // Asked for, never volunteered: the end time appears when the mark is
                    // tapped and goes again on its own.
                    var showEndChip by remember { mutableStateOf(false) }
                    LaunchedEffect(showEndChip) {
                        if (showEndChip) {
                            kotlinx.coroutines.delay(2000)
                            showEndChip = false
                        }
                    }

                    // Launcher Home View. A running session shows this same screen rather
                    // than one of its own: it only gains an end-time chip under the date and
                    // narrows what the dock and drawer will open.
                    Column(
                        modifier = modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Black, Color.Black),
                                radius = 1400f
                            )
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (dragAmount.y < -12f) {
                                        showAppDrawer = true
                                    }
                                }
                            )
                        }
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // The launcher wears its own mark rather than a clock, session or no
                    // session. Watching the time pass is the habit all of this is meant to
                    // break, and during a session the end-time chip answers the only question
                    // about time worth asking.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 48.dp)
                    ) {
                        Text(
                            text = "FOCUS",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (FocusService.isRunning) showEndChip = true },
                                // Held, not tapped, and only when no block session is running --
                                // there is deliberately no way out once a session has begun.
                                onLongClick = {
                                    if (!FocusService.isRunning) showExitSheet = true
                                }
                            )
                        )

                        if (nextReminder != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            NextReminderChip(
                                reminder = nextReminder,
                                alsoWaiting = alsoWaiting,
                                onOpen = { reminderSession++; showReminderScreen = true }
                            )
                        }

                        if (FocusService.isRunning) {
                            Spacer(modifier = Modifier.height(16.dp))

                            val endTimeMs = FocusService.getSessionEndTime(context)
                            val endText = remember(FocusService.remainingSeconds, endTimeMs) {
                                if (endTimeMs <= 0) {
                                    "Ends --:--"
                                } else {
                                    val nowCal = Calendar.getInstance()
                                    val endCal = Calendar.getInstance().apply { timeInMillis = endTimeMs }
                                    val sameDay = nowCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
                                            nowCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
                                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(endCal.time)
                                    if (sameDay) {
                                        "Ends at $timeStr"
                                    } else {
                                        val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(endCal.time)
                                        "Ends on $dateStr at $timeStr"
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = showEndChip,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    color = Color(0xFF0F265C).copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = endText,
                                        color = Color(0xFF64B5F6),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main dashboard or mindfulness quote (if Focus service is active, show status)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(184.dp)
                                    .background(Color(0xFF0F0F0F), shape = CircleShape)
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFF0F265C),
                                                Color(0xFF64B5F6),
                                                Color(0xFF0F265C)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                // Mid-session the ring is the focus session's
                                                // control; before one, it is how a block
                                                // session is started at all.
                                                if (FocusService.isRunning ||
                                                    FocusService.hasLiveSession(context)
                                                ) {
                                                    showPomodoroSheet = true
                                                } else {
                                                    showFocusBottomSheet = true
                                                    focusSheetStep = 0
                                                }
                                            },
                                            // Held down instead of tapped: the record of past
                                            // sessions is not something wanted mid-session, so
                                            // it sits behind a deliberate press.
                                            onLongPress = { showFocusStats = true }
                                        )
                                    }
                            ) {
                                if (pomo.onScreen) {
                                    // The ring fills as the phase runs down, so the shape the
                                    // user already looks at carries the countdown instead of a
                                    // second clock appearing beside it. Nothing is written
                                    // inside it but the time: a session count and a phase name
                                    // are noise around the one number that matters.
                                    val sweep by animateFloatAsState(
                                        targetValue = pomo.progress,
                                        animationSpec = tween(600),
                                        label = "pomoSweep"
                                    )
                                    val ringColor = when {
                                        pomo.paused -> Color(0xFF7A7A7A)
                                        pomo.phase == PomoPhase.FOCUS -> Color(0xFF64B5F6)
                                        else -> Color(0xFF81C784)
                                    }
                                    // A faint full circle under the sweep, so the ring reads as
                                    // a dial with a distance still to run rather than a stray arc.
                                    CircularProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier.size(172.dp),
                                        color = Color(0xFF171717),
                                        trackColor = Color.Transparent,
                                        strokeWidth = 6.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    CircularProgressIndicator(
                                        progress = { sweep },
                                        modifier = Modifier.size(172.dp),
                                        color = ringColor,
                                        trackColor = Color.Transparent,
                                        strokeWidth = 6.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    Text(
                                        text = Pomodoro.clock(pomo.remainingSeconds),
                                        color = if (pomo.paused) Color(0xFF8A8A8A) else Color.White,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = (-1.5).sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.HourglassFull,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(66.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = BlockerActivity.currentQuote,
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }

                    // Bottom Apps Dock (Dynamic layout adjust sizes/spacing automatically)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                    ) {
                        // The launcher's own two screens sit above the apps as two ordinary
                        // tiles -- no tray, no box around them. Smaller than an app icon
                        // because they belong to the launcher rather than to the phone, and
                        // laid out on the same centred rhythm so the two rows read as one
                        // stack rather than two unrelated things.
                        if (shelfIds.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp,
                                    Alignment.CenterHorizontally
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)
                            ) {
                                shelfIds.forEachIndexed { index, id ->
                                    ShelfTile(
                                        id = id,
                                        index = index,
                                        count = shelfIds.size,
                                        onOpen = {
                                            when (id) {
                                                ACTION_HABITS -> showHabitsScreen = true
                                                ACTION_TASKS -> showTasksScreen = true
                                                ACTION_YOUTUBE -> showYouTubeScreen = true
                                            }
                                        },
                                        onRemove = {
                                            shelfIds = shelfIds.filterNot { it == id }
                                            ShelfLayout.set(context, shelfIds)
                                        },
                                        onMove = { shift ->
                                            val to = (index + shift).coerceIn(0, shelfIds.lastIndex)
                                            if (to != index) {
                                                val m = shelfIds.toMutableList()
                                                m.add(to, m.removeAt(index))
                                                shelfIds = m
                                                ShelfLayout.set(context, shelfIds)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (homeScreenApps.isNotEmpty()) {
                            val count = homeScreenApps.size
                            val cardSize = if (count > 6) 42.dp else 46.dp
                            val colWidth = if (count > 6) 46.dp else 52.dp
                            val iconSize = if (count > 6) 24.dp else 28.dp
                            val spacing = if (count > 6) 6.dp else 12.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                homeScreenApps.forEachIndexed { index, app ->
                                    var offsetX by remember { mutableStateOf(0f) }
                                    var isMenuForThisAppExpanded by remember { mutableStateOf(false) }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(colWidth)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(cardSize)
                                                .offset { IntOffset(offsetX.roundToInt(), 0) }
                                                .pointerInput(homeScreenApps) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            offsetX += dragAmount.x
                                                        },
                                                        onDragEnd = {
                                                            val itemWidthPx = (colWidth.toPx() + spacing.toPx())
                                                            val shift = (offsetX / itemWidthPx).roundToInt()
                                                            if (shift != 0) {
                                                                val newIndex = (index + shift).coerceIn(0, homeScreenApps.lastIndex)
                                                                moveHomeApp(app.packageName, newIndex)
                                                            }
                                                            offsetX = 0f
                                                        },
                                                        onDragCancel = {
                                                            offsetX = 0f
                                                        }
                                                    )
                                                }
                                                .combinedClickable(
                                                    onClick = {
                                                        if (app.packageName == "custom.action.focus") {
                                                            showFocusBottomSheet = true
                                                            focusSheetStep = 0
                                                        } else if (app.packageName == ACTION_HABITS) {
                                                            showHabitsScreen = true
                                                        } else if (app.packageName == ACTION_TASKS) {
                                                            showTasksScreen = true
                                                        } else if (app.packageName == ACTION_ALARM) {
                                                            showAlarmScreen = true
                                                        } else if (app.packageName == ACTION_REMINDERS) {
                                                            reminderSession++
                                                            showReminderScreen = true
                                                        } else if (app.packageName == ACTION_YOUTUBE) {
                                                            showYouTubeScreen = true
                                                        } else {
                                                            val isFocusActive = FocusService.isRunning
                                                            val isWhitelisted = WhitelistManager.getWhitelistedPackages(context).contains(app.packageName)
                                                            if (isFocusActive && !isWhitelisted) {
                                                                Toast.makeText(context, "${app.name} is blocked during Block Session", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                                try {
                                                                    BlockerActivity.isLaunchingWhitelistedApp = true
                                                                    launcherApps.startMainActivity(app.componentName, app.user, null, null)
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Could not open ${app.name}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isMenuForThisAppExpanded = true
                                                    }
                                                )
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                if (app.packageName == "custom.action.focus") {
                                                    Icon(
                                                        imageVector = Icons.Default.HourglassFull,
                                                        contentDescription = "Block",
                                                        tint = Color(0xFF81C784),
                                                        modifier = Modifier.size(iconSize - 6.dp)
                                                    )
                                                } else if (app.packageName == ACTION_HABITS) {
                                                    // No tile, no tint, no badge -- just the
                                                    // flame on the black the dock already is.
                                                    Text(
                                                        text = "🔥",
                                                        fontSize = (iconSize.value - 4).sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                } else if (app.packageName == ACTION_TASKS) {
                                                    AtomGlyph(
                                                        size = iconSize - 4.dp,
                                                        spinning = true
                                                    )
                                                } else if (app.packageName == ACTION_REMINDERS) {
                                                    BellGlyph(size = iconSize - 4.dp)
                                                } else if (app.packageName == ACTION_ALARM) {
                                                    AlarmGlyph(size = iconSize - 4.dp)
                                                } else if (app.packageName == ACTION_YOUTUBE) {
                                                    // Same treatment as the streak flame: the
                                                    // glyph itself on the dock's own black.
                                                    BulbGlyph(size = iconSize - 4.dp)
                                                } else {
                                                    AppIconWithLimit(
                                                    context = context,
                                                    packageName = app.packageName,
                                                    bitmap = app.icon,
                                                    size = iconSize
                                                )
                                                }

                                                DropdownMenu(
                                                    expanded = isMenuForThisAppExpanded,
                                                    onDismissRequest = { isMenuForThisAppExpanded = false },
                                                    modifier = Modifier.background(Color(0xFF0F0F0F))
                                                ) {
                                                    if (!isOwnScreen(app.packageName) && app.packageName != "custom.action.focus") {
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Home", color = Color(0xFFE57373), fontSize = 13.sp) },
                                                            onClick = {
                                                                isMenuForThisAppExpanded = false
                                                                val mutable = homeScreenPackages.toMutableList()
                                                                mutable.remove(app.packageName)
                                                                saveHomeApps(mutable)
                                                                Toast.makeText(context, "${app.name} removed from Home Screen", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                    if (index > 0) {
                                                        DropdownMenuItem(
                                                            text = { Text("Move Left", color = Color.White, fontSize = 13.sp) },
                                                            onClick = {
                                                                isMenuForThisAppExpanded = false
                                                                moveHomeApp(app.packageName, index - 1)
                                                                Toast.makeText(context, "Moved Left", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                    if (index < homeScreenApps.lastIndex) {
                                                        DropdownMenuItem(
                                                            text = { Text("Move Right", color = Color.White, fontSize = 13.sp) },
                                                            onClick = {
                                                                isMenuForThisAppExpanded = false
                                                                moveHomeApp(app.packageName, index + 1)
                                                                Toast.makeText(context, "Moved Right", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = app.name,
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    if (index < homeScreenApps.lastIndex) {
                                        Spacer(modifier = Modifier.width(spacing))
                                    }
                                }
                            }
                        }
                    }
            }
        }
                if (showExitSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showExitSheet = false },
                        containerColor = Color(0xFF0C0C0C),
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF2E2E2E)) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)
                        ) {
                            Text(
                                "Leave the launcher",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Exit opens your phone's home-app setting, where you can switch the default launcher back.",
                                color = Color(0xFF8A8A8A),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(22.dp))
                            Button(
                                onClick = {
                                    showExitSheet = false
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_HOME_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_MAIN)
                                                    .addCategory(Intent.CATEGORY_HOME)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (inner: Exception) {
                                            Toast.makeText(context, "Could not open launcher settings.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) {
                                Text(
                                    "EXIT",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }


            // Focus Session Sheet -- the pomodoro that runs inside a block session.
            if (showPomodoroSheet) {
                val saved = remember { Pomodoro.lastConfig(context) }
                var focusMin by remember { mutableStateOf(saved.first) }
                var breakMin by remember { mutableStateOf(saved.second) }
                var sessionCount by remember { mutableStateOf(saved.third) }
                val live = Pomodoro.state(context)

                ModalBottomSheet(
                    onDismissRequest = { showPomodoroSheet = false },
                    containerColor = Color(0xFF0C0C0C),
                    // Skipping the half-open step matters here: everything fits on one screen,
                    // so a sheet that opened half way would only ask to be dragged for nothing.
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF2E2E2E)) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, bottom = 26.dp)
                    ) {
                        if (live.onScreen) {
                            Text(
                                text = when {
                                    live.paused -> "PAUSED"
                                    live.phase == PomoPhase.FOCUS -> "FOCUS"
                                    else -> "BREAK"
                                },
                                color = when {
                                    live.paused -> Color(0xFF8A8A8A)
                                    live.phase == PomoPhase.FOCUS -> Color(0xFF64B5F6)
                                    else -> Color(0xFF81C784)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.5.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = Pomodoro.clock(live.remainingSeconds),
                                color = Color.White,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-2).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Session ${live.session} of ${live.totalSessions}",
                                color = Color(0xFF6A6A6A),
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(26.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        if (live.paused) Pomodoro.resume(context)
                                        else Pomodoro.pause(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1A1A1A)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(52.dp)
                                ) {
                                    Icon(
                                        imageVector = if (live.paused) Icons.Default.PlayArrow
                                        else Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (live.paused) "RESUME" else "PAUSE",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        Pomodoro.stop(context)
                                        showPomodoroSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2A1414)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF5A2A2A)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "STOP",
                                        color = Color(0xFFE57373),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Stopping keeps the minutes you have already focused.",
                                color = Color(0xFF4A4A4A),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "FOCUS SESSION",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Runs inside your block session",
                                color = Color(0xFF6A6A6A),
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            PomoDial("Focus", focusMin, "min", 1, 180, Color(0xFF64B5F6)) { focusMin = it }
                            Spacer(modifier = Modifier.height(8.dp))
                            PomoDial("Break", breakMin, "min", 0, 60, Color(0xFF81C784)) { breakMin = it }
                            Spacer(modifier = Modifier.height(8.dp))
                            PomoDial("Sessions", sessionCount, "x", 1, 12, Color(0xFFFFB74D)) { sessionCount = it }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Total " + Pomodoro.clock(
                                    (focusMin * sessionCount + breakMin * (sessionCount - 1)) * 60
                                ),
                                color = Color(0xFF6A6A6A),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    Pomodoro.start(context, focusMin, breakMin, sessionCount)
                                    showPomodoroSheet = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("START", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Focus Setup Bottom Sheet Overlay
            AnimatedVisibility(
                visible = showFocusBottomSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showFocusBottomSheet = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                        border = BorderStroke(1.dp, Color.DarkGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .navigationBarsPadding()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                             if (focusSheetStep == 0) {
                                 Text(
                                     text = "SELECT BLOCK DURATION",
                                     color = Color.White,
                                     fontSize = 15.sp,
                                     fontWeight = FontWeight.Bold,
                                     letterSpacing = 1.sp
                                 )
                                 Spacer(modifier = Modifier.height(24.dp))

                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.SpaceEvenly
                                 ) {
                                     listOf(25, 45, 60).forEach { mins ->
                                         Button(
                                             onClick = {
                                                 selectedDuration = mins
                                                 focusSheetStep = 2
                                             },
                                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161616)),
                                             border = BorderStroke(1.dp, Color.DarkGray),
                                             shape = RoundedCornerShape(12.dp),
                                             contentPadding = PaddingValues(0.dp),
                                             modifier = Modifier
                                                 .weight(1f)
                                                 .padding(horizontal = 4.dp)
                                                 .height(54.dp)
                                         ) {
                                             Text("$mins min", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                         }
                                     }

                                     Button(
                                         onClick = {
                                             customMinutesInput = selectedDuration.toString()
                                             selectedDaysVal = 0
                                             selectedHoursVal = 0
                                             selectedMinutesVal = 0
                                             showCustomTimeSheet = true
                                         },
                                         colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161616)),
                                         border = BorderStroke(1.dp, Color.DarkGray),
                                         shape = RoundedCornerShape(12.dp),
                                         contentPadding = PaddingValues(0.dp),
                                         modifier = Modifier
                                             .weight(1f)
                                             .padding(horizontal = 4.dp)
                                             .height(54.dp)
                                     ) {
                                         Text("+ Custom", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                     }
                                 }
                                 Spacer(modifier = Modifier.height(16.dp))
                             } else {
                                 Text(
                                     text = "FOCUS OPTIONS",
                                     color = Color.White,
                                     fontSize = 15.sp,
                                     fontWeight = FontWeight.Bold,
                                     letterSpacing = 1.sp
                                 )
                                 Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Duration: $selectedDuration minutes",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Whitelist Apps (${selectedAllowedApps.size})",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "+ Add",
                                        color = Color(0xFF64B5F6),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            showAppWhitelistSelector = true
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (selectedAllowedApps.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Text(
                                            text = "Add the apps you want to use during the block",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                     Row(
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .height(64.dp)
                                             .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                                             .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                             .padding(horizontal = 12.dp),
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.Start
                                     ) {
                                         LazyRow(
                                             horizontalArrangement = Arrangement.spacedBy(8.dp),
                                             verticalAlignment = Alignment.CenterVertically,
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             items(selectedAllowedApps.toList()) { pkg ->
                                                 installedApps.firstOrNull { it.packageName == pkg }?.let { app ->
                                                     Card(
                                                         shape = RoundedCornerShape(8.dp),
                                                         colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                                                         modifier = Modifier.size(44.dp)
                                                     ) {
                                                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                             CachedAppIcon(bitmap = app.icon, modifier = Modifier.size(28.dp))
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                 }

                                  val alwaysAllowedList = remember(installedApps, defaultDockPackages) {
                                      val list = mutableListOf<String>()
                                      // 1. Phone (Dialer)
                                      try {
                                          val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                          telecom.defaultDialerPackage?.let { list.add(it) }
                                      } catch (e: Exception) {}
                                      if (list.isEmpty()) list.add("com.android.dialer")

                                      // 2. Messages (SMS)
                                      try {
                                          Telephony.Sms.getDefaultSmsPackage(context)?.let { list.add(it) }
                                      } catch (e: Exception) {}
                                      if (list.size < 2) list.add("com.android.mms")

                                      // 3. Gmail
                                      list.add("com.google.android.gm")

                                      // 4. Clock
                                      val clock = defaultDockPackages.firstOrNull { it.contains("clock") } ?: "com.android.deskclock"
                                      list.add(clock)

                                      // 5. Calculator
                                      val calc = defaultDockPackages.firstOrNull { it.contains("calculator") } ?: "com.android.calculator2"
                                      list.add(calc)

                                      // 6. Camera
                                      val cam = defaultDockPackages.firstOrNull { it.contains("camera") } ?: "com.android.camera"
                                      list.add(cam)

                                      val orderedPackages = list.distinct()
                                      orderedPackages.mapNotNull { pkg ->
                                          installedApps.firstOrNull { it.packageName == pkg }
                                      }
                                  }

                                 Spacer(modifier = Modifier.height(16.dp))

                                 Text(
                                     text = "Always Allowed Apps",
                                     color = Color.Gray,
                                     fontSize = 12.sp,
                                     fontWeight = FontWeight.Bold,
                                     modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                 )

                                 Row(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .height(64.dp)
                                         .background(Color(0xFF161616).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                         .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                         .padding(horizontal = 12.dp),
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.Start
                                 ) {
                                     LazyRow(
                                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                                         verticalAlignment = Alignment.CenterVertically,
                                         modifier = Modifier.fillMaxWidth()
                                     ) {
                                         items(alwaysAllowedList) { app ->
                                             Card(
                                                 shape = RoundedCornerShape(8.dp),
                                                 colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                                                 modifier = Modifier.size(44.dp)
                                             ) {
                                                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                     CachedAppIcon(bitmap = app.icon, modifier = Modifier.size(28.dp))
                                                 }
                                             }
                                         }
                                     }
                                 }

                                 Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        // A live ADB shell can force-stop or uninstall the
                                        // blocker, so the session must not start behind one.
                                        if (DeveloperOptionsGuard.isUsbDebuggingEnabled(context)) {
                                            showUsbDebuggingBlocker = true
                                            return@Button
                                        }

                                        context.getSharedPreferences("focus_session_prefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("custom_focus_message", focusMessage)
                                            .apply()

                                        val serviceIntent = Intent(context, FocusService::class.java).apply {
                                            putExtra("duration_minutes", selectedDuration)
                                        }
                                        ContextCompat.startForegroundService(context, serviceIntent)

                                        showFocusBottomSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0F265C),
                                        disabledContainerColor = Color(0xFF1A1A1A)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF1E88E5)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(
                                        text = "Start Block Session",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showHabitEditor) {
                HabitEditorScreen(
                    existing = habitBeingEdited,
                    onCancel = { showHabitEditor = false },
                    onSave = { emoji, text ->
                        val target = habitBeingEdited
                        if (target == null) {
                            dbHelper.insertHabit(emoji, text, HabitStats.today())
                        } else {
                            dbHelper.updateHabit(target.id, emoji, text)
                        }
                        showHabitEditor = false
                        habitsList = dbHelper.getAllHabits()
                    }
                )
            }

            if (showUsbDebuggingBlocker) {
                AlertDialog(
                    onDismissRequest = { showUsbDebuggingBlocker = false },
                    containerColor = Color(0xFF0F0F0F),
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Text(
                            text = "Turn off USB debugging",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Text(
                            text = "A block session can't start while USB debugging is on — it " +
                                "lets a connected computer force-stop or uninstall the blocker.\n\n" +
                                "Switch USB debugging off in Developer options, then start the " +
                                "session. Developer options themselves can stay enabled.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showUsbDebuggingBlocker = false
                            DeveloperOptionsGuard.openDeveloperSettings(context)
                        }) {
                            Text("OPEN SETTINGS", color = Color(0xFFFF9E4A), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUsbDebuggingBlocker = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                    }
                )
            }

            // Whitelist App Selector Overlay
            AnimatedVisibility(
                visible = showAppWhitelistSelector,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                val pickerApps = remember(installedApps, defaultDockPackages) {
                    installedApps.filter { app ->
                        val isAlwaysAllowed = WhitelistManager.CORE_SYSTEM_PACKAGES.contains(app.packageName) ||
                                              WhitelistManager.PRODUCTIVITY_DEFAULT_PACKAGES.contains(app.packageName) ||
                                              defaultDockPackages.contains(app.packageName)
                        !isAlwaysAllowed
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "WHITELIST APPS (${selectedAllowedApps.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (selectedAllowedApps.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "Add the apps you want to use during the block",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(selectedAllowedApps.toList()) { pkg ->
                                        installedApps.firstOrNull { it.packageName == pkg }?.let { app ->
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CachedAppIcon(bitmap = app.icon, modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "select the apps you want to use during the block",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pickerApps) { app ->
                                val isAlwaysAllowed = WhitelistManager.CORE_SYSTEM_PACKAGES.contains(app.packageName) ||
                                      WhitelistManager.PRODUCTIVITY_DEFAULT_PACKAGES.contains(app.packageName) ||
                                                      defaultDockPackages.contains(app.packageName)
                                val isChecked = selectedAllowedApps.contains(app.packageName)

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isAlwaysAllowed) Color(0xFF1E88E5) else if (isChecked) Color(0xFF64B5F6) else Color.DarkGray),
                                    colors = CardDefaults.cardColors(containerColor = if (isAlwaysAllowed) Color(0xFF0F265C).copy(alpha = 0.25f) else if (isChecked) Color(0xFF0F265C).copy(alpha = 0.15f) else Color(0xFF0F0F0F)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clickable {
                                            if (isAlwaysAllowed) {
                                                Toast.makeText(context, "Always allowed by default", Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (isChecked) {
                                                    selectedAllowedApps = selectedAllowedApps - app.packageName
                                                    WhitelistManager.deleteAppUsageLimit(context, app.packageName)
                                                    if (FocusService.isRunning) {
                                                        val stopIntent = Intent(context, FocusService::class.java)
                                                        context.stopService(stopIntent)
                                                        BlockerActivity.isSessionCompleted = true
                                                        Toast.makeText(context, "Block session cancelled due to whitelist changes", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    appToLimitDialog = app
                                                }
                                            }
                                        }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize().padding(6.dp)
                                    ) {
                                        AppIconWithLimit(
                                                context = context,
                                                packageName = app.packageName,
                                                bitmap = app.icon,
                                                size = 36.dp
                                            )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = app.name,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        if (isAlwaysAllowed) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Always allowed",
                                                color = Color(0xFF64B5F6),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                WhitelistManager.saveWhitelistedPackages(context, selectedAllowedApps)
                                showAppWhitelistSelector = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F265C)),
                            border = BorderStroke(1.dp, Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Save Allowed Apps", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (appToLimitDialog != null) {
                val app = appToLimitDialog!!
                var isUnlimited by remember { mutableStateOf(true) }
                var limitMinutes by remember { mutableStateOf(15) }
                
                AlertDialog(
                    onDismissRequest = { appToLimitDialog = null },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Set the limit",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Unlimited",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                                androidx.compose.material3.Switch(
                                    checked = isUnlimited,
                                    onCheckedChange = { isUnlimited = it },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF64B5F6),
                                        checkedTrackColor = Color(0xFF0F265C)
                                    )
                                )
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            
                            if (!isUnlimited) {
                                Text(
                                    text = "$limitMinutes min",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.material3.Slider(
                                    value = limitMinutes.toFloat(),
                                    onValueChange = { limitMinutes = it.toInt().coerceIn(1, 60) },
                                    valueRange = 1f..60f,
                                    steps = 58,
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        thumbColor = Color(0xFF64B5F6),
                                        activeTrackColor = Color(0xFF1E88E5)
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { appToLimitDialog = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        val minutes = if (isUnlimited) -1 else limitMinutes
                                        WhitelistManager.setAppUsageLimitMinutes(context, app.packageName, minutes)
                                        selectedAllowedApps = selectedAllowedApps + app.packageName
                                        
                                        // Cancel running focus session
                                        if (FocusService.isRunning) {
                                            val stopIntent = Intent(context, FocusService::class.java)
                                            context.stopService(stopIntent)
                                            BlockerActivity.isSessionCompleted = true
                                            Toast.makeText(context, "Block session cancelled due to whitelist changes", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        appToLimitDialog = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F265C)),
                                    border = BorderStroke(1.dp, Color(0xFF1E88E5)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {},
                    containerColor = Color(0xFF121212),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Custom App Drawer Panel (Smooth Slide Up)
            AnimatedVisibility(
                visible = showAppDrawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFA0A0A0A))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (dragAmount.y > 12f) {
                                        showAppDrawer = false
                                        drawerSearchQuery = ""
                                        drawerTab = 0
            drawerTab = 0
                                    }
                                }
                            )
                        }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Header of the drawer (search only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = drawerSearchQuery,
                            onValueChange = { drawerSearchQuery = it },
                            placeholder = { Text("Search apps...", color = Color.Gray, fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF141414),
                                unfocusedContainerColor = Color(0xFF141414),
                                focusedBorderColor = Color(0xFF1E88E5),
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Grid of Apps. During a session the drawer lists only what the session
                    // will actually open -- the whitelist already carries the default apps --
                    // so the user is never offered an icon that answers with a refusal.
                    val sessionAllowed = remember(showAppDrawer, FocusService.isRunning, triggerAppReload) {
                        if (FocusService.isRunning) {
                            WhitelistManager.getWhitelistedPackages(context)
                        } else {
                            emptySet()
                        }
                    }
                    // The two screens this launcher owns have no package to be listed under,
                    // so they are put into the drawer by hand. They belong there: it is where
                    // the user goes looking for an app, and where they add one to the home
                    // screen from.
                    val ownScreens = listOf(
                        AppInfoCache(name = "Streak", packageName = ACTION_HABITS, icon = null),
                        AppInfoCache(name = "Target", packageName = ACTION_TASKS, icon = null),
                        AppInfoCache(name = "Reminder", packageName = ACTION_REMINDERS, icon = null),
                        AppInfoCache(name = "Alarm", packageName = ACTION_ALARM, icon = null),
                        AppInfoCache(name = "Resource", packageName = ACTION_YOUTUBE, icon = null)
                    )
                    // What the user chose to allow, as opposed to what was allowed for them.
                    // Splitting the two answers the question a locked-down drawer provokes --
                    // "why is that one still here?" -- without the user having to go and read
                    // the whitelist to find out.
                    val userAllowed = remember(showAppDrawer, FocusService.isRunning, triggerAppReload) {
                        if (FocusService.isRunning) {
                            WhitelistManager.getCustomWhitelistedPackages(context)
                        } else {
                            emptySet()
                        }
                    }

                    val visible = (ownScreens + installedApps).filter {
                        it.name.contains(drawerSearchQuery, ignoreCase = true) &&
                            (isOwnScreen(it.packageName) ||
                                !FocusService.isRunning ||
                                sessionAllowed.contains(it.packageName))
                    }

                    val filteredApps = if (!FocusService.isRunning) {
                        visible
                    } else if (drawerTab == 0) {
                        // This launcher's own screens sit with the chosen ones: they are the
                        // reason a session is running at all.
                        visible.filter {
                            isOwnScreen(it.packageName) || userAllowed.contains(it.packageName)
                        }
                    } else {
                        visible.filter {
                            !isOwnScreen(it.packageName) && !userAllowed.contains(it.packageName)
                        }
                    }

                    if (FocusService.isRunning) {
                        DrawerTabs(
                            current = drawerTab,
                            allowedCount = visible.count {
                                isOwnScreen(it.packageName) || userAllowed.contains(it.packageName)
                            },
                            systemCount = visible.count {
                                !isOwnScreen(it.packageName) && !userAllowed.contains(it.packageName)
                            },
                            onPick = { drawerTab = it }
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredApps) { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                var isMenuForThisAppExpanded by remember { mutableStateOf(false) }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isOwnScreen(app.packageName)) {
                                                    // These open a screen, not a package, so
                                                    // there is nothing for LauncherApps to start.
                                                    when (app.packageName) {
                                                        ACTION_HABITS -> showHabitsScreen = true
                                                        ACTION_TASKS -> showTasksScreen = true
                                                        ACTION_REMINDERS -> {
                                                            reminderSession++
                                                            showReminderScreen = true
                                                        }
                                                        ACTION_ALARM -> showAlarmScreen = true
                                                        ACTION_YOUTUBE -> showYouTubeScreen = true
                                                    }
                                                    showAppDrawer = false
                                                    drawerSearchQuery = ""
                                        drawerTab = 0
            drawerTab = 0
                                                    return@combinedClickable
                                                }
                                                val isFocusActive = FocusService.isRunning
                                                val isWhitelisted = WhitelistManager.getWhitelistedPackages(context).contains(app.packageName)
                                                if (isFocusActive && !isWhitelisted) {
                                                    Toast.makeText(context, "${app.name} is blocked during Block Session", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                    try {
                                                        BlockerActivity.isLaunchingWhitelistedApp = true
                                                        launcherApps.startMainActivity(app.componentName, app.user, null, null)
                                                        showAppDrawer = false
                                                        drawerSearchQuery = ""
                                        drawerTab = 0
            drawerTab = 0
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Could not open ${app.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                isMenuForThisAppExpanded = true
                                            }
                                        )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (app.packageName == ACTION_TASKS) {
                                            AtomGlyph(size = 30.dp, spinning = true)
                                        } else if (app.packageName == ACTION_REMINDERS) {
                                            BellGlyph(size = 30.dp)
                                        } else if (app.packageName == ACTION_ALARM) {
                                            AlarmGlyph(size = 30.dp)
                                        } else if (app.packageName == ACTION_YOUTUBE) {
                                            BulbGlyph(size = 30.dp)
                                        } else if (isOwnScreen(app.packageName)) {
                                            Text(
                                                text = "\uD83D\uDD25",
                                                fontSize = 26.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            AppIconWithLimit(
                                                context = context,
                                                packageName = app.packageName,
                                                bitmap = app.icon,
                                                size = 36.dp
                                            )
                                        }

                                        if (app.user != android.os.Process.myUserHandle()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .size(6.dp)
                                                    .background(Color(0xFFFBC02D), shape = androidx.compose.foundation.shape.CircleShape)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = isMenuForThisAppExpanded,
                                            onDismissRequest = { isMenuForThisAppExpanded = false },
                                            modifier = Modifier.background(Color(0xFF141414))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Add to Home", color = Color.White, fontSize = 13.sp) },
                                                onClick = {
                                                    isMenuForThisAppExpanded = false
                                                    when {
                                                        // The launcher's own three live on the
                                                        // shelf, not the dock, so they go back
                                                        // there when re-added.
                                                        app.packageName in ShelfLayout.ALL -> {
                                                            if (shelfIds.contains(app.packageName)) {
                                                                Toast.makeText(context, "Already on Home Screen", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                shelfIds = ShelfLayout.ALL.filter {
                                                                    it in shelfIds || it == app.packageName
                                                                }
                                                                ShelfLayout.set(context, shelfIds)
                                                                Toast.makeText(context, "Added to Home Screen", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        homeScreenPackages.contains(app.packageName) ->
                                                            Toast.makeText(context, "Already on Home Screen", Toast.LENGTH_SHORT).show()
                                                        homeScreenApps.size >= MAX_HOME_APPS ->
                                                            Toast.makeText(
                                                                context,
                                                                "Home screen holds $MAX_HOME_APPS apps. Remove one first.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        else -> {
                                                            saveHomeApps(homeScreenPackages + app.packageName)
                                                            Toast.makeText(context, "Added to Home Screen", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("App Info", color = Color.White, fontSize = 13.sp) },
                                                onClick = {
                                                    isMenuForThisAppExpanded = false
                                                    if (app.user != android.os.Process.myUserHandle()) {
                                                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                        try {
                                                            launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.fromParts("package", app.packageName, null)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Uninstall", color = Color(0xFFE57373), fontSize = 13.sp) },
                                                onClick = {
                                                    isMenuForThisAppExpanded = false
                                                    if (app.user != android.os.Process.myUserHandle()) {
                                                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                                        try {
                                                            launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        val intent = Intent(Intent.ACTION_DELETE).apply {
                                                            data = Uri.fromParts("package", app.packageName, null)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = app.name,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // A. Custom Time Bottom Sheet
    if (showCustomTimeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomTimeSheet = false },
            containerColor = Color.Transparent // Allow custom background gradient to draw perfectly
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F0F0F), Color(0xFF000000))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Custom Duration",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Days Dropdown Picker (0 to 30)
                    ScrollableDropdownPicker(
                        label = "DAYS",
                        value = selectedDaysVal,
                        options = (0..30).toList(),
                        onValueChange = { selectedDaysVal = it }
                    )

                    // Hours Dropdown Picker (0 to 24)
                    ScrollableDropdownPicker(
                        label = "HOURS",
                        value = selectedHoursVal,
                        options = (0..24).toList(),
                        onValueChange = { selectedHoursVal = it }
                    )

                    // Minutes Dropdown Picker (0 to 59)
                    ScrollableDropdownPicker(
                        label = "MINUTES",
                        value = selectedMinutesVal,
                        options = (0..59).toList(),
                        onValueChange = { selectedMinutesVal = it }
                    )
                }

                val totalMins = (selectedDaysVal.toLong() * 24 * 60) + (selectedHoursVal.toLong() * 60) + selectedMinutesVal
                
                val targetCal = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + totalMins * 60 * 1000
                }
                val todayCal = java.util.Calendar.getInstance()
                val tomorrowCal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }

                val isToday = targetCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                              targetCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)

                val isTomorrow = targetCal.get(java.util.Calendar.YEAR) == tomorrowCal.get(java.util.Calendar.YEAR) &&
                                 targetCal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrowCal.get(java.util.Calendar.DAY_OF_YEAR)

                val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                val timeStr = timeFormat.format(targetCal.time)

                val summaryText = when {
                    totalMins == 0L -> "Select duration to view unlock time"
                    isToday -> "Your app will be unlocked today at $timeStr"
                    isTomorrow -> "Your app will be unlocked tomorrow at $timeStr"
                    else -> {
                        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        val dateStr = dateFormat.format(targetCal.time)
                        "Your app will be unlocked on $dateStr at $timeStr"
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = summaryText,
                    color = if (totalMins > 0) Color(0xFF64B5F6) else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (totalMins > 0) {
                            selectedDuration = totalMins.toInt()
                            showCustomTimeSheet = false
                            focusSheetStep = 2
                        } else {
                            Toast.makeText(context, "Please select a duration greater than 0", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Apply Time", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // B. Start Focus Session Confirmation Bottom Sheet
    if (showStartConfirmationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStartConfirmationSheet = false },
            containerColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F0F0F), Color(0xFF000000))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Confirm Block Session",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                val totalMins = selectedDuration.toLong()
                val targetCal = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + totalMins * 60 * 1000
                }
                val todayCal = java.util.Calendar.getInstance()
                val tomorrowCal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }

                val isToday = targetCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                              targetCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)

                val isTomorrow = targetCal.get(java.util.Calendar.YEAR) == tomorrowCal.get(java.util.Calendar.YEAR) &&
                                 targetCal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrowCal.get(java.util.Calendar.DAY_OF_YEAR)

                val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                val timeStr = timeFormat.format(targetCal.time)

                val unlockDayText = when {
                    isToday -> "today at $timeStr"
                    isTomorrow -> "tomorrow at $timeStr"
                    else -> {
                        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        val dateStr = dateFormat.format(targetCal.time)
                        "on $dateStr at $timeStr"
                    }
                }

                val d = totalMins / (24 * 60)
                val h = (totalMins % (24 * 60)) / 60
                val m = totalMins % 60

                val durationText = buildString {
                    append("The app will remain blocked for ")
                    if (d > 0) append("$d days, ")
                    if (h > 0 || d > 0) append("$h hours, ")
                    append("$m minutes.")
                }

                Text(
                    text = "Your app will be unlocked $unlockDayText.",
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = durationText,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { showStartConfirmationSheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212)),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(50.dp)
                    ) {
                        Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showStartConfirmationSheet = false
                            
                            val serviceIntent = Intent(context, FocusService::class.java).apply {
                                putExtra("duration_minutes", selectedDuration)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }

                            // Nothing else to open: the session runs on this very screen,
                            // which redraws itself the moment the service reports running.
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(50.dp)
                    ) {
                        Text("CONTINUE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun AllowedAppsScreen(
    installedApps: List<AppInfoCache>,
    initialSelected: Set<String>,
    onBack: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedApps by remember { mutableStateOf(initialSelected) }
    var selectedTab by remember { mutableStateOf("Default") }

    BackHandler {
        onSave(selectedApps)
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CANCEL",
                color = Color(0xFFE57373),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "ALLOWED APPS",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "SAVE",
                color = Color(0xFF1E88E5),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSave(selectedApps) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ordered defaults (Calling, Messaging, Calculator, Gallery)
        val defaultPackagesOrder = remember { WhitelistManager.getDefaultPackagesInOrder(context) }
        
        // 1. Get default apps in exact order (only if selected)
        val defaultAppsOrdered = defaultPackagesOrder.mapNotNull { pkg ->
            if (selectedApps.contains(pkg)) {
                installedApps.firstOrNull { it.packageName == pkg }
            } else {
                null
            }
        }

        // 2. Get user-added custom apps (excluding defaults and core background services)
        val customAppsSelected = installedApps.filter { 
            selectedApps.contains(it.packageName) && 
            !defaultPackagesOrder.contains(it.packageName) && 
            !WhitelistManager.CORE_SYSTEM_PACKAGES.contains(it.packageName) 
        }

        // 3. Filter shelf list by tab choice
        val finalShelfList = if (selectedTab == "Default") defaultAppsOrdered else customAppsSelected

        // Tab selection chips
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Default", "Extra").forEach { tab ->
                val isSelected = selectedTab == tab
                Button(
                    onClick = { selectedTab = tab },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF132A17) else Color(0xFF161616),
                        contentColor = if (isSelected) Color.White else Color.Gray
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF1E88E5) else Color(0xFF222222)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(tab, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            if (finalShelfList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedTab == "Default") "Select default apps below" else "Select the apps you want to use",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(finalShelfList) { app ->
                        val isDefaultApp = defaultPackagesOrder.contains(app.packageName)
                        Box(contentAlignment = Alignment.TopEnd) {
                            CachedAppIcon(bitmap = app.icon, modifier = Modifier.size(44.dp))
                            // Show delete minus circle ONLY for custom user-added apps
                            if (!isDefaultApp) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFFD32F2F), shape = androidx.compose.foundation.shape.CircleShape)
                                        .clickable { selectedApps = selectedApps - app.packageName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(2.dp)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // All Apps Grid
        Text(
            text = "App Drawer Checklist",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Filter: Hide both core background system apps AND default whitelisted apps from the lower grid checklist
        val displayApps = installedApps.filter { 
            !WhitelistManager.CORE_SYSTEM_PACKAGES.contains(it.packageName) &&
            !defaultPackagesOrder.contains(it.packageName)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(displayApps) { app ->
                val isChecked = selectedApps.contains(app.packageName)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedApps = if (isChecked) {
                                selectedApps - app.packageName
                            } else {
                                selectedTab = "Extra"
                                selectedApps + app.packageName
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = if (isChecked) Color(0xFF0F265C) else Color(0xFF121212),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isChecked) Color(0xFF1E88E5) else Color(0xFF222222),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        CachedAppIcon(bitmap = app.icon, modifier = Modifier.size(42.dp))
                        if (isChecked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(Color(0xFF1E88E5), shape = RoundedCornerShape(7.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = app.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CircularTimer(
    durationMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(230.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background Circle Track (Dark #111111)
            drawCircle(
                color = Color(0xFF111111),
                style = Stroke(width = 6.dp.toPx())
            )
            // Premium blue sweep highlight
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF0F265C), Color(0xFF64B5F6), Color(0xFF0F265C))
                ),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
              )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val days = durationMinutes / (24 * 60)
            val hours = (durationMinutes % (24 * 60)) / 60
            val minutes = durationMinutes % 60
            val fontSize = if (days > 0) 26.sp else if (hours > 0) 30.sp else 38.sp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (days > 0) {
                    Text(text = String.format("%02d:%02d:", days, hours), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = String.format("%02d", minutes), color = Color(0xFF64B5F6), fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = ":00", color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                } else if (hours > 0) {
                    Text(text = String.format("%02d:", hours), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = String.format("%02d", minutes), color = Color(0xFF64B5F6), fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = ":00", color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                } else {
                    Text(text = String.format("%02d", minutes), color = Color(0xFF64B5F6), fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(text = ":00", color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            val formatLabel = if (days > 0) {
                "DD : HH : MM : SS"
            } else if (hours > 0) {
                "HH : MM : SS"
            } else {
                "MM : SS"
            }
            Text(
                text = formatLabel,
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to Customize",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CachedAppIcon(bitmap: ImageBitmap?, modifier: Modifier = Modifier) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        // Fallback default icon using system resources
        Image(
            bitmap = ImageBitmap(1, 1), // Dummy pixel if null
            contentDescription = null,
            modifier = modifier
        )
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/**
 * Opens this app's "display over other apps" page.
 *
 * There is no runtime dialog for this one: the only way to grant it is the settings screen,
 * and the intent carries the package so the user lands on this app rather than a list of
 * every app on the phone.
 */
private fun requestOverlayPermission(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

private fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val activity = context as? android.app.Activity
        activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, UrlBlockerService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun ScrollableDropdownPicker(
    label: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(48.dp)
                .background(Color(0xFF111111), shape = RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(10.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF121212))
                    .border(1.dp, Color(0xFF222222))
                    .heightIn(max = 240.dp)
                    .width(84.dp)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt.toString(),
                                color = if (opt == value) Color(0xFF1E88E5) else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            onValueChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogCompose(
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                return utcTimeMillis >= todayStart
            }
        }
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DatePicker(state = datePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        onDismiss()
                    }) {
                        Text("OK", color = Color(0xFF1E88E5))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogCompose(
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(initialHour = 0, initialMinute = 0)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(24.dp).border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    TextButton(onClick = {
                        onTimeSelected(timePickerState.hour, timePickerState.minute)
                        onDismiss()
                    }) {
                        Text("OK", color = Color(0xFF1E88E5))
                    }
                }
            }
        }
    }
}

private fun isAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    val adminComponent = android.content.ComponentName(context, com.example.androidkotlinapp.FocusDeviceAdminReceiver::class.java)
    return dpm.isAdminActive(adminComponent)
}

private fun requestAdminPermission(context: Context) {
    val adminComponent = android.content.ComponentName(context, com.example.androidkotlinapp.FocusDeviceAdminReceiver::class.java)
    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enabling this prevents the app from being uninstalled during active block sessions.")
    }
    context.startActivity(intent)
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Stylized Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "FOCUS",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "LAUNCHER",
                color = Color(0xFF64B5F6),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
            )
        }

        // Central Premium Focus Ring / Hourglass Graphic
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .background(Color(0xFF0F0F0F), shape = androidx.compose.foundation.shape.CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFF0F265C), Color(0xFF64B5F6), Color(0xFF0F265C))
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.HourglassFull,
                contentDescription = null,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(48.dp)
            )
        }

        // Minimalist Mission Statement
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "A minimalist, distraction-free space designed to help you lock in and reclaim your time.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(48.dp))

            // Start Journey Button
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Start the Journey →",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun PermissionsSetupScreen(
    context: Context,
    hasUsagePermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isAdminEnabled: Boolean,
    isDefaultLauncher: Boolean,
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    allPermissionsGranted: Boolean,
    onEnterLauncher: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Left-aligned, on the same edge the cards start from. A centred heading over a
            // left-aligned list gives the eye two margins to follow instead of one.
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = "Set up Focus",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Six switches, once. They are what let a block session actually hold.",
                color = Color(0xFF6E6E6E),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            // One gap, stated once. The six used to be separated by 10dp, 16dp, 16dp and then
            // nothing at all, which is what made the list look like it had been assembled in
            // pieces rather than laid out.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                PermissionChecklistItem(
                    step = 1,
                    title = "Usage Stats Access",
                    description = "Sees which app is in front.",
                    isGranted = hasUsagePermission,
                    onGrant = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open settings. Please enable manually.", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                PermissionChecklistItem(
                    step = 2,
                    title = "Accessibility Blocker",
                    description = "Catches blocked links and launches.",
                    isGranted = hasAccessibilityPermission,
                    onGrant = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open settings. Please enable manually.", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                PermissionChecklistItem(
                    step = 3,
                    title = "Uninstall Lock",
                    description = "Stops the app being removed mid-session.",
                    isGranted = isAdminEnabled,
                    onGrant = { requestAdminPermission(context) }
                )

                PermissionChecklistItem(
                    step = 4,
                    title = "Default Launcher",
                    description = "Keeps you on this home screen.",
                    isGranted = isDefaultLauncher,
                    onGrant = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                )

                PermissionChecklistItem(
                    step = 5,
                    title = "Notifications",
                    description = "Shows the session countdown.",
                    isGranted = hasNotificationPermission,
                    onGrant = { requestNotificationPermission(context) }
                )

                PermissionChecklistItem(
                    step = 6,
                    title = "Display Over Apps",
                    description = "Floats the countdown over other apps.",
                    isGranted = hasOverlayPermission,
                    onGrant = { requestOverlayPermission(context) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // The way out sits still at the foot of the screen rather than riding the scroll, so
        // it is in the same place whether the list has been moved or not. The fade above it
        // says the list carries on, instead of leaving a card looking sliced in half.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black)
                    )
                )
        )
        Box(
            modifier = Modifier
                .background(Color.Black)
                .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 22.dp)
        ) {
            Button(
                onClick = onEnterLauncher,
                enabled = allPermissionsGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C6BFF),
                    disabledContainerColor = Color(0xFF141414)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = if (allPermissionsGranted) "Enter launcher" else "Grant all six to continue",
                    color = if (allPermissionsGranted) Color.White else Color(0xFF5A5A5A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
fun PermissionChecklistItem(
    step: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    // A granted row recedes rather than lighting up. Six green cards would shout about work
    // that is already done and bury the one row still asking for something.
    val body by animateColorAsState(
        targetValue = if (isGranted) Color(0xFF0A0A0A) else Color(0xFF101010),
        animationSpec = tween(300),
        label = "permBody"
    )
    val edge by animateColorAsState(
        targetValue = if (isGranted) Color(0xFF151515) else Color(0xFF262626),
        animationSpec = tween(300),
        label = "permEdge"
    )

    Surface(
        color = body,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, edge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The step number turns into a tick in the same circle, so the list keeps its own
            // score without a second column of status text beside it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isGranted) Color(0xFF66BB6A).copy(alpha = 0.12f)
                        else Color(0xFF7C6BFF).copy(alpha = 0.14f)
                    )
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Text(
                        text = "$step",
                        color = Color(0xFF7C6BFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isGranted) Color(0xFF787878) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = if (isGranted) Color(0xFF3E3E3E) else Color(0xFF6E6E6E),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            // Nothing to press once it is on, and the row keeps the width it had so the list
            // does not shift sideways as switches are granted.
            if (!isGranted) {
                Spacer(modifier = Modifier.width(14.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6BFF)),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        "Allow",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** One labelled stepper in the focus-session sheet. */
@Composable
private fun PomoDial(
    label: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    tint: Color,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF151515))
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        StepButton("\u2212", value > min, tint) { onChange(value - 1) }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.width(52.dp)
        ) {
            Text(
                text = "$value",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                color = Color(0xFF6A6A6A),
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        StepButton("+", value < max, tint) { onChange(value + 1) }
    }
}

/** A round tap target for the steppers, small enough to keep the sheet short. */
@Composable
private fun StepButton(glyph: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (enabled) tint.copy(alpha = 0.13f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = glyph,
            color = if (enabled) tint else Color(0xFF333333),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * One of the launcher's own screens on its shelf above the dock.
 *
 * Smaller than an app icon on purpose: these belong to the launcher rather than to the phone,
 * and sizing them the same would put them in competition with the apps the user chose.
 */

/**
 * The two halves of a locked-down drawer.
 *
 * Mid-session the drawer only lists what will actually open, which raises the obvious
 * question about the apps the user never picked. Naming the two groups answers it in place:
 * one is their whitelist, the other is what the launcher keeps open regardless -- the dialler,
 * the keyboard, the pickers.
 */
@Composable
private fun DrawerTabs(
    current: Int,
    allowedCount: Int,
    systemCount: Int,
    onPick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF0F0F0F))
            .padding(4.dp)
    ) {
        DrawerTab("Allowed", current == 0, allowedCount, Modifier.weight(1f)) { onPick(0) }
        DrawerTab("System", current == 1, systemCount, Modifier.weight(1f)) { onPick(1) }
    }
}

@Composable
private fun DrawerTab(
    label: String,
    selected: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = Color(0xFF64B5F6)
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.15f) else Color.Transparent,
        tween(180),
        label = "drawerTabBg"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp)
    ) {
        Text(
            if (count > 0) "$label  $count" else label,
            color = if (selected) accent else Color(0xFF6E6E6E),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ShelfTile(
    id: String,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    val label = when (id) {
        ACTION_HABITS -> "Streak"
        ACTION_TASKS -> "Target"
        else -> "Resource"
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                    onLongClick = { menuOpen = true }
                )
                .padding(vertical = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                when (id) {
                    ACTION_HABITS -> Text("\uD83D\uDD25", fontSize = 20.sp, textAlign = TextAlign.Center)
                    ACTION_TASKS -> AtomGlyph(size = 26.dp, spinning = true)
                    else -> BulbGlyph(size = 26.dp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                color = Color(0xFF7E7E7E),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(Color(0xFF141414))
        ) {
            DropdownMenuItem(
                text = { Text("Remove from Home", color = Color(0xFFE57373), fontSize = 13.sp) },
                onClick = { menuOpen = false; onRemove() }
            )
            if (index > 0) {
                DropdownMenuItem(
                    text = { Text("Move Left", color = Color.White, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onMove(-1) }
                )
            }
            if (index < count - 1) {
                DropdownMenuItem(
                    text = { Text("Move Right", color = Color.White, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onMove(1) }
                )
            }
        }
    }
}

@Composable
private fun OwnScreenTile(
    glyph: String,
    label: String,
    drawn: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // A fixed column, so the two labels sit on the same baseline whatever they say and
        // the pair stays centred as one unit.
        // The same column width and gap the dock uses, so the two rows sit on one grid.
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(30.dp)
        ) {
            if (drawn != null) drawn()
            else Text(text = glyph, fontSize = 20.sp, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            color = Color(0xFF7E7E7E),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
