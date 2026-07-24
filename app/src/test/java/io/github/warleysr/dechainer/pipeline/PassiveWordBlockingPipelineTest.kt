package io.github.warleysr.dechainer.pipeline

import android.accessibilityservice.AccessibilityService
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.activities.BlockedWordActivity
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.awaitUntil
import io.github.warleysr.dechainer.support.contentChangedEvent
import io.github.warleysr.dechainer.support.globalActionsPerformed
import io.github.warleysr.dechainer.support.startBlockingService
import io.github.warleysr.dechainer.support.startedActivity
import io.github.warleysr.dechainer.support.windowStateEvent
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel]
 * (`updatePassiveWords`, which persists per-package `passive_words_<pkg>` sets) and reads through
 * the real [io.github.warleysr.dechainer.DechainerAccessibilityService], so the only thing under
 * test is the end-to-end behaviour. A window-state event first establishes the current package;
 * then a window-content event carrying the forbidden word drives the passive path, which reports
 * enforcement through two observable channels: a synchronous `GLOBAL_ACTION_BACK` and an
 * asynchronously launched [BlockedWordActivity]. Passive words are scoped to their own package, and
 * clearing them removes the record entirely. Nothing here reaches into class internals.
 */
@RunWith(AndroidJUnit4::class)
class PassiveWordBlockingPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `a passive word appearing on screen in its package triggers back and the block screen`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updatePassiveWords(targetPackage, "kumar")

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(contentChangedEvent("bugün kumar oynadım"))

        service.globalActionsPerformed() shouldContain AccessibilityService.GLOBAL_ACTION_BACK

        awaitUntil { service.startedActivity() != null }

        val intent = service.startedActivity().shouldNotBeNull()
        intent.component?.className shouldBe BlockedWordActivity::class.java.name
        intent.getStringExtra("word") shouldBe "kumar"
    }

    @Test
    fun `a passive word does not trigger in a different package`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updatePassiveWords(targetPackage, "kumar")

        val service = startBlockingService()
        val otherPackage = "com.example.other"
        service.onAccessibilityEvent(windowStateEvent(otherPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(contentChangedEvent("bugün kumar oynadım"))

        service.globalActionsPerformed().shouldBeEmpty()
        service.startedActivity().shouldBeNull()
    }

    @Test
    fun `clearing a package's passive words removes the record so it no longer blocks`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updatePassiveWords(targetPackage, "kumar")
        viewModel.updatePassiveWords(targetPackage, "")

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(contentChangedEvent("bugün kumar oynadım"))

        service.globalActionsPerformed().shouldBeEmpty()
        service.startedActivity().shouldBeNull()
    }
}
