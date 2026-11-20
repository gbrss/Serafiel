package com.example

import android.app.Application
import android.content.Context
import com.example.i18n.AppStrings
import com.example.i18n.Lang

class SerafielApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.example.data.NetworkMonitor.start(this)
        AppStrings.init(this)
        com.example.ui.theme.ThemeSettings.init(this)
    }
}
