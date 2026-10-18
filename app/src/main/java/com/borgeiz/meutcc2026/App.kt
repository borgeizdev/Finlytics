package com.borgeiz.meutcc2026

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.borgeiz.meutcc2026.notifications.NotificationHelper

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val mode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val validModes = setOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        AppCompatDelegate.setDefaultNightMode(
            if (mode in validModes) mode else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        NotificationHelper.createChannels(this)
    }
}
