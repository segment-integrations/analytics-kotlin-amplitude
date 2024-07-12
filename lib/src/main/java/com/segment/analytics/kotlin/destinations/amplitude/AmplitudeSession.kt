package com.segment.analytics.kotlin.destinations.amplitude

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.VersionedPlugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.putIntegrations

// A Destination plugin that adds session tracking to Amplitude cloud mode.
class AmplitudeSession (private val sessionTimeoutMs : Long = 300000) : EventPlugin, VersionedPlugin {

    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics
    private var eventSessionId = -1L
    private var sessionId = eventSessionId
    private val key = "Actions Amplitude"
    private val ampSessionEndEvent = "session_end"
    private val ampSessionStartEvent = "session_start"
    private var active = false
    private var lastEventFiredTime = java.lang.System.currentTimeMillis()

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        startNewSessionIfNecessary()
    }
    override fun update(settings: Settings, type: Plugin.UpdateType) {
        active = settings.hasIntegrationSettings(key)
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        if (!active) { // If amplitude destination is disabled, no need to do this enrichment
            return event
        }

        startNewSessionIfNecessary()

        val modified = super.execute(event)
        analytics.log(
            message = "Running ${event.type} payload through AmplitudeSession"
        )

        if (event is TrackEvent) {
            if(event.event == ampSessionStartEvent) {
                // Update session ID for all events after this in the queue
                eventSessionId = sessionId
                analytics.log(message = "NewSession = $eventSessionId")
            }
            if (event.event == ampSessionEndEvent) {
                analytics.log(message = "EndSession = $eventSessionId")
            }
        }

        return modified?.putIntegrations(key, mapOf("session_id" to eventSessionId))
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
        startNewSessionIfNecessary()
    }

    private fun startNewSession() {
        analytics.track(ampSessionStartEvent)
    }

    private fun endSession() {
        analytics.track(ampSessionEndEvent)
    }

    private fun startNewSessionIfNecessary() {
        val current = java.lang.System.currentTimeMillis()
        // Make sure the first event has a valid ID and we send a session start.
        // Subsequent events should have session IDs updated after the session track event is sent
        if(eventSessionId == -1L || sessionId == -1L) {
            sessionId = current
            eventSessionId = current
            startNewSession()
        } else if (current - lastEventFiredTime >= sessionTimeoutMs) {
            sessionId = current

            endSession()
            startNewSession()
        }
        lastEventFiredTime = current
    }

    override fun version(): String {
        return BuildConfig.VERSION_NAME
    }
}
