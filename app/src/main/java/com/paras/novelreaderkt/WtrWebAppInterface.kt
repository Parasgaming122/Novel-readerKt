package com.paras.novelreaderkt

import android.webkit.JavascriptInterface

class WtrWebAppInterface(
    val tabId: Long,
    private val onPlaybackStateChanged: (isPlaying: Boolean, title: String, subtitle: String) -> Unit,
    private val onUrlSynced: (url: String, title: String) -> Unit = { _, _ -> }
) {
    @JavascriptInterface
    fun syncUrl(url: String?, title: String?) {
        val safeUrl = url?.take(2048) ?: ""
        val safeTitle = title?.take(1024) ?: ""
        if (WtrAudioControlBridge.currentlyActiveTabId.value == tabId) {
            onUrlSynced(safeUrl, safeTitle)
        }
    }

    @JavascriptInterface
    fun syncMetadata(novelTitle: String?, chapterTitle: String?, coverImage: String?) {
        val safeNovel = novelTitle?.take(1024) ?: ""
        val safeChapter = chapterTitle?.take(1024) ?: ""
        val safeCover = coverImage?.take(2048) ?: ""
        WtrAudioControlBridge.onMetadataExtracted?.invoke(tabId, safeNovel, safeChapter, safeCover)
    }

    @JavascriptInterface
    fun postPlaybackState(isPlaying: Boolean, title: String?, subtitle: String?) {
        val safeTitle = title?.take(1024) ?: "Unknown"
        val safeSubtitle = subtitle?.take(1024) ?: ""
        if (isPlaying) {
            WtrAudioControlBridge.setActiveTtsTabId(tabId)
        }
        onPlaybackStateChanged(isPlaying, safeTitle, safeSubtitle)
    }

    @JavascriptInterface
    fun syncPollState(isPlaying: Boolean, title: String?, subtitle: String? = "") {
        val safeTitle = title?.take(1024) ?: "Unknown"
        val rawSubtitle = subtitle ?: ""
        if (isPlaying) {
            WtrAudioControlBridge.setActiveTtsTabId(tabId)
        }
        val sub = if (rawSubtitle.isNotEmpty()) {
            rawSubtitle.take(1024)
        } else {
            if (isPlaying) "Playing Wtr-Lab Novel" else "Paused"
        }
        onPlaybackStateChanged(isPlaying, safeTitle, sub)
    }

    @JavascriptInterface
    fun speakNative(text: String?, rate: Float, pitch: Float, lang: String?) {
        val safeText = text?.take(100000) ?: ""
        if (safeText.isEmpty()) return
        
        // Clamp rate and pitch to robust, sensible TTS ranges
        val levelRate = rate.coerceIn(0.1f, 3.0f)
        val levelPitch = pitch.coerceIn(0.1f, 3.0f)
        val safeLang = lang?.take(10) ?: ""
        
        WtrAudioControlBridge.setActiveTtsTabId(tabId)
        WtrAudioControlBridge.onSpeakNative?.invoke(safeText, levelRate, levelPitch, safeLang)
    }

    @JavascriptInterface
    fun cancelNative() {
        WtrAudioControlBridge.onCancelNative?.invoke()
    }

    @JavascriptInterface
    fun pauseNative() {
        WtrAudioControlBridge.onPauseNative?.invoke()
    }

    @JavascriptInterface
    fun resumeNative() {
        WtrAudioControlBridge.onResumeNative?.invoke()
    }
}
