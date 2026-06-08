package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.FirebaseInitializer
import com.example.ui.AuthScreen
import com.example.ui.MainAppContainer
import com.example.ui.MovieViewModel
import com.example.ui.theme.MovieHuntTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val viewModel: MovieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pre-create WebView cache directories to prevent chromium/ad-SDK initialization directory errors
        try {
            val webViewJsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!webViewJsDir.exists()) {
                val created = webViewJsDir.mkdirs()
                android.util.Log.d("MainActivity", "Pre-created WebView JS cache folder: $created")
            }
            val webViewWasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!webViewWasmDir.exists()) {
                val created = webViewWasmDir.mkdirs()
                android.util.Log.d("MainActivity", "Pre-created WebView WASM cache folder: $created")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error pre-creating WebView cache directories", e)
        }

        // Initialize custom Firebase Realtime Database and Auth Client using keys from FirebaseInitializer
        try {
            FirebaseInitializer.initialize(applicationContext)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "FirebaseInitializer unexpected crash", e)
        }



        // Ask for runtime notification permission on Android 13+ (API 33+) to support background and status-bar notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        super.onCreate(savedInstanceState)

        setContent {
            // Dynamic thematic settings collection
            val sharedPrefs = remember { this@MainActivity.getSharedPreferences("moviehunt_prefs", Context.MODE_PRIVATE) }
            var activeThemeName by remember { mutableStateOf(sharedPrefs.getString("active_theme", "classic-red") ?: "classic-red") }
            var isThemeDark by remember { mutableStateOf(sharedPrefs.getBoolean("is_theme_dark", true)) }

            // Listen for any theme preference changes
            DisposableEffect(sharedPrefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "active_theme") {
                        activeThemeName = sharedPrefs.getString("active_theme", "classic-red") ?: "classic-red"
                    } else if (key == "is_theme_dark") {
                        isThemeDark = sharedPrefs.getBoolean("is_theme_dark", true)
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            MovieHuntTheme(themeName = activeThemeName, isDark = isThemeDark) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1800)
                        showSplash = false
                    }

                    if (showSplash) {
                        SplashScreen()
                    } else {
                        var authStateChangedTrigger by remember { mutableStateOf(0) }
                        var isAuthenticated by remember(authStateChangedTrigger) {
                            mutableStateOf(
                                try {
                                    FirebaseAuth.getInstance().currentUser != null
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "FirebaseAuth currentUser check failed", e)
                                    false
                                }
                            )
                        }

                        // Also check Guest state
                        val isGuestUser by viewModel.isGuestUser.collectAsState()

                        if (isAuthenticated || isGuestUser) {
                            MainAppContainer(
                                viewModel = viewModel,
                                onLogout = {
                                    try {
                                        FirebaseAuth.getInstance().signOut()
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "FirebaseAuth signOut failed", e)
                                    }
                                    viewModel.handleLogout()
                                    authStateChangedTrigger++
                                }
                            )
                        } else {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    viewModel.handleAuthUpdate()
                                    authStateChangedTrigger++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = startAnimation,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800, easing = EaseOutBack)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "MOVIE",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "HUNT",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                     text = "Cinematic Streaming Experience",
                     color = Color.LightGray.copy(alpha = 0.7f),
                     fontSize = 14.sp,
                     fontWeight = FontWeight.Medium,
                     letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
