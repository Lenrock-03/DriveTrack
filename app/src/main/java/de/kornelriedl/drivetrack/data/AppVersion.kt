package de.kornelriedl.drivetrack.data

import android.content.Context
import android.content.pm.PackageManager

/**
 * Liest die versionName aus dem PackageManager statt aus BuildConfig (das erfordert kein
 * zusätzliches "buildFeatures.buildConfig = true" in build.gradle.kts und spiegelt garantiert
 * die tatsächlich installierte APK wider). Ursprünglich privat in SettingsScreen.kt, seit 0.15.0
 * hierher verschoben und geteilt - MainActivity.kt braucht sie ebenfalls für den "Was ist neu"-
 * Dialog (siehe ReleaseNotes.kt), der beim ersten Öffnen nach einem Update erscheint.
 */
fun appVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
