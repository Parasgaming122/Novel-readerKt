# Data Layer Reference — Novel Reader Android App

> Complete persistence, caching, and backup subsystem documentation.
> Covers Room database schema, DAO queries, repository business logic,
> streaming backup/restore, and inter-component data flow.

---

## Table of Contents

1. [AppDatabase.kt — Room Configuration](#1-appdatabasetkt--room-configuration)
2. [Entity Schemas](#2-entity-schemas)
3. [BrowserDao.kt — Complete Query Reference](#3-browserdaokt--complete-query-reference)
4. [BrowserRepository.kt — Business Logic Layer](#4-browserrepositorykt--business-logic-layer)
5. [Backup JSON Format (Version 2)](#5-backup-json-format-version-2)
6. [BackupEncryption.kt — AES Streaming](#6-backupencryptionkt--aes-streaming)
7. [StreamingJsonParser.kt — Incremental Import](#7-streamingjsonparserkt--incremental-import)
8. [WtrAudioControlBridge.kt — TTS State Store](#8-wtraudiocontrolbridgekt--tts-state-store)
9. [SharedPreferences Schema](#9-sharedpreferences-schema)
10. [Data Flow Diagram](#10-data-flow-diagram)

---

## 1. AppDatabase.kt — Room Configuration

**File:** `data/AppDatabase.kt` (31 lines)

```kotlin
@Database(
    entities = [HistoryEntry::class, BookmarkEntry::class, TabEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase { ... }
    }
}
```

| Property | Value |
|---|---|
| Entities | `HistoryEntry`, `BookmarkEntry`, `TabEntry` |
| Schema version | 4 |
| Export schema | `false` |
| Database name | `"wtr_browser_db"` |
| Migration strategy | `fallbackToDestructiveMigration()` |
| Singleton pattern | `@Volatile` + `synchronized` double-check locking |
| DAO accessor | `abstract fun browserDao(): BrowserDao` |

> **Note:** No incremental migrations exist. Any schema version change destroys and
> recreates all tables. This is intentional — backup/restore handles data continuity.

---

## 2. Entity Schemas

### 2.1 TabEntry — `tabs` Table

**File:** `data/TabEntry.kt` (16 lines)

```kotlin
@Entity(tableName = "tabs")
data class TabEntry(...)
```

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | `Long` | `0` | `@PrimaryKey(autoGenerate = true)` |
| `url` | `String` | — | `"chrome://newtab"` for new tabs |
| `title` | `String` | — | Page title or `"New Tab"` |
| `isCurrent` | `Boolean` | `false` | Exactly one tab has `isCurrent = true` |
| `isDesktopMode` | `Boolean` | `false` | Per-tab User-Agent toggle |
| `groupId` | `Long?` | `null` | Tab group ID; `null` = standalone |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Creation time |

**Indices:** None defined.

**Invariants:**
- Exactly one row must have `isCurrent = true` at all times.
- Closing the last tab resets it to `chrome://newtab` rather than deleting.
- Tab IDs are auto-generated; used as keys in the `webViewsMap` in the UI layer.

---

### 2.2 HistoryEntry — `history` Table

**File:** `data/HistoryEntry.kt` (20 lines)

```kotlin
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], name = "idx_history_url"),
        Index(value = ["timestamp"], name = "idx_history_timestamp")
    ]
)
data class HistoryEntry(...)
```

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | `Long` | `0` | `@PrimaryKey(autoGenerate = true)` |
| `url` | `String` | — | Normalized URL (query params stripped) |
| `title` | `String` | — | Best available title |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Last visit time (updated on dedup merge) |

**Indices:**

| Index Name | Column(s) | Purpose |
|---|---|---|
| `idx_history_url` | `url` | Fast dedup lookup |
| `idx_history_timestamp` | `timestamp` | Chronological ordering + pruning |

**Invariants:**
- Auto-pruned to 500 entries via `pruneHistory(500)`.
- Duplicate URLs are merged — best title wins, most recent timestamp kept.
- Entries with empty URLs are flagged by `validateDatabaseIntegrity()`.

---

### 2.3 BookmarkEntry — `bookmarks` Table

**File:** `data/BookmarkEntry.kt` (30 lines)

```kotlin
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], name = "idx_bookmark_url"),
        Index(value = ["domain"], name = "idx_bookmark_domain"),
        Index(value = ["isNovel"], name = "idx_bookmark_isnovel")
    ]
)
data class BookmarkEntry(...)
```

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | `Long` | `0` | `@PrimaryKey(autoGenerate = true)` |
| `url` | `String` | — | Original page URL at bookmark time |
| `title` | `String` | — | Page title (may be updated to translated title) |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Bookmark creation time |
| `isNovel` | `Boolean` | `false` | Novel bookmark vs standard website bookmark |
| `novelTitle` | `String?` | `null` | Extracted novel name (from registry or title parsing) |
| `chapterTitle` | `String?` | `null` | Chapter title at bookmark creation |
| `imageUrl` | `String?` | `null` | Cover image URL (from `og:image` or JS extraction) |
| `domain` | `String?` | `null` | Clean domain (stripped of `www.` and `translate.goog`) |
| `lastViewedChapterUrl` | `String?` | `null` | Deep-link to the last read chapter |
| `lastViewedChapterTitle` | `String?` | `null` | Title of the last read chapter |

**Indices:**

| Index Name | Column(s) | Purpose |
|---|---|---|
| `idx_bookmark_url` | `url` | Fast lookup for `isBookmarked()`, `deleteBookmarkByUrl()` |
| `idx_bookmark_domain` | `domain` | Per-domain novel matching in `updateReadingProgress()` |
| `idx_bookmark_isnovel` | `isNovel` | Fast filter for novel-only queries in BookmarksPanel |

**Invariants:**
- Novel bookmarks carry progressive reading state (`lastViewedChapterUrl`/`Title`).
- `novelTitle` is used as the primary key for matching during reading progress updates.
- The `isNovel` flag drives UI segmentation in `BookmarksPanel` (Websites vs Novels tabs).

---

## 3. BrowserDao.kt — Complete Query Reference

**File:** `data/BrowserDao.kt` (83 lines)

### 3.1 History Queries (8 methods)

| # | Method | Annotation | SQL | Return | Notes |
|---|--------|------------|-----|--------|-------|
| 1 | `getAllHistory()` | `@Query` | `SELECT * FROM history ORDER BY timestamp DESC` | `Flow<List<HistoryEntry>>` | Reactive, collected by ViewModel |
| 2 | `getAllHistoryList()` | `@Query`, `suspend` | `SELECT * FROM history ORDER BY timestamp DESC` | `List<HistoryEntry>` | One-shot for dedup logic |
| 3 | `getHistoryByUrl(url)` | `@Query`, `suspend` | `SELECT * FROM history WHERE url = :url LIMIT 1` | `HistoryEntry?` | Single entry lookup |
| 4 | `pruneHistory(limit)` | `@Query`, `suspend` | `DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :limit)` | `Unit` | Keeps newest N entries |
| 5 | `insertHistory(entry)` | `@Insert(REPLACE)` | — | `Unit` | Upsert by primary key |
| 6 | `deleteHistory(id)` | `@Query`, `suspend` | `DELETE FROM history WHERE id = :id` | `Unit` | Single entry delete |
| 7 | `clearHistory()` | `@Query`, `suspend` | `DELETE FROM history` | `Unit` | Full table wipe |
| 8 | `deleteHistoryDuplicates(url, keepId)` | `@Query`, `suspend` | `DELETE FROM history WHERE url = :url AND id != :keepId` | `Unit` | Dedup cleanup after merge |

### 3.2 Bookmark Queries (9 methods)

| # | Method | Annotation | SQL | Return | Notes |
|---|--------|------------|-----|--------|-------|
| 9 | `getAllBookmarks()` | `@Query` | `SELECT * FROM bookmarks ORDER BY timestamp DESC` | `Flow<List<BookmarkEntry>>` | Reactive |
| 10 | `getAllBookmarksList()` | `@Query`, `suspend` | `SELECT * FROM bookmarks ORDER BY timestamp DESC` | `List<BookmarkEntry>` | One-shot |
| 11 | `insertBookmark(entry)` | `@Insert(REPLACE)` | — | `Unit` | Upsert by PK |
| 12 | `updateBookmark(entry)` | `@Update`, `suspend` | — | `Unit` | In-place update |
| 13 | `getNovelBookmark(novelTitle)` | `@Query`, `suspend` | `SELECT * FROM bookmarks WHERE isNovel = 1 AND novelTitle = :novelTitle LIMIT 1` | `BookmarkEntry?` | Exact novel title match |
| 14 | `getAllNovelBookmarks()` | `@Query`, `suspend` | `SELECT * FROM bookmarks WHERE isNovel = 1` | `List<BookmarkEntry>` | All novel bookmarks |
| 15 | `deleteBookmark(id)` | `@Query`, `suspend` | `DELETE FROM bookmarks WHERE id = :id` | `Unit` | By PK |
| 16 | `deleteBookmarkByUrl(url)` | `@Query`, `suspend` | `DELETE FROM bookmarks WHERE url = :url` | `Unit` | By URL |
| 17 | `clearBookmarks()` | `@Query`, `suspend` | `DELETE FROM bookmarks` | `Unit` | Full wipe |
| 18 | `isBookmarked(url)` | `@Query` | `SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url LIMIT 1)` | `Flow<Boolean>` | Reactive bookmark check |

### 3.3 Tab Queries (6 methods)

| # | Method | Annotation | SQL | Return | Notes |
|---|--------|------------|-----|--------|-------|
| 19 | `getAllTabsFlow()` | `@Query` | `SELECT * FROM tabs ORDER BY timestamp ASC` | `Flow<List<TabEntry>>` | Reactive, ASC by creation |
| 20 | `getAllTabs()` | `@Query`, `suspend` | `SELECT * FROM tabs ORDER BY timestamp ASC` | `List<TabEntry>` | One-shot |
| 21 | `insertTab(tab)` | `@Insert(REPLACE)` | — | `Long` | Returns generated row ID |
| 22 | `updateTab(tab)` | `@Update`, `suspend` | — | `Unit` | In-place update |
| 23 | `deleteTab(id)` | `@Query`, `suspend` | `DELETE FROM tabs WHERE id = :id` | `Unit` | By PK |
| 24 | `clearTabs()` | `@Query`, `suspend` | `DELETE FROM tabs` | `Unit` | Full wipe |

**Total: 24 DAO methods.**

---

## 4. BrowserRepository.kt — Business Logic Layer

**File:** `data/BrowserRepository.kt` (229 lines)

The repository wraps `BrowserDao` and adds domain-specific logic including URL
normalization, history deduplication, novel bookmark detection, and reading
progress tracking.

### 4.1 URL Normalization (`normalizeUrl`)

Strips tracking and UTM parameters before storage:

```
Removed parameters:
  _x_tr_sl, _x_tr_tl, _x_tr_hl, _x_tr_pto, _x_tr_sch  (Google Translate)
  utm_source, utm_medium, utm_campaign, utm_term, utm_content  (UTM tags)

Post-processing: trailing slash removed.
```

**Host extraction (`getHost`):**
- Lowercased, stripped of `.translate.goog`, `translate.goog`, and `www.` prefixes.

### 4.2 History Deduplication (`insertHistory`)

Protected by a `kotlinx.coroutines.sync.Mutex` to prevent concurrent write conflicts.

**Matching strategy:**
1. **Normalized URL match:** `normalizeUrl(entry.url) == normalizeUrl(inputUrl)`
2. **Title + host match:** `entry.title == cleanTitle && getHost(entry.url) == inputHost` (requires title > 3 chars)

**Merge logic on match:**
- `bestTitle`: longer of existing vs. new title
- `bestUrl`: shorter, HTTPS-preferred URL
- Timestamp updated to `currentTimeMillis()`
- Duplicates purged via `deleteHistoryDuplicates()` for both old and new URLs

**Post-insert:** Auto-pruning to 500 entries via `pruneHistory(500)`.

### 4.3 Novel Bookmark Detection (`insertBookmark`)

Determines `isNovel` based on three heuristics:

| Check | Condition |
|-------|-----------|
| **Host match** | `WebsiteSupportRegistry.findSupport(url) != null` OR host contains `translate.goog` |
| **Title pattern** | Title contains `"Chapter"`, `"Ch."`, or `"Ch "` (case-insensitive) |
| **Registry parse** | `extractNovelAndChapter()` returns a non-default chapter value |

If `isNovel = true`, the bookmark is enriched with:
- `novelTitle` / `chapterTitle` from `WebsiteSupportRegistry.extractNovelAndChapter()`
- `domain` (cleaned host)
- `imageUrl` (passed from caller)
- `lastViewedChapterUrl` / `lastViewedChapterTitle` set to current page

### 4.4 Reading Progress Tracking (`updateReadingProgress`)

Three-tier matching strategy to find the correct novel bookmark:

| Priority | Strategy | Logic |
|----------|----------|-------|
| 1 | **Exact title match** | `getNovelBookmark(novelTitle)` — direct SQL lookup |
| 2 | **Domain + path prefix** | Same domain AND bookmark URL contains the first path segment of current URL |
| 3 | **Fuzzy title (first 5 chars)** | Same domain AND current URL contains the first 5 characters of `bookmark.novelTitle` (handles translated titles) |

**On match:** Updates `lastViewedChapterUrl` and `lastViewedChapterTitle`. Detects
translated titles (longer than original, no Chinese characters) and updates both
`title` and `novelTitle` to the translated version.

### 4.5 Database Integrity Validation (`validateDatabaseIntegrity`)

```kotlin
suspend fun validateDatabaseIntegrity(context: Context?): Boolean
```

Checks all three tables for entries with empty `url` fields. Returns `false` if any
malformed entry exists. Errors are logged via `WtrLogManager`.

### 4.6 Backup Streaming (in BrowserViewModel)

**Export:** Streams JSON to an `OutputStream` wrapped in `BackupEncryption.getEncryptingStream()`.
Sequential writes: header → settings → history → bookmarks → tabs → closing brace.

**Import:** Uses `StreamingJsonParser.parseBackupStream()` with a 30-second timeout.
Detects encrypted vs. plain JSON by inspecting the first non-whitespace byte (`{` = plain).
Restores all tables by clearing first, then bulk-inserting.

### 4.7 Public API Surface

| Method | Delegates To |
|--------|-------------|
| `allHistory`, `allBookmarks`, `allTabsFlow` | DAO `Flow` properties |
| `insertHistory(url, title)` | Dedup + DAO insert |
| `deleteHistory(id)`, `clearHistory()` | DAO passthrough |
| `insertBookmark(url, title, imageUrl)` | Novel detection + DAO insert |
| `updateNovelMetadata(url, ...)` | DAO update |
| `updateReadingProgress(url, title)` | 3-tier match + DAO update |
| `deleteBookmark(id)`, `deleteBookmarkByUrl(url)` | DAO passthrough |
| `isBookmarked(url)` | DAO `Flow<Boolean>` |
| `insertTab(tab)`, `updateTab(tab)`, `deleteTab(id)`, `clearTabs()` | DAO passthrough |
| `validateDatabaseIntegrity()` | Cross-table validation |

---

## 5. Backup JSON Format (Version 2)

```json
{
  "version": 2,
  "timestamp": 1234567890,
  "settings": {
    "app_theme": "Dark",
    "custom_text_zoom": 115,
    "force_dark_content": false,
    "enable_web_trackplayer": false,
    "auto_focus_paragraphs": true,
    "remember_paragraphs": true,
    "auto_translate_enabled": true,
    "auto_translate_domains": "wtr-lab.com, novel543.com, ...",
    "gemini_translate_enabled": false,
    "gemini_api_key": "",
    "ad_blocker_enabled": true
  },
  "history": [
    { "url": "https://...", "title": "...", "timestamp": 1234567890 }
  ],
  "bookmarks": [
    {
      "url": "https://...",
      "title": "...",
      "timestamp": 1234567890,
      "isNovel": true,
      "novelTitle": "My Novel",
      "chapterTitle": "Chapter 42",
      "imageUrl": "https://...cover.jpg",
      "domain": "novel543.com",
      "lastViewedChapterUrl": "https://.../chapter-42",
      "lastViewedChapterTitle": "Chapter 42"
    }
  ],
  "tabs": [
    {
      "url": "https://...",
      "title": "...",
      "isCurrent": true,
      "isDesktopMode": false,
      "groupId": null,
      "timestamp": 1234567890
    }
  ]
}
```

**Key design decisions:**
- 11 SharedPreferences keys backed up (see section 9 for full list).
- Nullable fields use JSON `null` (not omitted).
- Tabs array includes `groupId` for tab group restoration.
- Streaming write — no `org.json.JSONObject` root build; manual string concatenation.

---

## 6. BackupEncryption.kt — AES Streaming

**File:** `BackupEncryption.kt` (120 lines)

| Property | Value |
|---|---|
| KeyStore alias | `"wtr_backup_key"` |
| Cipher | `AES/CBC/PKCS7Padding` |
| Key storage | Android KeyStore (hardware-backed) |
| Key spec | AES, CBC block mode, PKCS7 padding |
| IV size | 16 bytes (random per encryption) |
| Encoding | Base64 (DEFAULT flags, with line breaks) |

**Streaming wrappers:**
- `getEncryptingStream(outputStream)` → `Base64OutputStream` → writes IV → `CipherOutputStream`
- `getDecryptingStream(inputStream)` → `Base64InputStream` → reads 16-byte IV → `CipherInputStream`

**Graceful degradation:** If encryption stream init fails, export falls back to
plaintext. Import detects encrypted files by checking if the first non-whitespace
byte is NOT `{`, then attempts decryption.

---

## 7. StreamingJsonParser.kt — Incremental Import

**File:** `StreamingJsonParser.kt` (238 lines)

Uses `android.util.JsonReader` (pull parser) to avoid loading the entire backup
into memory. This is critical for large bookmark collections.

**Parse flow:**
1. `beginObject()` — root `{`
2. Read `version` (int) and `timestamp` (long)
3. `settings` — iterates key-value pairs; infers type from `JsonToken` (BOOLEAN, NUMBER, STRING)
4. `history` — delegates to `parseHistoryEntry()` per array element
5. `bookmarks` — delegates to `parseBookmarkEntry()` (handles NULL for nullable fields)
6. `tabs` — delegates to `parseTabEntry()` (handles NULL for `groupId`)

**Timeout:** 30 seconds (enforced in `BrowserViewModel.importBackup`).

---

## 8. WtrAudioControlBridge.kt — TTS State Store

**File:** `WtrAudioControlBridge.kt` (225 lines)

Singleton `object` bridging WebView JavaScript ↔ Android TTS engine ↔ foreground
service media controls. Uses `MutableStateFlow` for reactive state.

| State Flow | Type | Purpose |
|---|---|---|
| `isPlaying` | `Boolean` | Media session playback state |
| `title` | `String` | Current novel/chapter title |
| `subtitle` | `String` | Paragraph progress or status text |
| `novelName` / `chapterTitle` | `String` | Enriched metadata for lock screen |
| `activeWebsite` | `String` | Clean domain for UI display |
| `ttsSpeed` | `Float` | Speech rate multiplier (default 4.0) |
| `ttsPitch` | `Float` | Voice pitch (default 1.0) |
| `ttsVoiceName` | `String` | System TTS voice identifier |
| `ttsAccent` | `String` | `"US"`, `"UK"`, `"AU"`, or `"IN"` |
| `availableVoices` | `List<String>` | Enumerated system voices |
| `playTrackInputList` | `List<String>` | Extracted paragraph text list |
| `currentTrackIndex` | `Int` | Currently playing paragraph index |
| `isPlayerRunning` | `Boolean` | Custom TrackPlayer active state |
| `isAudiobookModeActive` | `Boolean` | Override `isPlaying` for audiobook UX |
| `currentlySpeakingText` | `String` | Current TTS utterance text |
| `extractedUrl` | `String` | URL of currently extracted page |
| `activeTtsTabId` | `Long?` | Tab ID currently driving TTS |
| `currentlyActiveTabId` | `Long?` | Tab ID currently visible to user |

**Callback slots** (set by `BrowserAppScreen` and `WtrBrowserService`):

| Callback | Direction | Purpose |
|---|---|---|
| `playAction` / `pauseAction` / `nextAction` / `prevAction` | Service → WebView | Media button JS injection |
| `onSpeakNative` / `onCancelNative` / `onPauseNative` / `onResumeNative` | WebView → Service | TTS engine commands |
| `onWebViewProgressTrigger` | Service → WebView | `WtrTtsTriggerEvent()` JS call |
| `onMetadataExtracted` | WebView → App | Novel metadata for bookmark updates |
| `onStateChangedCallback` | Bridge → Service | Notify media session update |
| `playCustomParagraphAction` | UI → Service | Start TTS at specific paragraph |
| `nextChapterAction` | Service → UI | Trigger next chapter navigation |

---

## 9. SharedPreferences Schema

**File:** `"wtr_browser_settings"` (accessed via `context.getSharedPreferences`)

| Key | Type | Default | Backed Up | Description |
|-----|------|---------|-----------|-------------|
| `app_theme` | `String` | `"Dark"` | Yes | Theme name (Dark/Grey/White/Sepia/Forest/Ocean) |
| `custom_text_zoom` | `Int` | `115` | Yes | WebView text zoom percentage (95–160) |
| `force_dark_content` | `Boolean` | `false` | Yes | Inject dark CSS into all pages |
| `enable_web_trackplayer` | `Boolean` | `false` | Yes | Enable paragraph TrackPlayer extraction |
| `auto_focus_paragraphs` | `Boolean` | `true` | Yes | JS highlight + scroll into view on paragraph change |
| `remember_paragraphs` | `Boolean` | `true` | Yes | Save paragraph index per URL |
| `auto_translate_enabled` | `Boolean` | `true` | Yes | Auto-redirect through Google Translate |
| `auto_translate_domains` | `String` | Registry default | Yes | Comma-separated domain keywords |
| `gemini_translate_enabled` | `Boolean` | `false` | Yes | Use Gemini AI instead of Google Translate |
| `gemini_api_key` | `String` | `""` | Yes | Gemini API key |
| `ad_blocker_enabled` | `Boolean` | `true` | Yes | Block ad network requests in WebView |
| `anti_captcha_delay` | `Boolean` | `false` | No | Anti-CAPTCHA delay on page load |
| `tts_speed` | `String` | `"4.0"` | No | TTS rate as string |
| `tts_pitch` | `String` | `"1.0"` | No | TTS pitch preset |
| `tts_accent` | `String` | `"US"` | No | Voice accent preference |
| `search_engine` | `String` | Google URL | No | Search engine query URL |

> **Note:** 11 keys are included in backups. Non-backed-up keys are restored to
> defaults after an import.

---

## 10. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERACTION                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  WebView  │  │  Back    │  │  Bookmark    │  │  Settings     │  │
│  │  Navigation│ │  Button  │  │  Button      │  │  Panel        │  │
│  └─────┬─────┘  └────┬─────┘  └──────┬───────┘  └──────┬────────┘  │
└────────┼──────────────┼──────────────┼─────────────────┼───────────┘
        │              │              │                 │
        ▼              │              │                 │
┌─────────┐            │              │                 │
│ WebView │            │              │                 │
│ Client  │            │              │                 │
└───────┬─┘            │              │                 │
        │              │              │                 │
        ▼              ▼              ▼                 ▼
┌───────────────────────────────────────────────────────────────────┐
│                      BrowserViewModel                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ onPageLoaded│  │ toggleBookmark│ │ exportBackup /          │  │
│  │ loadUrl     │  │ closeTab      │ │ importBackup           │  │
│  └──────┬──────┘  └──────┬───────┘  └───────────┬────────────┘  │
└─────────┼────────────────┼───────────────────────┼───────────────┘
          │                │                       │
          ▼                ▼                       │
┌─────────────────────────────────────────────────┼──────────────┐
│                   BrowserRepository               │               │
│  ┌──────────────────┐  ┌─────────────────────┐  │               │
│  │ insertHistory    │  │ insertBookmark       │  │               │
│  │ (dedup+normalize) │  │ (novel detection)    │  │               │
│  │ updateReadingProg │  │ updateNovelMetadata  │  │               │
│  └────────┬─────────┘  └──────────┬──────────┘  │               │
└───────────┼────────────────────────┼─────────────┼───────────────┘
            │                        │             │
            ▼                        ▼             ▼
┌───────────────────────────────────────────────────────────────────┐
│                       BrowserDao (Room)                             │
│  ┌────────────┐  ┌──────────────┐  ┌─────────────┐               │
│  │ history    │  │ bookmarks    │  │ tabs        │               │
│  │ table      │  │ table        │  │ table       │               │
│  └─────┬──────┘  └──────┬───────┘  └──────┬──────┘               │
└────────┼────────────────┼─────────────────┼──────────────────────┘
        │                │                 │
        ▼                ▼                 ▼
┌───────────────────────────────────────────────────────────────────┐
│                    SQLite (wtr_browser_db)                         │
└───────────────────────────────────────────────────────────────────┘

        ┌─────────────────────────────────────────┐
        │  BACKUP / RESTORE PIPELINE              │
        │                                         │
        │  Export:                                │
        │    Room → JSON Writer → AES Encrypt    │
        │           → Base64 → SAF OutputStream   │
        │                                         │
        │  Import:                                │
        │    SAF InputStream → Base64 decode      │
        │      → detect { vs encrypted            │
        │      → AES Decrypt (if needed)          │
        │      → StreamingJsonParser (pull)       │
        │      → Room bulk insert                 │
        └─────────────────────────────────────────┘
```

**WebView → JavaScript Bridge flow:**

```
Website JS  ──evaluateJavascript──►  WtrWebAppInterface ("WtrBridge")
     │                                     │
     │  speakNative()                       ├──► WtrAudioControlBridge.onSpeakNative
     │  cancelNative()                      ├──► WtrAudioControlBridge.onCancelNative
     │  syncPollState()                     ├──► WtrAudioControlBridge.updatePlaybackState
     │  syncUrl()                           ├──► BrowserViewModel.onPageLoaded
     │  syncMetadata()                      └──► WtrAudioControlBridge.onMetadataExtracted
     │                                           └──► BrowserViewModel.updateNovelMetadata
     │                                               └──► BrowserRepository.updateNovelMetadata
     │                                                   └──► BrowserDao.updateBookmark
     ▼
TTS Polyfill (WebScripts.kt)
  └──► MockSpeechSynthesis → window.WtrBridge.speakNative()
  └──► HTML5 Audio hooks → window.WtrBridge.postPlaybackState()
  └──► Metadata polling (500ms) → window.WtrBridge.syncMetadata()
```

---

*Generated from source analysis. All line counts and signatures match the codebase.*
