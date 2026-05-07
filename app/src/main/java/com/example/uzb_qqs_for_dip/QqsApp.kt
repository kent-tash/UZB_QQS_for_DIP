package com.example.uzb_qqs_for_dip

import android.app.Application
import com.example.uzb_qqs_for_dip.data.AppContainer

class QqsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
