package de.kornelriedl.drivetrack.data

import android.content.Context

object CarPreferences {
    private const val PREFS_NAME = "car_prefs"
    private const val KEY_SELECTED_CAR_ID = "selected_car_id"
    private const val KEY_DEFAULT_CAR_ID = "default_car_id"
    private const val NO_CAR = -1L

    fun getSelectedCarId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_SELECTED_CAR_ID, NO_CAR)
        return if (value == NO_CAR) null else value
    }

    fun setSelectedCarId(context: Context, carId: Long?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SELECTED_CAR_ID, carId ?: NO_CAR).apply()
    }

    /** Das Auto, das beim Start einer Aufzeichnung standardmäßig vorausgewählt sein soll. */
    fun getDefaultCarId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_DEFAULT_CAR_ID, NO_CAR)
        return if (value == NO_CAR) null else value
    }

    fun setDefaultCarId(context: Context, carId: Long?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DEFAULT_CAR_ID, carId ?: NO_CAR).apply()
    }
}
