package com.segment.analytics.destinations.mydestination.testapp

import android.app.Application
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.destinations.amplitude.AmplitudeSession

class MainApplication: Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()

        Analytics.debugLogsEnabled = true
        analytics = Analytics("YOUR WRITE KEY", applicationContext) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 10
        }
        analytics.add(AmplitudeSession(60 * 1000))
    }
}