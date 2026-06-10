package com.example.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

fun isWebEmbedUrl(url: String): Boolean {
    val lower = url.lowercase().trim()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return false
    }
    if (lower.contains("streamimdb") || 
        lower.contains("/embed/") || 
        lower.contains("/iframe/") || 
        lower.contains("youtube.com/embed") || 
        lower.contains("vimeo.com")
    ) {
        return true
    }
    val extensions = listOf(".mp4", ".m3u8", ".mpd", ".mkv", ".avi", ".3gp", ".webm", ".mov", ".ts", ".flv", "/m3u8", "/mp4")
    val hasVideoExtension = extensions.any { lower.contains(it) }
    return !hasVideoExtension
}

enum class OrientationMode {
    SENSOR,
    PORTRAIT,
    LANDSCAPE
}

@Composable
fun VideoPlayerView(
    videoUrl: String,
    initialProgress: Float = 0f,
    onProgressUpdate: (Float) -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var forceWebPlayer by remember { mutableStateOf(false) }

    if (isWebEmbedUrl(videoUrl) || forceWebPlayer) {
        WebVideoPlayerView(
            videoUrl = videoUrl,
            onBackClick = onBackClick,
            modifier = modifier
        )
    } else {
        NativeVideoPlayerView(
            videoUrl = videoUrl,
            initialProgress = initialProgress,
            onProgressUpdate = onProgressUpdate,
            onBackClick = onBackClick,
            onFallbackToWeb = {
                forceWebPlayer = true
            },
            modifier = modifier
        )
    }
}

@Composable
fun WebVideoPlayerView(
    videoUrl: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var orientationMode by remember { mutableStateOf(OrientationMode.SENSOR) }
    val activity = remember(context) { context.findActivity() }

    // Reactively update orientation based on user selection
    LaunchedEffect(orientationMode) {
        try {
            activity?.requestedOrientation = when (orientationMode) {
                OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } catch (e: Exception) {
            android.util.Log.e("WebVideoPlayerView", "Error shifting orientation mode to $orientationMode", e)
        }
    }

    // Hide system bars safely and restore original orientation when player exits
    DisposableEffect(context) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        try {
            activity?.window?.let { window ->
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Exception) {
            android.util.Log.e("WebVideoPlayerView", "Failed to apply fullscreen settings", e)
        }

        onDispose {
            try {
                activity?.requestedOrientation = originalOrientation
                activity?.window?.let { window ->
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (e: Exception) {
                android.util.Log.e("WebVideoPlayerView", "Failed to restore settings", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(videoUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay Close Header (With safeDrawingPadding to avoid camera notch clipping in landscape)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Exit Player",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Web Embed Stream",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // Modern orientation selector pill
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                    .clickable {
                        orientationMode = when (orientationMode) {
                            OrientationMode.SENSOR -> OrientationMode.PORTRAIT
                            OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                            OrientationMode.LANDSCAPE -> OrientationMode.SENSOR
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Screen Rotation Setting",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (orientationMode) {
                        OrientationMode.SENSOR -> "Auto-Rotate"
                        OrientationMode.PORTRAIT -> "Portrait Only"
                        OrientationMode.LANDSCAPE -> "Landscape Only"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

private fun trustAllHostsAndCertificates() {
    try {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
            }
        )
        val sc = javax.net.ssl.SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        android.util.Log.e("VideoPlayerView", "Error configuring trust-all SSL certificate", e)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun NativeVideoPlayerView(
    videoUrl: String,
    initialProgress: Float = 0f,
    onProgressUpdate: (Float) -> Unit = {},
    onBackClick: () -> Unit,
    onFallbackToWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var orientationMode by remember { mutableStateOf(OrientationMode.SENSOR) }
    val activity = remember(context) { context.findActivity() }

    var showFallbackDialog by remember { mutableStateOf(false) }

    // Reactively update orientation based on user selection
    LaunchedEffect(orientationMode) {
        try {
            activity?.requestedOrientation = when (orientationMode) {
                OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerView", "Error shifting orientation mode to $orientationMode", e)
        }
    }

    // Hide system bars safely and restore original orientation when player exits
    DisposableEffect(context) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        try {
            // Hide status and navigation bars for immersive full screen
            activity?.window?.let { window ->
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerView", "Failed to apply fullscreen settings", e)
        }

        onDispose {
            try {
                // Restore orientation and system bars
                activity?.requestedOrientation = originalOrientation
                activity?.window?.let { window ->
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerView", "Failed to restore fullscreen settings", e)
            }
        }
    }

    // Initialize TrackSelector
    val trackSelector = remember { androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context) }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        trustAllHostsAndCertificates()
        val defaultHttpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, defaultHttpDataSourceFactory)
        
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("VideoPlayerView", "ExoPlayer Error: ${error.message}", error)
                    android.widget.Toast.makeText(context, "Playback error: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    showFallbackDialog = true
                }
            })
        }
    }

    var audioTrackGroups by remember { mutableStateOf<List<androidx.media3.common.Tracks.Group>>(emptyList()) }
    var showAudioDialog by remember { mutableStateOf(false) }

    // Set up play sources
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            try {
                val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
                val lowerUrl = videoUrl.lowercase().trim()
                
                // Explicitly set MIME types based on extension structure to bypass incorrect application/octet-stream content headers
                val mimeType = when {
                    lowerUrl.contains(".m3u8") || lowerUrl.contains("/m3u8") || lowerUrl.contains(".m3u") -> "application/x-mpegURL"
                    lowerUrl.contains(".mpd") || lowerUrl.contains("/mpd") -> "application/dash+xml"
                    lowerUrl.contains(".ism") || lowerUrl.contains("/manifest") -> "application/vnd.ms-sstr+xml"
                    lowerUrl.contains(".mp4") || lowerUrl.contains("/mp4") -> "video/mp4"
                    lowerUrl.contains(".mkv") || lowerUrl.contains("/mkv") -> "video/x-matroska"
                    lowerUrl.contains(".webm") || lowerUrl.contains("/webm") -> "video/webm"
                    lowerUrl.contains(".ts") || lowerUrl.contains("/ts") || lowerUrl.contains(".mpegts") -> "video/mp2t"
                    lowerUrl.contains(".flv") || lowerUrl.contains("/flv") -> "video/x-flv"
                    lowerUrl.contains(".mov") || lowerUrl.contains("/mov") -> "video/quicktime"
                    lowerUrl.contains(".3gp") || lowerUrl.contains("/3gp") -> "video/3gpp"
                    lowerUrl.contains(".avi") || lowerUrl.contains("/avi") -> "video/x-msvideo"
                    else -> null
                }
                if (mimeType != null) {
                    mediaItemBuilder.setMimeType(mimeType)
                }
                
                val mediaItem = mediaItemBuilder.build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                
                exoPlayer.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        audioTrackGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && initialProgress > 0f) {
                            val duration = exoPlayer.duration
                            if (duration > 0) {
                                exoPlayer.seekTo((duration * initialProgress).toLong())
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerView", "Error playing media item", e)
            }
        }
    }

    // Capture position update loops
    LaunchedEffect(exoPlayer) {
        try {
            while (true) {
                delay(1000)
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    val duration = exoPlayer.duration
                    val position = exoPlayer.currentPosition
                    if (duration > 0) {
                        val prog = position.toFloat() / duration
                        onProgressUpdate(prog)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerView", "Error in progress update loop", e)
        }
    }

    // Sync state with Activity Lifecycles
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (exoPlayer.playbackState != Player.STATE_IDLE) {
                            exoPlayer.playWhenReady = false
                        }
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        if (exoPlayer.playbackState != Player.STATE_IDLE) {
                            exoPlayer.playWhenReady = true
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerView", "Error in lifecycle transition", e)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
                exoPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerView", "Error dispensing ExoPlayer resources", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Player Container View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay Close Header (With safeDrawingPadding to avoid camera notch clipping in landscape)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Exit Player",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Playing Stream",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))

            if (audioTrackGroups.isNotEmpty()) {
                val hasMultipleTracks = audioTrackGroups.sumOf { it.length } > 1
                if (hasMultipleTracks) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                            .clickable { showAudioDialog = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Audio Track",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audio",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Modern Web Player Switcher pill
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                    .clickable { onFallbackToWeb() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Switch to Web Player",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Web Player",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Modern orientation selector pill
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                    .clickable {
                        orientationMode = when (orientationMode) {
                            OrientationMode.SENSOR -> OrientationMode.PORTRAIT
                            OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                            OrientationMode.LANDSCAPE -> OrientationMode.SENSOR
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Screen Rotation Setting",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (orientationMode) {
                        OrientationMode.SENSOR -> "Auto-Rotate"
                        OrientationMode.PORTRAIT -> "Portrait Only"
                        OrientationMode.LANDSCAPE -> "Landscape Only"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }

        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Track") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        audioTrackGroups.forEach { group ->
                            for (trackIndex in 0 until group.length) {
                                val isSelected = group.isTrackSelected(trackIndex)
                                val trackName = group.getTrackFormat(trackIndex).language?.let { "Language: $it" }
                                    ?: group.getTrackFormat(trackIndex).label
                                    ?: "Audio Track ${trackIndex + 1}"
                                
                                item {
                                    TextButton(
                                        onClick = {
                                            trackSelector.setParameters(
                                                trackSelector.buildUponParameters()
                                                    .setOverrideForType(
                                                        androidx.media3.common.TrackSelectionOverride(
                                                            group.mediaTrackGroup, trackIndex
                                                        )
                                                    )
                                            )
                                            showAudioDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = trackName + if(isSelected) " (Selected)" else "",
                                            color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showFallbackDialog) {
            AlertDialog(
                onDismissRequest = { showFallbackDialog = false },
                title = { Text("Playback Error") },
                text = { Text("The native player had a problem playing this format/source. Would you like to switch to the alternative Web Player?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFallbackDialog = false
                            onFallbackToWeb()
                        }
                    ) {
                        Text("Switch to Web Player")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFallbackDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
