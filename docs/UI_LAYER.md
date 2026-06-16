# User Interface Subsystem & Jetpack Compose Layouts

> Complete layout hierarchy, theme design maps, panel definitions, and WebView pool
> mechanics in the Jetpack Compose Presentation Layer.

---

## Table of Contents

1. [UI Subsystem Topological Blueprint](#1-ui-subsystem-topological-blueprint)
2. [Universal Theme Design Map (Theme.kt)](#2-universal-theme-design-map-themekt)
3. [Core Composable Layout Hierarchy](#3-core-composable-layout-hierarchy)
4. [Overlay Panels (View Panels)](#4-overlay-panels-view-panels)
5. [WebView Pool Mechanics & Lifecycle](#5-webview-pool-mechanics--lifecycle)
6. [ChromeNewTabPage Component Specification](#6-chromenewtabpage-component-specification)
7. [Jetpack Compose State Observations](#7-jetpack-compose-state-observations)

---

## 1. UI Subsystem Topological Blueprint

The Compose UI manages state transitions and panel overlays overlaying WebView engines.

```
+────────────────────────────────────────────────────────────────────────┐
│                        BrowserAppScreen                                │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ TopAppBar: Back, URL Address TextInput, Refresh, Section Toggles│   │
│   └────────────────────────────────────────────────────────────────┘   │
│                                                                        │
│   [ BrowserSection State Router ]                                      │
│                                                                        │
│   ├── BrowserSection.WEB                                              │
│   │    ├── if URL start with "chrome://newtab"                         │
│   │    │     └── ChromeNewTabPage                                      │
│   │    └── else                                                        │
│   │          └── Tab-Isolated AndroidView WebViews                     │
│   │                                                                    │
│   ├── BrowserSection.TABS                                              │
│   │    └── TabsPanel (Single/Folder Double Grid layout)                │
│   │                                                                    │
│   ├── BrowserSection.BOOKMARKS                                         │
│   │    └── BookmarksPanel (Novels vs Website listings)                 │
│   │                                                                    │
│   ├── BrowserSection.HISTORY                                           │
│   │    └── HistoryPanel (Chronological listings, filter searches)      │
│   │                                                                    │
│   └── BrowserSection.SETTINGS                                          │
│        └── SettingsDialog (TTS, Dark overrides, Backup imports)        │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ Floating Action TTS Shelf (Dynamic, bottom pinned overlay)     │   │
│   │ - Play/Pause, Paragraph track sliders, Volume, Speed Controls  │   │
│   └────────────────────────────────────────────────────────────────┘   │
+────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Universal Theme Design Map (`Theme.kt`)

The theme layer exposes 6 selectable presets in SharedPreferences (`app_theme`), implementing Material 3 `ColorScheme` architectures.

| Theme Name | Primary Accent | Background Canvas | Surface Container | Applied Utility |
|------------|----------------|-------------------|-------------------|-----------------|
| **Dark** | `#A8C7FA` (Light Blue) | `#131314` (Deep Grey) | `#1E1F20` (Dark Charcoal) | Default night reading mode |
| **Grey** | `#938F99` (Slate Grey) | `#2A2A2B` (Medium Grey)| `#353536` (Light Charcoal) | Balanced, low-contrast mode |
| **White** | `#0b57d0` (Google Blue)| `#FFFFFF` (Solid White)| `#F2F2F2` (Warm White) | High-contrast day mode |
| **Sepia** | `#825500` (Bronze) | `#FBF0D9` (Soft Parchment)| `#EFE4CD` (Parchment Border)| Warm, antique paper tone |
| **Forest** | `#2E7D32` (Sap Green) | `#E8F5E9` (Mint Tint) | `#C8E6C9` (Mint Green) | Natural botanical reading tone|
| **Ocean** | `#0277BD` (Ocean Blue) | `#E1F5FE` (Aqua Blue) | `#B3E5FC` (Sky Accent) | Cooling, oceanic display tone |

### Edge-to-Edge Customization

In all dark modes, `darkTheme = true` is reported to the system. In light themes, system bars use light icons over appropriate containers. All theme instances define consistent line heights, generous letter spacing ratios, and custom system-level shapes.

---

## 3. Core Composable Layout Hierarchy

```
Scaffold (with WindowInsets Handling)
  ├── content
       └── Box(Modifier.fillMaxSize())
            │
            ├── Column(Modifier.fillMaxSize())
            │     ├── TopAppBar (URL, Navigation buttons, panel buttons)
            │     └── Box(Modifier.weight(1f)) {
            │           ├── Web content (ChromeNewTabPage OR WebViews)
            │           ├── TabsPanel
            │           ├── BookmarksPanel
            │           └── HistoryPanel
            │         }
            │
            ├── BottomAudioControlShelf (Floating overlay, bottom-aligned)
            └── SettingsDialog (Overlay when active)
```

---

## 4. Overlay Panels (View Panels)

Toggle-driven, replacement layout blocks matching the active `BrowserSection` enum:

### 4.1 TabsPanel
Renders double-grid layouts. Matches individual `TabEntry` items based on state:
- Supports vertical groups (Folder Cards) containing child tabs.
- Drag-and-drop grouping trigger capabilities.
- "Delete" dismisses elements instantly, updating DB rows.

### 4.2 BookmarksPanel
Categorized into two tabs: **Novels** (`isNovel = true`) and **Websites** (`isNovel = false`).
- Novel cards render cover images (using Coil's `SubcomposeAsyncImage` with placeholder fallback).
- Displays latest chapter title and last read deep-link details.
- Standard website cards show domain favicons and clean hosts.

### 4.3 HistoryPanel
Renders sequential scroll listings sorted chronologically.
- Features search filters to inspect specific historic URLs.
- Long-pressing elements opens a menu to copy, delete, or load.
- "Clear All" issues a full database wipe query.

### 4.4 SettingsPanel
The primary configuration modal dialog.
- Divided into sections: Audio (Speech pitch, accents, voices), Layout (Text Zoom, Force Dark), Automation (Auto-Translate, Gemini key configuration), and Storage (SAF JSON backup/restore exports).

---

## 5. WebView Pool Mechanics & Lifecycle

WebViews are extremely resource-heavy and prone to context leaks. The app manages them inside a centralized companion pool:

```kotlin
// In MainActivity:
val activeWebViewsPool = Collections.synchronizedList(ArrayList<WebView>())
```

### 5.1 Tab-Isolated Creation (BrowserAppScreen)

To prevent cross-tab navigation leaks and duplicate rendering, each tab's WebView is isolated using local snapshot scoping:

```kotlin
val currentTabState by viewModel.currentTab.collectAsStateWithLifecycle()
val allTabsList by viewModel.allTabs.collectAsStateWithLifecycle()

allTabsList.forEach { tab ->
    // Critical: Create a localized snapshot variable to isolate each WebView
    val tabForView = tab
    val isTabActive = currentTabState?.id == tabForView.id

    AnimatedVisibility(
        visible = isTabActive && currentSection == BrowserSection.WEB,
        enter = fadeIn(), exit = fadeOut()
    ) {
        key(tabForView.id) {
            AndroidView(
                factory = { ctx ->
                    val webView = WebView(ctx).apply {
                        // Establish core engineering configurations:
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // Set standard Mobile User Agent
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        
                        // Inject our JS-to-Native bridge with isolated scoping
                        addJavascriptInterface(
                            WtrWebAppInterface(
                                tabId = tabForView.id,
                                onPlaybackStateChanged = { playing, t, sub -> ... },
                                onUrlSynced = { url, title -> ... }
                            ),
                            "WtrBridge"
                        )
                        
                        webViewClient = object : WebViewClient() { ... }
                        webChromeClient = object : WebChromeClient() { ... }
                    }
                    // Register with central pool
                    MainActivity.activeWebViewsPool.add(webView)
                    webView
                },
                update = { webView ->
                    // Apply programmatic updates (e.g., text zoom updates or force-dark CSS)
                    webView.settings.textZoom = textZoomPref
                }
            )
        }
    }
}
```

### 5.2 Key Design Constraints

1. **`tabForView` Snapshot Scoping:** Declaring `val tabForView = tab` outside the `AndroidView` block prevents the `update` block from referencing stale pointer indices when user shifts from Tab A to Tab B.
2. **`key(tabForView.id)` Guard:** Ensures Jetpack Compose correctly tracks each instance, reusing existing WebViews instead of recreating them on recomposition.
3. **WtrBridge Scoping:** The `WtrWebAppInterface` is explicitly hard-scoped to `tabForView.id` so that callbacks received on the background thread only update the UI if they match the active tab's ID.

---

## 6. ChromeNewTabPage Component Specification

**File:** `ui/ChromeNewTabPage.kt` (~372 lines)

Renders when the tab URL is `"chrome://newtab"`. It bypasses WebView execution and renders native layouts for speed.

```
+────────────────────────────────────────────────────────┐
│  ChromeNewTabPage                                      │
│                                                        │
│  [ Visual Logo Section ]                               │
│  - Elegant displaying font header text "Wtr-Lab"       │
│                                                        │
│  [ Search Inputs Container ]                           │
│  - Custom filled text field + voice toggle filters     │
│                                                        │
│  [ Shortcut Directories Icon Grids ]                   │
│  - 8 Circular styled action buttons (favicons):        │
│    wtr-lab, timotxt, novel543, timo, novelhall, etc.   │
│                                                        │
│  [ Recent Historical Row Listings ]                    │
│  - 4 Horizontally scrollable recent visit items        │
│                                                        │
├────────────────────────────────────────────────────────┤
│  [ Bookmarked Novels Collection Grid ]                 │
│  - Covers showing read percentages & deep-link tags   │
└────────────────────────────────────────────────────────┘
```

The New Tab Page is built using Compose grids and rows, reading data directly from `viewModel.allHistory` and `viewModel.allBookmarks`.

---

## 7. Jetpack Compose State Observations

All observed state flows use `collectAsStateWithLifecycle` to prevent resource leaks:

```kotlin
val allTabs by viewModel.allTabs.collectAsStateWithLifecycle()
val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
val allBookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
```

### Lifecycle Benefits

- Stops collection when the app is in the background, freeing up CPU and binder bandwidth.
- Automatically resumes collection when the user returns, ensuring the UI is immediately in sync with the database.
- Used in conjunction with `SharingStarted.WhileSubscribed(5000)` in the ViewModel to prevent rapid database queries.
