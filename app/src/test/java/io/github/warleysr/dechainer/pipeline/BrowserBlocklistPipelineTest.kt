package io.github.warleysr.dechainer.pipeline

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.installBrowser
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.BrowserRestrictionsViewModel]
 * (`saveOrUpdateList`, `removeList`) and reads back the way the app's enforcement does — through the
 * device policy manager — so the only thing under test is the end-to-end behaviour: a saved list is
 * persisted to `browser_prefs`, then [io.github.warleysr.dechainer.BrowserRestrictionsManager] collects
 * every list's sites into a single deduplicated `URLBlocklist` and applies it to each installed browser
 * via `DevicePolicyManager.setApplicationRestrictions`. The path is synchronous. Nothing here reaches
 * into class internals.
 */
@RunWith(AndroidJUnit4::class)
class BrowserBlocklistPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val browserPackage = "com.fake.browser"

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    private fun appliedBlocklist(): List<String> =
        dpm.getApplicationRestrictions(admin, browserPackage)
            .getStringArray("URLBlocklist")?.toList() ?: emptyList()

    @Test
    fun `saving a blocklist applies its sites to the installed browser`() {
        installBrowser(browserPackage)
        shadowOf(dpm).setDeviceOwner(admin)
        val viewModel = Fixtures.browserRestrictionsViewModel()

        viewModel.saveOrUpdateList(null, "Adult", "a.com\nb.com")

        appliedBlocklist() shouldContainExactlyInAnyOrder listOf("a.com", "b.com")
    }

    @Test
    fun `removing a list clears the blocklist and applies an empty list`() {
        installBrowser(browserPackage)
        shadowOf(dpm).setDeviceOwner(admin)
        val viewModel = Fixtures.browserRestrictionsViewModel()
        viewModel.saveOrUpdateList(null, "Adult", "a.com\nb.com")

        viewModel.removeList(viewModel.blockedLists.first().id)

        viewModel.blockedLists.shouldBeEmpty()
        appliedBlocklist().shouldBeEmpty()
    }

    @Test
    fun `multiple lists merge into one deduplicated blocklist`() {
        installBrowser(browserPackage)
        shadowOf(dpm).setDeviceOwner(admin)
        val viewModel = Fixtures.browserRestrictionsViewModel()

        viewModel.saveOrUpdateList("list-1", "Adult", "a.com\nb.com")
        viewModel.saveOrUpdateList("list-2", "Gambling", "b.com\nc.com")

        appliedBlocklist() shouldContainExactlyInAnyOrder listOf("a.com", "b.com", "c.com")
    }
}
