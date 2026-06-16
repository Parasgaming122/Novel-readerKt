# Translation System — Google Translate Proxy & Gemini AI

> Two translation systems serve the Novel Reader app: a **Google Translate proxy**
> for automatic page-level translation via URL rewriting, and **Gemini AI** for
> contextual paragraph-level translation of Chinese web novels.

---

## 1. Overview

| System | Method | Scope | Latency | Quality |
|--------|--------|-------|---------|---------|
| Google Translate Proxy | URL rewriting | Entire page | Low (server-side) | Good for general text |
| Gemini AI Translation | API call per chapter | Paragraph-level | Medium (~2-5s) | Excellent for literary content |

### Key Source Files

| File | Role |
|------|------|
| `MainActivity.kt` (package-level `getProxyTranslatedUrl()`) | URL encoding for Google Translate proxy |
| `BrowserAppScreen.kt` | Translation orchestration, anti-loop guard, CSS cleanup |
| `GeminiTranslator.kt` | Gemini AI translation pipeline |
| `WebScripts.kt` | `injectTranslateCssCleanup()` — hides Google Translate chrome |
| `WebsiteSupport.kt` / `WebsiteSupportImpls.kt` | `requiresAutoTranslate` flag per site |
| `WebsiteSupportRegistry.kt` | `findSupport()`, `getAutoTranslateSites()` |

---

## 2. Google Translate Proxy

### URL Rewriting Rules

The proxy converts a normal URL into a Google Translate URL that renders the
translated page directly in the WebView.

**Encoding Algorithm (`getProxyTranslatedUrl()`):**

```
Original:  https://www.timotxt.com/novel/123
                      └── remove "www." ──┘

Host part:  timotxt.com
            │          │
            │    replace "." with "-"
            │    replace "-" with "--"
            ▼            ▼
Result:     timotxt--com

Append:     .translate.goog

Final host: timotxt--com.translate.goog

Add query:  ?_x_tr_sl=auto&_x_tr_tl=en

─────────────────────────────────────────────────
Proxy URL:  https://timotxt--com.translate.goog/novel/123?_x_tr_sl=auto&_x_tr_tl=en
```

**Step-by-step:**
1. Remove `www.` prefix from host
2. Replace all `-` with `--` (double-hyphen encoding)
3. Replace all `.` with `-` (single-hyphen encoding)
4. Append `.translate.goog`
5. Preserve original path and query string
6. Append `_x_tr_sl=auto` (source language: auto-detect) and `_x_tr_tl=en` (target: English)

**Guard:** If the input URL already contains `translate.goog` or `translate.google`,
it is returned unchanged (no double-translation).

### Reverse Decoding

When cleaning a proxy URL back to the original (for display, bookmarks, history):

```
Proxy host: timotxt--com.translate.goog
            │         │
            │    replace "--" with "__HYPHEN__"
            │    replace "-" with "."
            │    replace "__HYPHEN__" with "-"
            ▼         ▼
Original:   timotxt.com
```

This decoding is used in:
- `cleanInputUrl()` for URL bar display
- `findSupport()` for site-specific feature matching
- Tab tracking for same-site detection

### Auto-Translation Flow

```
User navigates to URL (or uses keyword shortcut)
    │
    ▼
BrowserViewModel.cleanInputUrl() checks WebsiteSupportRegistry
    │
    ▼
WebsiteSupport.requiresAutoTranslate == true?
    │ No → proceed normally
    │ Yes ▼
BrowserAppScreen.shouldOverrideUrlLoading()
    │
    ▼
shouldTranslateUrl(url) checks:
    ├── Gemini translation enabled? → skip Google proxy
    ├── Domain matched in auto_translate_domains? → proceed
    ├── Already on translate.goog? → skip
    └── Anti-loop guard passed? → proceed
    │
    ▼
view.loadUrl(getProxyTranslatedUrl(url))
    │
    ▼
Google Translate renders translated page in WebView
    │
    ▼
onPageFinished fires:
    ├── injectTranslateCssCleanup() hides Google Translate UI
    └── TTS extraction runs on translated DOM (after anti-CAPTCHA delay)
```

### Google Translate CSS Cleanup

`injectTranslateCssCleanup()` (in `WebScripts.kt`) hides all Google Translate
UI chrome by injecting a style tag with `!important` overrides:

```css
#gt-nvframe, #goog-gt-tt, .goog-te-banner-frame, .goog-te-gadget,
.skiptranslate, #translate-banner, iframe[id*="translate"],
.goog-tooltip, .goog-te-balloon {
    display: none !important;
    visibility: hidden !important;
    height: 0px !important;
}
body {
    top: 0px !important;
    margin-top: 0px !important;
}
```

Also resets `body` top margin that Google Translate adds for its banner.

### Anti-Loop Guard

Prevents infinite redirect loops when Google Translate shows a CAPTCHA page
or encounters errors.

**Implementation:**
- Tracks translation attempts per cleaned URL in a `MutableMap<String, Int>`
- Records timestamp per attempt in a `MutableMap<String, Long>`
- **Rule:** Blocks after 2 attempts within 10 seconds
- **Log:** `"Translation loop detected for {url}! Skipping Google Translate redirection."`

### Anti-CAPTCHA Delay

When auto-advancing to the next translated chapter:

- **Delay:** 4500ms before loading the next URL
- **Purpose:** Allows Google Translate to fully render the current page and
  avoid triggering CAPTCHA checks from rapid successive requests
- **Condition:** Only on `translate.goog` domains when `antiCaptchaDelay` is enabled
- **Toast:** "Auto-Next: Pausing 4.5s to bypass Google CAPTCHA filters..."
- **SharedPreferences key:** `anti_captcha_delay` (default: `false`)

For TTS extraction on first translated page load, an 800ms delay is applied
after the redirect to allow Google Translate to settle before paragraph
extraction begins.

---

## 3. Gemini AI Translation

### Model

**Google Gemini 2.5 Flash** via the Generative AI SDK (version 0.9.0).

### System Prompt

```
You are a professional literary translator and expert localizer specializing in Chinese web novels (including Xianxia, Wuxia, Xuanhuan, Danmei, LitRPG/System, and historical court intrigue). Your task is to translate each provided Chinese text segment into polished, publication-grade, and deeply immersive English.

You will receive a JSON array of text blocks, each with an 'id' and 'text'. You MUST translate each block and return a JSON array matching the exact structure: [{"id": 0, "text": "Translated English text..."}, ...] without markdown or explanations.

CRITICAL TRANSLATION MANDATES:
1. THOUGHT-FOR-THOUGHT (NoveLM Style):
   - Do NOT translate word-for-word. Capture the visceral energy, poetic flow, and dramatic momentum.
   - Elevate literal raw translation to vivid prose. (e.g., Instead of "Xiao Yan's fighting energy burst like a volcano, strange fire condensed into long sword", translate to: "Xiao Yan's Dou Qi erupted like a dormant volcano, while the Heavenly Flame coalesced in his palm into a crimson greatsword.")
   - Enhance dialogue, internal monologue, and scene descriptions to read like a professionally authored English novel.

2. TRANSLATE IDIOMS & PHRASES (No Chinese Clichés):
   - Convert Chinese machine clichés to elegant natural expressions:
     * "You court death!" -> "You seek your own doom!" or "How dare you!"
     * "Coughing up blood" -> "Spat a mouthful of blood" or "Gasped weakly"
     * "Didn't know whether to laugh or cry" -> "Exasperated yet amused" or "Shook their head in amusement"
     * "Face ashen" -> "Pale as death" or "White as a sheet"
     * "Given an inch, advance ten feet" -> "Given an inch, they will seize a mile"

3. NOVEL TERMINOLOGY & PROPER NOUNS:
   - Character Personal Names: Retain in Chinese Pinyin (e.g., Xiao Yan, Xie Lian, San Lang) with standard spelling and spacing.
   - Sects, Peaks, Domains, Cities, Weapons, and Titles: Translate into their elegant English equivalent meanings rather than raw transliteration (e.g., "Tian Guan" -> "Heavens", "一叶之秋" -> "One Autumn Leaf", "嘉世战队" -> "Team Jiashi").
   - Constant Cultivation Realms & Energy terms: Use highly accurate, consistent terms (e.g., Dou Qi, Qi, Spiritual Energy, Dantian / Core, Foundation Establishment, Nascent Soul, etc.).

4. NUMBER SCALING:
   - Convert large Chinese numeral units (万 = 10,000, 亿 = 100 million) correctly and naturally to Western notation (e.g., "10万" -> "100,000" or "a hundred thousand", "1亿" -> "100,000,000" or "a hundred million").
   
5. FORMATTING & BRACKETS:
   - NEVER use wildcards, bold formatting, or outer conversational wrappers.
   - Preserve all original layout punctuation and brackets such as 【】 and 『』 exactly as in the source.
   
6. OUTPUT VALID JSON ARRAY ONLY:
   - You must return ONLY the raw JSON array. Never wrap in ```json or add conversation. Strict conformance is mandatory.
```

### Generation Configuration

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `temperature` | `0.3f` | Consistent, accurate translations with minimal randomness |
| `responseMimeType` | `"application/json"` | Ensures structured JSON output, no prose wrapping |

### API Key Management

1. **Input:** User enters API key in Settings panel (show/hide toggle for security).
2. **Storage:** Encrypted at rest via `SecurePreferences.getGeminiApiKey(context)` using Android's Keystore-backed Jetpack `EncryptedSharedPreferences`. Auto-migrates from the unencrypted legacy SharedPreferences store cleanly on first run.
3. **Build-time secrets (optional):** `.env` file → Secrets Gradle Plugin → `BuildConfig`
4. **Validation:** Empty key throws `IllegalArgumentException` before API call.

### Translation Pipeline

```
onPageFinished fires for a novel chapter URL
    │
    ▼
1. Pre-flight checks:
    ├── geminiTranslateEnabled == true?
    ├── geminiApiKey is non-empty?
    ├── URL matches a translation-eligible domain?
    └── isNovelChapterUrl(url) == true?
    │ All pass ▼
    │
2. Set isGeminiTranslating = true
   (prevents premature TTS extraction)
    │
    ▼
3. JS extracts all <p> tag text from page:
    │
    │  webView.evaluateJavascript("""
    │      (function() {
    │          let paragraphs = [];
    │          let pTags = document.querySelectorAll('p');
    │          pTags.forEach((p, i) => {
    │              let text = (p.innerText || p.textContent || '').trim();
    │              if (text.length > 5) {
    │                  paragraphs.push(text);
    │                  p.setAttribute('wtr-translation-id', i);
    │              }
    │          });
    │          return JSON.stringify(paragraphs);
    │      })();
    │  """)
    │
    ▼
4. Build JSON input: [{"id": 0, "text": "原始文本"}, ...]
    │
    ▼
5. Call GeminiTranslator.translateParagraphs(paragraphs, apiKey)
    │   on Dispatchers.IO
    │
    ▼
6. API returns JSON array with translated text
    │
    ▼
7. Parse response:
    ├── Strip markdown code fences if present
    ├── Map id → translated text
    └── Fall back to original text for missing translations
    │
    ▼
8. JS injects translated text back into DOM:
    │
    │  paragraphs.forEachIndexed { index, translated ->
    │      webView.evaluateJavascript("""
    │          (function() {
    │              let el = document.querySelector(
    │                  '[wtr-translation-id="${index}"]');
    │              if (el) el.innerText = ${Json.encode(translated)};
    │          })();
    │      """)
    │  }
    │
    ▼
9. Set isGeminiTranslating = false
    │
    ▼
10. If audiobook mode active:
    ├── Wait 400ms for DOM to settle
    └── Trigger runHtmlTextExtractionAndPlay()
        (TTS extraction runs on now-translated DOM)
```

### Error Handling

| Scenario | Behavior |
|----------|----------|
| Missing individual translation | Falls back to original text (no blank paragraph) |
| API returns empty response | Throws `Exception("Received empty response from Gemini API")` |
| Markdown code fences in response | Stripped via substring operations (`` ```json ... ``` ```) |
| Network/API error | Exception propagated, `isGeminiTranslating` reset in `finally` block |
| Empty API key | `IllegalArgumentException` thrown before API call |

### Translation Gating

The `isGeminiTranslating` boolean flag is critical for coordination:

- **Set to `true`** before the API call begins
- **Prevents** `runHtmlTextExtractionAndPlay()` from executing prematurely
- **Reset to `false`** in the `finally` block regardless of success/failure
- If audiobook mode is active, extraction is triggered 400ms after translation completes

---

## 4. Translation-Eligible Sites

Sites with `requiresAutoTranslate = true` in their `WebsiteSupport` implementation:

| Site | Domain(s) | Translation Method |
|------|-----------|-------------------|
| TimoTxt | `timotxt.com`, `timotxt.cn` | Google Translate Proxy or Gemini |
| Novel543 | `novel543.com` | Google Translate Proxy or Gemini |
| Twkan | `twkan.com` | Google Translate Proxy or Gemini |
| NovelHubApp | `novelhubapp.com` | Google Translate Proxy or Gemini |

### Domain Matching

The `auto_translate_domains` SharedPreferences key stores a comma-separated list
of domain strings. Default value is auto-populated from
`WebsiteSupportRegistry.getAutoTranslateSites()`.

Users can add/remove domains in Settings → Language Translation → Auto-Translate
Domains.

### Priority Logic (shouldTranslateUrl)

When determining whether to translate a URL:

1. **Gemini takes priority:** If `geminiTranslateEnabled && geminiApiKey.isNotEmpty()
   && isDomainMatchedForTranslation && isNovelChapterUrl` → Gemini handles it,
   skip Google proxy.
2. **Google Translate proxy:** If domain matches `auto_translate_domains` and
   Gemini is not handling it → redirect to proxy URL.
3. **Already translated:** If URL contains `translate.goog` → skip.
4. **Anti-loop:** If 2+ attempts in 10 seconds → skip.
5. **Not eligible:** Otherwise → load original URL.

---

## 5. Interaction Between Translation and TTS

The translation system and TTS engine must coordinate carefully to avoid
extracting untranslated text or double-triggering.

### Coordination Flow

```
Page loads
    │
    ▼
Gemini enabled AND eligible?
    │ Yes: isGeminiTranslating = true
    │       → Gemini translates paragraphs
    │       → JS injects translated text into DOM
    │       → isGeminiTranslating = false
    │       → Wait 400ms
    │       → TTS extraction runs on translated DOM ✓
    │
    │ No (or Google Translate):
    │       → If auto-translate domain, redirect to translate.goog
    │       → Wait for redirect (poll every 300ms, up to 5 times)
    │       → On translated page: injectTranslateCssCleanup()
    │       → Wait 800ms (anti-CAPTCHA settle time)
    │       → TTS extraction runs on translated DOM ✓
    │
    │ No translation needed:
    │       → TTS extraction runs on original DOM ✓
```

### Language Detection Graceful Handling

The TTS engine's `detectLanguageTag()` handles mixed-language content:

- If a paragraph contains primarily English characters after translation, it gets
  `en-US` tag and uses the selected English voice.
- If Chinese or Cyrillic characters dominate (and the playlist is not primarily
  English), the appropriate language tag is applied.
- `isPlaylistPrimarilyEnglish()` prevents jarring voice switches by sampling
  the first 15 paragraphs — if the majority are English, all paragraphs use
  the English voice even if a few contain foreign characters.

### Anti-CAPTCHA Delay in Audiobook Mode

When auto-advancing chapters in audiobook mode on a translated page:

1. Last paragraph of current chapter finishes
2. `triggerNextChapter()` is called
3. Service checks if current URL is on `translate.goog`
4. If `antiCaptchaDelay` is enabled → wait 4500ms
5. Execute next chapter JS (which triggers a new page load)
6. New page redirects through Google Translate again
7. TTS extraction waits for translation to settle

---

## 6. Settings Reference

All translation-related SharedPreferences keys in `wtr_browser_settings`:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `auto_translate_enabled` | Boolean | `true` | Master toggle for Google Translate proxy |
| `auto_translate_domains` | String | (auto-populated) | Comma-separated domain list for auto-translation |
| `gemini_translate_enabled` | Boolean | `false` | Enable Gemini AI paragraph translation |
| `gemini_api_key` | String | `""` | Gemini API key for AI translation |
| `anti_captcha_delay` | Boolean | `false` | Enable 4.5s delay before next chapter on translated pages |
