# Design Brief & Interface Blueprint: Novel Reader (Wtr-Lab Browser)

This document is compiled as a comprehensive structural and functional blueprint of the **Novel Reader (Wtr-Lab Browser)** application. It is designed to be fed directly into visual and UX compilers (e.g., Google Stitch) to generate state-of-the-art visual schemes, wireframes, and design components.

---

## 1. Product Identity & Core User Value
**Novel Reader (Wtr-Lab Browser)** is an offline-capable, highly-specialized Android web browser built from the ground up to scrape, translate, cache, and synthesize online web novel chapters.

*   **Primary Value Hook**: Turn any online web novel index (especially Chinese aggregator sites) into a polished, distraction-free native electronic book with fully automated background **Text-To-Speech (TTS)** audio playback, automatic smooth page-by-page scrolling, and seamless localization (Google Translate proxy or Gemini translation engine).
*   **Operating Thesis**: Web Novel reading is inherently marred by:
    1.  Aggressive ad scripts, tracker counters, popups, and broken styles.
    2.  Hanging connection pools caused by defunct analytics packages (e.g., CNZZ, Umeng).
    3.  A lack of high-fidelity Text-To-Speech that continues writing and playing when the screen is locked or the application is running in the background.
    **Wtr-Lab Browser** strips these distractions, prepares chapters for native TTS, and provides an immersive media player dashboard over a standard WebView.

---

## 2. Technical Stack Map

*   **Platform**: Android Native (Kotlin 2.2.10, Jetpack Compose, Material Design 3).
*   **Database**: Room Database (bookmarks, tabs, history) with v4 migrations and encrypted streaming JSON backups.
*   **Network / Scraper API**: OkHttp 4 + Retrofit 2 + Moshi for REST interactions; custom CSS-injector patterns + regex registers for in-line scraping.
*   **Translation Engines**:
    1.  **Server-Side Proxy**: Google Translate proxy routing.
    2.  **API-Driven isolated translation**: Gemini 2.5 Flash (`temperature = 0.3`) translating chapters into JSON paragraphs recursively without losing the underlying original DOM reference.
*   **Visual Delivery Engine**: Hardware-accelerated Android System WebView with custom JS Bridges, Asset caching filters, and CSS override injectors.

---

## 3. High-Fidelity App Screen Mapping

The system utilizes a central screen selector structure consisting of 5 distinct visual overlays or sliding sheets, coordinated via state flags.

### Screen A: Central Browser Dashboard (`BrowserAppScreen.kt`)
This is the primary runtime layout. It features an adaptive, dual-layout structure combining a fully-featured Web View with a Floating Audio Control Dashboard / Media Shelf.

#### UI Elements & Layout Architecture:
1.  **Title Overlay & Address Bar (Top Bar)**:
    *   *Search & Go*: A clean, filled-style custom text input field supporting search queries (automatically routed to search engines if not a URL) and URL entry.
    *   *Back & Forward*: Micro-inline navigation arrows specific to the active tab’s back-stack.
    *   *Quick Actions*: Desktop-mode toggle icon, bookmark-active filled star, and settings entry cog.
2.  **The Canvas Area**:
    *   An isolated system `WebView` matching parent constraints, optimized with custom viewport scales and hardware-layer acceleration.
    *   Auto-Focus paragraph highlighting layer: A CSS injection that paints a soft background or text glow over the *currently active, playing paragraph* using `scrollIntoView({block: "center", behavior: "smooth"})`.
3.  **Floating Media Overlay Shelf (Bottom Shelf)**:
    *   A floating card designed with a modern glassmorphic look (rounded corners, subtle border stroke, high-contrast dark palette).
    *   *Progress & Title*: Displays parsed Book Title and Chapter Name as scrolling ticker text.
    *   *Playback Engine Icons*: Previous/Next Chapter skips, play/pause toggles, and speed sliding indicators.
    *   *Audio-mode indicator*: Displays a subtle animated sound wave if background audiobook mode is active.

---

### Screen B: The Multi-Tab Registry Grid (`TabsPanel.kt`)
Provides visual sandbox sandboxing for concurrent novel sessions.

#### UI Elements & Layout Architecture:
1.  **Primary Action Bar**:
    *   "New Tab" floating dynamic button centered at the bottom.
2.  **Sandboxed Tab Cards**:
    *   Arranged in a responsive, adaptive Grid layout (2-column on compact mobile, 3-column or 4-column wide grid on tablets).
    *   Each card presents a snapshot preview or favicon of the target page, the current tab-title, and a clean domain badge (e.g., `[Timotxt]`).
    *   *Visual States*: Active tab has a thick colored dynamic border; closed icons are positioned at top-right with an active touch target of at least 48dp.

---

### Screen C: The Bookmark Vault (`BookmarksPanel.kt`)
The primary offline cataloging center separating regular sites from tracked novels.

#### UI Elements & Layout Architecture:
1.  **Smart Novel Metadata Cards**:
    *   Cards displaying book covers, tracked domain labels, last read/viewed chapters, and last visit time stamps.
    *   *Swipe-to-Delete*: Supports swipe gestures to quickly delete bookmarks, complete with an "Undo" snackbar.
2.  **Index Tabs**:
    *   Separate lists for "All Bookmarks" vs. "Novels" (auto-detected via registered supports).

---

### Screen D: Historical Navigation Registry (`HistoryPanel.kt`)
A clean, infinite scroll list mapping past navigations.

#### UI Elements & Layout Architecture:
1.  **Navigation Rows**:
    *   Simple, highly-readable row elements with custom SVG host icons, bold page titles, and clean, relative timestamps (e.g., "15 mins ago").
2.  **Clear Records Action**:
    *   A prominent "Clear History" button with a confirmation popup dialog.

---

### Screen E: Contextual Settings Console (`SettingsPanel.kt`)
A comprehensive panel containing diagnostic utilities alongside fine-grain configurations.

#### UI Elements & Layout Architecture:
1.  **Audio Engine Options**:
    *   Voice selection list (filtered by locale: English, Chinese, etc.).
    *   Pitch sliders, speed sliders, and accent switches.
2.  **Ad-Blocker & Caching Hub**:
    *   Ad-blocker toggle, caching details showing space taken, and auto-translate domain lists.
3.  **Encrypted Backup System**:
    *   Dedicated action cards to "Export Backup" and "Import Backup" using standard Android Storage Access Framework launchers.
4.  **Log Viewer Utility**:
    *   Diagnostic log streams pulled from a 100-entry logging ring buffer (`WtrLogManager`). Shows log tags with time parameters and an export TXT button.

---

## 4. Key UX & Interaction Guardrails (Design Rules)

Any compile or layout system rewriting this interface must prioritize:

1.  **Strict Visual Contrast & Eye Comfort**:
    *   Online readers spend hours looking at chapter texts. The color scheme must use neutral grays, warm sepia, cream, or absolute night-dark slates.
    *   Avoid high-contrast primary neon boundaries around text blocks.
2.  **Access and Precision**:
    *   Every interactive node (tabs, sliders, icons, next buttons) must adhere to a strict minimum **48dp x 48dp touch footprint**.
    *   Buttons should produce solid ripple animations to indicate interaction.
3.  **Media Integration**:
    *   The state of the media player (on/off, buffer index, tracking word) must cleanly reflect in the system notification frame and lock screen.
4.  **Flexible Layout Adaptivity**:
    *   If viewed on expanded tablets, the panels should transition smoothly to canonical side-by-side split screens (e.g., Bookmarks on the left pane, reader WebView on the right pane) using standard rail or navigation drawer layouts.

---
*(This document serves as the canonical UX/UI mapping for Stitch-guided generation/modification lines).*
