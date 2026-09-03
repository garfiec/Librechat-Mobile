package com.garfiec.librechat.feature.auth.oauth

import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL

class IosOAuthLauncher : OAuthLauncher {

    // WKWebView's default (persistent, non-ephemeral) WKWebsiteDataStore -- the one
    // OAuthWebView.ios.kt's WKWebView uses -- synchronizes with
    // NSHTTPCookieStorage.sharedHTTPCookieStorage; a cookie the flow just set is visible here.
    override fun extractTokenFromCookies(serverUrl: String): String? {
        val url = NSURL.URLWithString(serverUrl) ?: return null
        val cookies = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(url) ?: return null

        @Suppress("UNCHECKED_CAST")
        val cookieList = cookies as List<NSHTTPCookie>
        return cookieList.firstOrNull { it.name == "refreshToken" }?.value
    }

    override fun clearOAuthCookie(serverUrl: String) {
        val url = NSURL.URLWithString(serverUrl) ?: return
        val cookies = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(url) ?: return

        @Suppress("UNCHECKED_CAST")
        val cookieList = cookies as List<NSHTTPCookie>
        cookieList.filter { it.name == "refreshToken" }.forEach {
            NSHTTPCookieStorage.sharedHTTPCookieStorage.deleteCookie(it)
        }
    }
}
