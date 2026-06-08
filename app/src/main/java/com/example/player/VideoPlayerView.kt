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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
    if (isWebEmbedUrl(videoUrl)) {
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

@OptIn(UnstableApi::class)
@Composable
fun NativeVideoPlayerView(
    videoUrl: String,
    initialProgress: Float = 0f,
    onProgressUpdate: (Float) -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
            android.util.Log.e("VideoPlayerView", "Error shifting orientation mode to $orientationMode", e)
        }
    }

    // Hide system bars safely and restore original orientation when player exits
    DisposableEffect(context) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        try {
            // Hide status and navigation bars for immersive full screen
            activity?.window?.let { window ->
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
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerView", "Failed to restore fullscreen settings", e)
            }
        }
    }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Set up play sources
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            try {
                val mediaItem = MediaItem.fromUri(videoUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (initialProgress > 0f) {
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                val duration = exoPlayer.duration
                                if (duration > 0) {
                                    exoPlayer.seekTo((duration * initialProgress).toLong())
                                }
                            }
                        }
                    })
                }
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
