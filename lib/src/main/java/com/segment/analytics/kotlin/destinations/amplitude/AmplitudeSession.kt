package com.segment.analytics.kotlin.destinations.amplitude

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.VersionedPlugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.putIntegrations
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import com.segment.analytics.kotlin.core.utilities.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import androidx.core.content.edit

// A Destination plugin that adds session tracking to Amplitude cloud mode.
class AmplitudeSession (private val sessionTimeoutMs : Long = 300000) : EventPlugin, VersionedPlugin {

    companion object {
        const val LAST_EVENT_ID = "last_event_id"
        const val PREVIOUS_SESSION_ID = "previous_session_id"
        const val LAST_EVENT_TIME = "last_event_time"

        const val AMP_PREFIX = "[Amplitude] "
        const val AMP_SESSION_END_EVENT = "session_end"
        const val AMP_SESSION_START_EVENT = "session_start"
    }

    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics
    private val key = "Actions Amplitude"


    private val active = AtomicBoolean(false)
    private var prefs: SharedPreferences? = null

    private val _sessionId = AtomicLong(-1L)
    private var sessionId
        get() = _sessionId.get()
        set(value) {
            _sessionId.set(value)
            prefs?.edit(commit = true) { putLong(PREVIOUS_SESSION_ID, value) }
        }
    private val _lastEventTime = AtomicLong(-1L)
    private var lastEventTime
        get() = _lastEventTime.get()
        set(value) {
            _lastEventTime.set(value)
            prefs?.edit(commit = true) { putLong(LAST_EVENT_TIME, value) }
        }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        var context: Context? = null
        if (analytics.configuration.application is Context){
            context = analytics.configuration.application as Context
        }
        context?.let {
            prefs = context.getSharedPreferences("analytics-android-${analytics.configuration.writeKey}", Context.MODE_PRIVATE)
            prefs?.let {
                _sessionId.set(it.getLong(PREVIOUS_SESSION_ID, -1))
                _lastEventTime.set(it.getLong(LAST_EVENT_TIME, -1))
            }
        }

        if (sessionId == -1L) {
            startNewSession()
        }
        else {
            startNewSessionIfNecessary()
        }
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        if (type != Plugin.UpdateType.Initial) return

        active.set(settings.hasIntegrationSettings(key))
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        if (!active.get()) { // If amplitude destination is disabled, no need to do this enrichment
            return event
        }

        startNewSessionIfNecessary()

        analytics.log(
            message = "Running ${event.type} payload through AmplitudeSession"
        )

        var modified = super.execute(event)
        if (modified is ScreenEvent) {
            val screenName = modified.name
            modified.properties = updateJsonObject(modified.properties) {
                // amp needs screen name in the properties for screen event
                it["name"] = screenName
            }
        }
        else if (modified is TrackEvent) {
            if(modified.event == AMP_SESSION_START_EVENT) {
                modified = modified.disableCloudIntegrations(exceptKeys = listOf(key))
                analytics.log(message = "NewSession = $sessionId")
            }
            else if (modified.event == AMP_SESSION_END_EVENT) {
                modified = modified.disableCloudIntegrations(exceptKeys = listOf(key))
                analytics.log(message = "EndSession = $sessionId")
            }
            else if (modified.event.contains(AMP_PREFIX)) {
                modified = modified.disableCloudIntegrations(exceptKeys = listOf(key))
                modified = modified.putIntegrations(key, mapOf("session_id" to sessionId))
            }
        }

        // renew the session if there are activities
        lastEventTime = java.lang.System.currentTimeMillis()
        return modified
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
        lastEventTime = java.lang.System.currentTimeMillis()
    }

    private fun onForeground() {
        startNewSessionIfNecessary()
    }

    private fun startNewSession() {
        sessionId = java.lang.System.currentTimeMillis()

        // need to snapshot the current sessionId
        // because when the enrichment closure is applied the sessionId might have changed
        val copy = sessionId
        analytics.track(AMP_SESSION_START_EVENT) { event ->
            event?.putIntegrations(key, mapOf("session_id" to copy))
        }
    }

    private fun endSession() {
        // need to snapshot the current sessionId
        // because when the enrichment closure is applied the sessionId might have changed
        val copy = sessionId
        analytics.track(AMP_SESSION_END_EVENT) { event ->
            event?.putIntegrations(key, mapOf("session_id" to copy))
        }
    }

    private fun startNewSessionIfNecessary() {
        val current = java.lang.System.currentTimeMillis()
        val withinSessionLimit = (current - lastEventTime < sessionTimeoutMs)
        if (sessionId >= 0 && withinSessionLimit) return

        // we'll consider this our new lastEventTime
        lastEventTime = current
        endSession()
        startNewSession()
    }

    override fun version(): String {
        return BuildConfig.VERSION_NAME
    }
}
