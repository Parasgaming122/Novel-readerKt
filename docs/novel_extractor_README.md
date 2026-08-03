# Novel Extractor

**Generic, language-agnostic chapter text & title extraction engine for web-novel reader applications with Text-to-Speech (TTS).**

## Table of Contents
- [Overview & Purpose](#overview--purpose)
- [Key Features](#key-features)
- [Recent Optimizations & Best Practices](#recent-optimizations--best-practices)
- [Architecture & Pipeline](#architecture--pipeline)
- [Core Algorithms & Heuristics](#core-algorithms--heuristics)
- [AI Script Synthesis & Bug Fix Audit](#ai-script-synthesis--bug-fix-audit)
- [Installation & Dependencies](#installation--dependencies)
- [CLI Usage](#cli-usage)
- [Python API Usage](#python-api-usage)
- [Kotlin / JS Porting Guide for Mobile WebView](#kotlin--js-porting-guide-for-mobile-webview)

---

## Overview & Purpose

Web novel reading websites (both English platforms like RoyalRoad and Chinese platforms like 笔趣阁 or novel543) surround story content with extensive "site chrome": top navigation bars, sidebars, recommended book carousels, comment threads, ad banners, popups, and prev/next chapter button clusters.

When using Text-to-Speech (TTS) in a mobile novel reader app, feeding raw HTML directly to the TTS engine causes it to read out site menus, copyright notices, and recommendation lists before or after the chapter prose. This makes the listening experience unusable.

`novel_extractor.py` provides a generic, heuristic-based content extraction layer. It does not rely on hardcoded per-site CSS selectors or site scrapers. Instead, it combines structural signals, character density, Readability-style score propagation, and multi-factor confidence estimation to extract just the chapter title and clean body text from any arbitrary novel website in English or Chinese.

---

## Key Features

- **Language-Agnostic Scoring:** Evaluates text density using punctuation-cleansed character counts rather than space-delimited word tokenization, making it equally accurate on English and CJK (Chinese, Japanese, Korean) text.
- **Hybrid Scoring Engine:** Combines direct container density scoring with Readability.js-style leaf-to-parent score propagation.
- **Identity-Based Runner-Up Filtering:** Solves a common Readability flaw by excluding parent/ancestor wrappers when computing confidence margins.
- **Duplication-Free Serialization:** Uses leaf-node block traversal to prevent parent and child elements (`<div><p>text</p></div>`) from emitting duplicate lines of text.
- **60% Text Volume Safety Guard:** Never accidentally prunes a container element during post-clean if it holds >60% of the total page text.
- **Host-Token-Aware Title Cleanup:** Dynamically strips website brand names from `<title>` tags without hardcoded domain rules.
- **Text-Prefix Notice Filtering:** Prunes un-classed notice boxes (e.g. `温馨提示:`, `最新网址:`) in both Simplified and Traditional Chinese.
- **Multi-Factor Confidence Metric:** Produces a `0.0 – 0.95` confidence score allowing downstream mobile apps to decide whether to trust extraction, prompt manual selection, or fall back to full-page WebView rendering.

---

## Recent Optimizations & Best Practices

The latest iteration of the engine has been refactored to align with modern Python best practices (PEP 8, PEP 484) and improve runtime performance on large DOM trees:

1. **Algorithmic Efficiency ($O(N^2) \to O(N)$):** Replaced nested list-based deduplication loops in candidate selection (`_select_content`) and fast-path discovery (`_fast_path_candidates`) with $O(1)$ `set` lookups using memory addresses (`id(tag)`).
2. **Safe Identity Hashing:** BeautifulSoup `Tag` objects can have unpredictable `__hash__` behaviors across parser backends. Sets now track `id(tag)` to guarantee strict identity semantics and prevent accidental deep-DOM equality checks.
3. **Memory & Immutability:** Converted static configuration lists to `tuple` and membership groups to `frozenset` (e.g., `_ARIA_NOISE_ROLES`, `_MAIN_TAGS`, `_NAV_FOOTER_ASIDE_TAGS`) for faster `in` lookups and lower memory overhead.
4. **Strict Type Safety:** Migrated from `collections.namedtuple` to `typing.NamedTuple` with comprehensive PEP 484 type hints across all function signatures, enabling robust static analysis (e.g., `mypy`) and IDE autocompletion.
5. **Robust CLI & Logging:** Replaced `sys.stderr` prints with Python's standard `logging` module. Added `sys.stderr` UTF-8 reconfiguration to prevent `UnicodeEncodeError` crashes on Windows consoles when tracebacks contain CJK characters.

---

## Architecture & Pipeline

`novel_extractor.py` processes raw HTML through a 9-stage transformation pipeline:

```text
 ┌─────────────────────────────────────────────────────────────┐
 │ Stage 1: Parse HTML (lxml -> html.parser fallback)          │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 2: Pre-clean (junk tags, hidden elements, ARIA roles) │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 3: Fast-path semantic candidate discovery             │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 4: Hybrid candidate scoring & score propagation       │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 5: Specificity refinement & adjacent merge fallback   │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 6: Multi-tier scored title detection cascade          │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 7: Post-clean (noise pruning, safety guards, author)  │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 8: Leaf-only text serialization & <br> formatting     │
 └──────────────────────────────┬──────────────────────────────┘
                                │
 ┌──────────────────────────────▼──────────────────────────────┐
 │ Stage 9: Weighted multi-factor confidence estimation        │
 └─────────────────────────────────────────────────────────────┘
```

---

## Core Algorithms & Heuristics

### 1. Language-Agnostic Scoring Length
Standard word tokenization (`text.split()`) fails on Chinese text because CJK languages do not use whitespace between words. `novel_extractor.py` strips CJK symbols, ASCII punctuation, and whitespace before evaluating text volume for scoring:

```python
_SCORING_STRIP_RE = re.compile(r"[^\w\s]|_", re.UNICODE)

def _scoring_len(s):
    if not s: return 0
    s = _SCORING_STRIP_RE.sub("", s)
    s = _WS_RE.sub("", s)
    return len(s)
```
*Note: Punctuation is stripped only during scoring calculations. All original punctuation and formatting are fully preserved in the extracted output text.*

### 2. Pre-Cleaning & Safe Node Pruning
Stage 2 removes DOM elements that never contain story prose:
- **Junk tags:** `<script>`, `<style>`, `<noscript>`, `<iframe>`, `<svg>`, `<form>`, `<button>`, `<input>`, `<select>`, `<textarea>`, `<canvas>`, `<video>`, `<audio>`, `<object>`, `<embed>`, `<template>`.
- **Hidden elements:** Elements matching `display:none`, `visibility:hidden`, `aria-hidden="true"`, or `[hidden]`.
- **ARIA noise roles:** Elements with `role="navigation"`, `role="banner"`, `role="complementary"`, or `role="contentinfo"`.
- **HTML comments:** Removed completely.

**Crucial Design Choice:** `<nav>`, `<footer>`, `<header>`, and `<aside>` are **NOT** deleted during pre-cleaning. They are penalized during candidate scoring and removed during post-cleaning. This prevents premature data loss if a site uses a wrapper class name that matches a negative keyword.

### 3. Hybrid Candidate Scoring
The extractor combines two complementary scoring systems:

#### System A: Direct Container Density Features
For each candidate tag (`div`, `section`, `article`, `main`, `td`), features are extracted:
- `density` = `chars / tag_count`
- `p_ratio` = text inside `<p>` / total text
- `link_density` = text inside `<a>` / total text
- `effective_p_count` = `<p>` count + `floor(<br> count / 2)` *(treats CJK `<br>` line breaks as half-weight paragraphs)*
- `long_run_ratio` = text runs ≥60 chars / total text

**The composite score formula:**
$$ \text{score} = \text{density} \times \log_{10}(\text{chars} + 10) $$
$$ \text{score} \leftarrow \text{score} \times (1.0 + \min(\text{p\_ratio}, 1.0) \times 1.5) $$
$$ \text{score} \leftarrow \text{score} \times (1.0 + \min(\text{long\_run\_ratio}, 1.0) \times 0.6) $$
$$ \text{score} \leftarrow \text{score} \times \max(0.05, (1.0 - \text{link\_density})^2) $$
$$ \text{score} \leftarrow \text{score} \times \max(0.3, 1.0 - \text{short\_ratio} \times 0.7) $$
$$ \text{score} \leftarrow \text{score} \times \text{tag\_type\_multiplier} \times \text{class\_hint\_multiplier} $$

#### System B: Readability-Style Leaf Propagation
Leaf text nodes (`<p>`, `<pre>`, `<blockquote>`, and direct-text `<div>`/`<span>`) are scored based on length, CJK/ASCII punctuation marks, link density, and noise keywords.
- Leaf scores are propagated upwards to the immediate `parent` (1.0× weight) and `grandparent` (0.5× weight).
- Container class/id hints adjust the propagated totals.

Both candidate sets are merged and sorted by score descending.

### 4. Specificity Refinement & Ancestor-Filtered Margin
**Specificity Refinement:** If a child container within the top 12 candidates achieves ≥90% of a parent wrapper's score while holding ≥65% of its text, the child is selected. This isolates the inner content box over a larger outer wrapper container.

**Ancestor-Filtered Margin:** When computing candidate dominance (margin over runner-up), parent and grandparent wrapper nodes naturally score almost identically to their inner children. Standard Readability algorithms treat the parent as the "runner-up", resulting in a false margin of `0.0`. `novel_extractor.py` walks parent chains using Python `is` identity comparison to exclude any candidate that is an ancestor or descendant of the winner when calculating the runner-up score.

### 5. Structural Adjacency Merge Fallback
When no single candidate DOM element dominates (e.g. chapters split across multiple sibling `<div>` tags separated by inline ad banners), the extractor triggers a merge fallback:
1. Filters top candidates scoring ≥35% of the highest score.
2. Verifies structural adjacency using `_structurally_adjacent(tags)` (ensuring candidates share a common parent or grandparent).
3. If combined character count exceeds 1.25× the best single candidate, candidates are merged in document order.

### 6. Scored Title Detection Cascade
Title detection follows a strict priority cascade:
1. **Candidate Headings:** Searches `<h1>`, `<h2>`, `<h3>` inside the candidate, immediately preceding the candidate, or as direct children of the candidate's parent/grandparents. Headings are scored (`h1` +25, `h2` +15, `CHAPTER_TITLE_RE` +40, negative container -60).
2. **Meta Tags:** OpenGraph (`og:title`) or Twitter (`twitter:title`).
3. **Page Headings:** Highest-scoring `<h1>`/`<h2>` across the body.
4. **HTML `<title>` Tag:** Cleaned using host domain tokens.

**Host-Token Title Cleanup:** Extracts host domain tokens (e.g. `https://read.novelsite.com/1.html` → `["read", "novelsite"]`). When splitting `<title>` on separators (`-`, `|`, `_`, `:`), title parts matching host tokens or site suffix regexes (`SITEISH_END_RE`, `SITEISH_CN_END_RE` like `小说网`, `阅读`) are stripped from the ends. The first remaining non-site segment is chosen as the title.

### 7. Post-Cleaning with Safety Guards
Once the winning content container is selected, post-cleaning prunes internal noise:
- **60% Text Volume Safety Guard:** Before decomposing a negative-attribute element, the extractor checks `el_text / total_text`. If the element contains >60% of the container's total text, pruning is skipped.
- **Text-Prefix Notice Removal:** Prunes elements whose visible text starts with site warning strings (e.g. `温馨提示:`, `提示：`, `最新网址:`).
- **Author Note Pruning:** Detects author note indicator strings (`author's note`, `作者的话`, `作者说`, `ps.`) and prunes that paragraph along with all subsequent siblings.
- **Contextual Anchor Handling:** Navigation links or short anchor tags (<40 chars) matching UI label patterns are decomposed (`a.decompose()`), while prose links within story paragraphs are unwrapped (`a.unwrap()`).

### 8. Leaf-Only Text Serialization
To avoid duplicate text output when iterating over nested block structures (e.g., `<div><p>Paragraph text</p></div>`), serialization emits only leaf block elements:
```python
for el in root.find_all(TEXT_BLOCK_TAGS):
    if el.parent is None: continue
    if el.find(TEXT_BLOCK_TAGS): continue  # Has block children; let those emit instead
    add_raw(el.get_text(" ", strip=False))
```
`<br>` tags are converted to line breaks `\n` prior to extraction. Block elements insert double newlines (`\n\n`) to preserve paragraph structure. Consecutive identical lines and detected title headings are deduplicated.

### 9. Weighted Multi-Factor Confidence Estimation
Instead of multiplying 6 small fractions together (which artificially depresses confidence to ~0.3), core quality signals are combined via a weighted sum and then scaled by penalty multipliers:

$$ \text{core\_score} = 0.30 \cdot \text{length\_comp} + 0.35 \cdot \text{text\_comp} + 0.20 \cdot \text{margin\_comp} + 0.15 \cdot \text{ratio\_comp} $$
$$ \text{confidence} = \text{core\_score} \times \text{link\_comp} \times \text{mode\_mult} \times \text{negative\_mult} \times \text{hint\_mult} $$

Where:
- `length_comp`: `min(1.0, chars / 1200.0)`
- `text_comp`: `0.35 + 0.65 * min(1.0, max(p_ratio, long_run_ratio * 0.8))`
- `margin_comp`: `0.55 + 0.45 * min(1.0, margin * 2.0)` *(using identity-filtered margin)*
- `ratio_comp`: Penalizes full-page grabs where extracted text >95% of total page chars.
- `link_comp`: `max(0.0, 1.0 - link_density * 1.5)`
- `confidence` is capped at `0.95`.

---

## AI Script Synthesis & Bug Fix Audit

`novel_extractor.py` was built by analyzing and synthesizing the strongest components from 5 AI-generated scripts:

| Source Script | Contribution Cherry-Picked |
| :--- | :--- |
| **Qwen** (1401 lines) | Leaf-only serialization, specificity refinement pass, logarithmic length scoring, host-token title cleanup, multi-factor confidence architecture. |
| **Claude** (659 lines) | Readability-style leaf-to-parent score propagation, quadratic link-density penalty formula $(1.0 - ld)^2$, page-ratio confidence score component. |
| **Z-AI** (698 lines) | Identity-based ancestor filtering (`_is_ancestor_or_descendant`), CJK `<br>` half-weight paragraph counting, text-prefix notice detection (`溫馨提示:`), 60% text volume safety guard, Traditional Chinese keyword support. |
| **Kimi** (952 lines) | Author note trailing paragraph removal (`作者的话`, `author's note`), parser fallback handling. |
| **DeepSeek** (526 lines) | ARIA noise role removal (`role="navigation"`), Chinese site suffix regex patterns (`小说网`, `读书网`). |

### Critical Bugs Fixed From Prior Implementation Attempts
- **Fixed DeepSeek `\s+` Newline Flattening Bug:** `re.sub(r'\s+', ' ', text)` converted all newlines into spaces before paragraph splitting could execute. Fixed by handling line breaks separately from inline spaces.
- **Fixed Kimi Fast-Path Premature Short-Circuit:** Kimi returned `confidence >= 0.85` fast-path results without scoring alternatives. Fixed by always evaluating fast-path candidates inside the main candidate pool.
- **Fixed Kimi/DeepSeek Pre-Clean Data Loss:** Removing negative-class containers during pre-cleaning caused data loss when story text lived inside an ambiguously named wrapper. Fixed by penalizing negative classes during scoring and deferring removal to post-cleaning with safety guards.
- **Extended Claude 2-Sibling Merge Constraint:** Generalized sibling container merging to $N$ adjacent candidates sharing a parent or grandparent.
- **Fixed Claude Title Segment Selection:** Claude selected the longest split segment from `<title>`, often picking site descriptions over short chapter titles. Fixed by preferring the first non-site segment.
- **Fixed BeautifulSoup `.attrs` NoneType Crash:** Added `getattr(tag, "attrs", None)` checks in `_attr_text` and `_is_hidden` to handle special BS4 nodes safely.

---

## Installation & Dependencies

### System Requirements
- Python 3.8+
- `beautifulsoup4`
- `lxml` (recommended for fast parsing; falls back to standard `html.parser` if missing)
- `requests` (optional; required only for fetching live URLs in the test harness)

### Installation
```bash
pip install beautifulsoup4 lxml requests
```

---

## CLI Usage

```bash
# Process a local HTML file
python novel_extractor.py chapter.html

# Process a live URL
python novel_extractor.py "https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother"

# Output as JSON
python novel_extractor.py chapter.html --json

# Set content preview length (default 500 chars)
python novel_extractor.py chapter.html --preview 800

# Run built-in test harness
python novel_extractor.py --test

# Run test harness against a file containing URLs (one per line)
python novel_extractor.py --test my_urls.txt
```

---

## Python API Usage

```python
from novel_extractor import extract_chapter

# Option A: Extract from raw HTML string
html_content = "<html>...</html>"
result = extract_chapter(html_content, url="https://example.com/chapter-1")

print("Title:     ", result["title"])
print("Confidence:", result["confidence"])
print("Content:   ", result["content"])
```

### Return Contract
```json
{
  "title": "String — Chapter title",
  "content": "String — Clean chapter text with paragraphs separated by \\n\\n",
  "confidence": "Float between 0.0 and 0.95"
}
```

---

## Kotlin / JS Porting Guide for Mobile WebView

This Python prototype is designed to be ported to Kotlin / JavaScript for injection into an Android WebView novel reader app with TTS.

### Key Guidelines for Kotlin/JS Porting
1. **DOM Traversal via Standard JS APIs:**
   - Use `document.querySelectorAll('div, section, article, main, p')` for candidate collection.
   - Use `element.parentElement` for parent and grandparent propagation.
2. **Identity Comparison in JS:**
   - Use strict reference equality (`nodeA === nodeB`) for `_is_ancestor_or_descendant` checking.
3. **Character Length Function in JS:**
   - Replace Python regex `_scoring_len` with JS Unicode regex:
     ```javascript
     function scoringLen(text) {
       if (!text) return 0;
       return text.replace(/[^\p{L}\p{N}]/gu, '').length;
     }
     ```
4. **Node Deletion vs. Detachment:**
   - Perform post-cleaning on a cloned DOM fragment (`element.cloneNode(true)`) to avoid mutating the live WebView display.
5. **Handling Line Breaks:**
   - Convert `<br>` elements to `\n` before extracting `textContent`.

### Downstream Confidence Thresholds
- **`≥ 0.75`:** Trust extraction automatically for TTS reading.
- **`0.45 – 0.75`:** Present extracted text with a toggle to view full original page.
- **`< 0.45`:** Prompt user for manual text selection or fall back to full-page reader mode.
