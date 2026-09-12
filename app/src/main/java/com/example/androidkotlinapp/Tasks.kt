package com.example.androidkotlinapp

import android.content.Context
import android.content.ContentValues
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One step towards a goal. */
data class GoalTask(
    val id: String,
    val title: String,
    val done: Boolean
)

/** Something to finish, with a day it must be finished by. */
data class Goal(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val tasks: List<GoalTask>,
    val createdAt: Long
) {
    val total: Int get() = tasks.size
    val completed: Int get() = tasks.count { it.done }

    /** 0f to 1f. A goal with no tasks yet reads as untouched rather than as finished. */
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total

    val isDone: Boolean get() = total > 0 && completed == total
}

/** A date to be reminded of, and nothing more. */
data class Reminder(
    val id: String,
    val title: String,
    val dateMs: Long,
    val createdAt: Long,
    /** Comes round again on the same day every year -- birthdays, anniversaries, renewals. */
    val repeatYearly: Boolean = false,
    /** How many days ahead it starts appearing on the home screen. */
    val showBeforeDays: Int = 7
) {
    /**
     * The next time this falls, which for a yearly reminder is not the date it was created on.
     *
     * A birthday set in 2024 should read "in 40 days", not "600 days ago" -- so the stored date
     * supplies the day and month, and the year is whichever one is still ahead.
     */
    val nextOccurrenceMs: Long
        get() {
            if (!repeatYearly) return dateMs
            val today = Tasks.startOfDay(System.currentTimeMillis())
            val cal = Calendar.getInstance().apply { timeInMillis = Tasks.startOfDay(dateMs) }
            val now = Calendar.getInstance().apply { timeInMillis = today }
            cal.set(Calendar.YEAR, now.get(Calendar.YEAR))
            if (cal.timeInMillis < today) cal.add(Calendar.YEAR, 1)
            return cal.timeInMillis
        }
}

/**
 * Goals and reminders, kept in the app's database.
 *
 * A goal owns an ordered list of steps, which is a relationship rather than a value, so it
 * lives in two tables joined on the goal's id -- ticking one step writes one row instead of
 * rewriting every goal the user has.
 *
 * Both are counted in whole days rather than in hours: a deadline is a day on a calendar, not
 * a moment, and "3 days left" is what someone actually wants to know. Every date is pinned to
 * the start of its day so that arithmetic never drifts by an hour either way.
 */
object Tasks {

    private fun db(context: Context) = FocusDatabaseHelper(context)

    private fun newId() = System.currentTimeMillis().toString(36) +
        (0..9999).random().toString(36)

    // ---------------------------------------------------------------- days

    fun startOfDay(atMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = atMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Whole days from today until [atMs]. Zero means today, negative means it has passed.
     */
    fun daysUntil(atMs: Long): Int {
        val today = startOfDay(System.currentTimeMillis())
        val then = startOfDay(atMs)
        return ((then - today) / (24 * 60 * 60 * 1000L)).toInt()
    }

    /** "3 days left", "Today", "Tomorrow", "2 days ago". */
    fun countdown(atMs: Long): String = when (val d = daysUntil(atMs)) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        in 2..Int.MAX_VALUE -> "$d days left"
        else -> "${-d} days ago"
    }

    fun dateLabel(atMs: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(atMs))

    fun shortDate(atMs: Long): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(atMs))

    /** How far through a goal's span today sits, 0f to 1f. */
    fun elapsedFraction(startMs: Long, endMs: Long): Float {
        val start = startOfDay(startMs)
        val end = startOfDay(endMs)
        if (end <= start) return if (System.currentTimeMillis() >= end) 1f else 0f
        val now = startOfDay(System.currentTimeMillis())
        return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    // ---------------------------------------------------------------- goals

    fun goals(context: Context): List<Goal> {
        val out = mutableListOf<Goal>()
        try {
            db(context).readableDatabase.use { d ->
                d.rawQuery(
                    "SELECT id, title, start_ms, end_ms, created_at FROM " +
                        "${FocusDatabaseHelper.TABLE_GOALS}",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        out.add(
                            Goal(
                                id = id,
                                title = c.getString(1),
                                startMs = c.getLong(2),
                                endMs = c.getLong(3),
                                tasks = stepsFor(d, id),
                                createdAt = c.getLong(4)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Finished goals sink, live ones stay up: a goal with nothing left to do is a record,
        // not a thing to act on. Among the live ones, whichever ends soonest sits at the top,
        // split by the earlier start date when two share a deadline. Nothing is ever deleted
        // by this -- a finished goal keeps its place at the bottom of the list.
        return out.sortedWith(compareBy({ it.isDone }, { it.endMs }, { it.startMs }))
    }

    private fun stepsFor(d: android.database.sqlite.SQLiteDatabase, goalId: String): List<GoalTask> {
        val steps = mutableListOf<GoalTask>()
        d.rawQuery(
            "SELECT id, title, done FROM ${FocusDatabaseHelper.TABLE_GOAL_STEPS} " +
                "WHERE goal_id = ? ORDER BY position ASC",
            arrayOf(goalId)
        ).use { c ->
            while (c.moveToNext()) {
                steps.add(GoalTask(c.getString(0), c.getString(1), c.getInt(2) == 1))
            }
        }
        return steps
    }

    fun addGoal(
        context: Context,
        title: String,
        startMs: Long,
        endMs: Long,
        taskTitles: List<String>
    ): Goal {
        val goal = Goal(
            id = newId(),
            title = title.trim(),
            startMs = startOfDay(startMs),
            endMs = startOfDay(endMs),
            tasks = taskTitles.filter { it.isNotBlank() }
                .map { GoalTask(newId(), it.trim(), false) },
            createdAt = System.currentTimeMillis()
        )
        try {
            db(context).writableDatabase.use { d ->
                d.insertWithOnConflict(
                    FocusDatabaseHelper.TABLE_GOALS,
                    null,
                    ContentValues().apply {
                        put("id", goal.id)
                        put("title", goal.title)
                        put("start_ms", goal.startMs)
                        put("end_ms", goal.endMs)
                        put("created_at", goal.createdAt)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                goal.tasks.forEachIndexed { i, t -> insertStep(d, goal.id, t, i) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return goal
    }

    private fun insertStep(
        d: android.database.sqlite.SQLiteDatabase,
        goalId: String,
        step: GoalTask,
        position: Int
    ) {
        d.insertWithOnConflict(
            FocusDatabaseHelper.TABLE_GOAL_STEPS,
            null,
            ContentValues().apply {
                put("id", step.id)
                put("goal_id", goalId)
                put("title", step.title)
                put("done", if (step.done) 1 else 0)
                put("position", position)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Rewrites a goal and the order of its steps -- what a drag-to-reorder needs. */
    fun updateGoal(context: Context, goal: Goal) {
        try {
            db(context).writableDatabase.use { d ->
                d.beginTransaction()
                try {
                    d.update(
                        FocusDatabaseHelper.TABLE_GOALS,
                        ContentValues().apply {
                            put("title", goal.title)
                            put("start_ms", goal.startMs)
                            put("end_ms", goal.endMs)
                        },
                        "id = ?",
                        arrayOf(goal.id)
                    )
                    d.delete(
                        FocusDatabaseHelper.TABLE_GOAL_STEPS,
                        "goal_id = ?",
                        arrayOf(goal.id)
                    )
                    goal.tasks.forEachIndexed { i, t -> insertStep(d, goal.id, t, i) }
                    d.setTransactionSuccessful()
                } finally {
                    d.endTransaction()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeGoal(context: Context, goalId: String) {
        try {
            db(context).writableDatabase.use { d ->
                // Steps go with it by way of the cascade on the foreign key.
                d.delete(FocusDatabaseHelper.TABLE_GOALS, "id = ?", arrayOf(goalId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addTask(context: Context, goalId: String, title: String) {
        if (title.isBlank()) return
        try {
            db(context).writableDatabase.use { d ->
                val next = d.rawQuery(
                    "SELECT IFNULL(MAX(position), -1) + 1 FROM " +
                        "${FocusDatabaseHelper.TABLE_GOAL_STEPS} WHERE goal_id = ?",
                    arrayOf(goalId)
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                insertStep(d, goalId, GoalTask(newId(), title.trim(), false), next)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** One row written, rather than every goal rewritten. */
    fun toggleTask(context: Context, goalId: String, taskId: String) {
        try {
            db(context).writableDatabase.use { d ->
                d.execSQL(
                    "UPDATE ${FocusDatabaseHelper.TABLE_GOAL_STEPS} " +
                        "SET done = CASE done WHEN 1 THEN 0 ELSE 1 END WHERE id = ?",
                    arrayOf(taskId)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeTask(context: Context, goalId: String, taskId: String) {
        try {
            db(context).writableDatabase.use { d ->
                d.delete(FocusDatabaseHelper.TABLE_GOAL_STEPS, "id = ?", arrayOf(taskId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- reminders

    fun reminders(context: Context): List<Reminder> {
        val out = mutableListOf<Reminder>()
        try {
            db(context).readableDatabase.use { d ->
                d.rawQuery(
                    "SELECT id, title, date_ms, created_at, " +
                        "IFNULL(repeat_yearly, 0), IFNULL(show_before_days, 7) FROM " +
                        "${FocusDatabaseHelper.TABLE_REMINDERS}",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        out.add(
                            Reminder(
                                id = c.getString(0),
                                title = c.getString(1),
                                dateMs = c.getLong(2),
                                createdAt = c.getLong(3),
                                repeatYearly = c.getInt(4) == 1,
                                showBeforeDays = c.getInt(5)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Still to come first, soonest at the top. Dates that have passed sink below them,
        // most recent first, and are kept rather than removed.
        val today = startOfDay(System.currentTimeMillis())
        val (upcoming, past) = out.partition { it.nextOccurrenceMs >= today }
        return upcoming.sortedBy { it.nextOccurrenceMs } +
            past.sortedByDescending { it.nextOccurrenceMs }
    }

    fun addReminder(
        context: Context,
        title: String,
        dateMs: Long,
        repeatYearly: Boolean = false,
        showBeforeDays: Int = 7,
        id: String? = null
    ): Reminder {
        val reminder = Reminder(
            id = id ?: newId(),
            title = title.trim(),
            dateMs = startOfDay(dateMs),
            createdAt = System.currentTimeMillis(),
            repeatYearly = repeatYearly,
            showBeforeDays = showBeforeDays
        )
        try {
            db(context).writableDatabase.use { d ->
                d.insertWithOnConflict(
                    FocusDatabaseHelper.TABLE_REMINDERS,
                    null,
                    ContentValues().apply {
                        put("id", reminder.id)
                        put("title", reminder.title)
                        put("date_ms", reminder.dateMs)
                        put("created_at", reminder.createdAt)
                        put("repeat_yearly", if (reminder.repeatYearly) 1 else 0)
                        put("show_before_days", reminder.showBeforeDays)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return reminder
    }

    /**
     * Every reminder still ahead, soonest first.
     *
     * All of them are eligible for the home screen -- a reminder stays on show until its day
     * has been and gone. Only one fits at a time, so the home screen rotates through this list
     * rather than picking a winner and hiding the rest.
     */
    fun upcomingReminders(context: Context): List<Reminder> {
        val today = startOfDay(System.currentTimeMillis())
        return reminders(context)
            .filter { it.nextOccurrenceMs >= today }
            .sortedBy { it.nextOccurrenceMs }
    }

    fun removeReminder(context: Context, id: String) {
        try {
            db(context).writableDatabase.use { d ->
                d.delete(FocusDatabaseHelper.TABLE_REMINDERS, "id = ?", arrayOf(id))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * Writes a goal back exactly as it was, id and step order included.
     *
     * Separate from [addGoal] because that one mints a new id and stamps a fresh createdAt --
     * right for something being made, wrong for something coming home.
     */
    fun restoreGoal(context: Context, goal: Goal) {
        try {
            db(context).writableDatabase.use { d ->
                d.beginTransaction()
                try {
                    d.insertWithOnConflict(
                        FocusDatabaseHelper.TABLE_GOALS,
                        null,
                        ContentValues().apply {
                            put("id", goal.id)
                            put("title", goal.title)
                            put("start_ms", goal.startMs)
                            put("end_ms", goal.endMs)
                            put("created_at", goal.createdAt)
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )
                    d.delete(FocusDatabaseHelper.TABLE_GOAL_STEPS, "goal_id = ?", arrayOf(goal.id))
                    goal.tasks.forEachIndexed { i, t -> insertStep(d, goal.id, t, i) }
                    d.setTransactionSuccessful()
                } finally {
                    d.endTransaction()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreReminder(context: Context, reminder: Reminder) {
        try {
            db(context).writableDatabase.use { d ->
                d.insertWithOnConflict(
                    FocusDatabaseHelper.TABLE_REMINDERS,
                    null,
                    ContentValues().apply {
                        put("id", reminder.id)
                        put("title", reminder.title)
                        put("date_ms", reminder.dateMs)
                        put("created_at", reminder.createdAt)
                        put("repeat_yearly", if (reminder.repeatYearly) 1 else 0)
                        put("show_before_days", reminder.showBeforeDays)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
