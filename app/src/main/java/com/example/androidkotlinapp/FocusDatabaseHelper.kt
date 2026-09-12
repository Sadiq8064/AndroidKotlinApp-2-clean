package com.example.androidkotlinapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * A habit the user has committed to performing every day.
 *
 * Habits replace the old "Goals" task manager. Every habit that exists counts -- there is
 * no per-session selection -- so a focus session cannot start until at least one has been
 * created, and any habit left unperformed for the day costs an extra hour of session time.
 *
 * [createdDate] is the IST day the habit was created and is where its activity grid begins.
 */
data class HabitRecord(
    val id: Int,
    val emoji: String,
    val text: String,
    val displayOrder: Int,
    val createdDate: String,
    val createdAt: Long,
    val synced: Int
)

class FocusDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "focus_analytics.db"
        private const val DATABASE_VERSION = 13

        /**
         * Session history table, dropped in v5. Sessions are no longer recorded at all --
         * the timer runs and nothing about it is kept -- so the table only exists here as
         * the name the upgrade path has to clean up.
         */
        private const val TABLE_LEGACY_SESSIONS = "focus_sessions"

        // Tasks Table Constants
        private const val TABLE_TASKS = "tasks"
        private const val COL_TASK_ID = "id"
        private const val COL_TASK_TEXT = "text"
        private const val COL_TASK_COMPLETED = "completed"
        private const val COL_TASK_SELECTED = "selected_for_session"
        private const val COL_TASK_SESSION_ID = "session_id"
        private const val COL_TASK_DATE_COMPLETED = "date_completed"
        private const val COL_TASK_ORDER = "display_order"
        private const val COL_TASK_SYNCED = "synced"
        private const val COL_TASK_TIMESTAMP = "timestamp"

        // Habits Table Constants
        private const val TABLE_HABITS = "habits"
        private const val COL_HABIT_ID = "id"
        private const val COL_HABIT_EMOJI = "emoji"
        private const val COL_HABIT_TEXT = "text"
        private const val COL_HABIT_ORDER = "display_order"
        private const val COL_HABIT_CREATED_DATE = "created_date"
        private const val COL_HABIT_CREATED_AT = "created_at"
        private const val COL_HABIT_SYNCED = "synced"

        const val DEFAULT_HABIT_EMOJI = "🎯"

        // One row per (habit, day) the habit was actually performed.
        private const val TABLE_HABIT_DONE = "habit_completions"
        private const val COL_DONE_HABIT_ID = "habit_id"
        private const val COL_DONE_DATE = "date_string"
        private const val COL_DONE_AT = "completed_at"

        // One row per (habit, day) a punishment was handed out. The UNIQUE constraint is
        // what makes a penalty irreversible and un-repeatable: it can never be applied
        // twice for the same habit on the same day, and nothing in the app deletes rows.
        private const val TABLE_HABIT_PENALTY = "habit_penalties"
        private const val COL_PENALTY_HABIT_ID = "habit_id"
        private const val COL_PENALTY_DATE = "date_string"
        private const val COL_PENALTY_SECONDS = "added_seconds"
        private const val COL_PENALTY_AT = "applied_at"

        /** Extra time added to a running session for each habit missed in a day. */
        const val PENALTY_SECONDS_PER_MISSED_HABIT = 3600

        /**
         * One whole day added for a day in which any habit went undone.
         *
         * Charged per day, not per habit: missing one of three habits and missing all three
         * both cost the same day, because the thing being punished is the broken day.
         */
        const val PENALTY_SECONDS_PER_MISSED_DAY = 86_400

        /**
         * A debt is a day that was missed, and whether its cost has been served yet.
         *
         * Kept apart from the running session so that a day missed with no session going is
         * still remembered, and collected the next time one starts. [COL_DEBT_SERVED] is the
         * whole mechanism: 0 means owed, 1 means it has already been added to a session.
         */
        const val TABLE_HABIT_DEBT = "habit_day_debt"
        const val COL_DEBT_DATE = "date_string"
        const val COL_DEBT_SECONDS = "owed_seconds"
        const val COL_DEBT_SERVED = "served"
        const val COL_DEBT_MISSED = "missed_names"
        const val COL_DEBT_AT = "recorded_at"

        /**
         * The furthest back a sweep will look for days it never settled.
         *
         * Without a bound, a phone left alone for a month would come back owing a month of
         * session time, which is a punishment nobody would serve -- they would uninstall the
         * app instead, which punishes the habit rather than the miss.
         */
        const val DEBT_SWEEP_MAX_DAYS = 7

        // Goals, their steps, and dated reminders. Added in v6, when these moved out of
        // preferences: a goal owns an ordered list of steps, which is a relationship a flat
        // key-value file can only fake by serialising the whole thing on every tick.
        /**
         * The dock, in the order the tiles sit in.
         *
         * A row per tile rather than one comma-joined string: the order is data, and a table
         * that states it is a table that can be reordered, read and synced without every
         * caller having to agree on a separator.
         */
        /**
         * Named folders in the Resource app.
         *
         * Videos and links used to live on two separate shelves, split by what kind of thing
         * they were -- which is never how anyone actually looks for them. A collection groups
         * by subject instead, so "System Design" holds the playlist and the article together.
         */
        /** Alarms. Days are stored as a comma-joined list of Calendar day constants. */
        const val TABLE_ALARMS = "alarms"

        const val TABLE_COLLECTIONS = "collections"
        const val COL_COLLECTION_ID = "id"
        const val COL_COLLECTION_NAME = "name"
        const val COL_COLLECTION_POSITION = "position"
        const val COL_COLLECTION_CREATED = "created_at"
        const val COL_COLLECTION_EMOJI = "emoji"

        const val TABLE_HOME_APPS = "home_apps"
        const val COL_HOME_PACKAGE = "package_name"
        const val COL_HOME_POSITION = "position"

        /**
         * Small named values that have no table of their own.
         *
         * Where a flag like "has the dock ever been set up" lives, so that question can be
         * answered from the database rather than from whether a preference key exists.
         */
        const val TABLE_SETTINGS = "settings"
        const val COL_SETTING_KEY = "key"
        const val COL_SETTING_VALUE = "value"

        const val TABLE_GOALS = "goals"
        const val TABLE_GOAL_STEPS = "goal_steps"
        const val TABLE_REMINDERS = "reminders"
    }

    private fun createHabitTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HABITS ("
                + "$COL_HABIT_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COL_HABIT_EMOJI TEXT DEFAULT '$DEFAULT_HABIT_EMOJI', "
                + "$COL_HABIT_TEXT TEXT NOT NULL, "
                + "$COL_HABIT_ORDER INTEGER DEFAULT 0, "
                + "$COL_HABIT_CREATED_DATE TEXT, "
                + "$COL_HABIT_CREATED_AT INTEGER, "
                + "$COL_HABIT_SYNCED INTEGER DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HABIT_DONE ("
                + "$COL_DONE_HABIT_ID INTEGER NOT NULL, "
                + "$COL_DONE_DATE TEXT NOT NULL, "
                + "$COL_DONE_AT INTEGER, "
                + "PRIMARY KEY ($COL_DONE_HABIT_ID, $COL_DONE_DATE))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HABIT_PENALTY ("
                + "$COL_PENALTY_HABIT_ID INTEGER NOT NULL, "
                + "$COL_PENALTY_DATE TEXT NOT NULL, "
                + "$COL_PENALTY_SECONDS INTEGER, "
                + "$COL_PENALTY_AT INTEGER, "
                + "PRIMARY KEY ($COL_PENALTY_HABIT_ID, $COL_PENALTY_DATE))"
        )
        createDebtTable(db)
    }

    private fun createDebtTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HABIT_DEBT ("
                + "$COL_DEBT_DATE TEXT PRIMARY KEY, "
                + "$COL_DEBT_SECONDS INTEGER NOT NULL, "
                + "$COL_DEBT_SERVED INTEGER NOT NULL DEFAULT 0, "
                + "$COL_DEBT_MISSED TEXT, "
                + "$COL_DEBT_AT INTEGER)"
        )
    }

    private fun createAlarmTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_ALARMS ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "hour INTEGER NOT NULL, "
                + "minute INTEGER NOT NULL, "
                + "label TEXT DEFAULT '', "
                + "days TEXT DEFAULT '', "
                + "enabled INTEGER NOT NULL DEFAULT 1, "
                + "special INTEGER NOT NULL DEFAULT 0, "     // the one habit alarm
                + "duration_days INTEGER NOT NULL DEFAULT 0, " // 7/21/30 for a special alarm
                + "created_at INTEGER NOT NULL DEFAULT 0, "
                + "face_unlock INTEGER NOT NULL DEFAULT 0)"
        )
    }

    private fun createCollectionTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_COLLECTIONS ("
                + "$COL_COLLECTION_ID TEXT PRIMARY KEY, "
                + "$COL_COLLECTION_NAME TEXT NOT NULL, "
                + "$COL_COLLECTION_POSITION INTEGER NOT NULL DEFAULT 0, "
                + "$COL_COLLECTION_CREATED INTEGER NOT NULL, "
                + "$COL_COLLECTION_EMOJI TEXT DEFAULT '')"
        )
    }

    private fun createLauncherTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HOME_APPS ("
                + "$COL_HOME_PACKAGE TEXT PRIMARY KEY, "
                + "$COL_HOME_POSITION INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_home_position ON $TABLE_HOME_APPS($COL_HOME_POSITION)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS ("
                + "$COL_SETTING_KEY TEXT PRIMARY KEY, "
                + "$COL_SETTING_VALUE TEXT)"
        )
    }

    private fun createGoalTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_GOALS ("
                + "id TEXT PRIMARY KEY, "
                + "title TEXT NOT NULL, "
                + "start_ms INTEGER NOT NULL, "
                + "end_ms INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL)"
        )
        // Steps belong to their goal and go with it: ON DELETE CASCADE means removing a goal
        // can never leave orphaned rows behind for a later query to trip over.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_GOAL_STEPS ("
                + "id TEXT PRIMARY KEY, "
                + "goal_id TEXT NOT NULL, "
                + "title TEXT NOT NULL, "
                + "done INTEGER DEFAULT 0, "
                + "position INTEGER NOT NULL, "
                + "FOREIGN KEY(goal_id) REFERENCES $TABLE_GOALS(id) ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_steps_goal ON $TABLE_GOAL_STEPS(goal_id, position)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_REMINDERS ("
                + "id TEXT PRIMARY KEY, "
                + "title TEXT NOT NULL, "
                + "date_ms INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL, "
                + "repeat_yearly INTEGER NOT NULL DEFAULT 0, "
                + "show_before_days INTEGER NOT NULL DEFAULT 7)"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Off by default on Android, and the steps table relies on it to clean up after a
        // deleted goal.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTasksTable = ("CREATE TABLE " + TABLE_TASKS + " ("
                + COL_TASK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TASK_TEXT + " TEXT, "
                + COL_TASK_COMPLETED + " INTEGER DEFAULT 0, "
                + COL_TASK_SELECTED + " INTEGER DEFAULT 0, "
                + COL_TASK_SESSION_ID + " TEXT, "
                + COL_TASK_DATE_COMPLETED + " TEXT, "
                + COL_TASK_ORDER + " INTEGER, "
                + COL_TASK_SYNCED + " INTEGER DEFAULT 0, "
                + COL_TASK_TIMESTAMP + " INTEGER)")
        db.execSQL(createTasksTable)

        createHabitTables(db)
        createGoalTables(db)
        createLauncherTables(db)
        createCollectionTable(db)
        createAlarmTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 13) {
            listOf(
                "special INTEGER NOT NULL DEFAULT 0",
                "duration_days INTEGER NOT NULL DEFAULT 0",
                "created_at INTEGER NOT NULL DEFAULT 0",
                "face_unlock INTEGER NOT NULL DEFAULT 0"
            ).forEach { col ->
                try { db.execSQL("ALTER TABLE $TABLE_ALARMS ADD COLUMN $col") }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
        if (oldVersion < 12) {
            try {
                createAlarmTable(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 11) {
            // Repeating dates, and how far ahead a reminder starts showing on the home screen.
            // Added one at a time because SQLite has no multi-column ALTER, and each is wrapped
            // separately so a column that already exists cannot stop the others landing.
            listOf(
                "repeat_yearly INTEGER NOT NULL DEFAULT 0",
                "show_before_days INTEGER NOT NULL DEFAULT 7"
            ).forEach { column ->
                try {
                    db.execSQL("ALTER TABLE $TABLE_REMINDERS ADD COLUMN $column")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (oldVersion < 10) {
            try {
                createCollectionTable(db)
                // ALTER on a table that may have just been created is harmless; on one that
                // already existed it is the whole point.
                db.execSQL(
                    "ALTER TABLE $TABLE_COLLECTIONS ADD COLUMN $COL_COLLECTION_EMOJI TEXT DEFAULT ''"
                )
            } catch (e: Exception) {
                // Thrown when the column is already there, which is fine.
                e.printStackTrace()
            }
        }
        if (oldVersion < 9) {
            try {
                createCollectionTable(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 8) {
            try {
                createLauncherTables(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 7) {
            try {
                createDebtTable(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 6) {
            try {
                createGoalTables(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 3) {
            try {
                val createTasksTable = ("CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " ("
                        + COL_TASK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_TASK_TEXT + " TEXT, "
                        + COL_TASK_COMPLETED + " INTEGER DEFAULT 0, "
                        + COL_TASK_SELECTED + " INTEGER DEFAULT 0, "
                        + COL_TASK_SESSION_ID + " TEXT, "
                        + COL_TASK_DATE_COMPLETED + " TEXT, "
                        + COL_TASK_ORDER + " INTEGER, "
                        + COL_TASK_SYNCED + " INTEGER DEFAULT 0, "
                        + COL_TASK_TIMESTAMP + " INTEGER)")
                db.execSQL(createTasksTable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 4) {
            try {
                createHabitTables(db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 5) {
            // Throws away every session an older build recorded, on purpose.
            try {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_LEGACY_SESSIONS")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // Habits Database Operations
    //
    // These deliberately do not call db.close(): the penalty checker runs on the
    // FocusService timer thread while the UI reads the same tables, and closing the
    // shared helper database out from under a live cursor crashes the reader.
    // ==========================================

    fun insertHabit(emoji: String, text: String, createdDate: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_HABIT_EMOJI, emoji.ifBlank { DEFAULT_HABIT_EMOJI })
            put(COL_HABIT_TEXT, text)
            put(COL_HABIT_ORDER, getAllHabits().size)
            put(COL_HABIT_CREATED_DATE, createdDate)
            put(COL_HABIT_CREATED_AT, System.currentTimeMillis())
            put(COL_HABIT_SYNCED, 0)
        }
        return db.insert(TABLE_HABITS, null, values)
    }

    fun getAllHabits(): List<HabitRecord> {
        val list = mutableListOf<HabitRecord>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_HABIT_ID, $COL_HABIT_EMOJI, $COL_HABIT_TEXT, $COL_HABIT_ORDER, "
                + "$COL_HABIT_CREATED_DATE, $COL_HABIT_CREATED_AT, $COL_HABIT_SYNCED FROM $TABLE_HABITS "
                + "ORDER BY $COL_HABIT_ORDER ASC, $COL_HABIT_ID ASC",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    HabitRecord(
                        id = cursor.getInt(0),
                        emoji = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: DEFAULT_HABIT_EMOJI,
                        text = cursor.getString(2) ?: "",
                        displayOrder = cursor.getInt(3),
                        createdDate = cursor.getString(4) ?: "",
                        createdAt = cursor.getLong(5),
                        synced = cursor.getInt(6)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    /** Renaming a habit is allowed at any time, including mid-session. */
    fun updateHabit(id: Int, emoji: String, text: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_HABIT_EMOJI, emoji.ifBlank { DEFAULT_HABIT_EMOJI })
            put(COL_HABIT_TEXT, text)
            put(COL_HABIT_SYNCED, 0)
        }
        db.update(TABLE_HABITS, values, "$COL_HABIT_ID = ?", arrayOf(id.toString()))
    }

    /**
     * Deleting a habit also drops its completion history, but never its penalties:
     * a punishment that has already been served stays on the record.
     */
    fun deleteHabit(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_HABITS, "$COL_HABIT_ID = ?", arrayOf(id.toString()))
        db.delete(TABLE_HABIT_DONE, "$COL_DONE_HABIT_ID = ?", arrayOf(id.toString()))
    }

    /** Every habit that exists is in force; there is no per-session opt-in. */
    fun getActiveHabits(): List<HabitRecord> = getAllHabits()

    /** All IST days on which [habitId] was performed, used for streaks and the activity grid. */
    fun getCompletionDatesForHabit(habitId: Int): Set<String> {
        val set = mutableSetOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_DONE_DATE FROM $TABLE_HABIT_DONE WHERE $COL_DONE_HABIT_ID = ?",
            arrayOf(habitId.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                cursor.getString(0)?.let { set.add(it) }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return set
    }

    fun setHabitDone(habitId: Int, dateString: String, done: Boolean) {
        val db = this.writableDatabase
        if (done) {
            val values = ContentValues().apply {
                put(COL_DONE_HABIT_ID, habitId)
                put(COL_DONE_DATE, dateString)
                put(COL_DONE_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                TABLE_HABIT_DONE, null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
        } else {
            db.delete(
                TABLE_HABIT_DONE,
                "$COL_DONE_HABIT_ID = ? AND $COL_DONE_DATE = ?",
                arrayOf(habitId.toString(), dateString)
            )
        }
    }

    fun getHabitIdsDoneOn(dateString: String): Set<Int> {
        val set = mutableSetOf<Int>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_DONE_HABIT_ID FROM $TABLE_HABIT_DONE WHERE $COL_DONE_DATE = ?",
            arrayOf(dateString)
        )
        if (cursor.moveToFirst()) {
            do {
                set.add(cursor.getInt(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return set
    }

    fun getHabitCompletionCountForDate(dateString: String): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_HABIT_DONE WHERE $COL_DONE_DATE = ?",
            arrayOf(dateString)
        )
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getTotalHabitCompletions(): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_HABIT_DONE", null)
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    /**
     * Records a punishment for missing [habitId] on [dateString].
     *
     * Returns true only the first time it is called for that pair; the primary key
     * rejects every later attempt. Callers use the return value to decide whether to
     * actually extend the session, so a habit can cost the user at most one hour per
     * day no matter how often the check runs.
     */
    fun recordHabitPenalty(habitId: Int, dateString: String, addedSeconds: Int): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_PENALTY_HABIT_ID, habitId)
            put(COL_PENALTY_DATE, dateString)
            put(COL_PENALTY_SECONDS, addedSeconds)
            put(COL_PENALTY_AT, System.currentTimeMillis())
        }
        val rowId = db.insertWithOnConflict(
            TABLE_HABIT_PENALTY, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return rowId != -1L
    }

    fun getPenaltySecondsForDate(dateString: String): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_PENALTY_SECONDS) FROM $TABLE_HABIT_PENALTY WHERE $COL_PENALTY_DATE = ?",
            arrayOf(dateString)
        )
        var seconds = 0
        if (cursor.moveToFirst()) seconds = cursor.getInt(0)
        cursor.close()
        return seconds
    }

    fun updateHabitOrder(orders: List<Pair<Int, Int>>) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            for ((habitId, order) in orders) {
                val values = ContentValues().apply {
                    put(COL_HABIT_ORDER, order)
                    put(COL_HABIT_SYNCED, 0)
                }
                db.update(TABLE_HABITS, values, "$COL_HABIT_ID = ?", arrayOf(habitId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }


    // ==========================================
    // Missed-day debt
    // ==========================================

    /**
     * Books a day as missed, if it has not been booked already.
     *
     * Returns true only the first time a given day is written, so a sweep can run as often as
     * it likes without charging the same day twice.
     */
    fun recordMissedDay(dateString: String, missedNames: List<String>): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DEBT_DATE, dateString)
            put(COL_DEBT_SECONDS, PENALTY_SECONDS_PER_MISSED_DAY)
            put(COL_DEBT_SERVED, 0)
            put(COL_DEBT_MISSED, missedNames.joinToString(", "))
            put(COL_DEBT_AT, System.currentTimeMillis())
        }
        // IGNORE rather than REPLACE: a day already on the books keeps its original standing,
        // served or not, and cannot be quietly reset to unserved by a later sweep.
        val row = db.insertWithOnConflict(
            TABLE_HABIT_DEBT, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return row != -1L
    }

    /** True when this day has already been booked, whether or not it has been served. */
    fun isDayBooked(dateString: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_HABIT_DEBT WHERE $COL_DEBT_DATE = ?",
            arrayOf(dateString)
        ).use { c -> return c.moveToFirst() }
    }

    /** Seconds owed and not yet added to any session. */
    fun unservedDebtSeconds(): Int {
        readableDatabase.rawQuery(
            "SELECT IFNULL(SUM($COL_DEBT_SECONDS), 0) FROM $TABLE_HABIT_DEBT " +
                "WHERE $COL_DEBT_SERVED = 0",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** The days still owed, oldest first, for telling the user what they are paying for. */
    fun unservedDebtDays(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT $COL_DEBT_DATE FROM $TABLE_HABIT_DEBT WHERE $COL_DEBT_SERVED = 0 " +
                "ORDER BY $COL_DEBT_DATE ASC",
            null
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Marks everything currently owed as served. Called once the time has been added. */
    fun markDebtServed() {
        writableDatabase.execSQL(
            "UPDATE $TABLE_HABIT_DEBT SET $COL_DEBT_SERVED = 1 WHERE $COL_DEBT_SERVED = 0"
        )
    }

    // ==========================================
    // Export and restore, for the encrypted mirror
    // ==========================================

    /**
     * Writes a habit back with the id it had.
     *
     * A restore has to keep ids: the completions and the debt rows point at them, and a habit
     * that came back under a fresh id would arrive with an empty history.
     */
    fun restoreHabit(
        id: Int,
        emoji: String,
        text: String,
        order: Int,
        createdDate: String,
        createdAt: Long
    ) {
        val values = ContentValues().apply {
            put(COL_HABIT_ID, id)
            put(COL_HABIT_EMOJI, emoji)
            put(COL_HABIT_TEXT, text)
            put(COL_HABIT_ORDER, order)
            put(COL_HABIT_CREATED_DATE, createdDate)
            put(COL_HABIT_CREATED_AT, createdAt)
            put(COL_HABIT_SYNCED, 1)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_HABITS, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Every (habit, day) that was performed, as a JSON array. */
    fun exportCompletions(): String {
        val arr = org.json.JSONArray()
        readableDatabase.rawQuery(
            "SELECT $COL_DONE_HABIT_ID, $COL_DONE_DATE, $COL_DONE_AT FROM $TABLE_HABIT_DONE",
            null
        ).use { c ->
            while (c.moveToNext()) {
                arr.put(
                    org.json.JSONObject().apply {
                        put("habitId", c.getInt(0))
                        put("date", c.getString(1))
                        put("at", c.getLong(2))
                    }
                )
            }
        }
        return arr.toString()
    }

    fun importCompletions(json: String) {
        val arr = org.json.JSONArray(json)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.insertWithOnConflict(
                    TABLE_HABIT_DONE,
                    null,
                    ContentValues().apply {
                        put(COL_DONE_HABIT_ID, o.getInt("habitId"))
                        put(COL_DONE_DATE, o.getString("date"))
                        put(COL_DONE_AT, o.optLong("at"))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Missed days and whether they have been served, as a JSON array. */
    fun exportDebt(): String {
        val arr = org.json.JSONArray()
        readableDatabase.rawQuery(
            "SELECT $COL_DEBT_DATE, $COL_DEBT_SECONDS, $COL_DEBT_SERVED, $COL_DEBT_MISSED, " +
                "$COL_DEBT_AT FROM $TABLE_HABIT_DEBT",
            null
        ).use { c ->
            while (c.moveToNext()) {
                arr.put(
                    org.json.JSONObject().apply {
                        put("date", c.getString(0))
                        put("seconds", c.getInt(1))
                        put("served", c.getInt(2))
                        put("missed", c.getString(3) ?: "")
                        put("at", c.getLong(4))
                    }
                )
            }
        }
        return arr.toString()
    }

    /**
     * Brings missed days back.
     *
     * Restored with their served flag intact, so reinstalling the app is not a way to wipe a
     * debt that has not been paid -- and not a way to be charged twice for one already served.
     */
    fun importDebt(json: String) {
        val arr = org.json.JSONArray(json)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.insertWithOnConflict(
                    TABLE_HABIT_DEBT,
                    null,
                    ContentValues().apply {
                        put(COL_DEBT_DATE, o.getString("date"))
                        put(COL_DEBT_SECONDS, o.getInt("seconds"))
                        put(COL_DEBT_SERVED, o.optInt("served"))
                        put(COL_DEBT_MISSED, o.optString("missed"))
                        put(COL_DEBT_AT, o.optLong("at"))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ==========================================
    // The dock, and odd named values
    // ==========================================

    fun getHomeApps(): List<String> {
        val out = mutableListOf<String>()
        try {
            readableDatabase.rawQuery(
                "SELECT $COL_HOME_PACKAGE FROM $TABLE_HOME_APPS ORDER BY $COL_HOME_POSITION ASC",
                null
            ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    /** Replaces the dock wholesale, since a reorder changes every position after the moved one. */
    fun setHomeApps(packages: List<String>) {
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_HOME_APPS, null, null)
                packages.forEachIndexed { i, pkg ->
                    if (pkg.isNotBlank()) {
                        db.insertWithOnConflict(
                            TABLE_HOME_APPS,
                            null,
                            ContentValues().apply {
                                put(COL_HOME_PACKAGE, pkg)
                                put(COL_HOME_POSITION, i)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSetting(key: String, fallback: String? = null): String? {
        return try {
            readableDatabase.rawQuery(
                "SELECT $COL_SETTING_VALUE FROM $TABLE_SETTINGS WHERE $COL_SETTING_KEY = ?",
                arrayOf(key)
            ).use { c -> if (c.moveToFirst()) c.getString(0) else fallback }
        } catch (e: Exception) {
            e.printStackTrace()
            fallback
        }
    }

    fun putSetting(key: String, value: String) {
        try {
            writableDatabase.insertWithOnConflict(
                TABLE_SETTINGS,
                null,
                ContentValues().apply {
                    put(COL_SETTING_KEY, key)
                    put(COL_SETTING_VALUE, value)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // Resource collections
    // ==========================================

    /** id, name, emoji -- in the order the folders are shown. */
    fun getCollections(): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        try {
            readableDatabase.rawQuery(
                "SELECT $COL_COLLECTION_ID, $COL_COLLECTION_NAME, " +
                    "IFNULL($COL_COLLECTION_EMOJI, '') FROM $TABLE_COLLECTIONS " +
                    "ORDER BY $COL_COLLECTION_POSITION ASC, $COL_COLLECTION_CREATED ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    /** Set once the model has picked one, so the lookup happens only at creation. */
    fun setCollectionEmoji(id: String, emoji: String) {
        try {
            writableDatabase.update(
                TABLE_COLLECTIONS,
                ContentValues().apply { put(COL_COLLECTION_EMOJI, emoji) },
                "$COL_COLLECTION_ID = ?",
                arrayOf(id)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCollection(id: String, name: String, emoji: String = "") {
        try {
            val next = readableDatabase.rawQuery(
                "SELECT IFNULL(MAX($COL_COLLECTION_POSITION), -1) + 1 FROM $TABLE_COLLECTIONS",
                null
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

            writableDatabase.insertWithOnConflict(
                TABLE_COLLECTIONS,
                null,
                ContentValues().apply {
                    put(COL_COLLECTION_ID, id)
                    put(COL_COLLECTION_NAME, name)
                    put(COL_COLLECTION_POSITION, next)
                    put(COL_COLLECTION_CREATED, System.currentTimeMillis())
                    put(COL_COLLECTION_EMOJI, emoji)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renameCollection(id: String, name: String) {
        try {
            writableDatabase.update(
                TABLE_COLLECTIONS,
                ContentValues().apply { put(COL_COLLECTION_NAME, name) },
                "$COL_COLLECTION_ID = ?",
                arrayOf(id)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Removes the folder only. What was inside it becomes unfiled rather than deleted. */
    fun removeCollection(id: String) {
        try {
            writableDatabase.delete(TABLE_COLLECTIONS, "$COL_COLLECTION_ID = ?", arrayOf(id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
