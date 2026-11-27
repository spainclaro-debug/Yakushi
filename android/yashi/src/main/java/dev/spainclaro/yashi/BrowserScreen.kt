package dev.spainclaro.yashi

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI

/**
 * Full-screen browser shell around the Yakushi web frontend.
 *
 * A small floating back button returns to Yashi's start screen. It is hidden
 * while the web UI is showing a catalogue/browse page or the manga reader, so
 * it does not cover the content the user is focused on.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(url: String, onExit: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showBack by remember { mutableStateOf(!isImmersivePage(url)) }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            onExit()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentWebView = remember {
            WebView(context).apply {
                // Keep this configuration in sync with the Yakushi Server app's
                // embedded WebView. In particular, image pages rely on normal
                // zoom/viewport behaviour; disabling zoom made reader pages
                // render as empty boxes on some WebViews.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                clearCache(true)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        loading = true
                        showBack = !isImmersivePage(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        loading = false
                        showBack = !isImmersivePage(url)
                        injectReaderFix(view, url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        showBack = !isImmersivePage(url)
                        injectReaderFix(view, url)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = false

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            Toast.makeText(
                                context,
                                "Could not reach this server. Check that Yakushi is running and LAN mode is enabled.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                loadUrl(url)
                webView = this
            }
        }

        DisposableEffect(currentWebView) {
            onDispose { currentWebView.destroy() }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            factory = { currentWebView },
            update = {},
        )

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth(),
            )
        }

        if (showBack) {
            SmallFloatingActionButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    // Keep clear of the web UI's bottom navigation bar; 16.dp
                    // sat directly on top of the Library button on phones.
                    .padding(start = 16.dp, bottom = 96.dp, top = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Yashi")
            }
        }
    }
}

/**
 * Last-resort client-side reader workaround for the Android WebView. The
 * browser renders the paged reader correctly, but some WebViews report an odd
 * viewport while the page uses 100vh and let the image intercept the tap zones.
 * This injects a stylesheet sized to the real WebView height, makes paged
 * images fit/full and non-interactive, raises the reader settings/topbar above
 * the fixed tap overlay, and provides a capture-phase left/right tap fallback.
 * It only runs on /chapter pages.
 */
private fun injectReaderFix(view: WebView?, url: String?) {
    if (view == null || url == null) return
    val path = runCatching { URI(url).path }.getOrNull() ?: return
    if (!path.startsWith("/chapter")) return
    view.evaluateJavascript(READER_FIX_JS, null)
}

private val READER_FIX_JS = """
(function() {
  if (window.__yashiReaderFix) {
    if (window.__yashiApplyReaderFix) window.__yashiApplyReaderFix();
    return;
  }
  window.__yashiReaderFix = true;
  var style = document.createElement('style');
  style.id = 'yashi-reader-fix';
  (document.head || document.documentElement).appendChild(style);

  window.__yashiApplyReaderFix = function() {
    var h = window.innerHeight || document.documentElement.clientHeight || 0;
    if (!h) return;
    style.textContent = [
      '#page-list { width: 100vw !important; height: ' + h + 'px !important; overflow: hidden !important; }',
      '#page-list > img { width: auto !important; height: auto !important; max-width: 100vw !important; max-height: ' + h + 'px !important; object-fit: contain !important; pointer-events: none !important; margin: auto !important; }',
      '#next, #prev, #hide-bar { pointer-events: auto !important; touch-action: manipulation !important; }',
      '.reader-settings.modal { z-index: 200 !important; }',
      '.topbar { z-index: 220 !important; }',
      '.bottombar { z-index: 210 !important; }'
    ].join('\n');
  };
  window.__yashiApplyReaderFix();
  window.addEventListener('resize', window.__yashiApplyReaderFix);

  document.addEventListener('click', function(e) {
    if (location.pathname.indexOf('/chapter') !== 0) return;
    // Only act on real taps; our own target.click() below is untrusted and
    // must reach the reader's normal handler without being intercepted again.
    if (!e.isTrusted) return;
    var t = e.target;
    if (t && t.closest && t.closest('button, a, input, textarea, select, label, .reader-settings, .topbar, .bottombar')) return;
    var x = (e.clientX || 0) / (window.innerWidth || 1);
    var target = x > 0.66 ? document.getElementById('next')
               : x < 0.33 ? document.getElementById('prev')
               : document.getElementById('hide-bar');
    if (target) {
      e.preventDefault();
      e.stopPropagation();
      if (window.__yashiTapGuard) return;
      window.__yashiTapGuard = true;
      target.click();
      setTimeout(function() { window.__yashiTapGuard = false; }, 0);
    }
  }, true);

  setInterval(function() {
    if (window.__yashiApplyReaderFix) window.__yashiApplyReaderFix();
  }, 1500);
})();
""".trimIndent()

private fun isImmersivePage(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val path = runCatching { URI(url).path }.getOrNull() ?: return false
    return path.startsWith("/catalogue") ||
        path.startsWith("/manga") ||
        path.startsWith("/chapter")
}
