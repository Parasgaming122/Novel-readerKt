package com.paras.novelreaderkt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background-safe next chapter handler.
 *
 * This object decouples the auto-next-chapter flow from the WebView/Compose lifecycle.
 * When the foreground service needs to go to the next chapter (screen off / app backgrounded),
 * it calls [handleNativeNextChapter] which:
 *
 *  1. Resolves the next chapter URL via [WtrChapterUrlResolver] (pure HTTP, no WebView).
 *  2. If Gemini Translate is active, loads the raw URL (no Google Translate proxy).
 *    The pageLoadBackgroundLogic in BrowserAppScreen handles Gemini translation + extraction.
 *  3. If only Google Translate is active, applies the translate.goog proxy.
 *  4. Loads the new URL into the WebView via [WtrAudioControlBridge.onLoadUrlInWebView].
 *  5. Waits for the page to load, polls for the URL to change.
 *  6. Triggers paragraph extraction and playback if timeout occurs.
 *
 * All of this runs in the foreground service's lifecycle — no Compose dependency.
 */
object WtrNextChapterHandler {

    private const val TAG = "WtrNextChapter"
    private val handler = Handler(Looper.getMainLooper())
    private var nextChapterJob: Job? = null
    private var scope: CoroutineScope? = null

    fun init(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun cancel() {
        nextChapterJob?.cancel()
        nextChapterJob = null
    }

    /**
     * Called from WtrBrowserService when the last paragraph finishes and auto-next is needed.
     * This is the background-safe replacement for the old WebView-JS-based triggerNextChapterNavigation.
     */
    fun handleNativeNextChapter() {
        val s = scope ?: return
        cancel()

        nextChapterJob = s.launch(Dispatchers.Main) {
            try {
                val extractedUrl = WtrAudioControlBridge.extractedUrl.value
                if (extractedUrl.isEmpty() || extractedUrl == "chrome://newtab") {
                    Log.w(TAG, "No extracted URL, cannot resolve next chapter")
                    return@launch
                }

                WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Starting next chapter resolution from $extractedUrl")

                // Read settings from SharedPreferences
                val ctx = WtrAudioControlBridge.lastKnownContext
                val prefs = ctx?.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                val autoTranslateEnabled = prefs?.getBoolean("auto_translate_enabled", true) ?: true
                val autoTranslateDomains = prefs?.getString("auto_translate_domains", "") ?: ""
                val antiCaptchaDelay = prefs?.getBoolean("anti_captcha_delay", false) ?: false
                val geminiTranslateEnabled = prefs?.getBoolean("gemini_translate_enabled", false) ?: false
                val geminiApiKey = com.paras.novelreaderkt.SecurePreferences.getGeminiApiKey(ctx ?: return@launch)

                // Strip translate.goog to get the real underlying URL for chapter resolution
                val realUrl = stripTranslateGoog(extractedUrl)

                // Step 1: Check if current URL is a translate.goog URL
                val isCurrentlyTranslated = extractedUrl.contains("translate.goog") || extractedUrl.contains("translate.google")

                // Step 2: Resolve next chapter URL (on IO thread, no WebView needed)
                val rawNextUrl = withContext(Dispatchers.IO) {
                    WtrChapterUrlResolver.resolveNextChapterUrl(
                        currentUrl = realUrl,
                        autoTranslateDomains = autoTranslateDomains,
                        autoTranslateEnabled = autoTranslateEnabled
                    )
                }

                if (rawNextUrl == null) {
                    Log.w(TAG, "[NativeNextChapter] Could not resolve next chapter URL")
                    WtrAudioControlBridge.setIsAudiobookModeActive(false)
                    WtrAudioControlBridge.setIsPlayerRunning(false)
                    WtrAudioControlBridge.updatePlaybackState(false, null, "Could not find next chapter")
                    return@launch
                }

                WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Resolved next URL: $rawNextUrl")

                // Step 3: Determine the final URL to load
                // KEY FIX: If Gemini is active with API key, NEVER apply Google Translate proxy.
                // The pageLoadBackgroundLogic in BrowserAppScreen will handle Gemini translation.
                val geminiActive = geminiTranslateEnabled && geminiApiKey.trim().isNotEmpty()
                val isChapterUrl = !NovelContextManager.isLikelyInfoPage(rawNextUrl)
                val domainMatched = isDomainMatched(rawNextUrl, autoTranslateDomains)

                val finalUrl = when {
                    // Gemini active + chapter URL + domain matched → load raw URL, Gemini handles translation
                    geminiActive && isChapterUrl && domainMatched -> {
                        WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Gemini active, loading raw URL (no Google Translate)")
                        rawNextUrl
                    }
                    // Google Translate path
                    autoTranslateEnabled && domainMatched -> {
                        if (isCurrentlyTranslated && antiCaptchaDelay) {
                            WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Anti-CAPTCHA delay: waiting 4.5s")
                            delay(4500)
                        }
                        getProxyTranslatedUrl(rawNextUrl)
                    }
                    else -> rawNextUrl
                }

                WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Loading URL in WebView: $finalUrl")

                // Step 4: Load the URL in the WebView (must be on main thread)
                WtrAudioControlBridge.onLoadUrlInWebView?.invoke(finalUrl)

                // Step 5: Wait for the page to load and extraction to complete
                // The onPageFinished callback in BrowserAppScreen will handle extraction + playback.
                // We just need to wait and verify it happened.
                val waitStart = System.currentTimeMillis()
                val maxWaitMs = if (geminiActive && isChapterUrl && domainMatched) {
                    45000L // 45s for Gemini (API call + injection + extraction)
                } else {
                    25000L // 25s for Google Translate / regular pages
                }

                while (System.currentTimeMillis() - waitStart < maxWaitMs) {
                    delay(400)
                    val currentExtracted = WtrAudioControlBridge.extractedUrl.value
                    val currentList = WtrAudioControlBridge.playTrackInputList.value
                    val isRunning = WtrAudioControlBridge.isPlayerRunning.value

                    // Check if the URL changed and new paragraphs were extracted
                    if (currentExtracted != extractedUrl && currentList.isNotEmpty() && isRunning) {
                        WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Page loaded and extraction complete. ${currentList.size} paragraphs ready.")
                        return@launch
                    }

                    // If URL changed but no extraction yet, keep waiting
                    if (currentExtracted != extractedUrl) {
                        WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] URL changed to $currentExtracted, waiting for extraction...")
                        continue
                    }
                }

                // Timeout — extraction didn't complete
                Log.w(TAG, "[NativeNextChapter] Timed out waiting for page load and extraction")
                WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Timed out. Attempting fallback extraction...")

                // Fallback: if the page loaded but extraction didn't trigger (e.g. background JS was throttled),
                // trigger it manually via the callback
                WtrAudioControlBridge.onManualExtractAndPlay?.invoke()

            } catch (e: CancellationException) {
                Log.d(TAG, "[NativeNextChapter] Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "[NativeNextChapter] Error: ${e.message}")
                e.printStackTrace()
                WtrLogManager.log(WtrAudioControlBridge.lastKnownContext, "[NativeNextChapter] Error: ${e.message}")
            }
        }
    }

    /**
     * Strip translate.goog encoding from a URL to get the real underlying URL.
     */
    private fun stripTranslateGoog(url: String): String {
        if (!url.contains("translate.goog")) return url
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return url
            val decodedHost = host
                .replace(".translate.goog", "")
                .replace("--", "_DASH_")
                .replace("-", ".")
                .replace("_DASH_", "-")
            val path = uri.path ?: ""
            val query = uri.query?.let { "?$it" } ?: ""
            "https://$decodedHost$path$query"
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Checks if a URL's domain matches any of the comma-separated auto-translate domains.
     */
    private fun isDomainMatched(url: String, autoTranslateDomains: String): Boolean {
        if (autoTranslateDomains.isBlank()) return false
        val urlLower = url.lowercase()
        if (urlLower.contains("translate.goog") || urlLower.contains("translate.google")) return false
        val domainsList = autoTranslateDomains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return domainsList.any { domain ->
            val cleanDomain = domain.replace("https://", "").replace("http://", "").replace("www.", "").trim('/')
            cleanDomain.isNotEmpty() && urlLower.contains(cleanDomain)
        }
    }
}