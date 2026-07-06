package com.emailhub.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val config by lazy { AppConfig.get(this) }

    /** Email address that should be pre-filled on the /send page, if any. */
    private var pendingRecipient: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract recipient from incoming mailto: or SEND intent
        pendingRecipient = extractRecipient(intent)

        webView = WebView(this).also { setContentView(it) }

        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = "$userAgentString EmailHubAndroid/1.0"
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = MailtoWebViewClient()

        if (!config.isConfigured) {
            openSettings(getString(R.string.msg_configure_first))
            return
        }

        loadApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRecipient = extractRecipient(intent)
        // If we're already on /send and got a new mailto:, inject recipient directly.
        val r = pendingRecipient?.jsEscapeOrNull() ?: return
        webView.evaluateJavascript(
            "(function(){var e=document.getElementById('to_email');if(e){e.value='$r';e.dispatchEvent(new Event('blur'));}})();",
            null
        )
    }

    private fun extractRecipient(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> {
                val data = intent.data
                if (data != null && data.scheme.equals("mailto", ignoreCase = true)) {
                    parseMailto(data.toString())
                } else null
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                text?.let { findFirstEmail(it) }
            }
            else -> null
        }
    }

    /** Parses a mailto: URI and returns just the address (ignores subject/body for now). */
    private fun parseMailto(mailto: String): String {
        val noScheme = mailto.removePrefix("mailto:").removePrefix("MAILTO:")
        val beforeQuery = noScheme.substringBefore('?')
        return Uri.decode(beforeQuery).trim()
    }

    /** Find the first email-looking token in arbitrary shared text. */
    private fun findFirstEmail(text: String): String? {
        val regex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
        return regex.find(text)?.value
    }

    private fun loadApp() {
        val base = config.serverUrl
        val target = if (pendingRecipient != null) {
            "$base/send"
        } else {
            "$base/dashboard"
        }

        // Restore saved session cookie (if any) so user stays logged in across launches.
        val savedCookie = config.sessionCookie
        if (savedCookie.isNotBlank()) {
            CookieManager.getInstance().setCookie(base, savedCookie)
            CookieManager.getInstance().flush()
        }

        webView.loadUrl(target)
    }

    private fun openSettings(message: String? = null) {
        val intent = Intent(this, SettingsActivity::class.java)
        if (message != null) intent.putExtra(SettingsActivity.EXTRA_MESSAGE, message)
        startActivity(intent)
    }

    @SuppressLint("InflateParams")
    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_logout)
            .setMessage(R.string.msg_logout_confirm)
            .setPositiveButton(R.string.action_logout) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                config.sessionCookie = ""
                openSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> { openSettings(); true }
        R.id.action_logout -> { showLogoutConfirm(); true }
        R.id.action_refresh -> { webView.reload(); true }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Use OnBackPressedDispatcher", ReplaceWith(""))
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (config.isConfigured && webView.url.isNullOrBlank()) {
            // User just configured credentials in Settings; load the app now.
            loadApp()
        } else if (!config.isConfigured && webView.url.isNullOrBlank()) {
            // Still not configured - user backed out of Settings; close the app.
            finish()
        }
    }

    /**
     * WebViewClient that:
     *  1. Keeps navigation inside the WebView (opens EmailHub pages only).
     *  2. Auto-submits the login form when the login page is reached.
     *  3. After the /send page finishes loading, injects the recipient email.
     */
    private inner class MailtoWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // External links open in the system browser
            if (!url.startsWith(config.serverUrl)) {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
            return false
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            // Capture session cookie whenever it changes, so it survives restarts.
            val cookies = CookieManager.getInstance().getCookie(config.serverUrl)
            if (!cookies.isNullOrBlank()) config.sessionCookie = cookies

            val base = config.serverUrl
            when {
                url == "$base/" || url == "$base/login" || url.endsWith("/login") -> {
                    autoLogin(view)
                }
                url.endsWith("/send") -> {
                    injectRecipient(view)
                }
            }
        }

        private fun autoLogin(view: WebView) {
            val u = config.username.jsEscapeOrNull() ?: return
            val p = config.password.jsEscapeOrNull() ?: return
            val js = """
                (function(){
                  var f = document.querySelector('form[action="/login"]') || document.forms[0];
                  if(!f) return;
                  var un = f.querySelector('input[name="username"]');
                  var pw = f.querySelector('input[name="password"]');
                  if(!un || !pw) return;
                  un.value = "$u";
                  pw.value = "$p";
                  f.submit();
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        }

        private fun injectRecipient(view: WebView) {
            val r = pendingRecipient?.jsEscapeOrNull() ?: return
            // Trigger the blur handler so the existing "sent before" check still runs.
            val js = """
                (function(){
                  var e = document.getElementById('to_email');
                  if(e){ e.value = "$r"; e.dispatchEvent(new Event('blur')); e.dispatchEvent(new Event('change')); }
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
            // Only inject once
            pendingRecipient = null
        }
    }
}

/** Helper: escape a string for safe embedding inside a JS double-quoted string. */
private fun String?.jsEscapeOrNull(): String? {
    if (this == null) return null
    return this.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
