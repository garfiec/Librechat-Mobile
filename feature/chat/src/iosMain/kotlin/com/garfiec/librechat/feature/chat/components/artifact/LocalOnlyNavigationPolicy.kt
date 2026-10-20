package com.garfiec.librechat.feature.chat.components.artifact

import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

private val LOCAL_NAVIGATION_SCHEMES = setOf("about", "data", "blob", "file")

/**
 * SECURITY: refuses a preview's main-frame navigation to anything but a local page. A link in one
 * (a docx hyperlink, an HTML artifact's <a>) would otherwise replace the page with a remote one,
 * running its script with any message handlers bound. `navigationDelegate` is weak: the caller
 * must hold this for the web view's lifetime.
 */
internal class LocalOnlyNavigationPolicy : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val action = decidePolicyForNavigationAction
        val scheme = action.request.URL?.scheme?.lowercase()
        val remoteMainFrame = action.targetFrame?.mainFrame == true && scheme !in LOCAL_NAVIGATION_SCHEMES
        decisionHandler(
            if (remoteMainFrame) {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            },
        )
    }
}
