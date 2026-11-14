package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.DetectedBrowserVideo
import com.example.ui.util.isLikelyVideoUrl
import java.net.URLEncoder

private data class QuickPortal(val label: String, val url: String)

private val QuickPortals = listOf(
    QuickPortal("Internet Archive", "https://archive.org/details/movies"),
    QuickPortal("Wikimedia Videos", "https://commons.wikimedia.org/wiki/Category:Videos"),
    QuickPortal("Pexels Videos", "https://www.pexels.com/videos/"),
    QuickPortal("Sample Streams", "https://test-streams.mux.dev/")
)

private const val VIDEO_SCANNER_JS = """
    (function() {
        try {
            var found = new Set();
            document.querySelectorAll('video').forEach(function(v) {
                if (v.currentSrc && v.currentSrc.startsWith('http')) found.add(v.currentSrc);
                if (v.src && v.src.startsWith('http')) found.add(v.src);
            });
            document.querySelectorAll('video source, source').forEach(function(s) {
                if (s.src && s.src.startsWith('http')) found.add(s.src);
            });
            document.querySelectorAll('a[href]').forEach(function(a) {
                var h = a.href || '';
                if (/\.(mp4|m3u8|mpd|webm|mkv|mov)(\?|#|$)/i.test(h)) {
                    found.add(h);
                }
            });
            found.forEach(function(url) {
                VeloPlayBridge.onVideoFound(url, document.title || '');
            });
        } catch (e) {}
    })();
"""

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowseScreen(
    detectedVideos: List<DetectedBrowserVideo>,
    bottomExtraPadding: Dp,
    onPageStarted: () -> Unit,
    onVideoDetected: (url: String, pageTitle: String, pageUrl: String) -> Unit,
    onPlayDetectedVideo: (DetectedBrowserVideo, Boolean) -> Unit,
    onSaveDetectedVideo: (DetectedBrowserVideo) -> Unit,
    onClearDetectedVideos: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var addressText by remember { mutableStateOf("https://archive.org/details/movies") }
    var currentPageTitle by remember { mutableStateOf("") }
    var loadProgress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showProposedSheet by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun navigateToInput(rawInput: String) {
        focusManager.clearFocus()
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val target = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        addressText = target
        webViewRef?.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    if (showProposedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProposedSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proposed Videos (${detectedVideos.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        onClearDetectedVideos()
                        showProposedSheet = false
                    }) {
                        Text("Clear")
                    }
                }
                if (detectedVideos.isEmpty()) {
                    Text(
                        text = "Play a video on the webpage or tap a direct video link to detect it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(detectedVideos, key = { it.url }) { item ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(item.formatLabel, color = Color.White)
                                        }
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Text(
                                        text = item.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                showProposedSheet = false
                                                onPlayDetectedVideo(item, true)
                                            }
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play")
                                        }
                                        TextButton(
                                            onClick = {
                                                showProposedSheet = false
                                                onPlayDetectedVideo(item, false)
                                            }
                                        ) {
                                            Icon(Icons.Rounded.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Mini")
                                        }
                                        TextButton(
                                            onClick = { onSaveDetectedVideo(item) }
                                        ) {
                                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Address Bar & Browser Navigation
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Browser Back")
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Browser Forward")
                    }
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        placeholder = { Text(stringResource(R.string.browser_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput(addressText) }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_url_input")
                    )
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh page")
                    }
                }

                // Quick Portals
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickPortals.forEach { portal ->
                        AssistChip(
                            onClick = { navigateToInput(portal.url) },
                            label = { Text(portal.label) }
                        )
                    }
                }

                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // WebView + Floating Proposed Videos Pill
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomExtraPadding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("in_app_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoFound(url: String, pageTitle: String) {
                                    post {
                                        onVideoDetected(url, pageTitle.ifBlank { currentPageTitle }, this@apply.url ?: addressText)
                                    }
                                }
                            },
                            "VeloPlayBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress == 100) {
                                    view?.evaluateJavascript(VIDEO_SCANNER_JS, null)
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) currentPageTitle = title
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (url != null) addressText = url
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                onPageStarted()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (url != null) addressText = url
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                view?.evaluateJavascript(VIDEO_SCANNER_JS, null)
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                if (url != null && isLikelyVideoUrl(url)) {
                                    post {
                                        onVideoDetected(url, currentPageTitle, view?.url ?: addressText)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                if (isLikelyVideoUrl(targetUrl)) {
                                    onVideoDetected(targetUrl, currentPageTitle, view?.url ?: addressText)
                                    showProposedSheet = true
                                    return true
                                }
                                return false
                            }
                        }

                        webViewRef = this
                        loadUrl(addressText)
                    }
                }
            )

            // Floating "Videos Proposed" pill
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedVideos.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .testTag("proposed_videos_pill")
                        .clickable { showProposedSheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoCall,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "${detectedVideos.size} Video${if (detectedVideos.size > 1) "s" else ""} Proposed — Tap to Play",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
