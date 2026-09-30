package me.ray.nexahub.oauth

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

fun interface OAuthAuthorizationLauncher {
    fun launch(context: Context, authorizationUrl: String)
}

/** Opens the OAuth authorization page with Custom Tabs. */
object CustomTabsOAuthAuthorizationLauncher : OAuthAuthorizationLauncher {
    override fun launch(context: Context, authorizationUrl: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, authorizationUrl.toUri())
    }
}
