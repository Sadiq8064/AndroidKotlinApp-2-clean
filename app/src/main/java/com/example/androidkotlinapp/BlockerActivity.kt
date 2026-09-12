package com.example.androidkotlinapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The shared state a focus session is watched through.
 *
 * This was once an Activity with a screen of its own. The session now draws on the home screen
 * instead, and the Activity went with it -- but its statics did not: they are what the service,
 * the launcher and the blocker all read to know whether a session is running, what quote is
 * showing, and whether an allowed app is being opened. Kept under the same name so every
 * caller reads the same as it always did.
 */
object BlockerActivity {

    var isActivityVisible = false
    var isSessionCompleted by mutableStateOf(false)

    /**
     * Set immediately before opening an allowed app. Flipping it true also opens a short
     * grace window in [FocusService] so a slow-starting app -- a game working through
     * splash screens and ad SDKs -- is not sampled mid-launch, judged blocked and killed.
     */
    var isLaunchingWhitelistedApp = false
        set(value) {
            field = value
            if (value) FocusService.noteAllowedAppLaunch()
        }

    var refreshTrigger by mutableStateOf(0)
    var totalFocusDurationString by mutableStateOf("25 min")
    const val ACTION_CLOSE_BLOCKER = "com.example.androidkotlinapp.ACTION_CLOSE_BLOCKER"
    const val ACTION_TIMER_TICK = "com.example.androidkotlinapp.ACTION_TIMER_TICK"

    var currentQuote by mutableStateOf("")
    var quoteSequenceIndex = 0

    val placementQuotes = listOf(
        "Every problem solved is one step to placement. 💻",
        "DSA today. Dream package tomorrow. 💰",
        "Someone else is coding right now. ⚡",
        "Your offer letter is earned daily. 📄",
        "Master DSA. Master interviews. 🎯",
        "Projects speak louder than CGPA. 🚀",
        "Code until confidence replaces fear. 💪",
        "Become the engineer companies fight for. 🏆",
        "Build skills. The package will follow. 💼",
        "Be what your resume claims. 📑",
        "Learn concepts, not shortcuts. 🧠",
        "One Git commit closer to success. ✅",
        "Your next project could change your life. 🚀",
        "Strong fundamentals. Strong future. 🏗️",
        "Debug today. Lead tomorrow. 🐞",
        "Interview preparation starts now. 🎤",
        "Outwork your competition. 🔥",
        "Practice until interviews feel easy. 😌",
        "Placement is earned, not wished for. 🎯",
        "Solve one more question. 🧩",
        "Keep coding. Stay dangerous. ⚡",
        "Every line of code compounds. 📈",
        "Consistency beats last-minute preparation. 🔄",
        "Become impossible to reject. 🌟",
        "Your future employer is waiting. 🚪"
    )

    val cityQuotes = listOf(
        "Picture yourself in Bangalore. 🌆",
        "Hyderabad is waiting for your talent. 🏙️",
        "Work where innovation happens. 🚀",
        "Dream beyond your hometown. 🌍",
        "Your passport deserves more stamps. ✈️",
        "Think global. Build global. 🌎",
        "Abroad starts with today's effort. 🛫",
        "The best engineers never stop learning. 📚",
        "Your dream city rewards hard work. 🌃",
        "Code today. Travel tomorrow. 🌍",
        "Big cities need big skills. 🏢",
        "Your future office is being built. 💼",
        "Work with the world's best. 🌟"
    )

    val timeMindsetQuotes = listOf(
        "Your time is non-refundable. ⏰",
        "Time is ticking. Focus. ⏳",
        "Today's effort becomes tomorrow's salary. 💰",
        "Don't trade your future for scrolling. 📵",
        "One focused hour beats a distracted day. 🎯",
        "Start now. You'll thank yourself later. ⚡",
        "Every minute compounds. 📈",
        "Tomorrow is built today. 📅",
        "Guard your time like money. 💎",
        "Execution beats excuses. 🏃",
        "Procrastination steals opportunities. 🚫",
        "Today's discipline creates tomorrow's freedom. 🔑",
        "Focus now. Celebrate later. 🎉",
        "Time rewards consistency. 🔄",
        "Don't waste your prime years. ⌛",
        "Every second is an investment. 📊",
        "You can't pause time. ⌚",
        "Do it now. Later becomes never. 🚀",
        "The clock doesn't wait. 🕰️",
        "Small efforts. Massive results. 🌱",
        "Stay humble. Stay hungry. 🌱",
        "Focus is your superpower. ⚡",
        "Never stop learning. 📚",
        "Discipline creates freedom. 🔑",
        "Be better than yesterday. 📈",
        "Success loves consistency. ❤️",
        "Stay blind to distractions. 🙈",
        "Win quietly. 🏆",
        "Growth begins outside comfort. 🧗",
        "Focus. Execute. Repeat. 🔄"
    )

    val quotes = placementQuotes + cityQuotes + timeMindsetQuotes

    fun updateRandomQuote() {
        if (quotes.isNotEmpty()) {
            val nextQuote = when (quoteSequenceIndex) {
                0 -> quotes.random() // 1. Random
                1 -> if (cityQuotes.isNotEmpty()) cityQuotes.random() else quotes.random() // 2. City
                2 -> quotes.random() // 3. Random
                3 -> if (placementQuotes.isNotEmpty()) placementQuotes.random() else quotes.random() // 4. Career
                4 -> quotes.random() // 5. Random
                else -> quotes.random()
            }
            currentQuote = nextQuote
            quoteSequenceIndex = (quoteSequenceIndex + 1) % 5
        }
    }
}
