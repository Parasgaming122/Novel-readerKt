package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.BrowserViewModel
import com.example.BrowserSection
import com.example.MainActivity
<<<<<<< HEAD
import com.example.TranslationEngine
import com.example.UnifiedTranslator
=======
import com.example.getProxyTranslatedUrl
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
import com.example.WtrAudioControlBridge
import com.example.sites.WebsiteSupportRegistry
import com.example.sites.commons.CommonSelectors
import com.example.WtrWebAppInterface
import com.example.data.*
import kotlinx.coroutines.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

fun isSameBaseOrTranslatedUrl(url1: String, url2: String): Boolean {
    fun clean(url: String): String {
        if (url.isEmpty()) return ""
        var cleanVal = url.lowercase()
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")

        if (cleanVal.contains(".translate.goog")) {
            try {
                val firstSlash = cleanVal.indexOf('/')
                val host = if (firstSlash >= 0) cleanVal.substring(0, firstSlash) else cleanVal
                val path = if (firstSlash >= 0) cleanVal.substring(firstSlash) else ""

                val hostWithoutTranslate = host.replace(".translate.goog", "")
                val decodedHost = hostWithoutTranslate
                    .replace("--", "_DASH_")
                    .replace("-", ".")
                    .replace("_DASH_", "-")

                cleanVal = decodedHost + path
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val noPagination = cleanVal.replace(Regex("""_\d+\.html$"""), ".html")
        return noPagination.split("?")[0].split("#")[0].trim('/')
    }
    return clean(url1) == clean(url2)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserAppScreen(onThemeChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: BrowserViewModel = viewModel()
    
    val activeTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val urlInput by viewModel.currentUrlInput.collectAsStateWithLifecycle()
    val tabsList by viewModel.allTabs.collectAsStateWithLifecycle()
    val searchEngineUrl by viewModel.searchEngine.collectAsStateWithLifecycle()

    val isBookmarked by activeTab?.let { 
        viewModel.isUrlBookmarked(it.url).collectAsStateWithLifecycle(initialValue = false) 
    } ?: remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        WtrAudioControlBridge.setCurrentlyActiveTabId(activeTab?.id)
    }

    var currentSection by remember { mutableStateOf(BrowserSection.WEB) }
    var webProgress by remember { mutableIntStateOf(100) }
    var isWebLoading by remember { mutableStateOf(false) }

    var longPressedUrl by remember { mutableStateOf<String?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }

    var showLogsDialog by remember { mutableStateOf(false) }

    val saveLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val logsText = com.example.WtrLogManager.logs.joinToString("\n")
                    outputStream.write(logsText.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, "Diagnostic logs saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to save logs: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sharedPrefs = remember(context) { context.getSharedPreferences("wtr_browser_settings", android.content.Context.MODE_PRIVATE) }
    var enableWebTrackplayer by remember { mutableStateOf(sharedPrefs.getBoolean("enable_web_trackplayer", false)) }
    var forceDarkContent by remember { mutableStateOf(sharedPrefs.getBoolean("force_dark_content", false)) } 
    var autoFocusParagraphs by remember { mutableStateOf(sharedPrefs.getBoolean("auto_focus_paragraphs", true)) } 
    var autoTranslateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("auto_translate_enabled", true)) }
    val defaultTranslateDomains = remember { WebsiteSupportRegistry.getAutoTranslateSites().joinToString(", ") }
    var autoTranslateDomains by remember { mutableStateOf(sharedPrefs.getString("auto_translate_domains", defaultTranslateDomains) ?: defaultTranslateDomains) }
<<<<<<< HEAD
    var translationEngineKey by remember { mutableStateOf(sharedPrefs.getString("translation_engine", TranslationEngine.GOOGLE_TRANSLATE.key) ?: TranslationEngine.GOOGLE_TRANSLATE.key) }
    var geminiTranslateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gemini_translate_enabled", false)) } // Keep for Gemini engine users
=======
    var geminiTranslateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gemini_translate_enabled", false)) }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
    var geminiApiKey by remember { mutableStateOf(com.example.SecurePreferences.getGeminiApiKey(context)) }
    var adBlockerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_blocker_enabled", true)) }
    var customTextZoom by remember { mutableStateOf(sharedPrefs.getInt("custom_text_zoom", 115)) }
    var antiCaptchaDelay by remember { mutableStateOf(sharedPrefs.getBoolean("anti_captcha_delay", false)) }
    var previousTabId by remember { mutableStateOf<Long?>(null) }
    var currentThemeName by remember { mutableStateOf(sharedPrefs.getString("app_theme", "Dark") ?: "Dark") }

    var urlText by remember { mutableStateOf("") }
    val extractedUrlOfActiveTracks by WtrAudioControlBridge.extractedUrl.collectAsStateWithLifecycle()

    val webViewsMap = remember { mutableStateMapOf<Long, WebView>() }

    LaunchedEffect(Unit) {
        WtrAudioControlBridge.onMetadataExtracted = { tabId, novelTitle, chapterTitle, coverImage ->
            val url = webViewsMap[tabId]?.url
            if (url != null) {
                viewModel.updateNovelMetadata(url, novelTitle, chapterTitle, coverImage)
            }
        }
    }

    LaunchedEffect(urlInput, isSearchFocused) {
        if (!isSearchFocused) {
            urlText = if (urlInput == "chrome//newtab" || urlInput == "chrome://newtab") "" else getCleanDisplayUrl(urlInput)
        } else {
            urlText = if (urlInput == "chrome//newtab" || urlInput == "chrome://newtab") "" else urlInput
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val activeTtsSpeed by WtrAudioControlBridge.ttsSpeed.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Maintain a map of dynamic WebViews keyed by Tab ID
    var runHtmlTextExtractionAndPlayRef by remember { mutableStateOf<(() -> Unit)?>(null) }

<<<<<<< HEAD
    // Translation engine state (replaces old Google Translate proxy anti-loop maps)
    var translationEngine by rememberUpdatedState(TranslationEngine.fromKey(translationEngineKey))
=======
    val translationAttempts = remember { mutableStateMapOf<String, Int>() }
    val lastTranslationTime = remember { mutableStateOf(mutableMapOf<String, Long>()) }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f

    val isNovelChapterUrl: (String?) -> Boolean = { url ->
        if (url == null) {
            false
        } else {
            val isSupportedNovelHost = com.example.sites.WebsiteSupportRegistry.findSupport(url) != null
            val urlLower = url.lowercase()
            val hasChapterKeyword = urlLower.contains("chapter") || 
                                    urlLower.contains("-ch-") || 
                                    urlLower.contains("/ch/") ||
                                    urlLower.contains("novelhubapp") ||
                                    urlLower.contains("wtr-lab")
            isSupportedNovelHost || hasChapterKeyword
        }
    }

    val isDomainMatchedForTranslation: (String?) -> Boolean = { url ->
        if (url == null || !autoTranslateEnabled) {
            false
        } else {
            val urlLower = url.lowercase()
<<<<<<< HEAD
            // No longer checking for translate.goog since we don't use proxy URLs anymore
            val domainsList = autoTranslateDomains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            domainsList.any { domain ->
                val cleanDomain = domain.replace("https://", "").replace("http://", "").replace("www.", "").trim('/')
                cleanDomain.isNotEmpty() && urlLower.contains(cleanDomain)
=======
            if (urlLower.contains("translate.goog") || urlLower.contains("translate.google")) {
                false
            } else {
                val domainsList = autoTranslateDomains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                domainsList.any { domain ->
                    val cleanDomain = domain.replace("https://", "").replace("http://", "").replace("www.", "").trim('/')
                    cleanDomain.isNotEmpty() && urlLower.contains(cleanDomain)
                }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
            }
        }
    }

<<<<<<< HEAD
    // shouldTranslateUrl is no longer used — translation is now handled in-page via DOM injection
    // (the old Google Translate proxy redirect approach has been replaced by UnifiedTranslator)

    var isGeminiTranslating by remember { mutableStateOf(false) }
    val currentTranslationEngine by rememberUpdatedState(translationEngine)
=======
    val shouldTranslateUrl: (String?) -> Boolean = { url ->
        if (geminiTranslateEnabled && geminiApiKey.trim().isNotEmpty() && isNovelChapterUrl(url)) {
            false
        } else if (!isDomainMatchedForTranslation(url)) {
            false
        } else {
            val urlLower = url!!.lowercase()
            val cleanUrl = urlLower.split("?")[0].split("#")[0].trim('/')
            val now = System.currentTimeMillis()
            val attempts = translationAttempts[cleanUrl] ?: 0
            val lastTime = lastTranslationTime.value[cleanUrl] ?: 0L

            if (now - lastTime < 10000) {
                if (attempts >= 2) {
                    android.util.Log.e("WtrBrowser", "Translation loop detected for $url! Skipping Google Translate redirection.")
                    false
                } else {
                    translationAttempts[cleanUrl] = attempts + 1
                    lastTranslationTime.value[cleanUrl] = now
                    true
                }
            } else {
                translationAttempts[cleanUrl] = 1
                lastTranslationTime.value[cleanUrl] = now
                true
            }
        }
    }

    var isGeminiTranslating by remember { mutableStateOf(false) }
    val currentGeminiTranslateEnabled by rememberUpdatedState(geminiTranslateEnabled)
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
    val currentGeminiApiKey by rememberUpdatedState(geminiApiKey)
    val currentAutoTranslateEnabled by rememberUpdatedState(autoTranslateEnabled)
    
    val pageLoadBackgroundLogic: (String, WebView) -> Unit = { urlVal, webView ->
        if (urlVal.isNotEmpty() && urlVal != "chrome://newtab") {
            viewModel.viewModelScope.launch(Dispatchers.Main) {
<<<<<<< HEAD
                // Unified translation: works for all engines (MyMemory, Gemini)
                val isTranslateTarget = currentAutoTranslateEnabled && isDomainMatchedForTranslation(urlVal) && isNovelChapterUrl(urlVal)
                if (isTranslateTarget) {
                    isGeminiTranslating = true
                    try {
                        // Use NovelExtractor for high-quality paragraph extraction
                        val extractionJs = extractParagraphsWithNovelExtractor(webView)

=======
                val isTranslateTarget = currentGeminiTranslateEnabled && currentGeminiApiKey.isNotEmpty() && isDomainMatchedForTranslation(urlVal) && isNovelChapterUrl(urlVal)
                if (isTranslateTarget) {
                    isGeminiTranslating = true
                    try {
                        val support = com.example.sites.WebsiteSupportRegistry.findSupport(urlVal)
                        val containerSelectorStr = if (support != null) {
                            support.containerSelectors.joinToString(", ")
                        } else {
                            ""
                        }
                        val pSel = support?.paragraphSelector ?: "p, .wtr-line-segment"
                        val excludeClasses = (support?.excludeSelectors ?: emptyList()).ifEmpty { 
                            com.example.sites.commons.CommonSelectors.COMMON_EXCLUDE 
                        }
                        val excludeClassesStr = excludeClasses.joinToString(", ")
                        val requiresBrPrepVal = if (support?.requiresBrPreparation == true) "true" else "false"

                        val extractionJs = """
                            (function() {
                                let paragraphs = [];
                                
                                function isJunk(text) {
                                    let t = text.toLowerCase().trim();
                                    if (t.length < 5) return true;
                                    const promoKeywords = ["join our discord", "join discord", "patreon", "support me", "support the author", "rate this", "please review", "please rate", "author's note", "author note", "recommend", "translator", "translation", "editor's note", "editor note"];
                                    return promoKeywords.some(keyword => t.includes(keyword));
                                }
                                
                                let containers = [];
                                const containerSelector = "${containerSelectorStr.replace("\"", "\\\"")}";
                                if (containerSelector) {
                                    let rawContainers = Array.from(document.querySelectorAll(containerSelector));
                                    containers = rawContainers.filter(c => !rawContainers.some(other => other !== c && other.contains(c)));
                                }
                                
                                function prepareBrParagraphs(contentEl) {
                                    if (!contentEl) return;
                                    if (contentEl.querySelector('.wtr-line-segment') || contentEl.querySelector('.wtr-focus-highlight')) return;
                                    
                                    const isTwkan = window.location.hostname.includes("twkan") || window.location.hostname.includes("ttkan") || window.location.href.includes("twkan") || window.location.href.includes("ttkan");
                                    if (isTwkan) {
                                        contentEl.querySelectorAll("div.txtad, div.txtcenter, div.ad, script, noscript, iframe, ins, .ad-placement, #ad-container").forEach(el => el.remove());
                                        let paragraphs = [];
                                        let currentPart = [];
                                        
                                        function flushPart() {
                                            if (currentPart.length > 0) {
                                                let joined = currentPart.join(" ").trim();
                                                joined = joined.replace(/^[\u2003\u3000\t ]+/g, "").trim();
                                                if (joined.length > 5) {
                                                    paragraphs.push(joined);
                                                }
                                                currentPart = [];
                                            }
                                        }
                                        
                                        let children = Array.from(contentEl.childNodes);
                                        children.forEach(node => {
                                            if (node.nodeType === 3) {
                                                let txt = node.textContent.trim();
                                                if (txt) currentPart.push(txt);
                                            } else if (node.nodeType === 1) {
                                                let tagName = node.tagName.toLowerCase();
                                                if (tagName === 'br') {
                                                    flushPart();
                                                } else if (tagName === 'font' || tagName === 'span' || tagName === 'b' || tagName === 'i' || tagName === 'strong' || tagName === 'em') {
                                                    let txt = node.innerText || node.textContent;
                                                    txt = txt.trim();
                                                    if (txt) currentPart.push(txt);
                                                } else {
                                                    flushPart();
                                                    let txt = node.innerText || node.textContent;
                                                    txt = txt.trim();
                                                    if (txt.length > 5) {
                                                        paragraphs.push(txt);
                                                    }
                                                }
                                            }
                                        });
                                        flushPart();
                                        
                                        let newHtml = "";
                                        paragraphs.forEach(pText => {
                                            newHtml += '<span class="wtr-line-segment">' + pText + '</span><br><br>';
                                        });
                                        contentEl.innerHTML = newHtml;
                                        return;
                                    }
                                    
                                    let pTags = contentEl.querySelectorAll('p');
                                    if (pTags.length > 5) return; 
                                    
                                    let html = contentEl.innerHTML;
                                    let parts = html.split(/<br\s*\/?>/i);
                                    let newParts = parts.map(part => {
                                        let trimmed = part.replace(/<[^>]+>/g, '').trim();
                                        if (trimmed.length > 5) {
                                            if (!part.trim().startsWith('<span class="wtr-line-segment"')) {
                                                return '<span class="wtr-line-segment">' + part + '</span>';
                                            }
                                        }
                                        return part;
                                    });
                                    contentEl.innerHTML = newParts.join('<br>');
                                }
                                
                                if ($requiresBrPrepVal == "true" || $requiresBrPrepVal == true) {
                                    containers.forEach(c => prepareBrParagraphs(c));
                                }
                                
                                let pTags = [];
                                if (containers.length > 0) {
                                    containers.forEach(contentEl => {
                                        let rawPTags = Array.from(contentEl.querySelectorAll("${pSel.replace("\"", "\\\"")}"));
                                        let filtered = rawPTags.filter(p => !rawPTags.some(parent => parent !== p && parent.contains(p)));
                                        pTags.push(...filtered);
                                    });
                                } else {
                                    pTags = Array.from(document.querySelectorAll('p'));
                                    if (pTags.length === 0) {
                                        pTags = Array.from(document.querySelectorAll('div, span'));
                                    }
                                }
                                
                                const excludeClass = "${excludeClassesStr.replace("\"", "\\\"")}";
                                pTags.forEach(p => {
                                    if (excludeClass && p.closest(excludeClass)) return;
                                    let text = p.innerText.trim();
                                    if (text.length > 5 && !isJunk(text)) {
                                        p.setAttribute('wtr-translation-id', paragraphs.length);
                                        paragraphs.push(text);
                                    }
                                });
                                return JSON.stringify(paragraphs);
                            })();
                        """.trimIndent()
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        val paragraphsJson = suspendCancellableCoroutine<String> { continuation ->
                            webView.evaluateJavascript(extractionJs) { result ->
                                if (continuation.isActive) continuation.resume(result ?: "[]")
                            }
                        }
                        val cleanJson = if (paragraphsJson.startsWith("\"") && paragraphsJson.endsWith("\"")) {
                            try { org.json.JSONTokener(paragraphsJson).nextValue() as String } catch(e: Exception) { paragraphsJson }
                        } else paragraphsJson
                        val paragraphsList = mutableListOf<String>()
                        try {
                            val jsonArray = org.json.JSONArray(cleanJson)
                            for (i in 0 until jsonArray.length()) paragraphsList.add(jsonArray.getString(i))
                        } catch (e: Exception) {}
<<<<<<< HEAD

                        com.example.WtrLogManager.log(context, "UnifiedTranslator: extracted ${paragraphsList.size} paragraphs from $urlVal using engine=${currentTranslationEngine.key}")

                        if (paragraphsList.isNotEmpty()) {
                            val injectionJs = withContext(Dispatchers.IO) {
                                val translatedList = UnifiedTranslator.translate(
                                    paragraphs = paragraphsList,
                                    engine = currentTranslationEngine,
                                    geminiApiKey = currentGeminiApiKey
                                )
=======
                        if (paragraphsList.isNotEmpty()) {
                            val injectionJs = withContext(Dispatchers.IO) {
                                val translatedList = com.example.GeminiTranslator.translateParagraphs(paragraphsList, currentGeminiApiKey)
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                                val translationMapJson = org.json.JSONArray()
                                translatedList.forEachIndexed { index, text ->
                                    val obj = org.json.JSONObject()
                                    obj.put("id", index)
                                    obj.put("text", text)
                                    translationMapJson.put(obj)
                                }
                                val escapedJsonString = org.json.JSONObject.quote(translationMapJson.toString())
                                """
                                    (function() {
                                        try {
                                            const translations = JSON.parse(${escapedJsonString});
                                            translations.forEach(item => {
                                                const el = document.querySelector('[wtr-translation-id="' + item.id + '"]');
                                                if (el) el.innerText = item.text;
                                            });
                                            return "success";
                                        } catch(e) { return "error: " + e.toString(); }
                                    })();
                                """.trimIndent()
                            }
                            suspendCancellableCoroutine<String> { continuation ->
                                webView.evaluateJavascript(injectionJs) { result ->
                                    if (continuation.isActive) continuation.resume(result ?: "")
                                }
                            }
                        }
<<<<<<< HEAD
                    } catch (e: Exception) {
                        com.example.WtrLogManager.log(context, "UnifiedTranslator error: ${e.message}")
                        e.printStackTrace()
                    } finally {
=======
                    } catch (e: Exception) { e.printStackTrace() } finally {
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        isGeminiTranslating = false
                        if (WtrAudioControlBridge.isAudiobookModeActive.value) {
                            delay(400)
                            runHtmlTextExtractionAndPlayRef?.invoke()
                        }
                    }
                } else {
<<<<<<< HEAD
                    // No translation needed — handle TTS extraction normally
                    if (WtrAudioControlBridge.isAudiobookModeActive.value) {
                        val isWtrLab = urlVal.contains("wtr-lab.com") || urlVal.isEmpty()
                        if (!isWtrLab && isNovelChapterUrl(urlVal)) {
                            delay(500)
                            runHtmlTextExtractionAndPlayRef?.invoke()
                        } else {
                            WtrAudioControlBridge.setIsPlayerRunning(false)
                            if (isWtrLab) {
                                WtrAudioControlBridge.setIsAudiobookModeActive(false)
                            }
                        }
=======
                    if (WtrAudioControlBridge.isAudiobookModeActive.value) {
                         val isTranslating = currentAutoTranslateEnabled && isDomainMatchedForTranslation(urlVal)
                         if (isTranslating && !urlVal.contains("translate.goog")) {
                             // Wait up to 1.5s to see if a redirect starts 
                             var redirected = false
                             for (i in 1..5) {
                                 delay(300)
                                 val currentTabUrl = viewModel.currentTab.value?.url ?: ""
                                 if (currentTabUrl.contains("translate.goog") || !isDomainMatchedForTranslation(currentTabUrl)) {
                                     redirected = true
                                     break
                                 }
                             }
                             if (!redirected) {
                                 // No redirect occurred, extract and play anyway!
                                 val isWtrLab = urlVal.contains("wtr-lab.com") || urlVal.isEmpty()
                                 if (!isWtrLab && isNovelChapterUrl(urlVal)) {
                                     delay(500)
                                     runHtmlTextExtractionAndPlayRef?.invoke()
                                 }
                             }
                         } else {
                             val isWtrLab = urlVal.contains("wtr-lab.com") || urlVal.isEmpty()
                             if (!isWtrLab && isNovelChapterUrl(urlVal)) {
                                 delay(800)
                                 runHtmlTextExtractionAndPlayRef?.invoke()
                             } else {
                                 WtrAudioControlBridge.setIsPlayerRunning(false)
                                 if (isWtrLab) {
                                     WtrAudioControlBridge.setIsAudiobookModeActive(false)
                                 }
                             }
                         }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                    }
                }
            }
        }
    }

<<<<<<< HEAD
    }

=======
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
    // Resolve or build WebView content for the current selected tab
    val currentActiveWebView = activeTab?.let { tab ->
        webViewsMap.getOrPut(tab.id) {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    
                    // Native Caching and Performance Optimization
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = false
                    
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    mediaPlaybackRequiresUserGesture = false
                    textZoom = customTextZoom
                    
                    userAgentString = if (tab.isDesktopMode) {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
                    } else {
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                    }
                    useWideViewPort = tab.isDesktopMode
                    loadWithOverviewMode = tab.isDesktopMode
                }
                
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (viewModel.currentTab.value?.id == tab.id) {
                            webProgress = newProgress
                            if (newProgress >= 100) {
                                isWebLoading = false
                            }
                        }
                        if (newProgress >= 10 && newProgress < 85) {
<<<<<<< HEAD
                            view?.let {
                                injectTtsBridgeScript(it)
                                injectNovelExtractorScript(it)
                            }
=======
                            view?.let { injectTtsBridgeScript(it) }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
<<<<<<< HEAD
                        // Translation is now handled in-page via DOM injection (UnifiedTranslator), not URL proxy
=======
                        if (url == null) return false
                        val currentUrl = view?.url ?: ""
                        if (!isSameBaseOrTranslatedUrl(currentUrl, url) && shouldTranslateUrl(url)) {
                            val translatedUrl = getProxyTranslatedUrl(url)
                            com.example.WtrLogManager.log(context, "shouldOverrideUrlLoading redirect tab=${tab.id} translation: $url -> $translatedUrl")
                            view?.loadUrl(translatedUrl)
                            return true
                        }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        return false
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
<<<<<<< HEAD
                        // Translation is now handled in-page via DOM injection (UnifiedTranslator), not URL proxy
=======
                        val url = request?.url?.toString() ?: return false
                        if (request.isForMainFrame && !request.url.toString().startsWith("intent://")) {
                            val currentUrl = view?.url ?: ""
                            if (!isSameBaseOrTranslatedUrl(currentUrl, url) && shouldTranslateUrl(url)) {
                                val translatedUrl = getProxyTranslatedUrl(url)
                                com.example.WtrLogManager.log(context, "shouldOverrideUrlLoading redirect tab=${tab.id} translation: $url -> $translatedUrl")
                                view?.loadUrl(translatedUrl)
                                return true
                            }
                        }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) {
                            com.example.WtrLogManager.log(context, "onPageStarted tab=${tab.id}: $url")
                        }
                        if (viewModel.currentTab.value?.id == tab.id) {
                            isWebLoading = true
                            webProgress = 10
                        }
<<<<<<< HEAD
                        view?.let {
                            injectTtsBridgeScript(it)
                            injectNovelExtractorScript(it)
                        }
=======
                        view?.let { injectTtsBridgeScript(it) }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) {
                            com.example.WtrLogManager.log(context, "onPageFinished tab=${tab.id}: $url (title: ${view?.title})")
                        }
                        if (viewModel.currentTab.value?.id == tab.id) {
                            isWebLoading = false
                            webProgress = 100
                            if (url != null && view != null) {
                                viewModel.onPageLoaded(url, view.title ?: "Wtr-Lab")
                                // Directly trigger background logic bypassing Compose pauses!
                                pageLoadBackgroundLogic(url, view)
                            }
                        }
                        injectTtsBridgeScript(this@apply)
<<<<<<< HEAD
                        injectNovelExtractorScript(this@apply)
                        if (forceDarkContent) {
                            injectForceDarkCss(this@apply)
                        }
=======
                        if (forceDarkContent) {
                            injectForceDarkCss(this@apply)
                        }
                        if (url != null && (url.contains("translate.goog") || url.contains("translate.google"))) {
                            injectTranslateCssCleanup(this@apply)
                        }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            com.example.WtrLogManager.log(context, "onReceivedError tab=${tab.id}: ${error?.description}")
                            
                            val rawDesc = error?.description?.toString() ?: "Network error occurred."
                            val safeDescription = rawDesc
                                .replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                                .replace("\"", "&quot;")
                                .replace("'", "&#x27;")
                            
                            val errorHtml = """
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1">
                                    <style>
                                        body { font-family: sans-serif; padding: 40px; text-align: center; color: #666; background: #f9f9f9; }
                                        h1 { color: #333; }
                                        .btn { margin-top: 20px; padding: 10px 20px; background: #007bff; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer; }
                                        @media (prefers-color-scheme: dark) {
                                            body { color: #aaa; background: #121212; }
                                            h1 { color: #ddd; }
                                            .btn { background: #bb86fc; color: #000; }
                                        }
                                    </style>
                                </head>
                                <body>
                                    <h1>Navigation Failed</h1>
                                    <p>${safeDescription}</p>
                                    <button class="btn" onclick="window.location.reload()">Try Again</button>
                                </body>
                                </html>
                            """.trimIndent()
                            view?.loadDataWithBaseURL(request.url.toString(), errorHtml, "text/html", "UTF-8", request.url.toString())
                        }
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString()
                        if (url != null) {
                            if (adBlockerEnabled) {
                                val urlLower = url.lowercase()
                                val adKeywords = listOf(
                                    "googlesyndication.com", "googleads", "doubleclick.net", "adservice.google",
                                    "adsystem", "popunder", "popads", "onclickads", "taboola", "outbrain",
                                    "mgid.com", "scorecardresearch", "analytics.google", "googletagmanager.com",
                                    "google-analytics.com", "cnzz.com", "51.la", "umeng.com", "umeng.co",
                                    "hm.baidu.com", "pos.baidu.com", "cpro.baidustatic.com", "pstatp.com",
                                    "tanx.com", "alimama.com"
                                )
                                if (adKeywords.any { urlLower.contains(it) }) {
                                    return android.webkit.WebResourceResponse(
                                        "text/javascript", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                            }
                            
                            // High-performance static assets cache specifically for wtr-lab.com
                            if (url.contains("wtr-lab.com")) {
                                val isStatic = url.contains(".js") || url.contains(".css") ||
                                        url.contains(".woff") || url.contains(".woff2") ||
                                        url.contains(".png") || url.contains(".jpg") || url.contains(".jpeg") || url.contains(".svg")
                                if (isStatic) {
                                    try {
                                        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
                                        val hashBytes = messageDigest.digest(url.toByteArray(Charsets.UTF_8))
                                        val safeFileName = hashBytes.joinToString("") { "%02x".format(it) }
                                        
                                        val cacheFolder = java.io.File(context.cacheDir, "wtr_static_cache")
                                        if (!cacheFolder.exists()) {
                                            cacheFolder.mkdirs()
                                        }
                                        val cacheFile = java.io.File(cacheFolder, safeFileName)
                                        
                                        if (cacheFile.exists() && cacheFile.length() > 0) {
                                            val mimeType = when {
                                                url.contains(".js") -> "text/javascript"
                                                url.contains(".css") -> "text/css"
                                                url.contains(".woff2") -> "font/woff2"
                                                url.contains(".woff") -> "font/woff"
                                                url.contains(".png") -> "image/png"
                                                url.contains(".jpg") || url.contains(".jpeg") -> "image/jpeg"
                                                url.contains(".svg") -> "image/svg+xml"
                                                else -> "application/octet-stream"
                                            }
                                            com.example.WtrLogManager.log(context, "⚡ Cache Hit for Wtr-Lab: $url -> Loaded from private storage")
                                            return android.webkit.WebResourceResponse(
                                                mimeType, "UTF-8", java.io.FileInputStream(cacheFile)
                                            )
                                        } else {
                                            // Asynchronously prefetch so we don't block the WebView's resource loading pipeline
                                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                var connection: java.net.HttpURLConnection? = null
                                                try {
                                                    connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                                    connection.connectTimeout = 3000
                                                    connection.readTimeout = 3000
                                                    if (connection.responseCode == 200) {
                                                        connection.inputStream.use { input ->
                                                            val tempFile = java.io.File(cacheFolder, "$safeFileName.tmp")
                                                            java.io.FileOutputStream(tempFile).use { output ->
                                                                input.copyTo(output)
                                                            }
                                                            tempFile.renameTo(cacheFile)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore background prefetch errors
                                                } finally {
                                                    try { connection?.disconnect() } catch (ignored: Exception) {}
                                                }
                                            }
                                            return null
                                        }
                                    } catch (e: Exception) {
                                        com.example.WtrLogManager.log(context, "Cache error on $url: ${e.message}")
                                    }
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                setOnLongClickListener { _ ->
                    val hr = hitTestResult
                    val type = hr.type
                    if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                        val url = hr.extra
                        if (url != null) {
                            longPressedUrl = url
                        }
                        true
                    } else {
                        false
                    }
                }

                addJavascriptInterface(WtrWebAppInterface(
                    tabId = tab.id,
                    onPlaybackStateChanged = { isPlaying, title, subtitle ->
                        WtrAudioControlBridge.updatePlaybackState(isPlaying, title, subtitle)
                    },
                    onUrlSynced = { syncedUrl, htmlTitle ->
                        val currentActive = viewModel.currentTab.value
                        val triggeringTab = viewModel.allTabs.value.find { it.id == tab.id }
                        val isWebUrl = syncedUrl.startsWith("http://") || syncedUrl.startsWith("https://")
                        
                        // ANTI-HIJACK: Verify the actual WebKit engine URL matches the expected tab metadata structure
                        // before allowing dynamic in-page JS synchronization processes to override the active URL.
                        val activeWV = webViewsMap[tab.id]
                        val wvUrl = activeWV?.url ?: ""
                        val isWebViewMatchingActive = wvUrl.isNotEmpty() && isSameBaseOrTranslatedUrl(wvUrl, currentActive?.url ?: "")

                        if (isWebUrl && currentActive?.id == tab.id && triggeringTab?.id == tab.id && isWebViewMatchingActive && (currentActive.url != syncedUrl || currentActive.title != htmlTitle) && currentActive.url != "chrome://newtab") {
                            com.example.WtrLogManager.log(context, "onUrlSynced matching tab ID=${tab.id} synchronized to: $syncedUrl (title: $htmlTitle)")
                            coroutineScope.launch {
                                viewModel.onPageLoaded(syncedUrl, htmlTitle)
                            }
                        }
                    }
                ), "WtrBridge")

                if (tab.url != "chrome://newtab" && tab.url.isNotEmpty()) {
                    loadUrl(tab.url)
                }
                
                MainActivity.activeWebViewsPool.add(this)
            }
        }
    }

    BackHandler(enabled = true) {
        if (currentSection != BrowserSection.WEB) {
            currentSection = BrowserSection.WEB
        } else if (currentActiveWebView?.canGoBack() == true) {
            currentActiveWebView.goBack()
        } else {
            viewModel.handleBackNavigation {
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    // Safely prune and destroy closed WebViews
    LaunchedEffect(tabsList) {
        val tabIds = tabsList.map { it.id }.toSet()
        val iterator = webViewsMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!tabIds.contains(entry.key)) {
                val wv = entry.value
                try {
                    wv.stopLoading()
                    wv.clearHistory()
                    wv.removeAllViews()
                    MainActivity.activeWebViewsPool.remove(wv)
                    wv.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                iterator.remove()
            }
        }
    }

    // Update WebView agents dynamically when Desktop Mode toggle values shift
    LaunchedEffect(activeTab?.isDesktopMode) {
        val tab = activeTab
        val wv = currentActiveWebView
        if (tab != null && wv != null) {
            val targetUA = if (tab.isDesktopMode) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            }
            if (wv.settings.userAgentString != targetUA) {
                wv.settings.userAgentString = targetUA
                wv.settings.useWideViewPort = tab.isDesktopMode
                wv.settings.loadWithOverviewMode = tab.isDesktopMode
                wv.reload()
            }
        }
    }

    // LISTENER FLOW: Unified single-directional navigation receiver to avoid tab freeze loops.
    // Use rememberUpdatedState to prevent capturing stale closures of currentActiveWebView
    val activeWebViewState = rememberUpdatedState(currentActiveWebView)
    LaunchedEffect(Unit) {
        viewModel.userNavigateTrigger.collect { navUrl ->
            activeWebViewState.value?.loadUrl(navUrl)
        }
    }

    // Driven synchronously via AndroidView's factory and update methods to ensure reliable renders without race conditions.

    // Inject force dark CSS style blocks if preference changes
    LaunchedEffect(forceDarkContent, currentActiveWebView) {
        if (forceDarkContent && currentActiveWebView != null) {
            injectForceDarkCss(currentActiveWebView)
        }
    }

    // Apply custom text zoom dynamically if preference changes
    LaunchedEffect(customTextZoom, currentActiveWebView) {
        val wv = currentActiveWebView
        if (wv != null) {
            wv.settings.textZoom = customTextZoom
        }
    }

    // Configure system Media Sync actions to bind with the currently highlighted tab's WebView
    LaunchedEffect(currentActiveWebView) {
        val activeWV = currentActiveWebView
        if (activeWV != null) {
            WtrAudioControlBridge.onWebViewProgressTrigger = { event, charIndex ->
                activeWV.post {
                    activeWV.evaluateJavascript(
                        "if (typeof window.WtrTtsTriggerEvent === 'function') { window.WtrTtsTriggerEvent('$event', $charIndex); }",
                        null
                    )
                }
            }

            WtrAudioControlBridge.playAction = {
                activeWV.post {
                    activeWV.evaluateJavascript("""
                        (function() {
                            if (window.speechSynthesis && window.speechSynthesis.paused) {
                                window.speechSynthesis.resume();
                            }
                            let playBtn = document.querySelector('button[aria-label*="Play"], button[title*="Play"], button[class*="play"], .play-btn, .btn-play, .play-pause-btn, .audio-player-button, .audio-play, #play-button, .play-icon');
                            if (playBtn) {
                                playBtn.click();
                            } else {
                                let buttons = Array.from(document.querySelectorAll('button, a, span, div.play'));
                                let target = buttons.find(b => {
                                    let t = (b.innerText || b.textContent || '').toLowerCase();
                                    return t.includes('play') || t.includes('listen') || t.includes('tts') || t.includes('开始朗读') || t.includes('播放');
                                });
                                if (target) target.click();
                            }
                        })();
                    """.trimIndent(), null)
                }
            }

            WtrAudioControlBridge.pauseAction = {
                activeWV.post {
                    activeWV.evaluateJavascript("""
                        (function() {
                            if (window.speechSynthesis && window.speechSynthesis.speaking) {
                                window.speechSynthesis.pause();
                            }
                            let pauseBtn = document.querySelector('button[aria-label*="Pause"], button[title*="Pause"], button[class*="pause"], .pause-btn, .btn-pause, .play-pause-btn, .audio-player-button');
                            if (pauseBtn) {
                                pauseBtn.click();
                            } else {
                                let buttons = Array.from(document.querySelectorAll('button, a, span'));
                                let target = buttons.find(b => b.innerText && b.innerText.toLowerCase().includes('pause'));
                                if (target) target.click();
                            }
                        })();
                    """.trimIndent(), null)
                }
            }

            WtrAudioControlBridge.nextAction = {
                activeWV.post {
                    activeWV.evaluateJavascript("""
                        (function() {
                            let nextBtn = document.querySelector('.btn-next, .next, .next-chapter, a[class*="next"], button[class*="next"]');
                            if (nextBtn) {
                                nextBtn.click();
                            } else {
                                let links = Array.from(document.querySelectorAll('a, button'));
                                let target = links.find(l => l.innerText && (l.innerText.toLowerCase().includes('next') || l.innerText.toLowerCase().includes('next chapter')));
                                if (target) {
                                    target.click();
                                } else {
                                    let currentUrl = window.location.href;
                                    let match = currentUrl.match(/chapter-(\d+)/);
                                    if (match) {
                                        let nextNum = parseInt(match[1]) + 1;
                                        window.location.href = currentUrl.replace(/chapter-\d+/, 'chapter-' + nextNum);
                                    }
                                }
                            }
                        })();
                    """.trimIndent(), null)
                }
            }

            WtrAudioControlBridge.prevAction = {
                activeWV.post {
                    activeWV.evaluateJavascript("""
                        (function() {
                            function isDangerousOrToggle(el) {
                                if (!el) return true;
                                let tag = el.tagName.toLowerCase();
                                if (tag === 'input' && (el.type === 'checkbox' || el.type === 'radio')) return true;
                                return false;
                            }

                            let prevBtn = document.querySelector('.btn-prev, .prev, .prev-chapter, .prev_chap, .prev-page, a[class*="prev"], button[class*="prev"], a[id*="prev"], button[id*="prev"]');
                            if (prevBtn) {
                                prevBtn.click();
                                if (prevBtn.tagName.toLowerCase() === 'a') {
                                    let href = prevBtn.getAttribute('href');
                                    if (href && href !== '#' && !href.startsWith('javascript:')) {
                                        if (href.startsWith('/')) href = window.location.origin + href;
                                        window.location.href = href;
                                    }
                                }
                            } else {
                                let links = Array.from(document.querySelectorAll('a, button font, a font'));
                                let target = links.find(l => {
                                    let txt = (l.innerText || l.textContent || '').toLowerCase();
                                    return (txt.includes('prev') || txt.includes('previous') || txt.includes('上一章') || txt.includes('上一页')) && !isDangerousOrToggle(l);
                                });
                                if (target) {
                                    let actualEl = target.tagName.toLowerCase() === 'font' ? target.parentElement : target;
                                    actualEl.click();
                                    if (actualEl.tagName.toLowerCase() === 'a') {
                                        let href = actualEl.getAttribute('href');
                                        if (href && href !== '#' && !href.startsWith('javascript:')) {
                                            if (href.startsWith('/')) href = window.location.origin + href;
                                            window.location.href = href;
                                        }
                                    }
                                } else {
                                    let currentUrl = window.location.href;
                                    let match = currentUrl.match(/chapter-(\d+)/);
                                    if (match) {
                                        let prevNum = parseInt(match[1]) - 1;
                                        if (prevNum > 0) {
                                            window.location.href = currentUrl.replace(/chapter-\d+/, 'chapter-' + prevNum);
                                        }
                                    }
                                }
                            }
                        })();
                    """.trimIndent(), null)
                }
            }
        }
    }

    // Custom TrackPlayer States for regular/different websites collected from service layer bridge
    val isAudiobookModeActiveRaw by WtrAudioControlBridge.isAudiobookModeActive.collectAsStateWithLifecycle()
    val playTrackInputListRaw by WtrAudioControlBridge.playTrackInputList.collectAsStateWithLifecycle()
    val currentTrackIndexRaw by WtrAudioControlBridge.currentTrackIndex.collectAsStateWithLifecycle()
    val currentlySpeakingTextRaw by WtrAudioControlBridge.currentlySpeakingText.collectAsStateWithLifecycle()
    val isPlayerRunningRaw by WtrAudioControlBridge.isPlayerRunning.collectAsStateWithLifecycle()

    val activeTtsTabId by WtrAudioControlBridge.activeTtsTabId.collectAsStateWithLifecycle()
    val isCurrentTabTtsActive = activeTab?.let { tab ->
        tab.id == activeTtsTabId
    } ?: false

    val isAudiobookModeActive = if (isCurrentTabTtsActive) isAudiobookModeActiveRaw else false
    val playTrackInputList = if (isCurrentTabTtsActive) playTrackInputListRaw else emptyList()
    val currentTrackIndex = if (isCurrentTabTtsActive) currentTrackIndexRaw else 0
    val currentlySpeakingText = if (isCurrentTabTtsActive) currentlySpeakingTextRaw else ""
    val isPlayerRunning = if (isCurrentTabTtsActive) isPlayerRunningRaw else false

    var isExtracting by remember { mutableStateOf(false) }

    // Unified play/pause custom track actions
    fun playCustomParagraph(index: Int) {
        val currentList = WtrAudioControlBridge.playTrackInputList.value
        if (currentList.isNotEmpty()) {
            val validIndex = index.coerceIn(0, currentList.size - 1)
            WtrAudioControlBridge.playCustomParagraphAction?.invoke(validIndex)
        }
    }

    fun stopCustomPlayback() {
        WtrAudioControlBridge.setPlayTrackInputList(emptyList())
        WtrAudioControlBridge.setCurrentTrackIndex(0)
        WtrAudioControlBridge.setIsPlayerRunning(false)
        WtrAudioControlBridge.setIsAudiobookModeActive(false)
        WtrAudioControlBridge.onCancelNative?.invoke()
    }

    fun pauseCustomVolume() {
        WtrAudioControlBridge.setIsPlayerRunning(false)
        WtrAudioControlBridge.onPauseNative?.invoke()
    }

    fun resumeCustomVolume() {
        WtrAudioControlBridge.setIsPlayerRunning(true)
        WtrAudioControlBridge.onResumeNative?.invoke()
    }

    // Reduced TTS latency and adaptive HTML readers extraction methods
    // Reduced TTS latency and adaptive HTML readers extraction methods
    val runHtmlTextExtractionAndPlay: () -> Unit = {
        val webView = currentActiveWebView
        if (webView != null && !isExtracting) {
            isExtracting = true
            viewModel.viewModelScope.launch(Dispatchers.Main) {
                try {
                    val currentUrl = webView.url ?: ""
                    val currentUrlLower = currentUrl.lowercase()
                    
                    val matchedSupport = WebsiteSupportRegistry.findSupport(currentUrl)
                    val containerSels = matchedSupport?.containerSelectors ?: listOf(
                        ".content", "#content", ".chapter-content", ".article-content", "article", "main"
                    )
                    val pSel = matchedSupport?.paragraphSelector ?: "p, .wtr-line-segment"
                    val excludeClasses = (matchedSupport?.excludeSelectors ?: emptyList()).ifEmpty { 
                        CommonSelectors.COMMON_EXCLUDE 
                    }

                    val containerSelectorStr = containerSels.joinToString(", ")
                    val excludeClassesStr = excludeClasses.joinToString(", ")

                    val siteJunkList = matchedSupport?.siteSpecificJunkKeywords ?: emptyList()
                    val siteJunkJson = siteJunkList.joinToString(separator = ", ") { "\"${it.replace("\"", "\\\"")}\"" }

                    val requiresBrPrep = matchedSupport?.requiresBrPreparation ?: false
                    
                    var attempts = 0
                    val maxAttempts = 35 // Wait up to 7 seconds for translation to finalize
                    val startTime = System.currentTimeMillis()
                    var list = emptyList<String>()
                    var startIdx = 0
                    var extractionSuccess = false
                    
                    while (attempts < maxAttempts && (System.currentTimeMillis() - startTime) < 7000L) {
                        val resultString = suspendCoroutine<String?> { continuation ->
                            webView.post {
                                val containerSelectorStrEscaped = containerSelectorStr.replace("\"", "\\\"")
                                val excludeClassesStrEscaped = excludeClassesStr.replace("\"", "\\\"")
                                val pSelEscaped = pSel.replace("\"", "\\\"")
                                val requiresBrPrepVal = if (requiresBrPrep) "true" else "false"

                                val jsToRun = """
                                    (function() {
                                        window.__wtrTextExtractor = function() {
                                            try {
                                                let host = window.location.hostname;
                                                const isTwkan = host.includes("twkan") || host.includes("ttkan") || window.location.href.includes("twkan") || window.location.href.includes("ttkan");
                                                if (isTwkan) {
                                                    let contentEl = document.querySelector('#txtcontent0') || document.querySelector('[id^="txtcontent"]') || document.querySelector('.txtcontent');
                                                    if (contentEl) {
                                                        // Ensure the contentEl is prepared into lines
                                                        prepareBrParagraphs(contentEl);
                                                        
                                                        let originalElements = Array.from(contentEl.querySelectorAll('p, span.wtr-line-segment') || []);
                                                        let paragraphs = [];
                                                        let elements = [];
                                                        
                                                        originalElements.forEach(el => {
                                                            let txt = el.innerText || el.textContent;
                                                            txt = txt.trim();
                                                            let t = txt.toLowerCase();
                                                            if (t.includes("twkan") || t.includes("ttkan")) return;
                                                            if (txt.length > 3) {
                                                                paragraphs.push(txt);
                                                                elements.push(el);
                                                            }
                                                        });
                                                        
                                                        // Remove old index markers and assign new sequential ones to active elements
                                                        document.querySelectorAll('[data-wtr-index]').forEach(oldEl => oldEl.removeAttribute('data-wtr-index'));
                                                        elements.forEach((el, index) => {
                                                            el.setAttribute('data-wtr-index', index.toString());
                                                        });
                                                        
                                                        let bestIndex = 0;
                                                        let minDistance = Infinity;
                                                        for (let i = 0; i < elements.length; i++) {
                                                            let rect = elements[i].getBoundingClientRect();
                                                            let dist = Math.abs(rect.top - 100);
                                                            if (dist < minDistance) {
                                                                minDistance = dist;
                                                                bestIndex = i;
                                                            }
                                                        }
                                                        return JSON.stringify({
                                                            paragraphs: paragraphs,
                                                            startIndex: bestIndex
                                                         });
                                                    }
                                                }
                                                
                                                let paragraphs = [];
                                                let elements = [];
                                                
                                                const containerSelector = "$containerSelectorStrEscaped";
                                                const pSelector = "$pSelEscaped";
                                                const excludeClass = "$excludeClassesStrEscaped";
                                                const siteJunk = [$siteJunkJson];
                                                const requiresBrPrep = $requiresBrPrepVal;
                                                
                                                function isJunk(text) {
                                                    let t = text.toLowerCase().trim();
                                                    if (t.length < 5) {
                                                        if (/[\u4e00-\u9fa5]{2,}/.test(text) && t.length >= 2) {
                                                            // Keep short Chinese phrases
                                                        } else {
                                                            return true;
                                                        }
                                                    }
                                                    if (t.includes("ad-blocker") || t.includes("adblocker") || t.includes("ad block") || t.includes("adblock") || t.includes("please disable") || t.includes("stop your ad blocker") || t.includes("ad blocker detected")) return true;
                                                    
                                                    if (t.includes(".com") || t.includes(".org") || t.includes(".net") || t.includes(".me") || t.includes(".xyz") || t.includes("http://") || t.includes("https://")) {
                                                        if (t.length < 100) return true;
                                                    }
                                                    
                                                    const promoKeywords = [
                                                        "join our discord", "join discord", "patreon", "support me", "support the author",
                                                        "rate this", "please review", "please rate", "author's note", "author note",
                                                        "editor's note", "editor note",
                                                        "find any errors", "broken links", "report us", "if you find any",
                                                        "next chapter", "previous chapter", "table of contents", "read online free", "read online for free",
                                                        "unlocked chapters", "bonus chapters", "sign up", "sign in", "subscribe to",
                                                        "follow my page", "download our app", "read this novel", "other novel", "like this book",
                                                        "stop your ad blocker", "ad blocker detected", "本章未完", "点击下一页", "继续阅读", "本章完", "（本章未完）", "(本章完)",
                                                        "最新网址", "手机用户请浏览", "更多精彩内容", "投推荐票", "上一章", "下一章", "目录", "书架", "加入书架", "返回封面"
                                                    ];
                                                    
                                                    if (t.length < 250) {
                                                        for (let keyword of promoKeywords) {
                                                            if (t.includes(keyword)) return true;
                                                        }
                                                        for (let keyword of siteJunk) {
                                                            if (t.includes(keyword.toLowerCase())) return true;
                                                        }
                                                    }
                                                    return false;
                                                }
                                                
                                                function prepareBrParagraphs(contentEl) {
                                                    if (!contentEl) return;
                                                    if (contentEl.querySelector('.wtr-line-segment') || contentEl.querySelector('.wtr-focus-highlight')) return;
                                                    
                                                    const isTwkan = window.location.hostname.includes("twkan") || window.location.hostname.includes("ttkan") || window.location.href.includes("twkan") || window.location.href.includes("ttkan");
                                                    if (isTwkan) {
                                                        contentEl.querySelectorAll("div.txtad, div.txtcenter, div.ad, script, noscript, iframe, ins, .ad-placement, #ad-container").forEach(el => el.remove());
                                                        let paragraphs = [];
                                                        let currentPart = [];
                                                        
                                                        function flushPart() {
                                                            if (currentPart.length > 0) {
                                                                let joined = currentPart.join(" ").trim();
                                                                joined = joined.replace(/^[\u2003\u3000\t ]+/g, "").trim();
                                                                if (joined.length > 5) {
                                                                    paragraphs.push(joined);
                                                                }
                                                                currentPart = [];
                                                            }
                                                        }
                                                        
                                                        let children = Array.from(contentEl.childNodes);
                                                        children.forEach(node => {
                                                            if (node.nodeType === 3) {
                                                                let txt = node.textContent.trim();
                                                                if (txt) currentPart.push(txt);
                                                            } else if (node.nodeType === 1) {
                                                                let tagName = node.tagName.toLowerCase();
                                                                if (tagName === 'br') {
                                                                    flushPart();
                                                                } else if (tagName === 'font' || tagName === 'span' || tagName === 'b' || tagName === 'i' || tagName === 'strong' || tagName === 'em') {
                                                                    let txt = node.innerText || node.textContent;
                                                                    txt = txt.trim();
                                                                    if (txt) currentPart.push(txt);
                                                                } else {
                                                                    flushPart();
                                                                    let txt = node.innerText || node.textContent;
                                                                    txt = txt.trim();
                                                                    if (txt.length > 5) {
                                                                        paragraphs.push(txt);
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        flushPart();
                                                        
                                                        let newHtml = "";
                                                        paragraphs.forEach(pText => {
                                                            newHtml += '<span class="wtr-line-segment">' + pText + '</span><br><br>';
                                                        });
                                                        contentEl.innerHTML = newHtml;
                                                        return;
                                                    }
                                                    
                                                    let pTags = contentEl.querySelectorAll('p');
                                                    if (pTags.length > 5) return; 
                                                    
                                                    let html = contentEl.innerHTML;
                                                    let parts = html.split(/<br\s*\/?>/i);
                                                    let newParts = parts.map(part => {
                                                        let trimmed = part.replace(/<[^>]+>/g, '').trim();
                                                        if (trimmed.length > 5) {
                                                            if (!part.trim().startsWith('<span class="wtr-line-segment"')) {
                                                                return '<span class="wtr-line-segment">' + part + '</span>';
                                                            }
                                                        }
                                                        return part;
                                                    });
                                                    contentEl.innerHTML = newParts.join('<br>');
                                                }

                                                let containers = [];
                                                if (containerSelector) {
                                                    let rawContainers = Array.from(document.querySelectorAll(containerSelector));
                                                    containers = rawContainers.filter(c => !rawContainers.some(other => other !== c && other.contains(c)));
                                                }

                                                if (containers.length > 0) {
                                                    let seenPTags = new Set();
                                                    containers.forEach(contentEl => {
                                                        if (requiresBrPrep) {
                                                            prepareBrParagraphs(contentEl);
                                                        }
                                                        let rawPTags = Array.from(contentEl.querySelectorAll(pSelector));
                                                        let pTags = rawPTags.filter(p => !rawPTags.some(parent => parent !== p && parent.contains(p)));
                                                        
                                                        pTags.forEach(p => {
                                                            if (!p.closest(excludeClass)) {
                                                                if (!seenPTags.has(p)) {
                                                                    seenPTags.add(p);
                                                                    let text = p.innerText.trim();
                                                                    if (text.length > 5 && !isJunk(text)) {
                                                                        paragraphs.push(text);
                                                                        elements.push(p);
                                                                    }
                                                                }
                                                            }
                                                        });
                                                    });
                                                }

                                                if (paragraphs.length === 0) {
                                                    let bestContainer = null;
                                                    let maxPLength = 0;
                                                    document.querySelectorAll('div, article, section').forEach(el => {
                                                        if (!el.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar')) {
                                                            let pList = el.querySelectorAll('p');
                                                            if (pList.length > maxPLength) {
                                                                maxPLength = pList.length;
                                                                bestContainer = el;
                                                            }
                                                        }
                                                    });

                                                    if (bestContainer && maxPLength > 3) {
                                                        bestContainer.querySelectorAll('p').forEach(p => {
                                                            let text = p.innerText.trim();
                                                            if (text.length > 5 && !isJunk(text)) {
                                                                paragraphs.push(text);
                                                                elements.push(p);
                                                            }
                                                        });
                                                    } else {
                                                        let pTags = document.querySelectorAll('p, li, h1, h2, h3, [class*="paragraph"], [id*="paragraph"]');
                                                        pTags.forEach(p => {
                                                            let t = p.innerText.trim();
                                                            let isChinese = /[\u4e00-\u9fa5]/.test(t);
                                                            let isValidLength = isChinese ? t.length > 5 : t.length > 15;
                                                            if (isValidLength && !p.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar, .menu, #menu')) {
                                                                if (!isJunk(t)) {
                                                                    paragraphs.push(t);
                                                                    elements.push(p);
                                                                }
                                                            }
                                                        });
                                                    }
                                                }

                                                document.querySelectorAll('[data-wtr-index]').forEach(el => el.removeAttribute('data-wtr-index'));
                                                elements.forEach((el, index) => {
                                                    el.setAttribute('data-wtr-index', index.toString());
                                                });

                                                let bestIndex = 0;
                                                let minDistance = Infinity;
                                                for (let i = 0; i < elements.length; i++) {
                                                    let rect = elements[i].getBoundingClientRect();
                                                    let dist = Math.abs(rect.top - 100);
                                                    if (dist < minDistance) {
                                                        minDistance = dist;
                                                        bestIndex = i;
                                                    }
                                                }

                                                return JSON.stringify({
                                                    paragraphs: paragraphs,
                                                    startIndex: bestIndex
                                                });
                                            } catch (e) {
                                                return JSON.stringify({
                                                    error: e.toString()
                                                });
                                            }
                                        };
                                        return window.__wtrTextExtractor();
                                    })();
                                """.trimIndent()
                                webView.evaluateJavascript(jsToRun) { res ->
                                    continuation.resume(res)
                                }
                            }
                        }
                        
                        // Verify non-empty structure returned
                        if (resultString != null && resultString != "null" && resultString != "{}" && resultString.isNotEmpty()) {
                            val cleanResult = try {
                                if (resultString.startsWith("\"") && resultString.endsWith("\"")) {
                                    org.json.JSONTokener(resultString).nextValue() as String
                                } else {
                                    resultString
                                }
                            } catch (e: Exception) {
                                resultString
                            }

                            try {
                                val jsonObject = org.json.JSONObject(cleanResult)
                                if (jsonObject.has("error")) {
                                    val err = jsonObject.getString("error")
                                    com.example.WtrLogManager.log(context, "JS Extraction Error on attempt $attempts: $err")
                                }
                                val array = jsonObject.getJSONArray("paragraphs")
                                val bestIndex = jsonObject.optInt("startIndex", 0)
                                
                                val temp = mutableListOf<String>()
                                for (i in 0 until array.length()) {
                                    val text = array.getString(i).trim()
                                    if (text.isNotEmpty()) {
                                        temp.add(text)
                                    }
                                }
                                
                                val isProxyTranslation = currentUrlLower.contains("translate.goog") || currentUrlLower.contains("translate.google")
                                fun isPageMostlyTranslatingOrChinese(paragraphs: List<String>): Boolean {
                                    if (paragraphs.isEmpty()) return false
                                    if (attempts > 15) return false // After 3 seconds, stop forcing translation check if we have text
                                    var chineseCount = 0
                                    var englishCount = 0
                                    val sample = paragraphs.take(10)
                                    for (p in sample) {
                                        for (c in p) {
                                            if (c in '\u4e00'..'\u9fa5') {
                                                chineseCount++
                                            } else if (c.isLetter() && (c.code < 128 || c in '\u00C0'..'\u00FF')) {
                                                englishCount++
                                            }
                                        }
                                    }
                                    // If we have very little text overall, don't claim it's Chinese-heavy yet
                                    if (chineseCount + englishCount < 20) return false
                                    return chineseCount > 5 && englishCount < (chineseCount * 0.4)
                                }
                                val isChinesePresent = isPageMostlyTranslatingOrChinese(temp)
                                
                                if (temp.isEmpty() && attempts < maxAttempts - 1) {
                                    val delayTime = (200L * java.lang.Math.pow(1.15, attempts.toDouble())).toLong().coerceIn(200, 600)
                                    attempts++
                                    delay(delayTime)
                                } else if (isProxyTranslation && isChinesePresent && attempts < maxAttempts - 1) {
                                    val delayTime = (250L * java.lang.Math.pow(1.15, attempts.toDouble())).toLong().coerceIn(250, 600)
                                    attempts++
                                    delay(delayTime) // Wait for translation overlay
                                } else {
                                    list = temp
                                    startIdx = bestIndex
                                    extractionSuccess = true
                                    break
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val delayTime = (150L * java.lang.Math.pow(1.15, attempts.toDouble())).toLong().coerceIn(150, 500)
                                attempts++
                                delay(delayTime)
                            }
                        } else {
                            val delayTime = (150L * java.lang.Math.pow(1.15, attempts.toDouble())).toLong().coerceIn(150, 500)
                            attempts++
                            delay(delayTime) // Fast fallback sleep
                        }
                    }
                    
                    if (extractionSuccess && list.isNotEmpty()) {
                        val tabTitle = activeTab?.title ?: "Web Chapter"
                        val tabUrl = activeTab?.url ?: ""
                        
                        val parsedInfo = extractNovelAndChapter(tabTitle, tabUrl)
                        WtrAudioControlBridge.setNovelAndChapter(parsedInfo.first, parsedInfo.second)
                        WtrAudioControlBridge.bookTitle = parsedInfo.first
                        
                        val webUri = try {
                            android.net.Uri.parse(tabUrl).host ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        val cleanHost = webUri.replace("www.", "").replace("translate.goog", "").trim('.')
                        WtrAudioControlBridge.setActiveWebsite(cleanHost)

                        // Stop previous tab's TTS session if any, then claim this tab ID
                        val curTabId = activeTab?.id
                        if (curTabId != null && curTabId != activeTtsTabId) {
                            WtrAudioControlBridge.onCancelNative?.invoke()
                        }
                        WtrAudioControlBridge.setActiveTtsTabId(curTabId)
                        
                        val previousExtractedUrl = WtrAudioControlBridge.extractedUrl.value
                        WtrAudioControlBridge.setPlayTrackInputList(list)
                        WtrAudioControlBridge.setExtractedUrl(tabUrl)
                        
                        val savedProgressVal = getSavedParagraphIndex(context, tabUrl)
                        val startParagraph = if (savedProgressVal in list.indices) savedProgressVal else startIdx
                        
                        val isSameTab = (curTabId == activeTtsTabId)
                        val isPlayingThisBook = WtrAudioControlBridge.isPlayerRunning.value && isSameTab && isSameBaseOrTranslatedUrl(previousExtractedUrl, tabUrl)
                        
                        if (!isPlayingThisBook) {
                            WtrAudioControlBridge.setCurrentTrackIndex(startParagraph)
                            android.widget.Toast.makeText(context, "Ready! Starting at Paragraph ${startParagraph + 1}", android.widget.Toast.LENGTH_SHORT).show()
                            playCustomParagraph(startParagraph)
                        } else {
                            // Already playing this URL in background, just updated the DOM (maybe Gemini translations arrived)
                            // Do not overwrite index to prevent resetting to 0 when opening app
                            com.example.WtrLogManager.log(context, "App opened while playing. Keeping track index at ${WtrAudioControlBridge.currentTrackIndex.value}")
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Ah, we couldn't segment paragraphs text here. Check settings.", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isExtracting = false
                }
            }
        }
    }

    runHtmlTextExtractionAndPlayRef = runHtmlTextExtractionAndPlay

    val triggerNextChapterNavigation: () -> Unit = {
        val webView = currentActiveWebView
        if (webView != null) {
            webView.post {
                webView.evaluateJavascript(
                    """
                    (function() {
                        let host = window.location.hostname.toLowerCase();
                        
                        function isDangerousOrToggle(el) {
                            if (!el) return true;
                            let tag = el.tagName.toLowerCase();
                            if (tag === 'input' && (el.type === 'checkbox' || el.type === 'radio')) return true;
                            let id = (el.id || '').toLowerCase();
                            let cl = (el.className || '').toLowerCase();
                            let text = (el.innerText || el.textContent || '').toLowerCase();
                            
                            let badKeywords = ['auto-continue', 'autocontinue', 'toggle', 'switch', 'opt-in', 'checkbox', 'unlock', 'purchase', 'fastpass', 'coin', 'comment', 'review', 'opinion', 'share', 'like', 'vote'];
                            for (let kw of badKeywords) {
                                if (id.includes(kw) || cl.includes(kw) || text.includes(kw)) {
                                    return true;
                                }
                            }
                            return false;
                        }
                        
                        if (host.includes("webnovel.com")) {
                            let bookMatch = window.location.href.match(/\/book\/(\d+)\/(\d+)/);
                            if (bookMatch) {
                                let bookId = bookMatch[1];
                                let currentChapId = bookMatch[2];
                                let anchors = Array.from(document.querySelectorAll('a'));
                                
                                let candidateLinks = anchors.filter(a => {
                                    let href = a.getAttribute('href') || '';
                                    return href.includes('/book/' + bookId + '/') && 
                                           !href.includes(currentChapId) && 
                                           !isDangerousOrToggle(a);
                                });
                                
                                let nextLink = candidateLinks.find(a => {
                                    let t = (a.innerText || '').toLowerCase();
                                    let cl = (a.className || '').toLowerCase();
                                    return t.includes('next') || cl.includes('next') || cl.includes('chap');
                                });
                                
                                if (nextLink) {
                                    nextLink.click();
                                    return true;
                                }
                            }
                            
                            // Fallback for Webnovel: try clicking standard bottom elements but excluding toggles
                            let nextElements = Array.from(document.querySelectorAll('.btn-next, .next, .next-chapter, .next_chap, a[class*="next"], button[class*="next"]'));
                            let safeNext = nextElements.find(el => !isDangerousOrToggle(el));
                            if (safeNext) {
                                safeNext.click();
                                return true;
                            }
                            
                            // Scroll down as dynamic backup trigger
                            window.scrollTo(0, document.body.scrollHeight);
                            return true;
                        }
                        
                        if (host.includes("timotxt") || host.includes("novel543") || host.includes("twkan") || host.includes("ttkan")) {
                            let nextElements = Array.from(document.querySelectorAll('a, button'));
                            
                            function getValidTarget(keywords) {
                                return nextElements.find(el => {
                                    let t = (el.innerText || el.textContent || '').trim();
                                    return keywords.some(k => t === k || t.includes(k)) && !isDangerousOrToggle(el);
                                });
                            }
                            
                            let target = getValidTarget(['下一章', 'Next Chapter', '下一頁', '下一页', 'Next Page']);
                            
                            if (target) {
                                target.click();
                                return true;
                            }
                        }
                        
                        // General case
                        let nextElements = Array.from(document.querySelectorAll('.btn-next, .next, .next-chapter, .next_chap, .next-page, a[class*="next"], button[class*="next"], a[id*="next"], button[id*="next"]'));
                        let safeNext = nextElements.find(el => !isDangerousOrToggle(el));
                        if (safeNext) {
                            safeNext.click();
                            return true;
                        } else {
                            let linksAndButtons = Array.from(document.querySelectorAll('a, button font, a font'));
                            let target = linksAndButtons.find(l => {
                                let txt = (l.innerText || l.textContent || '').toLowerCase();
                                return (txt.includes('next') || txt.includes('next chapter') || txt.includes('下一章') || txt.includes('下一页')) && !isDangerousOrToggle(l);
                            });
                            if (target) {
                                let actualEl = target.tagName.toLowerCase() === 'font' ? target.parentElement : target;
                                actualEl.click();
                                return true;
                            } else {
                                let currentUrl = window.location.href;
                                let match = currentUrl.match(/chapter-(\d+)/);
                                if (match) {
                                    let nextNum = parseInt(match[1]) + 1;
                                    window.location.href = currentUrl.replace(/chapter-\d+/, 'chapter-' + nextNum);
                                    return true;
                                }
                            }
                        }
                        return false;
                    })();
                    """.trimIndent()
                ) { result ->
                    val success = result == "true"
                    if (!success) {
                        android.widget.Toast.makeText(context, "Looking for next chapter...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Navigating to next chapter...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Navigation and resets of active state segments on chapter changes
    var previousUrl by remember { mutableStateOf("") }
    LaunchedEffect(activeTab?.url, activeTab?.id) {
        val currentUrl = activeTab?.url ?: ""
        val currentTabId = activeTab?.id
        
        val isSameTab = currentTabId == previousTabId
        val host1 = try {
            val h = android.net.Uri.parse(previousUrl).host?.replace("www.", "") ?: ""
            if (h.contains("translate.goog")) {
                h.replace(".translate.goog", "").replace("translate.goog", "")
                    .replace("--", "__HYPHEN__")
                    .replace("-", ".")
                    .replace("__HYPHEN__", "-")
            } else {
                h
            }
        } catch (e: Exception) { "" }
        val host2 = try {
            val h = android.net.Uri.parse(currentUrl).host?.replace("www.", "") ?: ""
            if (h.contains("translate.goog")) {
                h.replace(".translate.goog", "").replace("translate.goog", "")
                    .replace("--", "__HYPHEN__")
                    .replace("-", ".")
                    .replace("__HYPHEN__", "-")
            } else {
                h
            }
        } catch (e: Exception) { "" }
        val isSameHost = host1.isNotEmpty() && host2.isNotEmpty() && host1 == host2
        val urlChanged = isSameTab && currentUrl.isNotEmpty() && previousUrl.isNotEmpty() && !isSameBaseOrTranslatedUrl(previousUrl, currentUrl)
        
        if (urlChanged) {
            if (isAudiobookModeActive) {
                WtrAudioControlBridge.setPlayTrackInputList(emptyList())
                WtrAudioControlBridge.setCurrentTrackIndex(0)
                WtrAudioControlBridge.setIsPlayerRunning(false)
                WtrAudioControlBridge.onCancelNative?.invoke()
            } else {
                WtrAudioControlBridge.setPlayTrackInputList(emptyList())
                WtrAudioControlBridge.setCurrentTrackIndex(0)
                if (isPlayerRunning) {
                    WtrAudioControlBridge.setIsPlayerRunning(false)
                    WtrAudioControlBridge.onCancelNative?.invoke()
                }
            }
        }
        previousUrl = currentUrl
        previousTabId = currentTabId
    }

    // Removed LaunchedEffect(currentTrackIndex) as WtrBrowserService now saves it in the background reliably.
    LaunchedEffect(currentTrackIndexRaw, extractedUrlOfActiveTracks) {
        if (extractedUrlOfActiveTracks.isNotEmpty()) {
            saveParagraphIndex(context, extractedUrlOfActiveTracks, currentTrackIndexRaw)
        }
    }

    val currentRunExtractionAndPlay by rememberUpdatedState(runHtmlTextExtractionAndPlay)
    val currentTriggerNextChapter by rememberUpdatedState(triggerNextChapterNavigation)

    // Removed LaunchedEffect(isWebLoading, isAudiobookModeActive, activeTab?.url) and Gemini translation effects 
    // because they fail in the background when the Compose View is suspended.
    // They are now manually triggered via pageLoadBackgroundLogic inside onPageFinished!
    
    // Pre-extract paragraphs of the current page for fallback background playback on standard webpage TTS speechSynthesis
    // Pre-extract paragraphs of the current page for fallback background playback on standard webpage TTS speechSynthesis
    LaunchedEffect(isWebLoading, activeTab?.url) {
        if (!isWebLoading) {
            val urlVal = activeTab?.url ?: ""
            if (urlVal.isNotEmpty() && urlVal != "chrome://newtab") {
                delay(1200) // Settle DOM delay
                val webView = currentActiveWebView
                if (webView != null) {
                    val support = com.example.sites.WebsiteSupportRegistry.findSupport(urlVal)
                    val containerSelectorStr = support?.containerSelectors?.joinToString(", ") ?: ""
                    val pSel = support?.paragraphSelector ?: "p, .wtr-line-segment"
                    val excludeClasses = (support?.excludeSelectors ?: emptyList()).ifEmpty { 
                        com.example.sites.commons.CommonSelectors.COMMON_EXCLUDE 
                    }
                    val excludeClassesStr = excludeClasses.joinToString(", ")
                    val requiresBrPrepVal = if (support?.requiresBrPreparation == true) "true" else "false"
                    val siteJunkList = support?.siteSpecificJunkKeywords ?: emptyList()
                    val siteJunkJson = siteJunkList.joinToString(separator = ", ") { "\"${it.replace("\"", "\\\"")}\"" }

                    val containerSelectorStrEscaped = containerSelectorStr.replace("\"", "\\\"")
                    val pSelEscaped = pSel.replace("\"", "\\\"")
                    val excludeClassesStrEscaped = excludeClassesStr.replace("\"", "\\\"")

                    webView.evaluateJavascript(
                        """
                        (function() {
                            let paragraphs = [];
                            let host = window.location.hostname;
                            
                            const containerSelector = "$containerSelectorStrEscaped";
                            const pSelector = "$pSelEscaped";
                            const excludeClass = "$excludeClassesStrEscaped";
                            const siteJunk = [$siteJunkJson];
                            const requiresBrPrep = $requiresBrPrepVal;
                            
                            function isJunk(text) {
                                let t = text.toLowerCase().trim();
                                if (t.length < 5) {
                                    if (/[\u4e00-\u9fa5]{2,}/.test(text) && t.length >= 2) {
                                        // Keep short Chinese phrases
                                    } else {
                                        return true;
                                    }
                                }
                                if (t.includes("ad-blocker") || t.includes("adblocker") || t.includes("ad block") || t.includes("adblock") || t.includes("please disable") || t.includes("stop your ad blocker") || t.includes("ad blocker detected")) return true;
                                
                                if (t.includes(".com") || t.includes(".org") || t.includes(".net") || t.includes(".me") || t.includes(".xyz") || t.includes("http://") || t.includes("https://")) {
                                    if (t.length < 100) return true;
                                }
                                
                                const promoKeywords = [
                                    "join our discord", "join discord", "patreon", "support me", "support the author",
                                    "rate this", "please review", "please rate", "author's note", "author note",
                                    "editor's note", "editor note",
                                    "find any errors", "broken links", "report us", "if you find any",
                                    "next chapter", "previous chapter", "table of contents", "read online free", "read online for free",
                                    "unlocked chapters", "bonus chapters", "sign up", "sign in", "subscribe to",
                                    "follow my page", "download our app", "read this novel", "other novel", "like this book",
                                    "stop your ad blocker", "ad blocker detected", "本章未完", "点击下一页", "继续阅读", "本章完", "（本章未完）", "(本章完)",
                                    "最新网址", "手机用户请浏览", "更多精彩内容", "投推荐票", "上一章", "下一章", "目录", "书架", "加入书架", "返回封面"
                                ];
                                
                                if (t.length < 250) {
                                    for (let keyword of promoKeywords) {
                                        if (t.includes(keyword)) return true;
                                    }
                                    for (let keyword of siteJunk) {
                                        if (t.includes(keyword.toLowerCase())) return true;
                                    }
                                }
                                return false;
                            }
                            
                            function prepareBrParagraphs(contentEl) {
                                if (!contentEl) return;
                                if (contentEl.querySelector('.wtr-line-segment') || contentEl.querySelector('.wtr-focus-highlight')) return;
                                
                                const isTwkan = window.location.hostname.includes("twkan") || window.location.hostname.includes("ttkan") || window.location.href.includes("twkan") || window.location.href.includes("ttkan");
                                if (isTwkan) {
                                    contentEl.querySelectorAll("div.txtad, div.txtcenter, div.ad, script, noscript, iframe, ins, .ad-placement, #ad-container").forEach(el => el.remove());
                                    let paragraphs = [];
                                    let currentPart = [];
                                    
                                    function flushPart() {
                                        if (currentPart.length > 0) {
                                            let joined = currentPart.join(" ").trim();
                                            joined = joined.replace(/^[\u2003\u3000\t ]+/g, "").trim();
                                            if (joined.length > 5) {
                                                paragraphs.push(joined);
                                            }
                                            currentPart = [];
                                        }
                                    }
                                    
                                    let children = Array.from(contentEl.childNodes);
                                    children.forEach(node => {
                                        if (node.nodeType === 3) {
                                            let txt = node.textContent.trim();
                                            if (txt) currentPart.push(txt);
                                        } else if (node.nodeType === 1) {
                                            let tagName = node.tagName.toLowerCase();
                                            if (tagName === 'br') {
                                                flushPart();
                                            } else if (tagName === 'font' || tagName === 'span' || tagName === 'b' || tagName === 'i' || tagName === 'strong' || tagName === 'em') {
                                                let txt = node.innerText || node.textContent;
                                                txt = txt.trim();
                                                if (txt) currentPart.push(txt);
                                            } else {
                                                flushPart();
                                                let txt = node.innerText || node.textContent;
                                                txt = txt.trim();
                                                if (txt.length > 5) {
                                                    paragraphs.push(txt);
                                                }
                                            }
                                        }
                                    });
                                    flushPart();
                                    
                                    let newHtml = "";
                                    paragraphs.forEach(pText => {
                                        newHtml += '<span class="wtr-line-segment">' + pText + '</span><br><br>';
                                    });
                                    contentEl.innerHTML = newHtml;
                                    return;
                                }
                                
                                let pTags = contentEl.querySelectorAll('p');
                                if (pTags.length > 5) return; 
                                
                                let html = contentEl.innerHTML;
                                if (html) {
                                    let parts = html.split(/<br\s*\/?>/i);
                                    let newParts = parts.map(part => {
                                        let trimmed = part.replace(/<[^>]+>/g, '').trim();
                                        if (trimmed.length > 5) {
                                            if (!part.trim().startsWith('<span class="wtr-line-segment"')) {
                                                return '<span class="wtr-line-segment">' + part + '</span>';
                                            }
                                        }
                                        return part;
                                    });
                                    contentEl.innerHTML = newParts.join('<br>');
                                }
                            }
                            
                            let containers = [];
                            if (containerSelector) {
                                let rawContainers = Array.from(document.querySelectorAll(containerSelector));
                                containers = rawContainers.filter(c => !rawContainers.some(other => other !== c && other.contains(c)));
                            }
                            
                            if (containers.length > 0) {
                                let seenPTags = new Set();
                                containers.forEach(contentEl => {
                                    if (requiresBrPrep) {
                                        prepareBrParagraphs(contentEl);
                                    }
                                    let rawPTags = Array.from(contentEl.querySelectorAll(pSelector));
                                    let pTags = rawPTags.filter(p => !rawPTags.some(parent => parent !== p && parent.contains(p)));
                                    
                                    pTags.forEach(p => {
                                        if (!p.closest(excludeClass)) {
                                            if (!seenPTags.has(p)) {
                                                seenPTags.add(p);
                                                let text = p.innerText.trim();
                                                if (text.length > 5 && !isJunk(text)) {
                                                    paragraphs.push(text);
                                                }
                                            }
                                        }
                                    });
                                });
                            }
                            
                            if (paragraphs.length === 0) {
                                let bestContainer = null;
                                let maxPLength = 0;
                                document.querySelectorAll('div, article, section').forEach(el => {
                                    if (!el.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar')) {
                                        let pList = el.querySelectorAll('p');
                                        if (pList.length > maxPLength) {
                                            maxPLength = pList.length;
                                            bestContainer = el;
                                        }
                                    }
                                });
                                
                                if (bestContainer && maxPLength > 3) {
                                    bestContainer.querySelectorAll('p').forEach(p => {
                                        let text = p.innerText.trim();
                                        if (text.length > 5 && !isJunk(text)) paragraphs.push(text);
                                    });
                                } else {
                                    let pTags = document.querySelectorAll('p, li, h1, h2, h3, [class*="paragraph"], [id*="paragraph"], .wtr-line-segment');
                                    pTags.forEach(p => {
                                        let t = p.innerText.trim();
                                        let isChinese = /[\u4e00-\u9fa5]/.test(t);
                                        let isValidLength = isChinese ? t.length > 5 : t.length > 15;
                                        if (isValidLength && !p.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar, .menu, #menu')) {
                                            if (!isJunk(t)) paragraphs.push(t);
                                        }
                                    });
                                }
                            }
                            return JSON.stringify(paragraphs);
                        })();
                        """.trimIndent()
                    ) { jsonResult ->
                        if (jsonResult != null && jsonResult != "null" && jsonResult != "[]" && jsonResult.isNotEmpty()) {
                            try {
                                val cleanResult = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                    org.json.JSONTokener(jsonResult).nextValue() as String
                                } else {
                                    jsonResult
                                }
                                val array = org.json.JSONArray(cleanResult)
                                val list = mutableListOf<String>()
                                for (i in 0 until array.length()) {
                                    val text = array.getString(i).trim()
                                    if (text.isNotEmpty()) {
                                        list.add(text)
                                    }
                                }
                                WtrAudioControlBridge.setWebSpeakNativeFallbackList(list)
                                WtrAudioControlBridge.setWebSpeakNativeFallbackIndex(-1)

                                com.example.WtrLogManager.log(context, "Bypassed Web JS background lag: Pre-cached ${list.size} paragraphs for background fallback TTS.")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    // Register decoupled background-safe callbacks
<<<<<<< HEAD
    LaunchedEffect(Unit) {
        WtrAudioControlBridge.nextChapterAction = {
            currentTriggerNextChapter()
=======
    LaunchedEffect(antiCaptchaDelay) {
        WtrAudioControlBridge.nextChapterAction = {
            val currentUrl = viewModel.currentTab.value?.url ?: ""
            val isTranslated = currentUrl.contains("translate.goog") || currentUrl.contains("translate.google")
            if (isTranslated && antiCaptchaDelay) {
                com.example.WtrLogManager.log(context, "Anti-CAPTCHA Delay: Pausing 4.5s before loading next translated chapter.")
                android.widget.Toast.makeText(context, "Auto-Next: Pausing 4.5s to bypass Google CAPTCHA filters...", android.widget.Toast.LENGTH_SHORT).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    currentTriggerNextChapter()
                }, 4500)
            } else {
                currentTriggerNextChapter()
            }
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
        }
    }

    // Scroll active reading paragraph into view and highlight it (Auto-Focus mode)
    LaunchedEffect(currentTrackIndex, isPlayerRunning, autoFocusParagraphs, currentActiveWebView) {
        val webView = currentActiveWebView ?: return@LaunchedEffect
        val urlVal = activeTab?.url ?: ""
        if (isPlayerRunning && autoFocusParagraphs && urlVal.isNotEmpty()) {
            val support = com.example.sites.WebsiteSupportRegistry.findSupport(urlVal)
            val containerSelectorStr = support?.containerSelectors?.joinToString(", ") ?: ""
            val pSel = support?.paragraphSelector ?: "p, .wtr-line-segment"
            val excludeClasses = (support?.excludeSelectors ?: emptyList()).ifEmpty { 
                com.example.sites.commons.CommonSelectors.COMMON_EXCLUDE 
            }
            val excludeClassesStr = excludeClasses.joinToString(", ")
            val requiresBrPrepVal = if (support?.requiresBrPreparation == true) "true" else "false"
            val siteJunkList = support?.siteSpecificJunkKeywords ?: emptyList()
            val siteJunkJson = siteJunkList.joinToString(separator = ", ") { "\"${it.replace("\"", "\\\"")}\"" }

            val containerSelectorStrEscaped = containerSelectorStr.replace("\"", "\\\"")
            val pSelEscaped = pSel.replace("\"", "\\\"")
            val excludeClassesStrEscaped = excludeClassesStr.replace("\"", "\\\"")

            val jsCode = """
                (function() {
                    const targetIndex = $currentTrackIndex;
                    
                    document.querySelectorAll('.wtr-focus-highlight').forEach(el => {
                        el.classList.remove('wtr-focus-highlight');
                        el.style.backgroundColor = '';
                        el.style.borderRadius = '';
                        el.style.padding = '';
                        el.style.transition = '';
                    });
                    
                    let fastTarget = document.querySelector('[data-wtr-index="' + targetIndex + '"]');
                    if (fastTarget) {
                        fastTarget.classList.add('wtr-focus-highlight');
                        fastTarget.style.transition = 'background-color 0.4s ease-in-out';
                        fastTarget.style.backgroundColor = 'rgba(255, 235, 59, 0.25)';
                        fastTarget.style.borderRadius = '6px';
                        fastTarget.style.padding = '4px 8px';
                        fastTarget.scrollIntoView({
                            behavior: 'smooth',
                            block: 'center'
                        });
                        return;
                    }

                    const host = window.location.hostname;
                    
                    const containerSelector = "$containerSelectorStrEscaped";
                    const pSelector = "$pSelEscaped";
                    const excludeClass = "$excludeClassesStrEscaped";
                    const siteJunk = [$siteJunkJson];
                    const requiresBrPrep = $requiresBrPrepVal;
                    
                    function isJunk(text) {
                        let t = text.toLowerCase().trim();
                        if (t.length < 5) {
                            if (/[\u4e00-\u9fa5]{2,}/.test(text) && t.length >= 2) {
                                // Keep short Chinese phrases
                            } else {
                                return true;
                            }
                        }
                        if (t.includes("ad-blocker") || t.includes("adblocker") || t.includes("ad block") || t.includes("adblock") || t.includes("please disable") || t.includes("stop your ad blocker") || t.includes("ad blocker detected")) return true;
                        
                        if (t.includes(".com") || t.includes(".org") || t.includes(".net") || t.includes(".me") || t.includes(".xyz") || t.includes("http://") || t.includes("https://")) {
                            if (t.length < 100) return true;
                        }
                        
                        const promoKeywords = [
                            "join our discord", "join discord", "patreon", "support me", "support the author",
                            "rate this", "please review", "please rate", "author's note", "author note",
                            "editor's note", "editor note",
                            "find any errors", "broken links", "report us", "if you find any",
                            "next chapter", "previous chapter", "table of contents", "read online free", "read online for free",
                            "unlocked chapters", "bonus chapters", "sign up", "sign in", "subscribe to",
                            "follow my page", "download our app", "read this novel", "other novel", "like this book",
                            "stop your ad blocker", "ad blocker detected", "本章未完", "点击下一页", "继续阅读", "本章完", "（本章未完）", "(本章完)",
                            "最新网址", "手机用户请浏览", "更多精彩内容", "投推荐票", "上一章", "下一章", "目录", "书架", "加入书架", "返回封面"
                        ];
                        
                        if (t.length < 250) {
                            for (let keyword of promoKeywords) {
                                if (t.includes(keyword)) return true;
                            }
                            for (let keyword of siteJunk) {
                                if (t.includes(keyword.toLowerCase())) return true;
                            }
                        }
                        return false;
                    }

                    function prepareBrParagraphs(contentEl) {
                        if (!contentEl) return;
                        if (contentEl.querySelector('.wtr-line-segment') || contentEl.querySelector('.wtr-focus-highlight')) return;
                        
                        const isTwkan = window.location.hostname.includes("twkan") || window.location.hostname.includes("ttkan") || window.location.href.includes("twkan") || window.location.href.includes("ttkan");
                        if (isTwkan) {
                            contentEl.querySelectorAll("div.txtad, div.txtcenter, div.ad, script, noscript, iframe, ins, .ad-placement, #ad-container").forEach(el => el.remove());
                            let paragraphs = [];
                            let currentPart = [];
                            
                            function flushPart() {
                                if (currentPart.length > 0) {
                                    let joined = currentPart.join(" ").trim();
                                    joined = joined.replace(/^[\u2003\u3000\t ]+/g, "").trim();
                                    if (joined.length > 5) {
                                        paragraphs.push(joined);
                                    }
                                    currentPart = [];
                                }
                            }
                            
                            let children = Array.from(contentEl.childNodes);
                            children.forEach(node => {
                                if (node.nodeType === 3) {
                                    let txt = node.textContent.trim();
                                    if (txt) currentPart.push(txt);
                                } else if (node.nodeType === 1) {
                                    let tagName = node.tagName.toLowerCase();
                                    if (tagName === 'br') {
                                        flushPart();
                                    } else if (tagName === 'font' || tagName === 'span' || tagName === 'b' || tagName === 'i' || tagName === 'strong' || tagName === 'em') {
                                        let txt = node.innerText || node.textContent;
                                        txt = txt.trim();
                                        if (txt) currentPart.push(txt);
                                    } else {
                                        flushPart();
                                        let txt = node.innerText || node.textContent;
                                        txt = txt.trim();
                                        if (txt.length > 5) {
                                            paragraphs.push(txt);
                                        }
                                    }
                                }
                            });
                            flushPart();
                            
                            let newHtml = "";
                            paragraphs.forEach(pText => {
                                newHtml += '<span class="wtr-line-segment">' + pText + '</span><br><br>';
                            });
                            contentEl.innerHTML = newHtml;
                            return;
                        }
                        
                        let pTags = contentEl.querySelectorAll('p');
                        if (pTags.length > 5) return; 
                        
                        let html = contentEl.innerHTML;
                        if (html) {
                            let parts = html.split(/<br\s*\/?>/i);
                            let newParts = parts.map(part => {
                                let trimmed = part.replace(/<[^>]+>/g, '').trim();
                                if (trimmed.length > 5) {
                                    if (!part.trim().startsWith('<span class="wtr-line-segment"')) {
                                        return '<span class="wtr-line-segment">' + part + '</span>';
                                    }
                                }
                                return part;
                            });
                            contentEl.innerHTML = newParts.join('<br>');
                        }
                    }

                    let elements = [];
                    let containers = [];
                    if (containerSelector) {
                        let rawContainers = Array.from(document.querySelectorAll(containerSelector));
                        containers = rawContainers.filter(c => !rawContainers.some(other => other !== c && other.contains(c)));
                    }
                    
                    if (containers.length > 0) {
                        let seenPTags = new Set();
                        containers.forEach(contentEl => {
                            if (requiresBrPrep) {
                                prepareBrParagraphs(contentEl);
                            }
                            let rawPTags = Array.from(contentEl.querySelectorAll(pSelector));
                            let pTags = rawPTags.filter(p => !rawPTags.some(parent => parent !== p && parent.contains(p)));
                            
                            pTags.forEach(p => {
                                if (!p.closest(excludeClass)) {
                                    if (!seenPTags.has(p)) {
                                        seenPTags.add(p);
                                        let text = p.innerText.trim();
                                        if (text.length > 5 && !isJunk(text)) {
                                            elements.push(p);
                                        }
                                    }
                                }
                            });
                        });
                    }
                    
                    if (elements.length === 0) {
                        let bestContainer = null;
                        let maxPLength = 0;
                        document.querySelectorAll('div, article, section').forEach(el => {
                            if (!el.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar')) {
                                let pList = el.querySelectorAll('p');
                                if (pList.length > maxPLength) {
                                    maxPLength = pList.length;
                                    bestContainer = el;
                                }
                            }
                        });

                        if (bestContainer && maxPLength > 3) {
                            bestContainer.querySelectorAll('p').forEach(p => {
                                let text = p.innerText.trim();
                                if (text.length > 5 && !isJunk(text)) {
                                    elements.push(p);
                                }
                            });
                        } else {
                            let elems = document.querySelectorAll('p, li, h1, h2, h3, [class*="paragraph"], [id*="paragraph"], .wtr-line-segment');
                            elems.forEach(el => {
                                let t = el.innerText.trim();
                                let isChinese = /[\u4e00-\u9fa5]/.test(t);
                                let isValidLength = isChinese ? t.length > 5 : t.length > 15;
                                if (isValidLength && !el.closest('nav, footer, h1, fieldset, form, header, script, style, #comments, .comments, .nav, .footer, .sidebar, #sidebar, .menu, #menu')) {
                                    if (!isJunk(t)) {
                                        elements.push(el);
                                    }
                                }
                            });
                        }
                    }

                    document.querySelectorAll('.wtr-focus-highlight').forEach(el => {
                        el.classList.remove('wtr-focus-highlight');
                        el.style.backgroundColor = '';
                        el.style.borderRadius = '';
                        el.style.padding = '';
                        el.style.transition = '';
                    });

                    if (targetIndex >= 0 && targetIndex < elements.length) {
                        let targetEl = elements[targetIndex];
                        if (targetEl) {
                            targetEl.classList.add('wtr-focus-highlight');
                            targetEl.style.transition = 'background-color 0.4s ease-in-out';
                            targetEl.style.backgroundColor = 'rgba(255, 235, 59, 0.25)';
                            targetEl.style.borderRadius = '6px';
                            targetEl.style.padding = '4px 8px';
                            
                            targetEl.scrollIntoView({
                                behavior: 'smooth',
                                block: 'center'
                            });
                        }
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(jsCode, null)
        } else {
            val clearJs = """
                (function() {
                    document.querySelectorAll('.wtr-focus-highlight').forEach(el => {
                        el.classList.remove('wtr-focus-highlight');
                        el.style.backgroundColor = '';
                        el.style.borderRadius = '';
                        el.style.padding = '';
                        el.style.transition = '';
                    });
                })();
            """.trimIndent()
            webView.evaluateJavascript(clearJs, null)
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // URL Search pill styled exactly like Google Chrome with zero text clipping
                        val isHttps = urlText.startsWith("https://") || urlInput.startsWith("https://")
                        BasicTextField(
                            value = urlText,
                            onValueChange = {
                                urlText = it
                                viewModel.setUrlInput(it)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Go,
                                keyboardType = KeyboardType.Uri
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    viewModel.loadUrl(urlText)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    currentSection = BrowserSection.WEB
                                }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .weight(1f)
                                .height(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                  )
                                .padding(horizontal = 12.dp),
                            decorationBox = { innerTextField ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Icon(
                                        imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Search,
                                        contentDescription = "Security Status",
                                        tint = if (isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (urlText.isEmpty()) {
                                            Text(
                                                text = "Search or type URL",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 14.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (urlText.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                urlText = ""
                                                viewModel.setUrlInput("")
                                            },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear Text",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        // Google Chrome-styled interactive tab switcher badge button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(24.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clip(RoundedCornerShape(5.dp))
                                .clickable {
                                    currentSection = BrowserSection.TABS
                                }
                                .testTag("chrome_tab_badge_button")
                        ) {
                            Text(
                                text = tabsList.size.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Menu",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                // Top row of custom action buttons (Exactly like Google Chrome premium interface)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    // Back arrow button
                                    IconButton(
                                        enabled = currentActiveWebView?.canGoBack() == true,
                                        onClick = {
                                            currentActiveWebView?.goBack()
                                            showMenu = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (currentActiveWebView?.canGoBack() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }

                                    // Forward arrow button
                                    IconButton(
                                        enabled = currentActiveWebView?.canGoForward() == true,
                                        onClick = {
                                            currentActiveWebView?.goForward()
                                            showMenu = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                            contentDescription = "Forward",
                                            tint = if (currentActiveWebView?.canGoForward() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }

                                    // Star Bookmark button
                                    IconButton(
                                        onClick = {
                                            activeTab?.let { tab ->
                                                val wv = currentActiveWebView
                                                if (wv != null && !isBookmarked) {
                                                    wv.evaluateJavascript(
                                                        """
                                                        (function() {
                                                            let cover = '';
                                                            let meta = document.querySelector('meta[property="og:image"]');
                                                            if (meta && meta.content) cover = meta.content;
                                                            
                                                            if (!cover) {
                                                                let twitter = document.querySelector('meta[name="twitter:image"]');
                                                                if (twitter && twitter.content) cover = twitter.content;
                                                            }

                                                            if (!cover) {
                                                                let linkSrc = document.querySelector('link[rel="image_src"]');
                                                                if (linkSrc && linkSrc.href) cover = linkSrc.href;
                                                            }
                                                            
                                                            if (!cover) {
                                                                let img = document.querySelector('.book-cover img, .cover img, .novel-cover img, img.cover, .cover-box img, .pic img, img.book-img');
                                                                if (img && img.src) cover = img.src;
                                                            }

                                                            if (!cover) {
                                                                let allImgs = Array.from(document.querySelectorAll('img'));
                                                                for (let im of allImgs) {
                                                                    if (im.src && (im.src.includes('cover') || im.className.includes('cover') || im.id.includes('cover'))) {
                                                                        cover = im.src;
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                            
                                                            let dynTitle = document.title || "";
                                                            let h1 = document.querySelector('h1.title, h1.book-title, .novel-title');
                                                            if (h1 && h1.innerText && h1.innerText.length > 0) {
                                                                dynTitle = h1.innerText.trim();
                                                            }

                                                            return JSON.stringify({
                                                                cover: cover,
                                                                title: dynTitle
                                                            });
                                                        })()
                                                        """.trimIndent()
                                                    ) { res: String? ->
                                                        var coverUrl: String? = null
                                                        var currentTitle = tab.title
                                                        
                                                        try {
                                                            val jsonStr = if (res?.startsWith("\"") == true && res.endsWith("\"")) res.substring(1, res.length - 1).replace("\\\"", "\"").replace("\\\\", "\\") else res
                                                            if (!jsonStr.isNullOrEmpty() && jsonStr != "null") {
                                                                val json = org.json.JSONObject(jsonStr)
                                                                coverUrl = json.optString("cover", "")
                                                                val extractedTitle = json.optString("title", "")
                                                                if (extractedTitle.isNotEmpty()) {
                                                                    currentTitle = extractedTitle
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                        
                                                        val finalUrl = if (coverUrl.isNullOrEmpty() || coverUrl == "null") null else coverUrl
                                                        viewModel.toggleBookmark(tab.url, currentTitle, finalUrl)
                                                    }
                                                } else {
                                                    viewModel.toggleBookmark(tab.url, tab.title)
                                                }
                                            }
                                            showMenu = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isBookmarked) "Bookmarked" else "Add Bookmark",
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Reload button
                                    IconButton(
                                        onClick = {
                                            if (currentSection != BrowserSection.WEB) {
                                                currentSection = BrowserSection.WEB
                                            } else {
                                                currentActiveWebView?.reload()
                                            }
                                            showMenu = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Home page button
                                    IconButton(
                                        onClick = {
                                            viewModel.loadUrl("chrome://newtab")
                                            currentSection = BrowserSection.WEB
                                            showMenu = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Home",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                                DropdownMenuItem(
                                    text = { Text("Open New Tab") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        viewModel.addNewTab()
                                        currentSection = BrowserSection.WEB
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All Tabs (${tabsList.size})") },
                                    leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null) },
                                    onClick = {
                                        currentSection = BrowserSection.TABS
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    onClick = {
                                        currentSection = BrowserSection.BOOKMARKS
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Navigation History") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    onClick = {
                                        currentSection = BrowserSection.HISTORY
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Desktop site") },
                                    leadingIcon = { Icon(Icons.Default.Laptop, contentDescription = null) },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = activeTab?.isDesktopMode == true,
                                            onCheckedChange = { checked ->
                                                activeTab?.let { viewModel.toggleDesktopMode(it, checked) }
                                                showMenu = false
                                              }
                                        )
                                    },
                                    onClick = {
                                        activeTab?.let { viewModel.toggleDesktopMode(it, !(it.isDesktopMode)) }
                                        showMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Browser & Reader Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        currentSection = BrowserSection.SETTINGS
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Diagnostic Logs") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    onClick = {
                                        showLogsDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Cache & Storage") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        currentActiveWebView?.clearCache(true)
                                        android.webkit.WebStorage.getInstance().deleteAllData()
                                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                        android.widget.Toast.makeText(context, "Storage and cache cleared successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Translate Page") },
                                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                    onClick = {
                                        currentActiveWebView?.let { webView ->
                                            val currentUrl = webView.url
<<<<<<< HEAD
                                            if (currentUrl != null && currentUrl != "chrome://newtab") {
                                                // Trigger in-page translation using current engine
                                                isGeminiTranslating = true
                                                pageLoadBackgroundLogic(currentUrl, webView)
                                                android.widget.Toast.makeText(context, "Translating page with ${translationEngine.displayName}...", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Page is invalid", android.widget.Toast.LENGTH_SHORT).show()
=======
                                            if (currentUrl != null && !currentUrl.contains("translate.goog") && !currentUrl.contains("translate.google")) {
                                                val translatedUrl = getProxyTranslatedUrl(currentUrl)
                                                webView.loadUrl(translatedUrl)
                                            } else {
                                                android.widget.Toast.makeText(context, "Page is already translated or invalid", android.widget.Toast.LENGTH_SHORT).show()
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                                            }
                                        }
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // URL Suggestions like Chrome
                    val currentQuery = urlInput.trim().lowercase()
                    val builtInSuggestions = listOf(
                        "https://wtr-lab.com/en" to "Wtr-Lab (Main reader)",
                        "https://www.webnovel.com/" to "WebNovel",
                        "https://www.novelhall.com/" to "Novelhall",
                        "https://www.fanmtl.com/" to "Fanmtl",
                        "https://novelbin.me/" to "NovelBin",
                        "https://freewebnovel.com/index" to "Freewebnovel",
                        "https://www.timotxt.com/" to "TimoTxt",
                        "https://www.novel543.com/" to "Novel543",
                        "https://twkan.com/" to "Twkan",
                        "https://novelhub.net/" to "NovelHub",
                        "https://novelhubapp.com/" to "NovelHubApp (Reader App)"
                    )
                    
                    val suggestionsToDisplay = if (currentQuery.isNotEmpty() && currentQuery != "chrome://newtab") {
                        builtInSuggestions.filter { (url, title) ->
                            title.lowercase().contains(currentQuery) || 
                            url.lowercase().contains(currentQuery) ||
                            currentQuery.contains(title.lowercase()) ||
                            currentQuery.contains(url.lowercase().removePrefix("https://").removePrefix("www."))
                        }
                    } else {
                        emptyList()
                    }

                    if (isSearchFocused && suggestionsToDisplay.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "SUGGESTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                suggestionsToDisplay.forEach { (url, title) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setUrlInput(url)
                                                viewModel.loadUrl(url)
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                currentSection = BrowserSection.WEB
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = url,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                            contentDescription = "Navigate",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Loading progress indicator
                    if (isWebLoading && webProgress < 100) {
                        LinearProgressIndicator(
                            progress = { webProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Render our beautiful Chrome Home or the resolved dynamic WebView inside our view hierarchy
            if (activeTab != null && activeTab!!.url == "chrome://newtab") {
                ChromeNewTabPage(
                    onNavigate = { targetUrl ->
                        viewModel.loadUrl(targetUrl)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (activeTab != null && currentActiveWebView != null) {
                val tabForView = activeTab!!
                key(tabForView.id) {
                    AndroidView(
                        factory = { 
                            val wv = currentActiveWebView!!
                            if ((wv.url ?: "").isEmpty() && tabForView.url != "chrome://newtab") {
                                wv.loadUrl(tabForView.url)
                            }
                            wv
                        },
                        update = { wv ->
                            val targetUrl = tabForView.url
                            if (targetUrl.isNotEmpty() && targetUrl != "chrome://newtab") {
                                val currentUrl = wv.url ?: ""
                                if (currentUrl.isEmpty()) {
                                    wv.loadUrl(targetUrl)
                                } else if (!isSameBaseOrTranslatedUrl(currentUrl, targetUrl)) {
                                    wv.loadUrl(targetUrl)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Adjust webview visibility in parent bounds based on current section selection
            LaunchedEffect(currentSection, currentActiveWebView, activeTab?.url) {
                currentActiveWebView?.visibility = if (currentSection == BrowserSection.WEB && activeTab?.url != "chrome://newtab") {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.INVISIBLE
                }
            }

            val urlVal = activeTab?.url ?: ""
            val isWtrLab = urlVal.contains("wtr-lab.com") || urlVal.startsWith("file://") || urlVal.isEmpty()

            // Custom TrackPlayer Bar for any websites besides Wtr Lab
            if (currentSection == BrowserSection.WEB && !isWtrLab && enableWebTrackplayer) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    if (playTrackInputList.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Listen to Webpage",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Let the TTS engine read the text content of this page aloud.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        WtrAudioControlBridge.setIsAudiobookModeActive(true)
                                        runHtmlTextExtractionAndPlay()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    enabled = !isExtracting,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    if (isExtracting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Extract & Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        // Playback Tracker Panel Control
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Playing: ${activeTab?.title?.take(18) ?: "Web Article"}...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "${currentTrackIndex + 1}/${playTrackInputList.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { stopCustomPlayback() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Stop",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Interactive Text Preview window showing the sentence live
                                if (currentlySpeakingText.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = currentlySpeakingText,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Interactive Controls: Skip Previous, Play/Pause, Skip Next, Cycle Speeds
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(
                                        onClick = {
                                            val nextSp = when (activeTtsSpeed) {
                                                1.0f -> 2.0f
                                                2.0f -> 3.0f
                                                3.0f -> 4.0f
                                                4.0f -> 5.0f
                                                else -> 1.0f
                                            }
                                            WtrAudioControlBridge.setTtsSpeed(nextSp)
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "Speed: ${activeTtsSpeed.toInt()}x",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { playCustomParagraph(currentTrackIndex - 1) },
                                            enabled = currentTrackIndex > 0,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                                contentDescription = "Previous Paragraph",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (isPlayerRunning) {
                                                    pauseCustomVolume()
                                                } else {
                                                    resumeCustomVolume()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(23.dp))
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayerRunning) "Pause" else "Play",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = { playCustomParagraph(currentTrackIndex + 1) },
                                            enabled = currentTrackIndex < playTrackInputList.size - 1,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                                contentDescription = "Next Paragraph",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Secondary visual browser overlay screens (Tabs, Bookmarks, History Panels)
            AnimatedVisibility(
                visible = currentSection == BrowserSection.TABS,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                TabsPanel(
                    viewModel = viewModel,
                    onTabSelected = { currentSection = BrowserSection.WEB }
                )
            }

            AnimatedVisibility(
                visible = currentSection == BrowserSection.BOOKMARKS,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                BookmarksPanel(
                    viewModel = viewModel,
                    onUrlSelected = { url ->
                        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else {
                            url
                        }
                        viewModel.loadUrl(cleanUrl)
                        currentSection = BrowserSection.WEB
                    },
                    onDismiss = { currentSection = BrowserSection.WEB }
                )
            }

            AnimatedVisibility(
                visible = currentSection == BrowserSection.HISTORY,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                HistoryPanel(
                    viewModel = viewModel,
                    onUrlSelected = { url ->
                        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else {
                            url
                        }
                        viewModel.loadUrl(cleanUrl)
                        currentSection = BrowserSection.WEB
                    },
                    onDismiss = { currentSection = BrowserSection.WEB }
                )
            }

            // Settings Overlay panel
            AnimatedVisibility(
                visible = currentSection == BrowserSection.SETTINGS,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                SettingsPanel(
                    onDismissRequest = { 
                        currentSection = BrowserSection.WEB
                        // Sync preference values immediately upon settings closing to update our active components state dynamically
                        enableWebTrackplayer = sharedPrefs.getBoolean("enable_web_trackplayer", false)
                        forceDarkContent = sharedPrefs.getBoolean("force_dark_content", false)
                        autoFocusParagraphs = sharedPrefs.getBoolean("auto_focus_paragraphs", true)
                        autoTranslateEnabled = sharedPrefs.getBoolean("auto_translate_enabled", true)
                        autoTranslateDomains = sharedPrefs.getString("auto_translate_domains", defaultTranslateDomains) ?: defaultTranslateDomains
<<<<<<< HEAD
                        translationEngineKey = sharedPrefs.getString("translation_engine", com.example.TranslationEngine.GOOGLE_TRANSLATE.key) ?: com.example.TranslationEngine.GOOGLE_TRANSLATE.key
=======
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                        geminiTranslateEnabled = sharedPrefs.getBoolean("gemini_translate_enabled", false)
                        geminiApiKey = com.example.SecurePreferences.getGeminiApiKey(context)
                        adBlockerEnabled = sharedPrefs.getBoolean("ad_blocker_enabled", true)
                        customTextZoom = sharedPrefs.getInt("custom_text_zoom", 115)
                        antiCaptchaDelay = sharedPrefs.getBoolean("anti_captcha_delay", false)
                        currentThemeName = sharedPrefs.getString("app_theme", "Dark") ?: "Dark"
                    },
                    viewModel = viewModel,
                    onThemeChanged = { onThemeChanged(it) },
<<<<<<< HEAD
                    webViewsMap = webViewsMap,
                    translationEngineKey = translationEngineKey,
                    onTranslationEngineChanged = { key ->
                        translationEngineKey = key
                        sharedPrefs.edit().putString("translation_engine", key).apply()
                    }
=======
                    webViewsMap = webViewsMap
>>>>>>> 127957c0895eac519ea1f54e93e97d19a2b1b55f
                )
            }

            if (showLogsDialog) {
                AlertDialog(
                    onDismissRequest = { showLogsDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "System Diagnostic Logs",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            Text(
                                text = "Showing last ${com.example.WtrLogManager.logs.size} operations. Perfect for troubleshooting novel loading issues.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            
                            val logs = com.example.WtrLogManager.logs
                            if (logs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No logs recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    items(logs.size) { idx ->
                                        val logText = logs[idx]
                                        Text(
                                            text = logText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLogsDialog = false }) {
                            Text("Close")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    saveLogsLauncher.launch("wtr_diagnostic_logs.txt")
                                }
                            ) {
                                Text("Save as TXT", color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(
                                onClick = { 
                                    com.example.WtrLogManager.clear(context)
                                }
                            ) {
                                Text("Clear Logs", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }

            // Chromelike Long Press Context Menu Overlay
            longPressedUrl?.let { url ->
                AlertDialog(
                    onDismissRequest = { longPressedUrl = null },
                    title = {
                        Text(
                            text = if (url.length > 55) url.take(52) + "..." else url,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Open in Current Tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.loadUrl(url)
                                        currentSection = BrowserSection.WEB
                                        longPressedUrl = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Open Link",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Open in current tab",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Open in New Tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addNewTab(url, "New Tab")
                                        currentSection = BrowserSection.WEB
                                        longPressedUrl = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add New Tab",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Open in new tab",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Copy Link
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Link Address", url)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                        longPressedUrl = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy Link",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Copy link address",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Share Link
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Link"))
                                        longPressedUrl = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Link",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Share link",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { longPressedUrl = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

// Unified context-safe preferences helper for smart paragraph-level tracking
private fun getSavedParagraphIndex(context: android.content.Context, url: String): Int {
    if (url.isEmpty() || url == "chrome://newtab") return 0
    val settingsPrefs = context.getSharedPreferences("wtr_browser_settings", android.content.Context.MODE_PRIVATE)
    val enabled = settingsPrefs.getBoolean("remember_paragraphs", true)
    if (!enabled) return 0

    val prefs = context.getSharedPreferences("wtr_tts_progress", android.content.Context.MODE_PRIVATE)
    val cleanUrl = cleanUrlForTts(url)
    return prefs.getInt(cleanUrl, 0)
}

private fun saveParagraphIndex(context: android.content.Context, url: String, index: Int) {
    if (url.isEmpty() || url == "chrome://newtab" || index < 0) return
    val settingsPrefs = context.getSharedPreferences("wtr_browser_settings", android.content.Context.MODE_PRIVATE)
    val enabled = settingsPrefs.getBoolean("remember_paragraphs", true)
    if (!enabled) return

    val prefs = context.getSharedPreferences("wtr_tts_progress", android.content.Context.MODE_PRIVATE)
    val cleanUrl = cleanUrlForTts(url)
    prefs.edit().putInt(cleanUrl, index).apply()
}

private fun cleanUrlForTts(url: String): String {
    if (url.isEmpty() || url == "chrome://newtab") return ""
    var clean = url
    if (clean.contains("translate.goog")) {
        try {
            val uri = android.net.Uri.parse(clean)
            val uParam = uri.getQueryParameter("u")
            if (!uParam.isNullOrEmpty()) {
                clean = uParam
            } else {
                val host = uri.host ?: ""
                if (host.isNotEmpty()) {
                    var cleanHost = host.replace(".translate.goog", "")
                    // Google Translate encodes double-dash '--' as single dash '-', 
                    // and single dot '.' as single dash '-'.
                    // To decode: temporarily preserve double dashes '--' as a unique marker,
                    // replace single '-' with '.', then replace the marker with '-'
                    cleanHost = cleanHost.replace("--", "__DBL_DASH_MKR__")
                    cleanHost = cleanHost.replace("-", ".")
                    cleanHost = cleanHost.replace("__DBL_DASH_MKR__", "-")
                    
                    val scheme = if (url.startsWith("https")) "https" else "http"
                    clean = "$scheme://$cleanHost${uri.path ?: ""}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return clean.split("?")[0].split("#")[0]
}

private fun extractNovelAndChapter(title: String, url: String): Pair<String, String> {
    if (title.isEmpty()) return Pair("Wtr-Lab Browser", "Web Chapter")
    
    var cleanTitle = title
        .replace(" - NovelHall", "", ignoreCase = true)
        .replace(" - Read Novel Free", "", ignoreCase = true)
        .replace(" - WebNovel", "", ignoreCase = true)
        .replace(" - NovelBin", "", ignoreCase = true)
        .replace(" - FreeWebNovel", "", ignoreCase = true)
        .replace(" - FanMTL", "", ignoreCase = true)
        .replace(" - timotxt", "", ignoreCase = true)
        .replace(" - novel543", "", ignoreCase = true)
        .replace(" - twkan", "", ignoreCase = true)
        .replace(" - NovelHub", "", ignoreCase = true)
        .replace(" - NovelHubApp", "", ignoreCase = true)
        .replace(" online free", "", ignoreCase = true)
        .replace(" read online", "", ignoreCase = true)
        .replace("_timotxt", "", ignoreCase = true)
        .replace("_timotxt.com", "", ignoreCase = true)
        .replace("_novelhall.com", "", ignoreCase = true)
        .replace("_novel543.com", "", ignoreCase = true)
        .replace("_twkan.com", "", ignoreCase = true)
        .replace("_novelhub.net", "", ignoreCase = true)
        .replace("_novelhubapp.com", "", ignoreCase = true)
        .replace(" - timotxt.com", "", ignoreCase = true)
        .replace(" - novelhall.com", "", ignoreCase = true)
        .replace(" - novel543.com", "", ignoreCase = true)
        .replace(" - twkan.com", "", ignoreCase = true)
        .replace(" - novelhub.net", "", ignoreCase = true)
        .replace(" - novelhubapp.com", "", ignoreCase = true)
        .replace(Regex("""_\d+\.html"""), ".html")
        .trim()
        
    if (cleanTitle.startsWith("《") && cleanTitle.endsWith("》")) {
        cleanTitle = cleanTitle.substring(1, cleanTitle.length - 1).trim()
    }

    val chapterPatterns = listOf(
        Regex("""(?i)\b(?:chapter|chap|ch|episode|ep)\.?\s*(\d+)"""), // Chapter 123 / Ch. 123
        Regex("""(?i)\b(?:chapter|chap|ch|episode|ep)\.?\s*([ivxldcm]+)"""), // Roman
        Regex("""(第\s*[0-9一二三四五六七八九十百千]+[章回节集卷])"""), // Chinese: 第123章 / 第一百章
        Regex("""\b(\d+)\s*$""") // Digits at the very end of the title
    )

    var extractedChapter = ""
    var extractedNovel = ""

    val separators = listOf(" - ", " | ", " – ", " — ")
    for (sep in separators) {
        if (cleanTitle.contains(sep)) {
            val parts = cleanTitle.split(sep)
            if (parts.size >= 2) {
                val part0 = parts[0].trim()
                val part1 = parts.drop(1).joinToString(" - ").trim()
                
                var isPart1Chapter = false
                for (pattern in chapterPatterns) {
                    if (pattern.containsMatchIn(part1)) {
                        isPart1Chapter = true
                        break
                    }
                }
                
                var isPart0Chapter = false
                for (pattern in chapterPatterns) {
                    if (pattern.containsMatchIn(part0)) {
                        isPart0Chapter = true
                        break
                    }
                }

                if (isPart1Chapter && !isPart0Chapter) {
                    return Pair(part0, part1)
                } else if (isPart0Chapter && !isPart1Chapter) {
                    return Pair(part1, part0)
                } else {
                    return Pair(part0, part1)
                }
            }
        }
    }

    for (pattern in chapterPatterns) {
        val match = pattern.find(cleanTitle)
        if (match != null) {
            val fullMatch = match.value
            val idx = cleanTitle.indexOf(fullMatch)
            if (idx > 0) {
                extractedNovel = cleanTitle.substring(0, idx).trim(' ', ',', '-', '_', '(', ')', '《', '》', ':').trim()
                extractedChapter = cleanTitle.substring(idx).trim()
                break
            } else if (idx == 0) {
                extractedChapter = fullMatch
                extractedNovel = cleanTitle.substring(fullMatch.length).trim(' ', ',', '-', '_', ':', '(', ')').trim()
                break
            }
        }
    }

    if (extractedChapter.isEmpty()) {
        val urlPatterns = listOf(
            Regex("""(?i)chapter[-_]?(\d+)"""),
            Regex("""(?i)ch[-_]?(\d+)"""),
            Regex("""wtr=([a-zA-Z0-9_]+)"""),
            Regex("""/(\d+)\.html"""),
            Regex("""/(\d+)""")
        )
        for (pattern in urlPatterns) {
            val match = pattern.find(url)
            if (match != null) {
                val num = match.groupValues.getOrNull(1) ?: match.value
                extractedChapter = "Chapter $num"
                break
            }
        }
    }

    if (extractedNovel.isEmpty()) {
        extractedNovel = cleanTitle
    }
    
    if (extractedChapter.isEmpty()) {
        extractedChapter = "Chapter 1"
    }

    if (extractedNovel.startsWith("《") && extractedNovel.endsWith("》")) {
        extractedNovel = extractedNovel.substring(1, extractedNovel.length - 1).trim()
    }
    
    if (extractedNovel.isEmpty()) {
        extractedNovel = "Web Novel"
    }

    return Pair(extractedNovel, extractedChapter)
}

private fun getCleanDisplayUrl(url: String): String {
    if (url.isEmpty() || url == "chrome://newtab") return ""
    if (url.contains("translate.goog") || url.contains("translate.google")) {
        try {
            val uri = android.net.Uri.parse(url)
            val uParam = uri.getQueryParameter("u")
            if (!uParam.isNullOrEmpty()) {
                return uParam
            }
            val host = uri.host ?: ""
            if (host.isNotEmpty()) {
                var cleanHost = host.replace(".translate.goog", "")
                cleanHost = cleanHost.replace("--", "__DBL_DASH_MKR__")
                cleanHost = cleanHost.replace("-", ".")
                cleanHost = cleanHost.replace("__DBL_DASH_MKR__", "-")
                val scheme = if (url.startsWith("https")) "https" else "http"
                return "$scheme://$cleanHost${uri.path ?: ""}"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return url
}

