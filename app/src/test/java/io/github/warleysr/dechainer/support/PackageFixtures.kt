package io.github.warleysr.dechainer.support

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/**
 * Installs fake packages into the Robolectric [org.robolectric.shadows.ShadowPackageManager] so the
 * app's `PackageManager` queries resolve them.
 *
 * [installBrowser] registers a browser that
 * [io.github.warleysr.dechainer.BrowserRestrictionsManager.getPossibleBrowsers] can find: it resolves
 * the same three intents that method queries — `ACTION_MAIN` + `CATEGORY_APP_BROWSER`, and `ACTION_VIEW`
 * over `http`/`https` — and carries an activity info and label so loading the browser list never
 * crashes.
 */

fun installBrowser(packageName: String, label: String = "Fake Browser") {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shadowPackageManager = shadowOf(context.packageManager)

    val resolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.BrowserActivity"
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        }
        nonLocalizedLabel = label
    }

    val browserCategory = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_BROWSER)
    }
    val httpView = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
    val httpsView = Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri())

    listOf(browserCategory, httpView, httpsView).forEach { intent ->
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo)
    }
}
