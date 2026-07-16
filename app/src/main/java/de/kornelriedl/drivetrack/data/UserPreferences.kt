package de.kornelriedl.drivetrack.data

import android.content.Context

object UserPreferences {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val NO_USER = -1L

    fun getActiveUserId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_ACTIVE_USER_ID, NO_USER)
        return if (value == NO_USER) null else value
    }

    fun setActiveUserId(context: Context, userId: Long?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_ACTIVE_USER_ID, userId ?: NO_USER).apply()
    }
}
