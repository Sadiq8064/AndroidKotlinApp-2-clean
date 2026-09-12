package com.example.androidkotlinapp

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Which half of the cycle is running, if either. */
enum class PomoPhase { IDLE, FOCUS, BREAK, PAUSED, DONE }

/** Everything the screen needs to draw the ring. */
data class PomoState(
    val phase: PomoPhase,
    val session: Int,
    val totalSessions: Int,
    val remainingSeconds: Int,
    val phaseSeconds: Int
) {
    val running: Boolean get() = phase == PomoPhase.FOCUS || phase == PomoPhase.BREAK
    val paused: Boolean get() = phase == PomoPhase.PAUSED

    /** On screen at all -- running or held. */
    val onScreen: Boolean get() = running || paused

    /** 0f at the start of the current phase, 1f at its end. */
    val progress: Float
        get() = if (phaseSeconds <= 0) 0f
        else ((phaseSeconds - remainingSeconds).toFloat() / phaseSeconds).coerceIn(0f, 1f)
}

/**
 * Focus sessions inside a block session.
 *
 * The two are deliberately independent: a block session is the phone being locked down for a
 * stretch of the day, and focus sessions are how the user chooses to spend it. Finishing the
 * last focus session does not lift the block, and it is not meant to.
 *
 * Each phase also starts the phone's own clock timer, so the ring on screen and the alarm the
 * user actually hears are the same countdown rather than two that drift apart.
 */
object Pomodoro {

    private const val PREFS = "pomodoro_prefs"
    private const val KEY_PHASE = "phase"
    private const val KEY_SESSION = "session"
    private const val KEY_TOTAL = "total"
    private const val KEY_ENDS_AT = "ends_at"
    private const val KEY_FOCUS_MIN = "focus_min"
    private const val KEY_BREAK_MIN = "break_min"
    private const val KEY_PHASE_SECONDS = "phase_seconds"
    private const val KEY_PAUSED_FROM = "paused_from"
    private const val KEY_PAUSED_LEFT = "paused_left"

    /** Sensible starting point: the classic twenty-five and five, four times over. */
    const val DEFAULT_FOCUS_MIN = 25
    const val DEFAULT_BREAK_MIN = 5
    const val DEFAULT_SESSIONS = 4

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- reading

    fun state(context: Context): PomoState {
        val p = prefs(context)
        val phase = runCatching { PomoPhase.valueOf(p.getString(KEY_PHASE, "IDLE")!!) }
            .getOrDefault(PomoPhase.IDLE)
        if (phase == PomoPhase.PAUSED) {
            return PomoState(
                phase = PomoPhase.PAUSED,
                session = p.getInt(KEY_SESSION, 0),
                totalSessions = p.getInt(KEY_TOTAL, 0),
                remainingSeconds = p.getInt(KEY_PAUSED_LEFT, 0),
                phaseSeconds = p.getInt(KEY_PHASE_SECONDS, 0)
            )
        }

        val endsAt = p.getLong(KEY_ENDS_AT, 0L)
        val live = phase == PomoPhase.FOCUS || phase == PomoPhase.BREAK

        // A phase that ran out while nothing was ticking -- the block session ended, or the
        // process was killed -- is over, whatever the stored phase still says. Reporting it
        // as running would leave a countdown on screen that never moves.
        if (live && System.currentTimeMillis() >= endsAt) {
            return PomoState(PomoPhase.DONE, p.getInt(KEY_SESSION, 0), p.getInt(KEY_TOTAL, 0), 0, 0)
        }

        val remaining = if (live) {
            ((endsAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        } else {
            0
        }
        return PomoState(
            phase = phase,
            session = p.getInt(KEY_SESSION, 0),
            totalSessions = p.getInt(KEY_TOTAL, 0),
            remainingSeconds = remaining,
            phaseSeconds = p.getInt(KEY_PHASE_SECONDS, 0)
        )
    }

    fun lastConfig(context: Context): Triple<Int, Int, Int> {
        val p = prefs(context)
        return Triple(
            p.getInt(KEY_FOCUS_MIN, DEFAULT_FOCUS_MIN),
            p.getInt(KEY_BREAK_MIN, DEFAULT_BREAK_MIN),
            p.getInt(KEY_TOTAL, DEFAULT_SESSIONS).coerceAtLeast(1)
        )
    }

    // ---------------------------------------------------------------- driving

    fun start(context: Context, focusMin: Int, breakMin: Int, sessions: Int) {
        prefs(context).edit()
            .putInt(KEY_FOCUS_MIN, focusMin.coerceIn(1, 180))
            .putInt(KEY_BREAK_MIN, breakMin.coerceIn(0, 60))
            .putInt(KEY_TOTAL, sessions.coerceIn(1, 12))
            .putInt(KEY_SESSION, 1)
            .apply()
        beginPhase(context, PomoPhase.FOCUS, focusMin.coerceIn(1, 180))
        PomoNotifier.focusStarted(context, focusMin.coerceIn(1, 180))
    }

    /**
     * Holds the cycle where it is. The phone's timer is dismissed rather than left running --
     * an alarm that goes off during a pause would be announcing a phase that is not happening.
     */
    fun pause(context: Context) {
        val p = prefs(context)
        val current = state(context)
        if (!current.running) return
        p.edit()
            .putString(KEY_PHASE, PomoPhase.PAUSED.name)
            .putString(KEY_PAUSED_FROM, current.phase.name)
            .putInt(KEY_PAUSED_LEFT, current.remainingSeconds)
            .putLong(KEY_ENDS_AT, 0L)
            .apply()
        dismissClockTimer(context)
        PomoNotifier.showOngoingPaused(context, current.remainingSeconds)
    }

    fun resume(context: Context) {
        val p = prefs(context)
        if (p.getString(KEY_PHASE, "") != PomoPhase.PAUSED.name) return
        val from = runCatching { PomoPhase.valueOf(p.getString(KEY_PAUSED_FROM, "FOCUS")!!) }
            .getOrDefault(PomoPhase.FOCUS)
        val left = p.getInt(KEY_PAUSED_LEFT, 0).coerceAtLeast(1)
        p.edit()
            .putString(KEY_PHASE, from.name)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + left * 1000L)
            .apply()
        startClockTimer(context, left, if (from == PomoPhase.FOCUS) "Focus" else "Break")
    }

    /**
     * Ends the cycle early and keeps whatever focus was actually done.
     *
     * The part-finished stretch counts: someone who sat through eighteen minutes of a
     * twenty-five minute session did the eighteen minutes, and a record that threw them away
     * would be worth less than no record at all.
     */
    fun stop(context: Context) {
        // Nothing to reckon up here. Every second of focus was already written down as it
        // passed, so a session stopped half way has already kept its half.
        val p = prefs(context)

        // Say so, but only when a focus stretch was actually cut short -- stopping during a
        // break, or when nothing was running, is not worth a notification.
        val before = state(context)
        if (before.phase == PomoPhase.FOCUS) {
            val focused = (before.phaseSeconds - before.remainingSeconds).coerceAtLeast(0)
            PomoNotifier.focusStopped(context, focused)
        }

        p.edit()
            .putString(KEY_PHASE, PomoPhase.IDLE.name)
            .putLong(KEY_ENDS_AT, 0L)
            .putInt(KEY_SESSION, 0)
            .putInt(KEY_PHASE_SECONDS, 0)
            .putInt(KEY_PAUSED_LEFT, 0)
            .apply()
        dismissClockTimer(context)
        PomoNotifier.clearOngoing(context)
    }

    /**
     * Moves the cycle on when a phase runs out. Called once a second by the block session's
     * own timer, which is the only thing guaranteed to be awake while this is running.
     */
    fun tick(context: Context) {
        val p = prefs(context)
        val phase = runCatching { PomoPhase.valueOf(p.getString(KEY_PHASE, "IDLE")!!) }
            .getOrDefault(PomoPhase.IDLE)
        if (phase != PomoPhase.FOCUS && phase != PomoPhase.BREAK) return
        if (System.currentTimeMillis() < p.getLong(KEY_ENDS_AT, 0L)) return

        val session = p.getInt(KEY_SESSION, 1)
        val total = p.getInt(KEY_TOTAL, 1)
        val focusMin = p.getInt(KEY_FOCUS_MIN, DEFAULT_FOCUS_MIN)
        val breakMin = p.getInt(KEY_BREAK_MIN, DEFAULT_BREAK_MIN)

        if (phase == PomoPhase.FOCUS) {
            // The minutes themselves were counted as they were spent; this only marks that
            // one more stretch was seen through to the end.
            FocusStats.addCompletedSession(context)

            if (session >= total || breakMin <= 0) {
                if (session >= total) {
                    // The cycle is done. The block session is not -- it runs its own course.
                    p.edit()
                        .putString(KEY_PHASE, PomoPhase.DONE.name)
                        .putLong(KEY_ENDS_AT, 0L)
                        .apply()
                    PomoNotifier.cycleFinished(context, total)
                    PomoNotifier.clearOngoing(context)
                    return
                }
                // No break was asked for, so the next stretch follows straight on.
                p.edit().putInt(KEY_SESSION, session + 1).apply()
                beginPhase(context, PomoPhase.FOCUS, focusMin)
                PomoNotifier.focusStarted(context, focusMin)
                return
            }
            beginPhase(context, PomoPhase.BREAK, breakMin)
            // Says what was finished and what follows; the break's own greeting comes after.
            PomoNotifier.focusFinished(context, focusMin, breakMin)
        } else {
            p.edit().putInt(KEY_SESSION, session + 1).apply()
            beginPhase(context, PomoPhase.FOCUS, focusMin)
            PomoNotifier.breakFinished(context, focusMin)
        }
    }

    private fun beginPhase(context: Context, phase: PomoPhase, minutes: Int) {
        val seconds = minutes * 60
        prefs(context).edit()
            .putString(KEY_PHASE, phase.name)
            .putInt(KEY_PHASE_SECONDS, seconds)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + seconds * 1000L)
            .apply()
        startClockTimer(context, seconds, if (phase == PomoPhase.FOCUS) "Focus" else "Break")
        // The standing readout follows the phase, counting itself down from this end time.
        PomoNotifier.showOngoing(
            context,
            isFocus = phase == PomoPhase.FOCUS,
            endsAtMillis = System.currentTimeMillis() + seconds * 1000L
        )
    }

    /**
     * Starts the phone's own timer for this phase.
     *
     * SKIP_UI is what makes this bearable: without it the clock app opens on every phase
     * change and takes the screen. This phone's clock honours it, so the timer starts behind
     * the scenes and only its alarm is heard.
     */
    private fun startClockTimer(context: Context, seconds: Int, label: String) {
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // No clock app that takes the intent; the ring still counts down on its own.
            e.printStackTrace()
        }
    }

    /**
     * Restores the standing readout if the user swiped it away mid-phase. Cheap enough to sit
     * on the session's clock: it is a look at what is already on screen, not a rebuild.
     */
    fun keepNoticeAlive(context: Context) {
        val current = state(context)
        when {
            current.running -> PomoNotifier.ensureOngoing(
                context,
                isFocus = current.phase == PomoPhase.FOCUS,
                endsAtMillis = System.currentTimeMillis() + current.remainingSeconds * 1000L
            )
            current.paused -> PomoNotifier.ensureOngoingPaused(context, current.remainingSeconds)
        }
    }

    /** Clears the phone's timer, so a dismissed phase does not still ring. */
    private fun dismissClockTimer(context: Context) {
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** "25:00" or "1:05:00". */
    fun clock(totalSeconds: Int): String {
        val t = totalSeconds.coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
