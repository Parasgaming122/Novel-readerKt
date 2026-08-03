# Translation System — Unified In-Page DOM Translation

> The translation system translates foreign novel chapters **in-place** by extracting
> paragraph text from the DOM using the NovelExtractor JS engine, calling a translation
> API, and injecting the translated text back. Three engines are available:
>
> | Engine | Method | API Key | Quality | Speed |
> |--------|--------|---------|---------|-------|
> | **Google Translate** | Unofficial web endpoint (translate_a/single) | None (free, unlimited) | Excellent | Fast |
> | **MyMemory** | REST API (per paragraph) | None (free tier: 5000 chars/day) | Good | Fast |
> | **Gemini AI** | Google Generative AI SDK | Required (gemini-2.5-flash) | Excellent (literary) | Medium (2-5s) |

---

## 1. Architecture Overview

### Key Source Files

| File | Role |
|------|------|
| `TranslationEngine.kt` | `TranslationEngine` enum, `GoogleTranslateWebTranslator`, `MyMemoryTranslator`, `UnifiedTranslator`, `TranslationCache` |
| `GoogleTranslateWebTranslator.kt` | Google Translate web endpoint translator (default engine) |
| `GeminiTranslator.kt` | Gemini AI translation pipeline (kept for Gemini engine) |
| `BrowserAppScreen.kt` | Translation orchestration, NovelExtractor JS integration, DOM injection |
| `WebScripts.kt` | `injectNovelExtractorScript()`, `extractParagraphsWithNovelExtractor()` |
| `assets/novel_extractor.js` | Full novel_extractor.py port — heuristic chapter text extraction |
| `SettingsPanel.kt` | Translation engine selector UI, Gemini API key input |
| `WebsiteSupport.kt` | `requiresAutoTranslate` flag per site |
| `WebsiteSupportRegistry.kt` | `findSupport()`, `getAutoTranslateSites()` |

---

## 2. Translation Engines

### 2A. MyMemory (Default, Free)

- **API**: `https://api.mymemory.translated.net/get`
- **No API key required** for anonymous usage (5000 chars/day limit)
- Supports batch translation (up to 3 paragraphs per request)
- Auto-detects source language
- Target: English
- Graceful fallback to original text on rate limits or errors

### 2B. Gemini AI (Optional, High Quality)

- **Model**: Google Gemini 2.5 Flash
- **Requires**: User-provided API key (stored encrypted via `SecurePreferences`)
- Same literary translation system as before (specialized system prompt for Chinese web novels)
- Temperature: 0.3, response format: JSON

### Engine Selection

Users pick their engine in **Settings → Language Translation → Translation Engine**.
The choice is persisted in `SharedPreferences` under key `translation_engine`.
Default: `mymemory`.

---

## 3. Translation Pipeline

```
onPageFinished fires for a novel chapter URL
    │
    ▼
1. Pre-flight checks:
    ├── autoTranslateEnabled == true?
    ├── URL matches a translation-eligible domain?
    └── isNovelChapterUrl(url) == true?
    │ All pass ▼
    │
2. Set isGeminiTranslating = true
   (prevents premature TTS extraction)
    │
    ▼
3. JS extracts all <p> tag text from page:
    │  - Uses WebsiteSupport container/paragraph selectors
    │  - Handles <br>-based sites (Twkan) with special DOM prep
    │  - Assigns wtr-translation-id attribute to each paragraph
    │  - Filters junk (promo keywords, ads, short text <5 chars)
    │
    ▼
4. Call UnifiedTranslator.translate(paragraphs, engine, apiKey)
    │   - Routes to MyMemoryTranslator or GeminiTranslator
    │   - Checks TranslationCache first (hash-based, 2000 entry max)
    │   - Runs on Dispatchers.IO
    │
    ▼
5. API returns translated text per paragraph
    │
    ▼
6. JS injects translated text back into DOM via wtr-translation-id
    │
    ▼
7. Set isGeminiTranslating = false
    │
    ▼
8. If audiobook mode active:
    ├── Wait 400ms for DOM to settle
    └── Trigger TTS extraction on now-translated DOM
```

---

## 4. Translation Cache

- **Location**: In-memory `TranslationCache` singleton (not persisted across restarts)
- **Key**: Hash of first 200 chars of source text + engine name
- **Max size**: 2000 entries (evicts oldest 25% when full)
- **Purpose**: Avoids re-translating identical paragraphs when navigating back/forward

---

## 5. Translation-Eligible Sites

Sites with `requiresAutoTranslate = true` in their `WebsiteSupport` implementation:

| Site | Domain(s) |
|------|-----------|
| TimoTxt | `timotxt.com`, `timotxt.cn` |
| Novel543 | `novel543.com` |
| Twkan | `twkan.com`, `twkan.co`, `ttkan.co`, `ttkan.com` |
| NovelHubApp | `novelhubapp.com` |

Users can add/remove domains in Settings → Auto-Translate Domain Keywords.

---

## 6. Settings Reference

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `auto_translate_enabled` | Boolean | `true` | Master toggle for translation |
| `auto_translate_domains` | String | (auto-populated) | Comma-separated domain list |
| `translation_engine` | String | `"mymemory"` | Selected translation engine key |
| `gemini_api_key` | String | `""` | Gemini API key (encrypted storage) |

---

## 7. Migration from Google Translate Proxy

The old Google Translate proxy (`translate.goog` URL rewriting) has been replaced.
Key changes:

- **Removed**: `getProxyTranslatedUrl()`, `shouldTranslateUrl()`, anti-loop guard,
  `injectTranslateCssCleanup()`, Anti-CAPTCHA delay
- **Replaced**: URL proxy redirect → in-page DOM translation
- **Added**: `TranslationEngine.kt` with MyMemory backend, `TranslationCache`,
  engine selector in Settings
- **Kept**: `GeminiTranslator.kt` (now accessed via `UnifiedTranslator`),
  `isSameBaseOrTranslatedUrl()` (for backward compat with old bookmarks/history)
