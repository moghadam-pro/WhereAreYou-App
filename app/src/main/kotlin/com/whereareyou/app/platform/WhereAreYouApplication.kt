package com.whereareyou.app.platform

import android.app.Application

class WhereAreYouApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
