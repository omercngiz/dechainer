package io.github.warleysr.dechainer.pipeline

import android.accessibilityservice.AccessibilityService
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.globalActionsPerformed
import io.github.warleysr.dechainer.support.startBlockingService
import io.github.warleysr.dechainer.support.windowStateEvent
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.ActivityBlockerViewModel]
 * (`addBlockedActivity`/`removeBlockedActivity`, which persist a `blocked_activities` set) and reads
 * through the real [io.github.warleysr.dechainer.DechainerAccessibilityService], so the only thing
 * under test is the end-to-end behaviour. A window-state event whose class name contains "Activity"
 * drives the path: the service records it in the static [DechainerAccessibilityService.accessedActivities]
 * log and, when the class is blocked, fires a synchronous `GLOBAL_ACTION_BACK`. The log deduplicates
 * a repeated activity and never grows past 100 entries. Nothing here reaches into class internals.
 */
@RunWith(AndroidJUnit4::class)
class ActivityBlockingPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `opening a blocked activity triggers back`() {
        val viewModel = Fixtures.activityBlockerViewModel()
        val blockedClass = "com.example.socialapp.SettingsActivity"
        viewModel.addBlockedActivity(blockedClass)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, blockedClass))

        service.globalActionsPerformed() shouldContain AccessibilityService.GLOBAL_ACTION_BACK
    }

    @Test
    fun `removing a blocked activity stops it from being blocked`() {
        val viewModel = Fixtures.activityBlockerViewModel()
        val blockedClass = "com.example.socialapp.SettingsActivity"
        viewModel.addBlockedActivity(blockedClass)
        viewModel.removeBlockedActivity(blockedClass)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, blockedClass))

        service.globalActionsPerformed().shouldBeEmpty()
    }

    @Test
    fun `a seen activity is recorded in the access log`() {
        val service = startBlockingService()
        val seenClass = "com.example.socialapp.HomeActivity"
        service.onAccessibilityEvent(windowStateEvent(targetPackage, seenClass))

        DechainerAccessibilityService.accessedActivities.any {
            it.packageName == targetPackage && it.className == seenClass
        } shouldBe true
    }

    @Test
    fun `the same activity seen twice is recorded once`() {
        val service = startBlockingService()
        val seenClass = "com.example.socialapp.HomeActivity"
        service.onAccessibilityEvent(windowStateEvent(targetPackage, seenClass))
        service.onAccessibilityEvent(windowStateEvent(targetPackage, seenClass))

        DechainerAccessibilityService.accessedActivities.count {
            it.packageName == targetPackage && it.className == seenClass
        } shouldBe 1
        DechainerAccessibilityService.accessedActivities.size shouldBe 1
    }

    @Test
    fun `the access log never exceeds one hundred entries`() {
        val service = startBlockingService()
        for (i in 0..100) {
            service.onAccessibilityEvent(
                windowStateEvent(targetPackage, "com.example.socialapp.Activity$i")
            )
        }

        DechainerAccessibilityService.accessedActivities.size shouldBe 100
        DechainerAccessibilityService.accessedActivities.first().className shouldBe
            "com.example.socialapp.Activity100"
        DechainerAccessibilityService.accessedActivities.none {
            it.className == "com.example.socialapp.Activity0"
        } shouldBe true
    }
}
