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
        AppStrings.init(this)
    }
}
