package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object WtrAudioControlBridge {
    // Actions from notification / lockscreen to WebView
    var playAction: (() -> Unit)? = null
    var pauseAction: (() -> Unit)? = null
    var nextAction: (() -> Unit)? = null
    var prevAction: (() -> Unit)? = null

    // State from WebView to notification / lockscreen
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _title = MutableStateFlow("Wtr-Lab Browser")
    val title: StateFlow<String> = _title

    private val _subtitle = MutableStateFlow("Tap to browse novels")
    val subtitle: StateFlow<String> = _subtitle

    // Novel tracking properties for enriched lockscreen/notification display
    private val _novelName = MutableStateFlow("")
    val novelName: StateFlow<String> = _novelName

    private val _chapterTitle = MutableStateFlow("")
    val chapterTitle: StateFlow<String> = _chapterTitle

    private val _activeWebsite = MutableStateFlow("")
    val activeWebsite: StateFlow<String> = _activeWebsite
    
    private val _extractedUrl = MutableStateFlow("")
    val extractedUrl: StateFlow<String> = _extractedUrl

    fun setNovelAndChapter(novel: String, chapter: String) {
        _novelName.value = novel
        _chapterTitle.value = chapter
    }

    fun setActiveWebsite(website: String) {
        _activeWebsite.value = website
    }
    
    fun setExtractedUrl(url: String) {
        _extractedUrl.value = url
    }

    // Speech speed multiplier status
    private val _ttsSpeed = MutableStateFlow(4.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        onStateChangedCallback?.invoke()
    }

    // Pitch & Voice options
    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch

    private val _ttsVoiceName = MutableStateFlow("")
    val ttsVoiceName: StateFlow<String> = _ttsVoiceName

    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices

    private val _ttsAccent = MutableStateFlow("US")
    val ttsAccent: StateFlow<String> = _ttsAccent

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        onStateChangedCallback?.invoke()
    }

    fun setTtsVoiceName(name: String) {
        _ttsVoiceName.value = name
        onStateChangedCallback?.invoke()
    }

    fun setAvailableVoices(voices: List<String>) {
        _availableVoices.value = voices
    }

    fun setTtsAccent(accent: String) {
        _ttsAccent.value = accent
        onStateChangedCallback?.invoke()
    }

    // Connection check - ensures foreground service is listening
    var onStateChangedCallback: (() -> Unit)? = null

    // Callback when JS extracts metadata like cover image and title
    var onMetadataExtracted: ((tabId: Long, novelTitle: String, chapterTitle: String, coverImage: String) -> Unit)? = null

    // TTS actions routed to foreground service
    var onSpeakNative: ((text: String, rate: Float, pitch: Float, lang: String) -> Unit)? = null
    var onCancelNative: (() -> Unit)? = null
    var onPauseNative: (() -> Unit)? = null
    var onResumeNative: (() -> Unit)? = null

    // TTS events routed back to WebView JavaScript
    var onWebViewProgressTrigger: ((event: String, charIndex: Int) -> Unit)? = null

    // Optional callback for custom trackplayer sentence completion
    var onTtsDone: (() -> Unit)? = null

    // Secondary background TTS paragraph list to bypass background JS roundtrip throttling on Wtr-Lab / web speechSynthesis websites
    private val _webSpeakNativeFallbackList = MutableStateFlow<List<String>>(emptyList())
    val webSpeakNativeFallbackList: StateFlow<List<String>> = _webSpeakNativeFallbackList
    
    private val _webSpeakNativeFallbackIndex = MutableStateFlow(-1)
    val webSpeakNativeFallbackIndex: StateFlow<Int> = _webSpeakNativeFallbackIndex

    private val _activeTtsTabId = MutableStateFlow<Long?>(null)
    val activeTtsTabId: StateFlow<Long?> = _activeTtsTabId

    private val _currentlyActiveTabId = MutableStateFlow<Long?>(null)
    val currentlyActiveTabId: StateFlow<Long?> = _currentlyActiveTabId

    fun setActiveTtsTabId(id: Long?) {
        _activeTtsTabId.value = id
    }

    fun setCurrentlyActiveTabId(id: Long?) {
        _currentlyActiveTabId.value = id
    }

    fun setWebSpeakNativeFallbackList(list: List<String>) {
        _webSpeakNativeFallbackList.value = if (list.size > 300) list.take(300) else list
    }

    fun setWebSpeakNativeFallbackIndex(index: Int) {
        _webSpeakNativeFallbackIndex.value = index
    }

    // Background-safe state variables to bypass UI thread lag during TTS playback
    private val _playTrackInputList = MutableStateFlow<List<String>>(emptyList())
    val playTrackInputList: StateFlow<List<String>> = _playTrackInputList

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex

    private val _isPlayerRunning = MutableStateFlow(false)
    val isPlayerRunning: StateFlow<Boolean> = _isPlayerRunning

    private val _isAudiobookModeActive = MutableStateFlow(false)
    val isAudiobookModeActive: StateFlow<Boolean> = _isAudiobookModeActive

    private val _currentlySpeakingText = MutableStateFlow("")
    val currentlySpeakingText: StateFlow<String> = _currentlySpeakingText

    private var _bookTitle = "Wtr-Lab Novel Reader"
    var bookTitle: String
        get() = _bookTitle
        set(value) {
            _bookTitle = value
        }

    // Callbacks to trigger next chapter navigation and custom paragraph playback from background
    var playCustomParagraphAction: ((Int) -> Unit)? = null
    var nextChapterAction: (() -> Unit)? = null

    fun triggerNextChapter() {
        nextChapterAction?.invoke()
    }

    fun setPlayTrackInputList(list: List<String>) {
        _playTrackInputList.value = if (list.size > 300) list.take(300) else list
    }

    fun setCurrentTrackIndex(index: Int) {
        _currentTrackIndex.value = index
    }

    fun setIsPlayerRunning(running: Boolean) {
        _isPlayerRunning.value = running
        if (_isAudiobookModeActive.value) {
            _isPlaying.value = running
        }
    }

    fun setIsAudiobookModeActive(active: Boolean) {
        _isAudiobookModeActive.value = active
        if (active) {
            _isPlaying.value = _isPlayerRunning.value
        }
    }

    fun setCurrentlySpeakingText(text: String) {
        _currentlySpeakingText.value = text
    }

    fun updatePlaybackState(isPlaying: Boolean, title: String? = null, subtitle: String? = null) {
        if (_isAudiobookModeActive.value) {
            _isPlaying.value = _isPlayerRunning.value
        } else {
            _isPlaying.value = isPlaying
        }
        if (title != null && title.isNotEmpty()) {
            _title.value = title
        }
        if (subtitle != null && subtitle.isNotEmpty()) {
            _subtitle.value = subtitle
        }
        onStateChangedCallback?.invoke()
    }

    fun triggerPlay() {
        playAction?.invoke()
    }

    fun triggerPause() {
        pauseAction?.invoke()
    }

    fun triggerNext() {
        nextAction?.invoke()
    }

    fun triggerPrev() {
        prevAction?.invoke()
    }
}
