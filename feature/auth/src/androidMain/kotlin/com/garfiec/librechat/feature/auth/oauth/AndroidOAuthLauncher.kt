package com.garfiec.librechat.feature.auth.oauth

import android.webkit.CookieManager

class AndroidOAuthLauncher : OAuthLauncher {

    // CookieManager.getInstance() is the same global store OAuthWebView's embedded WebView writes
    // through, so a cookie the flow just set is visible here with no bridging needed.
    override fun extractTokenFromCookies(serverUrl: String): String? {
        val cookies = CookieManager.getInstance().getCookie(serverUrl) ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("refreshToken=") }
            ?.substringAfter("refreshToken=")
    }

    override fun clearOAuthCookie(serverUrl: String) {
        CookieManager.getInstance()
            .setCookie(serverUrl, "refreshToken=; expires=Thu, 01 Jan 1970 00:00:00 GMT")
    }
}
