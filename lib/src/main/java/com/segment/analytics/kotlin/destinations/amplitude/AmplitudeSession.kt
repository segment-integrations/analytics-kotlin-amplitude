package com.segment.analytics.kotlin.destinations.amplitude

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.VersionedPlugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogKind
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.putIntegrations

// A Destination plugin that adds session tracking to Amplitude cloud mode.
class AmplitudeSession (private val sessionTimeoutMs : Long = 300000) : EventPlugin, VersionedPlugin {

    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics
    var sessionID = java.lang.System.currentTimeMillis()
    private val key = "Actions Amplitude"
    private var active = false
    private var lastEventFiredTime = java.lang.System.currentTimeMillis()

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        active = settings.hasIntegrationSettings(key)
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        if (!active) { // If amplitude destination is disabled, no need to do this enrichment
            return event
        }

        val modified = super.execute(event)
        analytics.log(
            message = "Running ${event.type} payload through AmplitudeSession",
            kind = LogKind.DEBUG
        )
        lastEventFiredTime = java.lang.System.currentTimeMillis()

        return modified?.putIntegrations(key, mapOf("session_id" to sessionID))
    }

    override fun track(payload: TrackEvent): BaseEvent {
        if (payload.event == "Application Backgrounded") {
            onBackground()
        } else if (payload.event == "Application Opened") {
            onForeground()
        }

        return payload
    }

    private fun onBackground() {
    }

    private fun onForeground() {
        val current = java.lang.System.currentTimeMillis()
        if (current - lastEventFiredTime >= sessionTimeoutMs) {
            sessionID = current
        }
    }

    override fun version(): String {
        return BuildConfig.VERSION_NAME
    }
}
