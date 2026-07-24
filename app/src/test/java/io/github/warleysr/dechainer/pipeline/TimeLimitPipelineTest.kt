package io.github.warleysr.dechainer.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.activities.TimeUpActivity
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.prefs
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
import java.util.concurrent.TimeUnit

/**
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.AppsViewModel]
 * (`setAppTimeLimit`, `getAppUsage`) and reads through the real
 * [io.github.warleysr.dechainer.DechainerAccessibilityService], so the only thing under test is the
 * end-to-end behaviour. Usage is measured with `SystemClock.elapsedRealtime()`, which
 * [ShadowSystemClock.advanceBy] does move (unlike wall-clock time), so a session can be aged without
 * touching production code. The block path (`executeBlocking` -> `startActivity`) is synchronous, so
 * the launched [TimeUpActivity] is readable without awaiting. Nothing here reaches into class internals.
 */
@RunWith(AndroidJUnit4::class)
class TimeLimitPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"
    private val otherPackage = "com.example.other"
    private val neutralClass = "android.widget.FrameLayout"

    @Test
    fun `a foreground app past its time limit opens the time-up screen`() {
        val appsViewModel = Fixtures.appsViewModel()
        appsViewModel.setAppTimeLimit(targetPackage, 30)
        prefs("internal_usage_stats").edit()
            .putLong(targetPackage, TimeUnit.MINUTES.toMillis(31))
            .commit()

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))

        val intent = service.startedActivity().shouldNotBeNull()
        intent.component?.className shouldBe TimeUpActivity::class.java.name
        intent.getIntExtra("limit", 0) shouldBe 30
    }

    @Test
    fun `usage accrued by the service is read back by the view model`() {
        val appsViewModel = Fixtures.appsViewModel()
        appsViewModel.setAppTimeLimit(targetPackage, 30)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        service.onAccessibilityEvent(windowStateEvent(otherPackage, neutralClass))

        appsViewModel.getAppUsage(targetPackage, inMinutes = true) shouldBe 5L
        service.startedActivity().shouldBeNull()
    }

    @Test
    fun `an app with no time limit is neither blocked nor tracked`() {
        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, neutralClass))
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        service.onAccessibilityEvent(windowStateEvent(otherPackage, neutralClass))

        service.startedActivity().shouldBeNull()
        Fixtures.appsViewModel().getAppUsage(targetPackage) shouldBe 0L
    }
}
