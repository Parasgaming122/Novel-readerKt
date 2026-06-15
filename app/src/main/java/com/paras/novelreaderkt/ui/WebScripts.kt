package com.paras.novelreaderkt.ui

import android.webkit.WebView

fun injectForceDarkCss(webView: WebView) {
    val css = """
        html, body {
            background-color: #121212 !important;
            color: #f1f1f1 !important;
        }
        h1, h2, h3, h4, h5, h6, p, span, a, li, button, input {
            color: #f1f1f1 !important;
            background-color: transparent !important;
        }
        main, article, section, div, header, footer {
            background-color: #1a1a1a !important;
        }
    """.trimIndent()
    val js = "var style = document.createElement('style'); style.type = 'text/css'; style.innerHTML = '$css'; document.head.appendChild(style);"
    webView.evaluateJavascript(js, null)
}

fun injectTranslateCssCleanup(webView: WebView) {
    val jsCode = """
        (function() {
            var style = document.getElementById('wtr-translate-cleanup-css') || document.createElement('style');
            style.id = 'wtr-translate-cleanup-css';
            style.innerHTML = `
                #gt-nvframe, #goog-gt-tt, .goog-te-banner-frame, .goog-te-gadget, .skiptranslate, #translate-banner, iframe[id*="translate"], .goog-tooltip, .goog-te-balloon {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0px !important;
                }
                body {
                    top: 0px !important;
                    margin-top: 0px !important;
                }
            `;
            if (!document.head.contains(style)) {
                document.head.appendChild(style);
            }
        })();
    """.trimIndent()
    webView.evaluateJavascript(jsCode, null)
}

fun injectTtsBridgeScript(webView: WebView) {
    val jsScript = """
        (function() {
            if (window.WtrTtsPolyfilled) return;
            window.WtrTtsPolyfilled = true;
            console.log("Wtr-Lab Custom TTS Polyfill Injecting...");

            class MockVoice {
                constructor(name, lang, localService, defaultVal) {
                    this.name = name;
                    this.lang = lang;
                    this.voiceURI = name;
                    this.localService = localService;
                    this.default = defaultVal;
                }
            }

            const mockVoices = [
                new MockVoice("Google US English", "en-US", true, true),
                new MockVoice("Google UK English Female", "en-GB", true, false),
                new MockVoice("Google UK English Male", "en-GB", true, false),
                new MockVoice("Google Vietnamese Female", "vi-VN", true, false),
                new MockVoice("Google Vietnamese Male", "vi-VN", true, false),
                new MockVoice("Google Chinese Female", "zh-CN", true, false),
                new MockVoice("Google Spanish Female", "es-ES", true, false)
            ];

            let activeUtterance = null;
            let lastSpeakTime = 0;
            let isUserPlayingMode = false;

            class MockSpeechSynthesisUtterance extends EventTarget {
                constructor(text) {
                    super();
                    this._text = text || "";
                    this._lang = "en-US";
                    this._voice = mockVoices[0];
                    this._volume = 1.0;
                    this._rate = 1.0;
                    this._pitch = 1.0;
                    this.onstart = null;
                    this.onend = null;
                    this.onerror = null;
                    this.onpause = null;
                    this.onresume = null;
                    this.onboundary = null;
                }

                get text() { return this._text; }
                set text(v) { this._text = "" + v; }

                get lang() { return this._lang; }
                set lang(v) { this._lang = "" + v; }

                get voice() { return this._voice; }
                set voice(v) { this._voice = v; }

                get volume() { return this._volume; }
                set volume(v) { this._volume = parseFloat(v) || 1.0; }

                get rate() { return this._rate; }
                set rate(v) { this._rate = parseFloat(v) || 1.0; }

                get pitch() { return this._pitch; }
                set pitch(v) { this._pitch = parseFloat(v) || 1.0; }
            }

            try {
                Object.defineProperty(window, 'SpeechSynthesisUtterance', {
                    value: MockSpeechSynthesisUtterance,
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
            } catch(e) { window.SpeechSynthesisUtterance = MockSpeechSynthesisUtterance; }

            class MockSpeechSynthesis extends EventTarget {
                constructor() {
                    super();
                    this.speaking = false;
                    this.paused = false;
                    this.pending = false;
                    this._onvoiceschanged = null;
                }

                get onvoiceschanged() {
                    return this._onvoiceschanged;
                }

                set onvoiceschanged(handler) {
                    this._onvoiceschanged = handler;
                    if (typeof handler === 'function') {
                        setTimeout(() => {
                            try {
                                handler.call(this, new Event('voiceschanged'));
                            } catch(e) {}
                        }, 50);
                    }
                }

                getVoices() {
                    return mockVoices;
                }

                speak(utterance) {
                    if (!utterance) return;
                    console.log("Wtr-Lab speak requested size: " + (utterance.text ? utterance.text.length : 0));
                    activeUtterance = utterance;
                    this.speaking = true;
                    this.paused = false;
                    this.pending = false;
                    isUserPlayingMode = true;
                    lastSpeakTime = Date.now();

                    // Direct to Android custom interface
                    if (window.WtrBridge && window.WtrBridge.speakNative) {
                        let rateVal = parseFloat(utterance.rate);
                        if (isNaN(rateVal) || rateVal <= 0) rateVal = 1.0;
                        let pitchVal = parseFloat(utterance.pitch);
                        if (isNaN(pitchVal) || pitchVal <= 0) pitchVal = 1.0;
                        let langVal = utterance.lang || "en-US";
                        window.WtrBridge.speakNative(utterance.text || "", rateVal, pitchVal, langVal);
                    }
                }

                cancel() {
                    console.log("Wtr-Lab cancel requested");
                    this.speaking = false;
                    this.paused = false;
                    this.pending = false;
                    isUserPlayingMode = false;
                    activeUtterance = null;
                    if (window.WtrBridge && window.WtrBridge.cancelNative) {
                        window.WtrBridge.cancelNative();
                    }
                }

                pause() {
                    console.log("Wtr-Lab pause requested");
                    this.paused = true;
                    isUserPlayingMode = false;
                    if (window.WtrBridge && window.WtrBridge.pauseNative) {
                        window.WtrBridge.pauseNative();
                    }
                }

                resume() {
                    console.log("Wtr-Lab resume requested");
                    this.paused = false;
                    isUserPlayingMode = true;
                    lastSpeakTime = Date.now();
                    if (window.WtrBridge && window.WtrBridge.resumeNative) {
                        window.WtrBridge.resumeNative();
                    }
                }
            }

            const synthInstance = new MockSpeechSynthesis();

            // Override window.speechSynthesis
            try {
                Object.defineProperty(window, 'speechSynthesis', {
                    value: synthInstance,
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
            } catch(e) { window.speechSynthesis = synthInstance; }

            try {
                Object.defineProperty(window, 'SpeechSynthesis', {
                    value: MockSpeechSynthesis,
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
            } catch(e) { window.SpeechSynthesis = MockSpeechSynthesis; }

            try {
                Object.defineProperty(window, 'SpeechSynthesisVoice', {
                    value: MockVoice,
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
            } catch(e) { window.SpeechSynthesisVoice = MockVoice; }

            // Webkit prefixes routing too
            try {
                window.webkitSpeechSynthesis = synthInstance;
                window.webkitSpeechSynthesisUtterance = MockSpeechSynthesisUtterance;
            } catch(e) {}

            // Backwards-compatible event triggers for SpeechSynthesisUtterance
            window.WtrTtsTriggerEvent = function(event, charIndex) {
                if (!activeUtterance) return;
                
                let eType = event.toLowerCase();
                if (eType === 'start') {
                    synthInstance.speaking = true;
                    synthInstance.paused = false;
                } else if (eType === 'end') {
                    synthInstance.speaking = false;
                    synthInstance.paused = false;
                } else if (eType === 'pause') {
                    synthInstance.paused = true;
                } else if (eType === 'resume') {
                    synthInstance.paused = false;
                    synthInstance.speaking = true;
                } else if (eType === 'error') {
                    synthInstance.speaking = false;
                    synthInstance.paused = false;
                }

                let customEvent = new Event(eType);
                customEvent.charIndex = charIndex || 0;
                customEvent.elapsedTime = 0;
                customEvent.name = eType === 'boundary' ? 'word' : '';
                customEvent.utterance = activeUtterance;

                // Fire callback attribute if defined (e.g. onstart, onend, etc.)
                let propHandler = activeUtterance["on" + eType];
                if (typeof propHandler === 'function') {
                    try { propHandler(customEvent); } catch(err) { console.error(err); }
                }

                // Fire event via standard EventTarget
                try {
                    activeUtterance.dispatchEvent(customEvent);
                } catch(err) {
                    console.error("dispatchEvent failed: ", err);
                }

                if (eType === 'end') {
                    activeUtterance = null;
                }
            };

            // Hook HTML5 audio elements in case of fallback
            const originalPlay = HTMLAudioElement.prototype.play;
            HTMLAudioElement.prototype.play = function() {
                let header = document.querySelector('h1, h2, .chapter-title, .title') || {};
                let text = header.innerText || document.title || "Wtr-Lab Novel";
                window.WtrBridge.postPlaybackState(true, text, "Playing audio stream...");
                return originalPlay.apply(this, arguments);
            };

            const originalPause = HTMLAudioElement.prototype.pause;
            HTMLAudioElement.prototype.pause = function() {
                window.WtrBridge.postPlaybackState(false, null, "Playback paused");
                return originalPause.apply(this, arguments);
            };

            // Periodically check if speechSynthesis changed/reset on this window layout
            setInterval(() => {
                if (window.speechSynthesis !== synthInstance) {
                    console.log("Re-applying speechSynthesis polyfill...");
                    Object.defineProperty(window, 'speechSynthesis', {
                        value: synthInstance,
                        writable: true,
                        configurable: true,
                        enumerable: true
                    });
                }
            }, 1000);

            // Fire voiceschanged so website knows speech is ready to load
            setTimeout(() => {
                let initialEvent = new Event('voiceschanged');
                try {
                    synthInstance.dispatchEvent(initialEvent);
                    if (typeof synthInstance.onvoiceschanged === 'function') {
                        synthInstance.onvoiceschanged(initialEvent);
                    }
                } catch(err) {
                    console.error("voiceschanged event error: ", err);
                }
            }, 500);

            function findParagraphProgress() {
                // 1. Prioritize looking inside elements containing "tts" or "player" class/ID
                let ttsQueryMatches = document.querySelectorAll('[class*="tts" i], [id*="tts" i], [class*="player" i], [id*="player" i], .tts-bar, #tts-bar, .tts-hud');
                for (let container of ttsQueryMatches) {
                    let selectors = ['.tts-progress', '.paragraph-index', '.paragraph-count', '.progress', '.text', 'span', 'div', 'p', 'font'];
                    for (let sel of selectors) {
                        try {
                            let els = container.querySelectorAll(sel);
                            for (let el of els) {
                                let txt = (el.innerText || el.textContent || "").trim();
                                if (/chapter|chap|vol|ch\b/i.test(txt) || txt.includes("章")) {
                                    continue;
                                }
                                let match = txt.match(/\b\d+\s*([\/]|of|\|)\s*\d+\b/);
                                if (match) {
                                    return match[0].replace(/\s*of\s*/i, '/').replace(/\s*\|\s*/i, '/');
                                }
                            }
                        } catch(e) {}
                    }
                }

                // 2. Fallback to specific paragraph/TTS elements page-wide
                let selectors = [
                    '.tts-progress', '.paragraph-index', '.paragraph-count', 
                    '.player-progress', '.progress-text', '.tts-player-progress',
                    '.tts-status', '.speech-progress', '[class*="progress" i] [class*="index" i]',
                    '.cur-p'
                ];
                for (let sel of selectors) {
                    try {
                        let els = document.querySelectorAll(sel);
                        for (let el of els) {
                            let txt = (el.innerText || el.textContent || "").trim();
                            if (/chapter|chap|vol|ch\b/i.test(txt) || txt.includes("章")) {
                                continue;
                            }
                            let match = txt.match(/\b\d+\s*([\/]|of|\|)\s*\d+\b/);
                            if (match) {
                                return match[0].replace(/\s*of\s*/i, '/').replace(/\s*\|\s*/i, '/');
                            }
                        }
                    } catch(e) {}
                }
                return null;
            }

            // Setup periodic syncing & states polling
            setInterval(() => {
                let isPlaying = false;
                let title = document.title || "Wtr-Lab Novel";
                let header = document.querySelector('h1, h2, .chapter-title, .title') || {};
                let textVal = header.innerText || title;

                if (isUserPlayingMode) {
                    if (synthInstance.speaking && !synthInstance.paused) {
                        isPlaying = true;
                    } else if (Date.now() - lastSpeakTime < 3500) {
                        // Smoothly treat brief transition gaps as playing to prevent stutter
                        isPlaying = true;
                    }
                }

                // Check audio element status
                let audios = document.querySelectorAll('audio');
                for (let a of audios) {
                    if (!a.paused) {
                        isPlaying = true;
                    }
                }

                // Match specific playing class states on page
                let playingClass = document.querySelector('.audio-player-button-active, .tts-playing, .is-playing, [class*="playing"]');
                if (playingClass) {
                    isPlaying = true;
                }

                let progVal = findParagraphProgress();
                let subtitleVal = isPlaying ? (progVal ? "Paragraph " + progVal : "Reading novel text...") : "Paused";
                let syncedUrl = window.location.href;
                try {
                    // Unique tracking for SPA-style reader sites that don't change URL on chapter swap (NovelHubApp)
                    if (syncedUrl.includes("novelhubapp.com/reader") || syncedUrl.includes("novelhubapp.com/novel") || syncedUrl.includes("novelhub.net/novel")) {
                        let chapterIndicator = "";
                        // Try to find chapter ID or number in standard containers
                        let chEl = document.querySelector('.chapter-title, .title, .current-chapter, h1, h2');
                        if (chEl) {
                            chapterIndicator = chEl.innerText.trim();
                        } else {
                            chapterIndicator = document.title;
                        }
                        
                        if (chapterIndicator) {
                            // Extract numbers or specific strings to make hash unique
                            let cleanIndicator = chapterIndicator.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase();
                            if (cleanIndicator.length > 0) {
                                if (!syncedUrl.includes("#")) {
                                    syncedUrl += "#wtr=" + cleanIndicator;
                                } else if (!syncedUrl.includes("wtr=")) {
                                    syncedUrl += "&wtr=" + cleanIndicator;
                                } else {
                                    // Update existing wtr hash
                                    let base = syncedUrl.split("wtr=")[0];
                                    syncedUrl = base + "wtr=" + cleanIndicator;
                                }
                            }
                        }
                    }
                } catch(e) {}

                window.WtrBridge.syncPollState(isPlaying, textVal, subtitleVal);
                if (window.WtrBridge && window.WtrBridge.syncUrl) {
                    window.WtrBridge.syncUrl(syncedUrl, document.title || "Wtr-Lab Novel");
                }
                
                if (window.WtrBridge && window.WtrBridge.syncMetadata) {
                    let cover = '';
                    let meta = document.querySelector('meta[property="og:image"]');
                    if (meta && meta.content) cover = meta.content;
                    if (!cover) {
                        let linkSrc = document.querySelector('link[rel="image_src"]');
                        if (linkSrc && linkSrc.href) cover = linkSrc.href;
                    }
                    if (!cover) {
                        let img = document.querySelector('.book-cover img, .cover img, .novel-cover img, img.cover, .cover-box img, .pic img, img.book-img');
                        if (img && img.src) cover = img.src;
                    }

                    let dynTitle = document.title || "";
                    let h1 = document.querySelector('h1.title, h1.book-title, .novel-title');
                    if (h1 && h1.innerText && h1.innerText.length > 0) {
                        dynTitle = h1.innerText.trim();
                    }

                    window.WtrBridge.syncMetadata(document.title || "", dynTitle, cover);
                }
            }, 500);
        })();
    """.trimIndent()
    webView.evaluateJavascript(jsScript, null)
}
