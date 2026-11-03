@file:OptIn(ExperimentalComposeUiApi::class)

package it.jarvis.discover.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/** Ponte JavaScript → Kotlin. Espone solo tre notifiche innocue; ogni callback gira sul thread principale. */
private class PlayerBridge(
    private val onReadyCb: () -> Unit,
    private val onStateCb: (Int) -> Unit,
    private val onErrorCb: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        main.post(onReadyCb)
    }

    @JavascriptInterface
    fun onState(state: Int) {
        main.post { onStateCb(state) }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { onErrorCb(code) }
    }
}

/** Stati del player che interessano all'app (valori di YT.PlayerState). */
object PlayerState {
    const val ENDED = 0
}

/**
 * Player YouTube incorporato in una WebView. Parte da solo; si ferma quando l'app va in secondo piano.
 * [captions] ricarica il player (dallo stesso secondo) con i sottotitoli attivi; [speed] cambia la velocità.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    captions: Boolean = false,
    speed: Float = 1f,
    onError: (Int) -> Unit = {},
    onEnded: () -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var webView by remember { mutableStateOf<WebView?>(null) }
    var appliedCaptions by remember { mutableStateOf(captions) }
    val latestSpeed by rememberUpdatedState(speed)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnEnded by rememberUpdatedState(onEnded)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    // Il player non deve mai portare la pagina altrove (es. toccando il logo).
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                        request?.isForMainFrame == true
                }
                addJavascriptInterface(
                    PlayerBridge(
                        onReadyCb = { evaluateJavascript("setRate($latestSpeed)", null) },
                        onStateCb = { if (it == PlayerState.ENDED) latestOnEnded() },
                        onErrorCb = { latestOnError(it) },
                    ),
                    "Android",
                )
                PlayerHtml.build(videoId, captions)?.let {
                    loadDataWithBaseURL("${PlayerHtml.ORIGIN}/", it, "text/html", "utf-8", null)
                } ?: latestOnError(-1)
                webView = this
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("Android")
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )

    LaunchedEffect(speed, webView) {
        webView?.evaluateJavascript("setRate($speed)", null)
    }

    LaunchedEffect(captions, webView) {
        val view = webView ?: return@LaunchedEffect
        if (captions == appliedCaptions) return@LaunchedEffect
        appliedCaptions = captions
        view.evaluateJavascript("getTime()") { value ->
            val start = value?.toDoubleOrNull()?.toInt() ?: 0
            PlayerHtml.build(videoId, captions, start)?.let {
                view.loadDataWithBaseURL("${PlayerHtml.ORIGIN}/", it, "text/html", "utf-8", null)
            }
        }
    }

    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            val view = webView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    view.evaluateJavascript("pauseVideo()", null)
                    view.onPause()
                }
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
