package com.jxau.oj

import android.app.Application
import com.jxau.oj.core.AppContainer

class JxauOjApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
