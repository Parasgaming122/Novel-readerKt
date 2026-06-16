package com.paras.novelreaderkt

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paras.novelreaderkt.data.*
import com.paras.novelreaderkt.sites.WebsiteSupportRegistry
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BrowserRepository
    val allHistory: StateFlow<List<HistoryEntry>>
    val allBookmarks: StateFlow<List<BookmarkEntry>>
    val allTabs: StateFlow<List<TabEntry>>

    private val _currentTab = MutableStateFlow<TabEntry?>(null)
    val currentTab: StateFlow<TabEntry?> = _currentTab

    private val _currentUrlInput = MutableStateFlow("")
    val currentUrlInput: StateFlow<String> = _currentUrlInput

    private val _userNavigateTrigger = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8)
    val userNavigateTrigger: SharedFlow<String> = _userNavigateTrigger.asSharedFlow()

    private val _searchEngine = MutableStateFlow("https://www.google.com/search?q=")
    val searchEngine: StateFlow<String> = _searchEngine

    @Volatile private var lastHistoryUrl: String? = null

    // Backstack / Navigation History management for tabs
    private val tabNavigationHistory = java.util.LinkedHashSet<Long>()

    fun recordTabVisit(tabId: Long) {
        if (tabId <= 0) return
        tabNavigationHistory.remove(tabId)
        tabNavigationHistory.add(tabId)
    }

    fun forgetTab(tabId: Long) {
        tabNavigationHistory.remove(tabId)
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = BrowserRepository(db.browserDao())

        allHistory = repository.allHistory.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        allBookmarks = repository.allBookmarks.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        allTabs = repository.allTabsFlow.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        // Initialize tabs once from DB; subsequently, we manage states in memory and update DB asynchronously
        viewModelScope.launch {
            try {
                val tabsList = repository.allTabsFlow.first()
                if (tabsList.isEmpty()) {
                    // Create default tab
                    val defaultTab = TabEntry(url = "chrome://newtab", title = "New Tab", isCurrent = true)
                    val id = repository.insertTab(defaultTab)
                    _currentTab.value = defaultTab.copy(id = id)
                    _currentUrlInput.value = "chrome://newtab"
                    recordTabVisit(id)
                } else {
                    var current = tabsList.find { it.isCurrent } ?: tabsList.first()
                    // Validate URL is not malformed
                    if (!current.url.startsWith("chrome://") && !current.url.startsWith("http://") && !current.url.startsWith("https://") && current.url.isNotEmpty()) {
                        com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "Invalid URL detected on current tab: ${current.url}, resetting to home")
                        current = current.copy(url = "chrome://newtab")
                        repository.updateTab(current)
                    }
                    _currentTab.value = current
                    _currentUrlInput.value = current.url
                    recordTabVisit(current.id)
                }
            } catch (e: Exception) {
                com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "Session restoration failed, fallback to default tab: ${e.message}")
                val defaultTab = TabEntry(url = "chrome://newtab", title = "New Tab", isCurrent = true)
                val id = repository.insertTab(defaultTab)
                _currentTab.value = defaultTab.copy(id = id)
                _currentUrlInput.value = "chrome://newtab"
                recordTabVisit(id)
            }
        }
    }

    fun setUrlInput(url: String) {
        _currentUrlInput.value = url
    }

    fun setSearchEngine(engineQueryUrl: String) {
        _searchEngine.value = engineQueryUrl
    }

    fun addNewTab(url: String = "chrome://newtab", title: String = "New Tab", groupId: Long? = null) {
        viewModelScope.launch {
            com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "addNewTab requested: url=$url, title=$title")
            // Use in-memory snapshot to avoid redundant DB reads
            val tabsList = allTabs.value
            val previousCurrent = tabsList.find { it.isCurrent }
            if (previousCurrent != null) {
                repository.updateTab(previousCurrent.copy(isCurrent = false))
            }
            val newTab = TabEntry(url = url, title = title, isCurrent = true, groupId = groupId)
            val id = repository.insertTab(newTab)
            _currentTab.value = newTab.copy(id = id)
            _currentUrlInput.value = url
            recordTabVisit(id)
        }
    }

    fun switchToTab(tab: TabEntry) {
        viewModelScope.launch {
            // Use in-memory snapshot to avoid redundant DB reads
            val tabsList = allTabs.value
            val previousCurrent = tabsList.find { it.isCurrent }
            if (previousCurrent != null && previousCurrent.id != tab.id) {
                repository.updateTab(previousCurrent.copy(isCurrent = false))
            }
            val updated = tab.copy(isCurrent = true)
            repository.updateTab(updated)
            _currentTab.value = updated
            _currentUrlInput.value = updated.url
            recordTabVisit(updated.id)
        }
    }

    fun toggleDesktopMode(tab: TabEntry, enabled: Boolean) {
        viewModelScope.launch {
            val updated = tab.copy(isDesktopMode = enabled)
            repository.updateTab(updated)
            if (_currentTab.value?.id == tab.id) {
                _currentTab.value = updated
            }
        }
    }

    fun groupTabs(tabIds: List<Long>, targetGroupId: Long) {
        viewModelScope.launch {
            // Use in-memory snapshot to avoid redundant DB reads
            val tabsList = allTabs.value
            tabsList.forEach {
                if (tabIds.contains(it.id)) {
                    repository.updateTab(it.copy(groupId = targetGroupId))
                    if (_currentTab.value?.id == it.id) {
                        _currentTab.value = it.copy(groupId = targetGroupId)
                    }
                }
            }
        }
    }

    fun removeFromGroup(tab: TabEntry) {
        viewModelScope.launch {
            val updated = tab.copy(groupId = null)
            repository.updateTab(updated)
            if (_currentTab.value?.id == tab.id) {
                _currentTab.value = updated
            }
        }
    }

    fun closeTab(tab: TabEntry) {
        viewModelScope.launch {
            com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "closeTab requested: ID=${tab.id}, url=${tab.url}")
            forgetTab(tab.id)
            // Use in-memory snapshot to avoid redundant DB reads
            val tabsList = allTabs.value
            if (tabsList.size <= 1) {
                // If closing single last tab, just reset it to home
                val updated = tab.copy(url = "chrome://newtab", title = "New Tab", isCurrent = true, groupId = null)
                repository.updateTab(updated)
                _currentTab.value = updated
                _currentUrlInput.value = "chrome://newtab"
                return@launch
            }

            repository.deleteTab(tab.id)

            // If we closed the active tab, switch to another tab
            if (tab.isCurrent) {
                val remaining = tabsList.filter { it.id != tab.id }
                if (remaining.isNotEmpty()) {
                    val target = remaining.first()
                    val updated = target.copy(isCurrent = true)
                    repository.updateTab(updated)
                    _currentTab.value = updated
                    _currentUrlInput.value = updated.url
                }
            }
        }
    }

    fun handleBackNavigation(onFinish: () -> Unit) {
        viewModelScope.launch {
            val current = _currentTab.value
            if (_currentTab.value != null && tabNavigationHistory.size >= 1) {
                com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "handleBackNavigation closing current tab ID=${current?.id}")
                
                // Use in-memory tab list snapshot to avoid redundant DB reads
                val tabs = allTabs.value
                if (tabs.size <= 1) {
                    onFinish()
                    return@launch
                }
                
                // 1. Close the current tab
                forgetTab(current?.id ?: return@launch)
                repository.deleteTab(current.id)
                
                // 2. Identify and navigate back to the previous active tab
                val lastTabId = tabNavigationHistory.lastOrNull()
                val targetTab = if (lastTabId != null) {
                    tabs.find { it.id == lastTabId && it.id != current.id }
                } else {
                    null
                }
                
                // Fallback to any remaining tab if history trace is unavailable
                val fallbackTab = targetTab ?: tabs.firstOrNull { it.id != current.id }
                if (fallbackTab != null) {
                    // Direct in-memory switch, avoids additional DB reads
                    val updated = fallbackTab.copy(isCurrent = true)
                    repository.updateTab(updated)
                    _currentTab.value = updated
                    _currentUrlInput.value = updated.url
                    recordTabVisit(updated.id)
                } else {
                    onFinish()
                }
            } else {
                onFinish()
            }
        }
    }

    fun clearAllTabs() {
        viewModelScope.launch {
            repository.clearTabs()
            val defaultTab = TabEntry(url = "chrome://newtab", title = "New Tab", isCurrent = true)
            val id = repository.insertTab(defaultTab)
            _currentTab.value = defaultTab.copy(id = id)
            _currentUrlInput.value = "chrome://newtab"
        }
    }

    fun loadUrl(url: String) {
        viewModelScope.launch {
            val cleanUrl = cleanInputUrl(url, _searchEngine.value)
            com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "loadUrl: request=$url -> resolved=$cleanUrl")
            val current = _currentTab.value
            if (current != null) {
                val updated = current.copy(url = cleanUrl)
                repository.updateTab(updated)
                _currentTab.value = updated
                _currentUrlInput.value = cleanUrl
            }
            _userNavigateTrigger.emit(cleanUrl)
        }
    }

    fun onPageLoaded(url: String, title: String) {
        val trimmedUrl = url.trim()
        val trimmedTitle = title.trim()

        // Ignore invalid schemas or excessively large/data URIs
        if (trimmedUrl.startsWith("data:") || trimmedUrl.startsWith("blob:") || trimmedUrl.length > 2048) {
            return
        }

        // Clean control characters from titles and URLs to prevent database issues
        val sanitizedUrl = trimmedUrl.replace(Regex("\\p{Cc}"), "")
        val sanitizedTitle = trimmedTitle.replace(Regex("\\p{Cc}"), "").take(512)

        viewModelScope.launch {
            val current = _currentTab.value
            if (current != null && (current.url != sanitizedUrl || current.title != sanitizedTitle)) {
                com.paras.novelreaderkt.WtrLogManager.log(getApplication(), "onPageLoaded updates tab ID=${current.id} from ${current.url} to $sanitizedUrl (title=$sanitizedTitle)")
                val updated = current.copy(url = sanitizedUrl, title = sanitizedTitle)
                repository.updateTab(updated)
                _currentTab.value = updated
                _currentUrlInput.value = sanitizedUrl
            }
            if (lastHistoryUrl != sanitizedUrl) {
                lastHistoryUrl = sanitizedUrl
                repository.insertHistory(sanitizedUrl, sanitizedTitle)
            }
            // Run reading progress update off main thread — it does DB queries
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repository.updateReadingProgress(sanitizedUrl, sanitizedTitle)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun cleanInputUrl(input: String, searchEngineUrl: String): String {
        val trimmed = input.trim()
        if (trimmed == "chrome://newtab") {
            return trimmed
        }
        
        val matchedSupport = WebsiteSupportRegistry.findSupportByKeyword(trimmed)
        if (matchedSupport != null) {
            val pDomain = matchedSupport.domains.first()
            return if (matchedSupport.siteId == "wtr-lab") "https://wtr-lab.com/en" else "https://$pDomain/"
        }

        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.substring(7)
        } else if (trimmed.startsWith("https://")) {
            return trimmed
        }
        val hasSpace = trimmed.contains(" ")
        val hasDot = trimmed.contains(".")
        val isProbablyUrl = !hasSpace && hasDot && trimmed.length > 3
        return if (isProbablyUrl) {
            "https://$trimmed"
        } else {
            searchEngineUrl + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    // Bookmarks
    fun updateNovelMetadata(url: String, novelTitle: String, chapterTitle: String, coverImage: String) {
        viewModelScope.launch {
            repository.updateNovelMetadata(url, novelTitle, chapterTitle, coverImage)
        }
    }

    fun toggleBookmark(url: String, title: String, imageUrl: String? = null) {
        viewModelScope.launch {
            val isBookmarkedFlow = repository.isBookmarked(url)
            val exists = isBookmarkedFlow.first()
            if (exists) {
                repository.deleteBookmarkByUrl(url)
            } else {
                repository.insertBookmark(url, title, imageUrl)
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun isUrlBookmarked(url: String): Flow<Boolean> {
        return repository.isBookmarked(url)
    }

    fun exportBackup(uri: android.net.Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var rawOutputStream: java.io.OutputStream? = null
            var processingStream: java.io.OutputStream? = null
            var writer: java.io.BufferedWriter? = null
            try {
                val context = getApplication<Application>()
                rawOutputStream = context.contentResolver.openOutputStream(uri)
                if (rawOutputStream == null) {
                    throw Exception("Could not open destination file stream")
                }

                // Wrap in our streaming encryptor.
                // We use getEncryptingStream which handles Keystore/AES/Base64 streaming dynamically!
                var encryptingFailed = false
                try {
                    processingStream = BackupEncryption.getEncryptingStream(rawOutputStream)
                } catch (e: Exception) {
                    encryptingFailed = true
                    WtrLogManager.log(context, "Encryption stream initialization failed: ${e.message}. Using plain text.")
                    processingStream = rawOutputStream
                }

                writer = java.io.BufferedWriter(java.io.OutputStreamWriter(processingStream, "UTF-8"))

                // Start JSON stream writing
                // 1. Header & settings
                writer.write("{\"version\":2,\"timestamp\":${System.currentTimeMillis()},\"settings\":")
                
                val sharedPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                val settingsJson = org.json.JSONObject().apply {
                    put("app_theme", sharedPrefs.getString("app_theme", "Dark"))
                    put("custom_text_zoom", sharedPrefs.getInt("custom_text_zoom", 115))
                    put("force_dark_content", sharedPrefs.getBoolean("force_dark_content", false))
                    put("enable_web_trackplayer", sharedPrefs.getBoolean("enable_web_trackplayer", false))
                    put("auto_focus_paragraphs", sharedPrefs.getBoolean("auto_focus_paragraphs", true))
                    put("remember_paragraphs", sharedPrefs.getBoolean("remember_paragraphs", true))
                    put("auto_translate_enabled", sharedPrefs.getBoolean("auto_translate_enabled", true))
                    val defaultTranslate = WebsiteSupportRegistry.getAutoTranslateSites().joinToString(", ")
                    put("auto_translate_domains", sharedPrefs.getString("auto_translate_domains", defaultTranslate))
                    put("gemini_translate_enabled", sharedPrefs.getBoolean("gemini_translate_enabled", false))
                    put("ad_blocker_enabled", sharedPrefs.getBoolean("ad_blocker_enabled", true))
                }
                writer.write(settingsJson.toString())

                // 2. History streaming
                writer.write(",\"history\":[")
                val historyList = allHistory.value.ifEmpty { repository.allHistory.first() }
                historyList.forEachIndexed { index, entry ->
                    val obj = org.json.JSONObject().apply {
                        put("url", entry.url)
                        put("title", entry.title)
                        put("timestamp", entry.timestamp)
                    }
                    if (index > 0) writer.write(",")
                    writer.write(obj.toString())
                }
                writer.write("]")

                // 3. Bookmarks streaming
                writer.write(",\"bookmarks\":[")
                val bookmarksList = allBookmarks.value.ifEmpty { repository.allBookmarks.first() }
                bookmarksList.forEachIndexed { index, entry ->
                    val obj = org.json.JSONObject().apply {
                        put("url", entry.url)
                        put("title", entry.title)
                        put("timestamp", entry.timestamp)
                        put("isNovel", entry.isNovel)
                        put("novelTitle", entry.novelTitle ?: org.json.JSONObject.NULL)
                        put("chapterTitle", entry.chapterTitle ?: org.json.JSONObject.NULL)
                        put("imageUrl", entry.imageUrl ?: org.json.JSONObject.NULL)
                        put("domain", entry.domain ?: org.json.JSONObject.NULL)
                        put("lastViewedChapterUrl", entry.lastViewedChapterUrl ?: org.json.JSONObject.NULL)
                        put("lastViewedChapterTitle", entry.lastViewedChapterTitle ?: org.json.JSONObject.NULL)
                    }
                    if (index > 0) writer.write(",")
                    writer.write(obj.toString())
                }
                writer.write("]")

                // 4. Tabs streaming
                writer.write(",\"tabs\":[")
                val tabsList = repository.getAllTabs()
                tabsList.forEachIndexed { index, entry ->
                    val obj = org.json.JSONObject().apply {
                        put("url", entry.url)
                        put("title", entry.title)
                        put("isCurrent", entry.isCurrent)
                        put("isDesktopMode", entry.isDesktopMode)
                        put("groupId", entry.groupId ?: org.json.JSONObject.NULL)
                        put("timestamp", entry.timestamp)
                    }
                    if (index > 0) writer.write(",")
                    writer.write(obj.toString())
                }
                writer.write("]}")
                writer.flush()

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            } finally {
                try {
                    writer?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    processingStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    rawOutputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun importBackup(uri: android.net.Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var rawInputStream: java.io.InputStream? = null
            var processingStream: java.io.InputStream? = null
            try {
                val context = getApplication<Application>()
                rawInputStream = context.contentResolver.openInputStream(uri)
                if (rawInputStream == null) {
                    throw Exception("Could not open source file stream")
                }

                // Wrap context stream in BufferedInputStream to inspect the header byte
                val bufferedIn = java.io.BufferedInputStream(rawInputStream)
                bufferedIn.mark(100)

                var firstByte = -1
                while (true) {
                    val b = bufferedIn.read()
                    if (b == -1) break
                    if (!Character.isWhitespace(b)) {
                        firstByte = b
                        break
                    }
                }
                bufferedIn.reset()

                processingStream = if (firstByte == '{'.code) {
                    // Raw plaintext JSON
                    bufferedIn
                } else {
                    // Encrypted stream
                    try {
                        BackupEncryption.getDecryptingStream(bufferedIn)
                    } catch (e: Exception) {
                        WtrLogManager.log(context, "Backup decryption wrapper failed, checking raw parsing: ${e.message}")
                        bufferedIn.reset()
                        bufferedIn
                    }
                }

                // Use our streaming parser! This completely reads the stream without loading it whole.
                val backupData = kotlinx.coroutines.withTimeout(30000L) {
                    StreamingJsonParser.parseBackupStream(processingStream)
                }

                // 1. Restore SharedPreferences (whitelist only known safe keys)
                val sharedPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()

                val safeSettingsKeys = setOf(
                    "app_theme", "custom_text_zoom", "force_dark_content",
                    "enable_web_trackplayer", "auto_focus_paragraphs", "remember_paragraphs",
                    "auto_translate_enabled", "auto_translate_domains",
                    "gemini_translate_enabled", "ad_blocker_enabled", "anti_captcha_delay",
                    "tts_speed", "tts_pitch", "tts_voice_name", "tts_accent",
                    "enable_logs"
                )
                backupData.settings.forEach { (key, value) ->
                    if (key in safeSettingsKeys) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Double -> {
                                // SharedPreferences doesn't support Double, but standard fallback
                                editor.putFloat(key, value.toFloat())
                            }
                        }
                    }
                }
                editor.apply()

                // Get DB instances
                val db = AppDatabase.getDatabase(context)
                val dao = db.browserDao()

                var currentTabToLoad: TabEntry? = null

                // Restore tables inside a single atomic SQLite transaction
                db.withTransaction {
                    dao.clearHistory()
                    backupData.history.forEach { dao.insertHistory(it) }

                    dao.clearBookmarks()
                    backupData.bookmarks.forEach { dao.insertBookmark(it) }

                    dao.clearTabs()
                    backupData.tabs.forEach { tab ->
                        val newId = dao.insertTab(tab)
                        if (tab.isCurrent) {
                            currentTabToLoad = tab.copy(id = newId)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (currentTabToLoad != null) {
                        _currentTab.value = currentTabToLoad
                        _currentUrlInput.value = currentTabToLoad.url
                    } else {
                        val allRestored = dao.getAllTabs()
                        if (allRestored.isNotEmpty()) {
                            val target = allRestored.first()
                            _currentTab.value = target
                            _currentUrlInput.value = target.url
                        }
                    }
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            } finally {
                try {
                    processingStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    rawInputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

