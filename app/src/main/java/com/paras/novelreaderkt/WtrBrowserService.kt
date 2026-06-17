package com.paras.novelreaderkt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class WtrBrowserService : Service() {

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val NOTIFICATION_ID = 4048
    private val CHANNEL_ID = "wtr_tts_channel"

    // Cached SharedPreferences instances — avoid repeated lookups on every paragraph switch
    private lateinit var settingsPrefs: android.content.SharedPreferences
    private lateinit var paragraphsPrefs: android.content.SharedPreferences
    private var rememberParagraphs = true

    // Batch paragraph save: only write to disk every N paragraphs or on pause/stop
    private var paragraphSaveAccumulator = 0
    private var lastSavedParagraphIndex = -1

    // Throttling fields to prevent system notification rate-limiting error: "Package enqueue rate is ... Shedding"
    private var lastNotificationUpdateTime = 0L
    private var lastIsPlaying: Boolean? = null
    private var lastRenderedIsPlaying: Boolean? = null
    private var lastRenderedTitle: String? = null
    private var lastRenderedSubtitle: String? = null
    private val notificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingNotificationRunnable: Runnable? = null

    private var tts: TextToSpeech? = null
    @Volatile private var isTtsInitialized = false
    @Volatile private var currentSpeechText: String = ""
    @Volatile private var currentSpeechRate: Float = 4.0f
    @Volatile private var currentSpeechPitch: Float = 1.0f
    @Volatile private var currentSpeechLang: String = "en-US"
    @Volatile private var lastWordIndex: Int = 0

    // --- Performance caches for fast paragraph switching ---
    @Volatile private var cachedVoice: android.speech.tts.Voice? = null
    @Volatile private var cachedVoiceName: String = ""
    @Volatile private var cachedPlaylistIsEnglish: Boolean? = null
    @Volatile private var cachedPlaylistHash: Int = 0
    @Volatile private var cachedLangTag: String = "en-US"

    // Coroutine scope for service tasks
    private val serviceScope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    // Reusable handler for onDone callbacks — avoids creating new Handler per paragraph
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Debounce removed for speed — QUEUE_FLUSH handles instant transitions

    // Background WebView speech timeout detection and native backup takeover loop
    private val webviewSpeechTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var isBackupTakeoverActive = false
    private val webviewSpeechTimeoutRunnable = object : Runnable {
        override fun run() {
            val fallbackList = WtrAudioControlBridge.webSpeakNativeFallbackList.value
            val fallbackIdx = WtrAudioControlBridge.webSpeakNativeFallbackIndex.value
            val nextFallbackIdx = fallbackIdx + 1
            android.util.Log.d("WtrTts", "WebView speech timeout fired at fallback index $fallbackIdx. Starting native takeover for index $nextFallbackIdx...")
            if (fallbackList.isNotEmpty() && nextFallbackIdx < fallbackList.size) {
                isBackupTakeoverActive = true
                WtrAudioControlBridge.setWebSpeakNativeFallbackIndex(nextFallbackIdx)
                val nextText = fallbackList[nextFallbackIdx]
                speakText(nextText, currentSpeechRate, currentSpeechPitch, currentSpeechLang)
            } else {
                isBackupTakeoverActive = false
                WtrAudioControlBridge.onTtsDone?.invoke()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        updateNotification()

        // Initialize the background-safe next chapter handler with this service's coroutine scope
        WtrNextChapterHandler.init(serviceScope)
        WtrAudioControlBridge.lastKnownContext = applicationContext

        // Hook up the bridge to update notifications on playback changes
        WtrAudioControlBridge.onStateChangedCallback = {
            updateNotification()
        }

        // Hook up bridge TTS speaking controls
        WtrAudioControlBridge.onSpeakNative = { text, rate, pitch, lang ->
            webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
            isBackupTakeoverActive = false

            val fallbackList = WtrAudioControlBridge.webSpeakNativeFallbackList.value
            if (fallbackList.isNotEmpty()) {
                val cleanText = text.trim()
                var matchIdx = fallbackList.indexOf(cleanText)
                if (matchIdx == -1) {
                    matchIdx = fallbackList.indexOfFirst { it.lowercase().trim() == cleanText.lowercase() }
                }
                if (matchIdx != -1) {
                    WtrAudioControlBridge.setWebSpeakNativeFallbackIndex(matchIdx)
                }
            }
            speakText(text, rate, pitch, lang)
        }
        WtrAudioControlBridge.onCancelNative = {
            handleCancelNative()
        }
        WtrAudioControlBridge.onPauseNative = {
            pauseText()
        }
        WtrAudioControlBridge.onResumeNative = {
            resumeText()
        }
        WtrAudioControlBridge.playCustomParagraphAction = { index ->
            playCustomParagraph(index)
        }

        // Load initial values from SharedPreferences
        settingsPrefs = getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
        paragraphsPrefs = getSharedPreferences("wtr_browser_paragraphs", Context.MODE_PRIVATE)
        rememberParagraphs = settingsPrefs.getBoolean("remember_paragraphs", true)
        val speed = settingsPrefs.getFloat("tts_speed", 4.0f)
        val pitch = settingsPrefs.getFloat("tts_pitch", 1.0f)
        val accent = settingsPrefs.getString("tts_accent", "US") ?: "US"
        val voiceName = settingsPrefs.getString("tts_voice_name", "") ?: ""

        WtrAudioControlBridge.setTtsSpeed(speed)
        WtrAudioControlBridge.setTtsPitch(pitch)
        WtrAudioControlBridge.setTtsAccent(accent)
        WtrAudioControlBridge.setTtsVoiceName(voiceName)

        // Initialize TextToSpeech engine with self-healing recovery helper
        initTtsEngine()
        
        serviceScope.launch {
            delay(3500)
            if (!isTtsInitialized) {
                WtrLogManager.log(applicationContext, "TextToSpeech engine initialization taking longer than expected...")
            }
        }

        // Dynamically adjust SpeechRate and Pitch upon slider/choice settings changes
        serviceScope.launch {
            WtrAudioControlBridge.ttsSpeed.collect { s ->
                if (isTtsInitialized) {
                    try {
                        tts?.setSpeechRate(s)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        serviceScope.launch {
            WtrAudioControlBridge.ttsPitch.collect { p ->
                if (isTtsInitialized) {
                    try {
                        tts?.setPitch(p)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun fetchAndExposeAvailableVoices() {
        if (!isTtsInitialized) return
        try {
            val voicesList = tts?.voices ?: emptySet()
            // Filter English voices
            val englishVoices = voicesList.filter { 
                val loc = it.locale
                loc != null && (loc.language.lowercase() == "en" || loc.language.lowercase() == "eng")
            }
            val voiceNames = englishVoices.map { it.name }.sorted()
            WtrAudioControlBridge.setAvailableVoices(voiceNames)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTtsUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                WtrAudioControlBridge.updatePlaybackState(isPlaying = true)
                if (!isBackupTakeoverActive) {
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("start", 0)
                }
            }

            override fun onDone(utteranceId: String?) {
                // Keep playing state active since we transition seamlessly to next paragraph
                if (!isBackupTakeoverActive) {
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("end", 0)
                }
                
                val list = WtrAudioControlBridge.playTrackInputList.value
                val currentIndex = WtrAudioControlBridge.currentTrackIndex.value
                val nextIndex = currentIndex + 1
                
                if (list.isNotEmpty()) {
                    if (nextIndex < list.size) {
                        mainHandler.post {
                            playCustomParagraph(nextIndex)
                        }
                    } else {
                        if (WtrAudioControlBridge.isAudiobookModeActive.value) {
                            mainHandler.post {
                                // Use background-safe native next chapter handler instead of WebView JS
                                WtrNextChapterHandler.handleNativeNextChapter()
                                WtrAudioControlBridge.setIsAudiobookModeActive(true)
                                com.paras.novelreaderkt.WtrLogManager.log(applicationContext, "Auto-next triggered (native handler). Waiting for page load...")
                            }
                        } else {
                            WtrAudioControlBridge.setIsPlayerRunning(false)
                            WtrAudioControlBridge.setCurrentlySpeakingText("")
                            WtrAudioControlBridge.updatePlaybackState(false, null, "Completed Reading")
                        }
                    }
                } else {
                    // We are playing via Wtr-Lab / web speechSynthesis website bridge where the WebView page drives the queue.
                    // If we are already in background takeover mode, we want to immediately post the next chunk.
                    // If we are in standard foreground/unthrottled mode, we reschedule/post the background timeout to run after 1500ms
                    // in case the WebView's JS gets throttled/asleep in background mode or when the screen is turned off.
                    mainHandler.post {
                        webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
                        if (isBackupTakeoverActive) {
                            webviewSpeechTimeoutHandler.postDelayed(webviewSpeechTimeoutRunnable, 100L)
                        } else {
                            webviewSpeechTimeoutHandler.postDelayed(webviewSpeechTimeoutRunnable, 3000L)
                        }
                    }
                    WtrAudioControlBridge.onTtsDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!isBackupTakeoverActive) {
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("error", 0)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!isBackupTakeoverActive) {
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("error", 0)
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                lastWordIndex = start
                if (!isBackupTakeoverActive) {
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("boundary", start)
                }
            }
        })
    }

    private fun speakText(text: String, rate: Float, pitch: Float, lang: String) {
        if (!isTtsInitialized) {
            WtrLogManager.log(applicationContext, "TTS not ready. Attempting lazy recovery on demand...")
            initTtsEngine {
                if (isTtsInitialized) {
                    speakText(text, rate, pitch, lang)
                }
            }
            return
        }

        currentSpeechText = text
        currentSpeechRate = WtrAudioControlBridge.ttsSpeed.value
        currentSpeechPitch = WtrAudioControlBridge.ttsPitch.value
        currentSpeechLang = lang
        lastWordIndex = 0

        tts?.let {
            it.setSpeechRate(currentSpeechRate)
            it.setPitch(currentSpeechPitch)

            // Use cached voice reference — avoids O(n) voices scan on every paragraph
            val voiceName = WtrAudioControlBridge.ttsVoiceName.value
            if (voiceName.isNotEmpty()) {
                if (voiceName != cachedVoiceName || cachedVoice == null) {
                    cachedVoice = try { it.voices?.find { v -> v.name == voiceName } } catch (e: Exception) { null }
                    cachedVoiceName = voiceName
                }
                if (cachedVoice != null) {
                    try { it.voice = cachedVoice } catch (e: Exception) { e.printStackTrace() }
                }
            }

            // Only set language if no custom voice is set
            if (cachedVoice == null) {
                val locale = try {
                    if (lang == "en-US") {
                        when (WtrAudioControlBridge.ttsAccent.value) {
                            "UK" -> Locale.UK
                            "AU" -> Locale("en", "AU")
                            "IN" -> Locale("en", "IN")
                            else -> Locale.US
                        }
                    } else {
                        Locale.forLanguageTag(lang)
                    }
                } catch (e: Exception) {
                    Locale.US
                }
                it.language = locale
            }

            val utteranceId = "WTR_TTS_${System.currentTimeMillis()}"
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

            // QUEUE_FLUSH: instantly stops active audio and schedules new speech
            it.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            WtrAudioControlBridge.updatePlaybackState(isPlaying = true)
        }
    }

    private fun handleCancelNative() {
        webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
        isBackupTakeoverActive = false
        flushParagraphSave()
        if (isTtsInitialized) {
            tts?.stop()
        }
        WtrAudioControlBridge.updatePlaybackState(false, null, "Paused")
    }

    private fun stopText() {
        webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
        isBackupTakeoverActive = false
        flushParagraphSave()
        tts?.stop()
        WtrAudioControlBridge.updatePlaybackState(false, null, "Stopped")
    }

    private fun pauseText() {
        webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
        isBackupTakeoverActive = false
        flushParagraphSave()
        // TTS.pause()/resume() are not in the public Android SDK API.
        // Use stop() but save position so resumeText() can continue from the right spot.
        tts?.stop()
        WtrAudioControlBridge.updatePlaybackState(false, null, "Paused")
        WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("pause", lastWordIndex)
    }

    /** Flush any pending paragraph save to disk (called on pause/stop) */
    private fun flushParagraphSave() {
        if (paragraphSaveAccumulator > 0) {
            paragraphSaveAccumulator = 0
            val url = WtrAudioControlBridge.extractedUrl.value
            val idx = WtrAudioControlBridge.currentTrackIndex.value
            if (url.isNotEmpty() && url != "chrome://newtab" && rememberParagraphs) {
                try {
                    paragraphsPrefs.edit().putInt(url, idx).apply()
                } catch(e: Exception) {}
            }
        }
    }

    /** Cached language detection — avoids scanning playlist on every paragraph switch */
    private fun isPlaylistPrimarilyEnglish(): Boolean {
        val list = WtrAudioControlBridge.playTrackInputList.value
        val listHash = list.size * 31 + (list.firstOrNull()?.length?.hashCode() ?: 0)
        if (listHash == cachedPlaylistHash && cachedPlaylistIsEnglish != null) {
            return cachedPlaylistIsEnglish!!
        }
        cachedPlaylistHash = listHash
        if (list.isEmpty()) { cachedPlaylistIsEnglish = true; return true }
        var enCharCount = 0
        var foreignCharCount = 0
        val sampleSize = minOf(list.size, 10)
        for (i in 0 until sampleSize) {
            val text = list[i]
            for (char in text) {
                if (char.isLetter() && char.code < 128) {
                    enCharCount++
                } else if (char in '\u4e00'..'\u9fa5' || char in '\u0400'..'\u04FF') {
                    foreignCharCount++
                }
            }
        }
        val result = enCharCount >= foreignCharCount
        cachedPlaylistIsEnglish = result
        return result
    }

    private fun detectLanguageTag(text: String): String {
        if (text.isEmpty()) return "en-US"

        // Use cached language tag if the playlist language hasn't changed
        if (cachedLangTag != "en-US" || !isPlaylistPrimarilyEnglish()) {
            // Recompute only when not primarily English
            var zhCount = 0
            var ruCount = 0
            var enCount = 0
            val sampleLength = minOf(text.length, 100)
            for (i in 0 until sampleLength) {
                val c = text[i]
                when {
                    c in '\u4e00'..'\u9fa5' -> zhCount++
                    c in '\u0400'..'\u04FF' -> ruCount++
                    c.isLetter() && c.code < 128 -> enCount++
                }
            }
            val maxCount = maxOf(zhCount, ruCount, enCount)
            val detected = when {
                maxCount == 0 -> "en-US"
                maxCount == zhCount -> "zh-CN"
                maxCount == ruCount -> "ru-RU"
                else -> "en-US"
            }
            cachedLangTag = detected
            return detected
        }
        return "en-US"
    }

    private fun playCustomParagraph(index: Int) {
        val list = WtrAudioControlBridge.playTrackInputList.value
        if (list.isNotEmpty()) {
            val validIndex = index.coerceIn(0, list.size - 1)
            WtrAudioControlBridge.setCurrentTrackIndex(validIndex)
            
            // Batch paragraph save: use cached SharedPreferences, write every 3 paragraphs
            val url = WtrAudioControlBridge.extractedUrl.value
            if (url.isNotEmpty() && url != "chrome://newtab" && rememberParagraphs) {
                paragraphSaveAccumulator++
                if (paragraphSaveAccumulator >= 3) {
                    paragraphSaveAccumulator = 0
                    lastSavedParagraphIndex = validIndex
                    try {
                        paragraphsPrefs.edit().putInt(url, validIndex).apply()
                    } catch(e: Exception) {}
                }
            }
            
            val textToSpeak = list[validIndex]
            WtrAudioControlBridge.setCurrentlySpeakingText(textToSpeak)
            WtrAudioControlBridge.setIsPlayerRunning(true)

            WtrAudioControlBridge.updatePlaybackState(
                isPlaying = true,
                title = WtrAudioControlBridge.bookTitle,
                subtitle = "Paragraph ${validIndex + 1} of ${list.size}"
            )

            val detectedLang = detectLanguageTag(textToSpeak)
            speakText(textToSpeak, WtrAudioControlBridge.ttsSpeed.value, WtrAudioControlBridge.ttsPitch.value, detectedLang)
        }
    }

    private fun handleNextTrack() {
        val list = WtrAudioControlBridge.playTrackInputList.value
        val currentIndex = WtrAudioControlBridge.currentTrackIndex.value
        val nextIndex = currentIndex + 1
        if (list.isNotEmpty()) {
            if (nextIndex < list.size) {
                playCustomParagraph(nextIndex)
            } else if (WtrAudioControlBridge.isAudiobookModeActive.value) {
                mainHandler.post {
                    WtrNextChapterHandler.handleNativeNextChapter()
                    WtrAudioControlBridge.setIsAudiobookModeActive(true)
                    com.paras.novelreaderkt.WtrLogManager.log(applicationContext, "Auto-next triggered (native handler). Waiting for page load...")
                }
            }
        }
    }

    private fun handlePrevTrack() {
        val list = WtrAudioControlBridge.playTrackInputList.value
        val currentIndex = WtrAudioControlBridge.currentTrackIndex.value
        val prevIndex = currentIndex - 1
        if (list.isNotEmpty() && prevIndex >= 0) {
            playCustomParagraph(prevIndex)
        }
    }

    private fun resumeText() {
        val list = WtrAudioControlBridge.playTrackInputList.value
        if (list.isNotEmpty()) {
            // For custom track mode: replay from the saved word index within current paragraph
            val currentIndex = WtrAudioControlBridge.currentTrackIndex.value
            val currentText = list[currentIndex]
            if (lastWordIndex > 0 && lastWordIndex < currentText.length) {
                // Resume from where we paused — speak remaining text from lastWordIndex
                val remainingText = currentText.substring(lastWordIndex)
                tts?.let {
                    currentSpeechRate = WtrAudioControlBridge.ttsSpeed.value
                    it.setSpeechRate(currentSpeechRate)
                    it.setPitch(currentSpeechPitch)
                    val locale = try {
                        Locale.forLanguageTag(currentSpeechLang)
                    } catch (e: Exception) {
                        Locale.US
                    }
                    it.language = locale
                    val utteranceId = "WTR_TTS_RESUME_${System.currentTimeMillis()}"
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    it.speak(remainingText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    WtrAudioControlBridge.updatePlaybackState(isPlaying = true)
                    WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("resume", lastWordIndex)
                }
            } else {
                // No saved position — replay the full paragraph
                playCustomParagraph(currentIndex)
            }
        } else if (currentSpeechText.isNotEmpty() && lastWordIndex < currentSpeechText.length) {
            // Web-speech mode: resume from saved word index
            val remainingText = currentSpeechText.substring(lastWordIndex)
            tts?.let {
                currentSpeechRate = WtrAudioControlBridge.ttsSpeed.value
                it.setSpeechRate(currentSpeechRate)
                it.setPitch(currentSpeechPitch)
                val locale = try {
                    Locale.forLanguageTag(currentSpeechLang)
                } catch (e: Exception) {
                    Locale.US
                }
                it.language = locale
                val utteranceId = "WTR_TTS_RESUME_${System.currentTimeMillis()}"
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                it.speak(remainingText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                WtrAudioControlBridge.updatePlaybackState(isPlaying = true)
                WtrAudioControlBridge.onWebViewProgressTrigger?.invoke("resume", lastWordIndex)
            }
        } else {
            WtrAudioControlBridge.playAction?.invoke()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleAction(action)
        }

        // Keep foreground active
        updateNotification()
        return START_STICKY
    }

    private fun handleAction(action: String) {
        val hasCustomTracks = WtrAudioControlBridge.playTrackInputList.value.isNotEmpty()
        when (action) {
            "PLAY" -> {
                if (hasCustomTracks) {
                    resumeText()
                } else {
                    WtrAudioControlBridge.playAction?.invoke()
                }
            }
            "PAUSE" -> {
                if (hasCustomTracks) {
                    pauseText()
                } else {
                    WtrAudioControlBridge.pauseAction?.invoke()
                }
            }
            "PLAY_PAUSE" -> {
                val isPlaying = WtrAudioControlBridge.isPlaying.value
                if (isPlaying) {
                    if (hasCustomTracks) pauseText() else WtrAudioControlBridge.pauseAction?.invoke()
                } else {
                    if (hasCustomTracks) resumeText() else WtrAudioControlBridge.playAction?.invoke()
                }
            }
            "NEXT" -> {
                if (hasCustomTracks) {
                    handleNextTrack()
                } else {
                    WtrAudioControlBridge.nextAction?.invoke()
                }
            }
            "PREV" -> {
                if (hasCustomTracks) {
                    handlePrevTrack()
                } else {
                    WtrAudioControlBridge.prevAction?.invoke()
                }
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "WtrLabSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    handleAction("PLAY")
                }

                override fun onPause() {
                    handleAction("PAUSE")
                }

                override fun onSkipToNext() {
                    handleAction("NEXT")
                }

                override fun onSkipToPrevious() {
                    handleAction("PREV")
                }
            })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wtr-Lab TTS Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for novel TTS reading"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val isPlaying = WtrAudioControlBridge.isPlaying.value
        val title = WtrAudioControlBridge.title.value
        val subtitle = WtrAudioControlBridge.subtitle.value

        // Update MediaSession state instantly (lightweight, not rate-limited by system UI)
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        // Feed content artist & title metadata to MediaSession instantly so systems show labels without delay
        val bTitle = WtrAudioControlBridge.novelName.value.ifEmpty { WtrAudioControlBridge.bookTitle.ifEmpty { title } }
        val bChapter = WtrAudioControlBridge.chapterTitle.value
        val bWebsite = WtrAudioControlBridge.activeWebsite.value
        val currentIdx = WtrAudioControlBridge.currentTrackIndex.value
        val listSize = WtrAudioControlBridge.playTrackInputList.value.size

        val displayTitle = if (bChapter.isNotEmpty()) {
            "$bTitle - $bChapter"
        } else {
            bTitle
        }

        val displaySubtitle = if (listSize > 0) {
            val siteSuffix = if (bWebsite.isNotEmpty()) " on $bWebsite" else ""
            "P. ${currentIdx + 1}/$listSize$siteSuffix"
        } else {
            subtitle
        }

        try {
            val metadataBuilder = android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, displayTitle)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, displaySubtitle)
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "Wtr-Lab Novel Reader")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, -1L) // disables duration layout on system lock screens
            mediaSession?.setMetadata(metadataBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val currentTime = System.currentTimeMillis()
        val playStateChanged = lastIsPlaying != isPlaying
        lastIsPlaying = isPlaying

        // Skip completely redundant notification updates (where display title, subtitle, and play state are identical)
        // to save CPU, battery, and avoid Android OS Package Enqueue Rate limit shedding.
        val hasChanges = isPlaying != lastRenderedIsPlaying ||
                         displayTitle != lastRenderedTitle ||
                         displaySubtitle != lastRenderedSubtitle

        if (!hasChanges) {
            return
        }

        // Force immediate notification update on play/pause toggle.
        // For standard scroll updates or progress, update if at least 1500ms since last update.
        val throttleInterval = 1500L
        if (playStateChanged || (currentTime - lastNotificationUpdateTime >= throttleInterval)) {
            pendingNotificationRunnable?.let { notificationHandler.removeCallbacks(it) }
            pendingNotificationRunnable = null
            
            performActualNotificationUpdate(isPlaying, displayTitle, displaySubtitle)
            lastNotificationUpdateTime = currentTime
        } else {
            // Defer notification draw to reflect the last status accurately without spamming
            pendingNotificationRunnable?.let { notificationHandler.removeCallbacks(it) }
            val runnable = Runnable {
                performActualNotificationUpdate(isPlaying, displayTitle, displaySubtitle)
                lastNotificationUpdateTime = System.currentTimeMillis()
            }
            pendingNotificationRunnable = runnable
            notificationHandler.postDelayed(runnable, throttleInterval - (currentTime - lastNotificationUpdateTime))
        }
    }

    private fun performActualNotificationUpdate(isPlaying: Boolean, displayTitle: String, displaySubtitle: String) {
        // Record rendered state to prevent duplicate notifications
        lastRenderedIsPlaying = isPlaying
        lastRenderedTitle = displayTitle
        lastRenderedSubtitle = displaySubtitle

        // Intents
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, WtrBrowserService::class.java).apply { action = "PREV" }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playActionIntent = Intent(this, WtrBrowserService::class.java).apply {
            action = if (isPlaying) "PAUSE" else "PLAY"
        }
        val playPendingIntent = PendingIntent.getService(
            this, 2, playActionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, WtrBrowserService::class.java).apply { action = "NEXT" }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        // Build notification using native framework builder
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        notificationBuilder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(displayTitle)
            .setContentText(displaySubtitle)
            .setOngoing(isPlaying)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .setShowWhen(false) // suppress timeline tracking ("00:00") labels

        // Actions
        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous", prevPendingIntent
        ).build()
        val playPauseAction = Notification.Action.Builder(
            playPauseIcon, if (isPlaying) "Pause" else "Play", playPendingIntent
        ).build()
        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next, "Next", nextPendingIntent
        ).build()

        notificationBuilder.addAction(prevAction)
        notificationBuilder.addAction(playPauseAction)
        notificationBuilder.addAction(nextAction)

        val notification = notificationBuilder.build()

        // Android 14 requirements: Foreground service type mediaPlayback. 
        // We always use startForeground to enroll/keep active safely.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            WtrLogManager.log(applicationContext, "❌ startForeground failed: ${e.message}. Stopping service to avoid background ANR.")
            e.printStackTrace()
            try { stopSelf() } catch (ignored: Exception) {}
        }

        // Manage WakeLock & WifiLock based on playing state (called after startForeground for correct AppOps association)
        if (isPlaying) {
            acquireWakeLock()
            acquireWifiLock()
        } else {
            releaseWakeLock()
            releaseWifiLock()
        }
    }

    private fun acquireWakeLock() {
        synchronized(this) {
            if (wakeLock == null) {
                try {
                    val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WtrLab::PlaybackWakeLock").apply {
                        setReferenceCounted(false)
                    }
                    wakeLock?.acquire()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun releaseWakeLock() {
        synchronized(this) {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            wakeLock = null
        }
    }

    private fun acquireWifiLock() {
        synchronized(this) {
            if (wifiLock == null) {
                try {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "WtrLab::PlaybackWifiLock")
                    wifiLock?.acquire()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun releaseWifiLock() {
        synchronized(this) {
            if (wifiLock?.isHeld == true) {
                try {
                    wifiLock?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            wifiLock = null
        }
    }

    private fun initTtsEngine(onComplete: (() -> Unit)? = null) {
        if (isTtsInitialized && tts != null) {
            onComplete?.invoke()
            return
        }
        synchronized(this) {
            if (tts == null) {
                tts = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isTtsInitialized = true
                        setupTtsUtteranceListener()
                        fetchAndExposeAvailableVoices()
                        WtrLogManager.log(applicationContext, "✅ TextToSpeech engine initialized successfully")
                        onComplete?.invoke()
                    } else {
                        WtrLogManager.log(applicationContext, "❌ TextToSpeech failed to initialize: $status")
                    }
                }
            } else if (isTtsInitialized) {
                onComplete?.invoke()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
        WtrNextChapterHandler.cancel()
        WtrAudioControlBridge.onStateChangedCallback = null
        WtrAudioControlBridge.onSpeakNative = null
        WtrAudioControlBridge.onCancelNative = null
        WtrAudioControlBridge.onPauseNative = null
        WtrAudioControlBridge.onResumeNative = null
        WtrAudioControlBridge.playCustomParagraphAction = null
        WtrAudioControlBridge.onLoadUrlInWebView = null
        WtrAudioControlBridge.onManualExtractAndPlay = null
        WtrAudioControlBridge.lastKnownContext = null

        synchronized(this) {
            try {
                tts?.setOnUtteranceProgressListener(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tts?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    it.shutdown()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            tts = null
        }

        releaseWakeLock()
        releaseWifiLock()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
