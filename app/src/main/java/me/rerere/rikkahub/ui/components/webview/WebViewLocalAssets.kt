package me.rerere.rikkahub.ui.components.webview

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse

/**
 * Virtual domain used by the in-app WebView, giving loadDataWithBaseURL a stable origin.
 */
const val WEB_VIEW_BASE_URL = "https://rikkahub.local"

/**
 * Static resource prefix under the virtual domain, mapped to the app's assets directory.
 *
 * For example `https://rikkahub.local/assets/html/mermaid.min.js` maps to `assets/html/mermaid.min.js`,
 * so libraries like mermaid/katex load locally instead of being downloaded from a CDN (the first load could take tens of seconds).
 */
const val WEB_VIEW_ASSET_URL = "$WEB_VIEW_BASE_URL/assets"

private const val ASSET_HOST = "rikkahub.local"
private const val ASSET_PATH_PREFIX = "/assets/"

internal object WebViewLocalAssets {
    fun intercept(context: Context, uri: Uri): WebResourceResponse? {
        if (!uri.host.equals(ASSET_HOST, ignoreCase = true)) return null
        val path = uri.path ?: return null
        if (!path.startsWith(ASSET_PATH_PREFIX)) return null

        val assetPath = path.removePrefix(ASSET_PATH_PREFIX)
        if (assetPath.isEmpty() || assetPath.contains("..")) return null

        val mimeType = mimeTypeOf(assetPath)
        return runCatching {
            WebResourceResponse(
                mimeType,
                if (mimeType.startsWith("text/") || mimeType.endsWith("json") || mimeType.endsWith("xml")) "UTF-8" else null,
                context.assets.open(assetPath)
            )
        }.getOrNull()
    }

    private fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }
}
