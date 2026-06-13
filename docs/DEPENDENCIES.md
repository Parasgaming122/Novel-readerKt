# Dependency Reference & Third-Party Libraries

Complete reference for all dependencies used by the Novel Reader Android app, managed through the Gradle Version Catalog (`gradle/libs.versions.toml`).

---

## Table of Contents

1. [Active Dependencies](#1-active-dependencies)
2. [Build Plugins](#2-build-plugins)
3. [Commented-Out Dependencies (Future Use)](#3-commented-out-dependencies-future-use)
4. [Build Tools Summary](#4-build-tools-summary)

---

## 1. Active Dependencies

All versions below are sourced from `gradle/libs.versions.toml`. Libraries marked with the Compose BOM inherit their version from the BOM platform alignment.

### Jetpack Compose (BOM 2024.09.00)

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Compose BOM** | 2024.09.00 | Jetpack Compose version alignment for all Compose artifacts | `platform()` |
| **Material 3** | *(BOM)* | Material Design 3 UI components (buttons, cards, dialogs, etc.) | `implementation` |
| **Material Icons Core** | *(BOM)* | Core Material icon set (navigation, action, alert icons) | `implementation` |
| **Material Icons Extended** | *(BOM)* | Extended icon set (additional 2000+ icons) | `implementation` |
| **Compose UI** | *(BOM)* | Core Compose UI primitives (`Modifier`, layout nodes, input handling) | `implementation` |
| **Compose Graphics** | *(BOM)* | Graphics layer (canvas, shapes, painting, vectors) | `implementation` |
| **Compose Tooling Preview** | *(BOM)* | `@Preview` annotation support for design-time rendering | `implementation` |

### Room Database (2.7.0)

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Room Runtime** | 2.7.0 | SQLite ORM runtime — database instance, type converters, migrations | `implementation` |
| **Room KTX** | 2.7.0 | Kotlin coroutines extensions (`suspend` DAO functions, `Flow` returns) | `implementation` |
| **Room Compiler (KSP)** | 2.7.0 | Annotation processor — generates DAO implementations and database code | `ksp` |

### AndroidX Lifecycle (2.8.7)

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Lifecycle Runtime KTX** | 2.8.7 | `LifecycleOwner`, `lifecycleScope`, ViewModel lifecycle integration | `implementation` |
| **Lifecycle ViewModel Compose** | 2.8.7 | `viewModel()` composable function for ViewModel injection | `implementation` |
| **Lifecycle Runtime Compose** | 2.8.7 | `collectAsStateWithLifecycle()` — lifecycle-aware state collection | `implementation` |

### AI & Translation

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Generative AI (Gemini SDK)** | 0.9.0 | Google Gemini API client for AI-powered translation of novel text | `implementation` |

### Kotlin Coroutines (1.10.2)

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Coroutines Core** | 1.10.2 | Core coroutine primitives (`launch`, `async`, `Flow`, `Channel`) | `implementation` |
| **Coroutines Android** | 1.10.2 | `Dispatchers.Main`, `Dispatchers.IO` — Android-aware coroutine dispatchers | `implementation` |

### Networking

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **OkHttp** | 4.10.0 | Low-level HTTP client — connection pooling, interceptors, timeouts | `implementation` |
| **OkHttp Logging Interceptor** | 4.10.0 | HTTP request/response logging for debugging | `implementation` |
| **Retrofit** | 2.12.0 | Type-safe REST client — declarative API interface definitions | `implementation` |
| **Moshi Converter** | 2.12.0 | Retrofit `Converter.Factory` — serializes request/response bodies via Moshi | `implementation` |
| **Moshi Kotlin** | 1.15.2 | Kotlin-aware JSON serialization (handles nullability, default values) | `implementation` |
| **Moshi Codegen (KSP)** | 1.15.2 | Annotation processor — generates Moshi adapter classes at compile time | `ksp` |

### Image Loading

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Coil Compose** | 2.7.0 | Asynchronous image loading with Compose integration (bookmark cover images) | `implementation` |

### Activity

| Library | Version | Purpose | Configuration |
|---------|---------|---------|---------------|
| **Activity Compose** | 1.10.1 | `ComponentActivity` Compose support — `setContent {}`, `RememberLauncherForActivityResult` | `implementation` |

---

## 2. Build Plugins

| Plugin | Version | ID | Purpose |
|--------|---------|----|---------|
| **Android Gradle Plugin (AGP)** | 9.1.1 | `com.android.application` | Core Android build system — compiles, packages, and signs APKs |
| **Kotlin Compose Compiler** | 2.2.10 | `org.jetbrains.kotlin.plugin.compose` | Compiles Compose `@Composable` functions and `@Composable` annotations |
| **KSP** | 2.3.5 | `com.google.devtools.ksp` | Kotlin Symbol Processing — annotation processing for Room and Moshi (replaces KAPT) |
| **Roborazzi** | *(BOM)* | `io.github.takahirom.roborazzi` | Screenshot testing library — captures and diffs Compose UI screenshots |
| **Secrets Gradle Plugin** | — | `com.google.secrets` | Reads `.env` files and injects values into `BuildConfig` fields |

### Plugin Application Flow

```
Root build.gradle.kts          app/build.gradle.kts
┌─────────────────────┐       ┌─────────────────────────┐
│ plugins {           │       │ plugins {               │
│   id("...") apply   │──────▶│   id("...")             │
│   false             │       │ }                       │
│ }                   │       │                         │
└─────────────────────┘       └─────────────────────────┘
```

All 5 plugins are declared at the root level with `apply false` and then applied (without version) in `app/build.gradle.kts`.

---

## 3. Commented-Out Dependencies (Future Use)

The version catalog and build files contain several commented-out dependencies that are not currently active but indicate planned or exploratory features:

| Library | Purpose |
|---------|---------|
| **Accompanist Permissions** | Runtime permission request composables |
| **CameraX Core** | Camera access for potential OCR/scanning features |
| **CameraX Camera2** | Camera2 interop for CameraX |
| **CameraX Lifecycle** | Camera lifecycle binding |
| **CameraX View** | PreviewView composable for camera preview |
| **DataStore Preferences** | Jetpack DataStore as a SharedPreferences replacement |
| **Navigation Compose** | In-app navigation framework (currently using custom tab-based navigation) |
| **Firebase AI** | Potential alternative/complement to Gemini SDK |
| **ML Kit Translate** | On-device translation (alternative to cloud-based Gemini) |
| **Play Services Location** | Geolocation features (not currently needed for a reader app) |

These are kept in the codebase as reference for future development and can be uncommented when needed.

---

## 4. Build Tools Summary

| Tool | Version | Notes |
|------|---------|-------|
| **AGP** | 9.1.1 | Requires JDK 17+ |
| **Kotlin** | 2.2.10 | Language + standard library |
| **KSP** | 2.3.5 | Replaces KAPT for annotation processing |
| **JDK** | 17 | Auto-provisioned via Foojay Toolchains plugin |
| **Gradle** | Wrapper-managed | Exact version in `gradle/wrapper/gradle-wrapper.properties` |
| **Min SDK** | 24 (Android 7.0) | Covers 95%+ of active Android devices |
| **Target SDK** | 36 (Android 16) | Latest available |
| **Compile SDK** | 36 (Android 16) | Matches target SDK |
| **Java Compatibility** | 11 | Source and target bytecode level |

### Dependency Architecture

```
┌─────────────────────────────────────────────┐
│                 Application                  │
├─────────────┬──────────┬────────────────────┤
│   Compose    │  Room    │  Networking        │
│   UI Layer   │  Data    │  (Retrofit/OkHttp) │
│              │  Layer   │                    │
├─────────────┼──────────┼────────────────────┤
│ Material 3   │ Moshi    │  Gemini SDK        │
│ Icons        │ (JSON)   │  (Translation)     │
├─────────────┴──────────┴────────────────────┤
│         Lifecycle · Coroutines · Coil        │
├─────────────────────────────────────────────┤
│         Android SDK · Kotlin Stdlib          │
└─────────────────────────────────────────────┘
```

### Version Catalog Statistics

| Metric | Count |
|--------|-------|
| Version aliases (`[versions]`) | 42 |
| Library definitions (`[libraries]`) | 48 |
| Plugin definitions (`[plugins]`) | 5 |
| Active implementation dependencies | ~24 |
| KSP processors | 3 (Room, Moshi Codegen, Roborazzi) |
