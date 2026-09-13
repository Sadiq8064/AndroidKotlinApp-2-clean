package com.example.androidkotlinapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.androidkotlinapp.theme.AndroidKotlinAppTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  // Polled rather than event-driven: a session can start or end from the service, the alarm
  // ringer, or a face check, none of which are activities that could call
  // SessionLockdown themselves. A short poll while this screen is resumed is what closes that
  // gap without wiring a broadcast for every place session state can change.
  private val lockdownHandler = Handler(Looper.getMainLooper())
  private val lockdownPoll = object : Runnable {
    override fun run() {
      if (SessionLockdown.shouldBeLocked(this@MainActivity)) {
        SessionLockdown.engage(this@MainActivity)
      } else {
        SessionLockdown.release(this@MainActivity)
      }
      lockdownHandler.postDelayed(this, 1000L)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CloudSyncManager.syncWithCloud(this)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      AndroidKotlinAppTheme { Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    // "Set an alarm for 7" from the assistant, or the alarm slot in Settings, lands here.
    if (intent?.action == android.provider.AlarmClock.ACTION_SET_ALARM ||
        intent?.action == android.provider.AlarmClock.ACTION_SHOW_ALARMS
    ) {
      pendingRoute = ROUTE_ALARM
    }
    if (intent?.getBooleanExtra("open_habits_screen", false) == true) {
      openHabitsScreenAction = true
      intent.removeExtra("open_habits_screen")
    }
    // A notification names the screen it came from, so tapping it lands where the thing it is
    // about actually lives rather than on the home screen.
    intent?.getStringExtra(EXTRA_ROUTE)?.let { route ->
      pendingRoute = route
      intent.removeExtra(EXTRA_ROUTE)
    }
  }

  override fun onResume() {
    super.onResume()
    lockdownHandler.removeCallbacks(lockdownPoll)
    lockdownHandler.post(lockdownPoll)
    BlockerActivity.updateRandomQuote()
    // One chip fits under FOCUS, so which reminder gets it advances every time the launcher
    // is returned to. Done here rather than with a lifecycle observer in Compose: this
    // activity is the home screen and never really goes away, and onResume is the one signal
    // that reliably means "the user came back".
    homeReturnCount++

    // A sounding alarm owns the screen. Reopening the launcher while one rings -- after the
    // phone was turned off and on, say -- lands on the ring screen rather than the home
    // screen, so the only way past it is still Stop.
    if (AlarmRingService.ringing) {
      try {
        startActivity(
          Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmRingService.ringingId)
          }
        )
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    isActivityVisible = true
    Alarms.rescheduleAll(this)
    
    val endTime = FocusService.getSessionEndTime(this)
    if (endTime > System.currentTimeMillis()) {
        if (!FocusService.isRunning) {
            val serviceIntent = Intent(this, FocusService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        }
    }
  }

  override fun onPause() {
    super.onPause()
    lockdownHandler.removeCallbacks(lockdownPoll)
  }

  override fun onStop() {
    super.onStop()
    isActivityVisible = false
  }

  companion object {
    var isActivityVisible = false

    /**
     * A YouTube link the gate intercepted, waiting for the library screen to pick it up.
     * Cleared by whoever consumes it, so a stale link cannot reopen the add sheet later.
     */
    var pendingYouTubeLink: String? by androidx.compose.runtime.mutableStateOf(null)

    var openHabitsScreenAction by androidx.compose.runtime.mutableStateOf(false)

    /** Extra a notification carries to say which screen it belongs to. */
    const val EXTRA_ROUTE = "focus_route"
    const val ROUTE_HABITS = "habits"
    const val ROUTE_TARGET = "target"
    const val ROUTE_REMINDER = "reminder"
    const val ROUTE_RESOURCE = "resource"
    const val ROUTE_ALARM = "alarm"

    /** Set by a notification tap, consumed once by the home screen. */
    var pendingRoute: String? by androidx.compose.runtime.mutableStateOf(null)

    /** Incremented on every return to the launcher. Read by the home screen's reminder chip. */
    var homeReturnCount by androidx.compose.runtime.mutableStateOf(0)
  }
}
