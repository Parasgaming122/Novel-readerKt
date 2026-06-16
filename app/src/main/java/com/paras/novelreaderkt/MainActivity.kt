package com.paras.novelreaderkt

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.paras.novelreaderkt.ui.BrowserAppScreen
import com.paras.novelreaderkt.ui.theme.NovelReaderV3Theme
import java.util.ArrayList

class MainActivity : ComponentActivity() {

    companion object {
        // Core tracking pool to safely avoid leaks and handle standard onDestroy destruction
        val activeWebViewsPool = java.util.Collections.synchronizedList(ArrayList<WebView>())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReportManager.init(this)
        CrashReportManager.clearOldCrashReports(this)
        WtrLogManager.initialize(this)
        enableEdgeToEdge()

        // Fast request for notification permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Start Foreground Service to manage Media Session right away
        val serviceIntent = Intent(this, WtrBrowserService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            WtrLogManager.log(this, "Failed to start WtrBrowserService: ${e.message}")
        }

        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember(context) { context.getSharedPreferences("wtr_browser_settings", android.content.Context.MODE_PRIVATE) }
            var activeThemeName by remember { mutableStateOf(sharedPrefs.getString("app_theme", "Dark") ?: "Dark") }

            NovelReaderV3Theme(themeName = activeThemeName) {
                BrowserAppScreen(
                    onThemeChanged = { newTheme ->
                        activeThemeName = newTheme
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        WtrAudioControlBridge.onWebViewProgressTrigger = null
        synchronized(activeWebViewsPool) {
            for (wv in activeWebViewsPool) {
                try {
                    wv.stopLoading()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            activeWebViewsPool.clear()
        }
        super.onDestroy()
    }
}

fun getProxyTranslatedUrl(url: String): String {
    val lower = url.lowercase()
    if (lower.contains("translate.goog") || lower.contains("translate.google")) {
        return url
    }
    try {
        val uri = android.net.Uri.parse(url)
        val host = uri.host ?: return url
        
        // Google Translate proxy rule: original hyphens become double hyphens, dots become single hyphens
        val hostWithoutWww = host.removePrefix("www.")
        val cleanHost = hostWithoutWww.replace("-", "--").replace(".", "-") + ".translate.goog"
        
        val path = uri.encodedPath ?: ""
        val query = uri.encodedQuery
        
        val builder = android.net.Uri.Builder()
            .scheme("https")
            .encodedAuthority(cleanHost)
            .encodedPath(path)
            
        if (query != null) {
            builder.encodedQuery(query)
            builder.appendQueryParameter("_x_tr_sl", "auto")
            builder.appendQueryParameter("_x_tr_tl", "en")
        } else {
            builder.appendQueryParameter("_x_tr_sl", "auto")
            builder.appendQueryParameter("_x_tr_tl", "en")
        }
        return builder.build().toString()
    } catch (e: Exception) {
        return url
    }
}
