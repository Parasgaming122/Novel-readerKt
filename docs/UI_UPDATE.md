# UI/UX Specifications: Novel Reader (Wtr-Lab Browser)

This document is optimized for ingestion by **Google Stitch**, UI/UX design tools, and AI generation models. It provides a highly detailed, structural, and conceptual blueprint of the application's screens, design philosophy, state transitions, and unique hybrid architecture.

---

## 1. Visual Theme & Core Identity

Standard web browsers are designed for high-density, general-purpose information delivery, often feeling clinical, cluttered, and distracting. **Novel Reader (Wtr-Lab Browser)** is the antithesis of a standard browser: it is a highly specialized, immersive **"Web Novel Reading Theater & Ambient Audiobook Portal."**

Its design language must prioritize **cognitive comfort, endless vertical reading endurance, and sleek, entertainment-focused media control.**

### Design Guidelines:
*   **Color Palette (Aesthetic "Cosmic Slate")**:
    *   **Primary Background**: Deep, comforting charcoal/slate (`#12161A`) instead of pure black to reduce AMOLED glaring and white text harshness.
    *   **Surface Color**: Textured, ambient slate-gray (`#1A2026`) for cards, sheets, and overlays.
    *   **Primary Accent**: Warm celestial amber/gold (`#D97706`) or glowing electric teal (`#0EA5E9`) to draw attention to interactive controls, play/pause switches, and reading progress highlights.
    *   **Text Hierarchy**: High-contrast, but muted. Read text should never be neon white. Use a soft off-white/cream for readability, and a secondary medium gray for minor indicators.
*   **Typography**:
    *   **Headers & Interactive Controls**: Clean, high-legibility sans-serif displaying active tracking and comfortable line heights.
    *   **Reading Content / Intercepted Nodes**: Highly elegant serif pairings designed for long-term reading comfort (e.g., Georgia-style or custom web fonts), utilizing generous margins and fluid letter spacing.
*   **Ergonomics**:
    *   Zero cluttered address bars or heavy navigation headers.
    *   All secondary menus are transient and exist as floating overlays, slide-out drawers, or low-profile sheets.
    *   Large, safe touch targets (minimum **48dp** height and width) to accommodate one-handed vertical scrolling and tapping.

---

## 2. Comprehensive Screen Directory

### Screen 1: The Portal Dashboard (Dynamic New Tab Page)
This acts as the launcher and entrance hub. Instead of a blank screen or search bar, it resembles an premium entertainment shelf.

*   **Header Section**: 
    *   Comfortable greeting paired with a real-time system clock (UTC indicator).
    *   A sleek, low-profile search bar containing search/URL input with custom trailing and leading symbols. Entering a query automatically routes through a cleaned Google/DuckDuckGo search or resolves clean domains.
*   **The Supported Website Shelf**:
    *   A grid of high-contrast, designed cards showing supported source websites (Wtr-Lab, WebNovel, NovelHall, FanMTL, etc.).
    *   Each card features a stylized custom vector icon, domain badge, and indication of whether "Auto-Translate" is active for that specific source.
*   **Recent Reading Progress Deck**:
    *   Direct access to the most recently updated bookmarked novel chapters. 
    *   Presented as visual cards with a minimal progress outline, showing book title, current chapter title, and domain badge.

```
+----------------------------------------------------+
|  [Logo]  Novel Reader                   10:30 UTC  |
+----------------------------------------------------+
|  [🔎 Search or enter URL                         ] |
+----------------------------------------------------+
|  SUPPORTED NOVEL SOURCES                           |
|  +--------------+  +--------------+  +----------+  |
|  | [W] Wtr-Lab  |  | [W] WebNovel |  | + More   |  |
|  +--------------+  +--------------+  +----------+  |
+----------------------------------------------------+
|  RECENT READS                                      |
|  +-----------------------------------------------+ |
|  | Cover |  My Xianxia Cultivation Novel         | |
|  | Image |  Chapter 452: Formation Formation     | |
|  |       |  [==== Progress: 64% ====]            | |
|  +-----------------------------------------------+ |
+----------------------------------------------------+
```

---

### Screen 2: The Immersive Reading Engine & HUD (Main Web View)
This is the primary user habitat. It is a highly modified, secure WebView wrapper that strips away web clutter and injects local state.

*   **Clean-Canvas WebView**:
    *   A borderless, full-screen viewport. Webpage banners, site headers, sidebars, and ads are aggressively blocked by the internal ad-blocker.
    *   **CSS Force-Dark Override**: Automatically reformats bright, unoptimized websites into the soothing dark "Cosmic Slate" reading layout.
*   **Liquid Scroll Focus Highlight**:
    *   When the Audiobook engine (TTS) is active, the current speaking paragraph is softly outlined or glowing with a subtle background highlight.
    *   The page automatically scroll-aligns smoothly, keeping the highlighted reading segment perfectly centered in the viewport.
*   **The Floating Quick-Action Bubble (FAB)**:
    *   A low-profile floating bubble near the right margin.
    *   Tapping it quickly opens the expanded **Settings & Audio Dashboard** overlay to let the user adjust speed, voices, or toggle features without leaving their scroll position.

---

### Screen 3: The Persistent Audio Control Shelf (Bottom Sheet Media Bar)
When background listening or foreground reading is activated, this low-profile, unified media controller remains docked at the bottom of the viewport.

*   **Mini-Player State**:
    *   **Left Section**: Title of the novel and the active chapter being read, panning contextually as the text advances.
    *   **Center Section**: High-response play/pause button (Celestial Gold, with standard ripple and state toggles), preceded and succeeded by previous/next chapter arrows.
    *   **Right Section**: Active playback speed indicator (e.g., `1.25x`) and a visual indicator representing the active Speech engine.
*   **Expanded Audio Deck Drawer**:
    *   Dragging the shelf upward expands it into a beautiful, dedicated fullscreen audio overlay (similar to YouTube Music or Spotify).
    *   Features large pitch/speed sliders, a drop-down menu of high-grade voices/accents, dynamic track visualizers, and a continuous list structure of parsed reading paragraphs that are queued for speech generation.

```
+----------------------------------------------------+
| Chapter 452: Formation Formation                   |
| [<] Back  [ II ] Pause  [>] Next   (1.20x Speed)   |
+----------------------------------------------------+
```

---

### Screen 4: The Tabs Panel (Navigation Terminal)
A custom, multi-tab grid that moves away from plain lists to focus on organized multitasking.

*   **Grid Cards**:
    *   Each active Web tab is displayed as a beautifully bordered grid card.
    *   Cards display a styled page title, the domain badge, a close button, and a thumbnail representing the active state.
*   **Active Tab & Audio Badges**:
    *   The active reading tab is highlighted with a gold/teal border.
    *   If a background tab is currently holding the Speechengine lock, it features a glowing animated "Audio" equalizer wave badge, signalling that it is playing audio in the background.

---

### Screen 5: The Bookmarks Shelf (Virtual Library Rack)
This is not a list of raw URLs—it is a designed digital bookshelves system.

*   **Itemized Book Cards**:
    *   **Metadata Scraper**: The app parses active URLs and extracts cover image paths, author information, chapter indices, and site domains.
    *   **The Progress Track**: Below each book card is a horizontal progress slider indicating how far into the novel the reader has advanced.
    *   **Last Chapter visited**: Shows exact details of the last read page (e.g., *Chapter 12: True Qi Gathering*), allowing one-click return to reading.

---

### Screen 6: Settings Console & Technical Diagnostics (Admin Desk)
For tailoring options and viewing operational logs.

*   **Controls Deck**:
    *   Comprehensive pitch, dynamic volume accentuation, and voice selectors.
    *   Gemini API localization configurations (enabling translation, key inputs, auto-translate domain rules, anti-CAPTCHA delays).
*   **Diagnostic Terminal Console**:
    *   An on-screen live terminal showing the latest 100 system events (page loads, JS hook interactions, TTS engine swaps, API callbacks, DOM extraction logs). Includes an export button to write diagnostic `.txt` files securely.
    *   Backup management: Import/export mechanisms using secure SAF launchers to back up history and bookmarks into keystore-encrypted payloads.

---

## 3. The Core Under-the-Hood Architectural Bridges

These invisible technical blocks must be factored into any UI/UX or animations, as they drive the major state transitions of the app:

### System A: The Active Tab URL Gatekeeper (Anti-Hijack Bridge)
*   **The Problem**: Multiple web views loading in the background must not overwrite the main layout's address bar or active HUD title.
*   **The Design Solution**: The active tab maintains unique, immutable tab IDs. The top address bar and TTS engine only listen to events that pass a triple-gate verification (Triggering Tab ID match, Active Viewport ID Match, and physical WebView URL match).

### System B: The Gemini AI Literary Localization Pipeline (NoveLM Engine)
*   **The Concept**: This app does NOT use dry literal translators. It features an advanced translation pipeline using Gemini 2.5 Flash to localized Chinese Webnovel genres (Wuxia, Xianxia, Danmei).
*   **The Translation Path**:
    1.  The app extracts `<p>` elements and `.wtr-line-segment` spans from the active DOM.
    2.  If active, translation is locked, a beautiful circular loading spinner appears, and paragraphs are grouped into index arrays.
    3.  A JSON I/O request is sent to Gemini, formatted specifically for literary contextualization (reworking idioms, keeping names in Pinyin while elegantly parsing weapon names, sects, numeric scaling like 万 or 亿, and preserving unique punctuation layouts like bracket codes `【】` and `『』` ).
    4.  The translated JSON is returned and gracefully injected directly back into the live document DOM, smoothly replacing the source Chinese blocks before the TTS engine reads them.

### System C: The JS speechSynthesis Ad-Blocker Decoy (WtrBridge Protection)
*   **The Threat**: High-end novel hosting domains actively flag apps as bots or ad-blockers by scanning if `window.speechSynthesis` is hooked, disabled, or tampered with.
*   **The UX Solution**: Even during native android fallback speech, the client injects a decoy `WtrBridge` mimicking browser standard speech bindings. The UI must always cleanly represent audio state while hiding this background security handshake.

### System D: Safe Cryptographic Key Retention & Sandbox Hardening
*   **Privilege Level**: Any user-entered Gemini API key must be locked away using **Keystore-backed EncryptedSharedPreferences** operating in Android's sandbox.
*   **Sandbox Security**: `android:allowBackup="false"` is set, preventing local ADB extraction, backup compromises, or hardware cloning.

---

## 4. Operational State Flow Diagrams

```
[Портал Dashboard] ---> Tap Novel Card / Enter Query ---> [Immersive Reader HUD]
                                                                |
                                             Website Support Registry Identifies Domain
                                                                |
                                    +---------------------------+---------------------------+
                                    | Autotranslate Target (JP/CN) | Standard Novel Source  |
                                    +---------------------------+---------------------------+
                                                |                                   |
                                                |                     Loads "Cosmic Dark" CSS Theme
                                                V                                   |
                                  +-------------+-------------+                     |
                                  | Gemini Active (API Key)   |                     |
                                  +-------------+-------------+                     V
                                    /                       \             Extracts Paragraph DOM Nodes
                                   Y                         N                      |
                     Spins Loader (UI Gate)             Redirects to                V
                                   |              Google Translate Proxy   Loads Native Voice Synth
                        Sends Paragraph Arrays              |                       |
                     Localized Literary Return              |                       V
                                   |                        V             Smooth Center Scroll Focusing
                                   +------------+-----------+                       |
                                                |                                   V
                                                +----------------------------> [Audio Playback Status]
```

This highly cohesive structural model ensures that **Google Stitch** can synthesize a fluid, gorgeous, and consistent Android application interface, taking it away from standard boring browsers and transforming it into a luxurious **Immersion-First Reading and Audiobook Sanctuary**.
