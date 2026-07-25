package io.github.warleysr.dechainer.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.activities.ReopeningLimitActivity
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.startBlockingService
import io.github.warleysr.dechainer.support.startedActivity
import io.github.warleysr.dechainer.support.windowStateEvent
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.AppsViewModel]
 * (`setAppReopenTime`) and reads through the real
 * [io.github.warleysr.dechainer.DechainerAccessibilityService], so the only thing under test is the
 * end-to-end behaviour: opening an app, leaving it (which records the close time on
 * `SystemClock.elapsedRealtime()`), and returning. The cooldown is aged with
 * [ShadowSystemClock.advanceBy], which moves `elapsedRealtime()`, and the block path
 * (`executeBlocking` -> `startActivity`) is synchronous, so the launched [ReopeningLimitActivity] is
 * readable without awaiting. Nothing here reaches into class internals.
 */
@RunWith(AndroidJUnit4::class)
class ReopenCooldownPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"
    private val homePackage = "com.example.launcher"
    private val neutralClass = "android.widget.FrameLayout"

    @Test
    fun `returning within the cooldown opens the reopening-limit screen`() {
        Fixtures.appsViewModel().setAppReopenTime(targetPackage, 60)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))
        service.onAccessibilityEvent(windowStateEvent(homePackage, neutralClass))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))

        val intent = service.startedActivity().shouldNotBeNull()
        intent.component?.className shouldBe ReopeningLimitActivity::class.java.name
        intent.getIntExtra("limit", 0) shouldBe 50
    }

    @Test
    fun `returning after the cooldown has elapsed is not blocked`() {
        Fixtures.appsViewModel().setAppReopenTime(targetPackage, 60)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))
        service.onAccessibilityEvent(windowStateEvent(homePackage, neutralClass))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60))
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))

        service.startedActivity().shouldBeNull()
    }
}
