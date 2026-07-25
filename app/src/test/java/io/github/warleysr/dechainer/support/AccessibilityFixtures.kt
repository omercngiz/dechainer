package io.github.warleysr.dechainer.support

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.warleysr.dechainer.DechainerAccessibilityService
import org.robolectric.Robolectric
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService

/**
 * Drives [DechainerAccessibilityService] as a real running service in tests.
 *
 * [startBlockingService] builds the service and invokes `onServiceConnected` — which reads the
 * blocking prefs and starts listening — reflectively, because that callback is `protected` in the
 * framework and cannot be reached from this package.
 * [windowStateEvent], [textChangedEvent] and [contentChangedEvent] build the minimal
 * `AccessibilityEvent`s the tracking, active and passive blocking paths consume. The passive path
 * reports enforcement through two observable channels: [startedActivity] reads back (without
 * consuming) whatever activity the service launched, and [globalActionsPerformed] reads the global
 * actions (e.g. `GLOBAL_ACTION_BACK`) the service dispatched.
 */

fun startBlockingService(): DechainerAccessibilityService {
    val service = Robolectric.buildService(DechainerAccessibilityService::class.java)
        .create()
        .get()
    val onServiceConnected =
        AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
    onServiceConnected.isAccessible = true
    onServiceConnected.invoke(service)
    return service
}

fun windowStateEvent(packageName: String, className: String): AccessibilityEvent =
    AccessibilityEvent().apply {
        eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        this.packageName = packageName
        this.className = className
    }

fun textChangedEvent(content: String): AccessibilityEvent =
    AccessibilityEvent().apply {
        eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        text.add(content)
    }

fun contentChangedEvent(content: String): AccessibilityEvent =
    AccessibilityEvent().apply {
        eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        text.add(content)
    }

fun DechainerAccessibilityService.shadow(): ShadowAccessibilityService = Shadow.extract(this)

fun DechainerAccessibilityService.startedActivity(): Intent? = shadow().peekNextStartedActivity()

fun DechainerAccessibilityService.globalActionsPerformed(): List<Int> =
    shadow().globalActionsPerformed
