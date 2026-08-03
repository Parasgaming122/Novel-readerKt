#!/usr/bin/env python3
"""
novel_extractor.py
==================

Best-of-breed generic chapter-text extractor for novel-reading websites.

Given raw HTML from ANY novel site's chapter page, isolates the actual
chapter title + body text and strips out navigation, ads, comments,
"recommended novels", author notes, website chrome, etc.

This is a prototyping/testing script for the content-extraction layer
of a WebView-based Android novel reader app with TTS. Once validated,
the logic will be ported to Kotlin/JS.

Algorithm — 9-stage pipeline combining Readability.js-style propagation
with multi-signal density heuristics:
  1. Parse HTML (lxml preferred, html.parser fallback)
  2. Pre-clean: remove junk tags, hidden elements, ARIA noise roles
  3. Fast-path: check <article>, <main>, role="main" as candidate hints
  4. Density scoring: feature extraction + Readability-style leaf→parent
     score propagation, combined with direct container scoring
  5. Selection: specificity refinement, identity-filtered runner-up,
     structurally-adjacent merge fallback
  6. Title detection: scored headings → OG/Twitter meta → page headings
     → <title> with host-token-aware suffix stripping
  7. Post-clean: noise pruning with 60% safety guard, text-prefix noise,
     author-note removal, contextual anchor handling
  8. Text serialization: leaf-only block traversal, <br> handling,
     title dedup
  9. Confidence estimation: multi-factor product (length, text quality,
     link density, margin, mode, hints)

Dependencies: pip install beautifulsoup4 lxml requests

Usage:
    python novel_extractor.py chapter.html
    python novel_extractor.py https://example.com/chapter-1.html
    python novel_extractor.py file1.html file2.html --preview 800
    python novel_extractor.py --test                # built-in URL list
    python novel_extractor.py chapter.html --json
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import namedtuple
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlparse

try:
    from bs4 import BeautifulSoup, Comment, NavigableString, Tag
except ImportError:
    sys.exit(
        "BeautifulSoup4 is required.\n"
        "Install with: pip install beautifulsoup4 lxml"
    )

# Optional — only needed for fetching live URLs in the test harness.
try:
    import requests
except ImportError:
    requests = None


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

# Candidate: a scored DOM container for chapter content.
Candidate = namedtuple("Candidate", "score tag feat")


# ---------------------------------------------------------------------------
# Regex / keyword configuration
#
# These are generic heuristic patterns — no per-site rules.
# Tune carefully against real sites over time.
# ---------------------------------------------------------------------------

# Collapse whitespace for normalized visible text.
_WS_RE = re.compile(r"\s+", re.UNICODE)

# Used only for SCORING.
# Removes punctuation/symbols so English and Chinese text are scored by
# meaningful characters, not whitespace-delimited words.
# Final extracted output is NOT passed through this — punctuation preserved.
_SCORING_STRIP_RE = re.compile(r"[^\w\s]|_", re.UNICODE)

# Soft positive class/id hints.
# These boost a candidate's score — they don't guarantee selection.
HINT_ATTR_RE = re.compile(
    r"chapter|content|article|entry|post|text|body|read|reader|story|fiction"
    r"|novel|book|txt|main|bq|contents|"
    r"chaptercontent|readcontent|booktxt|novelcontent|entry-content|"
    r"article-content|chapter-content|book-content|story-content|"
    r"fiction-content|main-content|post-content|entry-body|article-body|"
    r"story-body|chapter-body|txtcontent|noveltext|booktext|chaptertext|"
    r"readtext|fictiontext|storytext|"
    r"内容|章节|正文|小说|阅读",
    re.I,
)

# Soft negative class/id hints.
# Applied as penalties during scoring and as removal hints during cleaning.
NEGATIVE_ATTR_RE = re.compile(
    r"comment|reply|replies|recommend|related|popular|hot|ranking|rank|"
    r"nav|menu|footer|sidebar|widget|share|social|"
    r"advert|adsense|banner|promo|sponsor|disqus|breadcrumb|"
    r"pagination|pager|\bprev(?:ious)?\b|\bnext\b|chapter-nav|"
    r"author|note|footnote|copyright|toc|directory|catalog|bookmark|"
    r"vote|donate|reward|tip|login|register|search|subscribe|"
    r"popup|modal|overlay|signup|newsletter|"
    r"(?<![A-Za-z0-9_])ad[s_.-]?|"
    r"评论|回复|推荐|相关|热门|排行|导航|菜单|页脚|页眉|侧边|分享|"
    r"广告|版权|声明|目录|书架|加入书签|打赏|投票|上一章|下一章|"
    r"作者有话说|作者说|书友|互动|登录|注册|搜索|订阅|"
    # Traditional Chinese variants (from Z-AI's battle-tested list)
    r"評論|推薦|相關|導航|廣告|贊助|側邊|登入|註冊|聯絡我們",
    re.I,
)

# Noise keywords for text-content prefix matching.
# From Z-AI: catches un-classed notice elements by their visible text.
# Both Simplified and Traditional Chinese variants included.
NOISE_TEXT_PREFIXES = [
    "温馨提示", "溫馨提示",  # Warm reminder (S/T)
    "提示：", "提示:\u3000",  # Hint: (full-width / ASCII colon)
    "免费小说", "免費小說",  # Free novel (S/T)
    "本站提示",              # Site reminder
    "本章未完结", "本章未完結",  # Chapter not finished (S/T)
    "最新网址", "最新網址",  # Latest URL (S/T)
]

# Used for removing small UI-like text blocks after candidate selection.
NEGATIVE_LABEL_RE = re.compile(
    r"^\s*[«»<>\[\]【】「」『』\s]*("
    r"上一章|下一章|前一章|后一章|目录|书架|加入书签|推荐本书|"
    r"推荐|相关推荐|推荐阅读|评论|打赏|投票|分享|广告|作者有话说|"
    r"作者说|版权声明|免责声明|"
    # Traditional Chinese
    r"上一章|下一章|目錄|書架|推薦|評論|"
    r"prev(?:ious)?\s*chapter|next\s*chapter|"
    r"table\s*of\s*contents|"
    r"recommended(?:\s*(?:novels?|books?|chapters?|reading))?|"
    r"related\s*(?:novels?|books?|chapters?)?|"
    r"comments?(?:\s*\(\d+\))?|"
    r"share(?:\s*(?:this|on|to))?|"
    r"donate|bookmark|advertisement"
    r")[»<>\[\]【】「」『』\s\d.:：,，!！?？_-]*$",
    re.I,
)

# Helps prefer headings that look like chapter titles.
CHAPTER_TITLE_RE = re.compile(
    r"\b(?:chapter|chap|episode|part|section|book|volume|prologue|epilogue)"
    r"\s*[\dIVXLCDM]+|"
    r"第\s*[0-9〇零一二三四五六七八九十百千两\d]+\s*[章节卷回部集篇]|"
    r"^\s*\d+\s*[\.、]\s*|"
    r"^\s*[IVXLCDM]+\s*[:.、]?\s*$",
    re.I,
)

# Author note detection patterns for trailing paragraph removal.
# From Kimi: removes author notes and everything after them.
AUTHOR_NOTE_PATTERNS = [
    "author's note", "author note", "a/n", "author:",
    "作者的话", "作者说", "作者有话说", "译者的话",
    "ps.", "p.s.", "note:", "notes:",
]

# Hidden-element detection.
STYLE_HIDDEN_RE = re.compile(r"display\s*:\s*none|visibility\s*:\s*hidden", re.I)
HIDDEN_RE = re.compile(
    r"(?<![\w-])(hidden|sr-only|visually-hidden|display-none)(?![\w-])",
    re.I,
)

# Title cleanup: split common title separators.
TITLE_SEP_RE = re.compile(r"\s*[\|｜]\s*|\s*[-–—]\s*|\s*[:：]\s*|\s*_+\s*")

# Generic site-like suffixes for <title> cleanup.
SITEISH_END_RE = re.compile(
    r"(novels?|read(?:ing|er)?|books?|fictions?|stor(?:y|ies)|web|site|"
    r"home|online|free|latest|txt|download|app|official|portal|hub|zone|"
    r"space|club|net|org)$",
    re.I,
)
SITEISH_CN_END_RE = re.compile(
    r"(小说|阅读|阅读器|章节|正文|网|书屋|书城|文学|在线|免费|最新|书网|书斋|"
    r"小说网|读书网|中文网|阅读网|文学网|全文|最新章节)$"
)

# Generic host tokens to ignore when cleaning <title>.
GENERIC_HOST_TOKENS = {
    "www", "m", "wap", "app", "blog", "html", "php", "asp", "aspx", "jsp",
    "cgi", "com", "org", "net", "edu", "gov", "io", "co", "us", "uk", "cc",
    "top", "xyz", "vip", "club", "site", "online", "store", "dev", "test",
    "localhost", "127", "0",
}

# Tags that are almost never useful for TTS chapter text.
NON_TEXT_TAGS = [
    "script", "style", "noscript", "template", "iframe", "svg", "math",
    "canvas", "audio", "video", "object", "embed", "form", "button",
    "input", "select", "textarea", "label", "option", "datalist", "dialog",
    "img", "picture", "source", "hr", "track", "map", "area", "frame",
    "frameset", "applet",
]

# Candidate container tags for density scoring.
CANDIDATE_TAGS = [
    "div", "section", "article", "main", "td", "center", "body",
]

# Tags eligible to be scored as content leaves (Readability propagation).
# From Claude: where actual prose lives.
LEAF_TAGS = {"p", "pre", "blockquote"}

# Minimum content chars for a leaf text run to count as real prose.
MIN_PARAGRAPH_CHARS = 20

# Block-like tags used when converting the selected root into final text.
TEXT_BLOCK_TAGS = [
    "p", "div", "section", "article", "main", "blockquote", "pre", "li",
    "h1", "h2", "h3", "h4", "h5", "h6", "td", "th", "figure", "figcaption",
    "dd", "dt",
]

# During final cleaning, remove these descendants from the selected block.
CLEAN_REMOVE_TAGS = NON_TEXT_TAGS + ["nav", "footer", "header", "aside"]

# Content character regex for explicit CJK+Latin counting.
# From Claude: explicit ranges so we don't accidentally count underscores.
CONTENT_CHAR_RE = re.compile(
    r"["
    r"a-zA-Z0-9"
    r"\u4e00-\u9fff"    # CJK Unified Ideographs
    r"\u3400-\u4dbf"    # CJK Extension A
    r"\uf900-\ufaff"    # CJK Compatibility Ideographs
    r"\u3040-\u30ff"    # Hiragana + Katakana
    r"\uac00-\ud7af"    # Hangul syllables
    r"]"
)


# ---------------------------------------------------------------------------
# Small text helpers
# ---------------------------------------------------------------------------

def _norm(s):
    """Normalize whitespace for display/text comparison."""
    if s is None:
        return ""
    return _WS_RE.sub(" ", str(s)).strip()


def _scoring_len(s):
    """
    Language-agnostic scoring length.

    Uses character count after removing whitespace and punctuation.
    This avoids word-count bias against Chinese, which has no spaces.
    """
    if not s:
        return 0
    s = _SCORING_STRIP_RE.sub("", s)
    s = _WS_RE.sub("", s)
    return len(s)


def _content_char_count(text):
    """
    Explicit CJK+Latin content-character count.

    From Claude's approach: counts only letters, digits, and CJK ideographs.
    Used for confidence ratio calculations where precision matters.
    """
    return len(CONTENT_CHAR_RE.findall(text or ""))


def _attr_text(tag):
    """
    Concatenate useful attribute values for class/id heuristic matching.
    Lowercased.
    """
    if not isinstance(tag, Tag) or getattr(tag, "attrs", None) is None:
        return ""

    parts = []
    for attr_name in (
        "id", "class", "role", "aria-label", "title", "itemprop",
        "data-type", "data-module", "data-component", "data-name",
    ):
        value = tag.get(attr_name)
        if not value:
            continue
        if isinstance(value, list):
            value = " ".join(str(x) for x in value)
        parts.append(str(value))

    return " ".join(parts).lower()


def _is_hidden(tag):
    """
    Heuristic for hidden UI elements.
    Hidden elements are usually not the main chapter text.
    """
    if not isinstance(tag, Tag) or getattr(tag, "attrs", None) is None:
        return False

    style = tag.get("style") or ""
    if isinstance(style, list):
        style = " ".join(style)

    if STYLE_HIDDEN_RE.search(style):
        return True

    if str(tag.get("aria-hidden", "")).lower() == "true":
        return True

    if tag.has_attr("hidden"):
        return True

    attrs = _attr_text(tag)
    if HIDDEN_RE.search(attrs):
        return True

    return False


def _looks_like_noise_text(el):
    """
    Text-prefix noise detection.

    From Z-AI: catches un-classed notice elements (e.g. 溫馨提示:...)
    that don't have negative class/id attributes but contain site boilerplate.
    """
    text_stripped = el.get_text(strip=True)
    if text_stripped:
        for prefix in NOISE_TEXT_PREFIXES:
            if text_stripped.startswith(prefix):
                return True
    return False


# ---------------------------------------------------------------------------
# Stage 1: Parsing
# ---------------------------------------------------------------------------

def _parse_soup(markup):
    """
    Parse HTML bytes or string.
    Prefer lxml if available; fall back to Python's html.parser.
    """
    try:
        return BeautifulSoup(markup, "lxml")
    except Exception:
        return BeautifulSoup(markup, "html.parser")


# ---------------------------------------------------------------------------
# Stage 2: Pre-cleaning
# ---------------------------------------------------------------------------

def _pre_clean(soup):
    """
    Remove elements that should not contribute to scoring.

    Strips:
      - HTML comments
      - Junk tags (script, style, iframe, form, etc.)
      - Hidden elements (display:none, aria-hidden, etc.)
      - ARIA noise roles (navigation, banner, complementary, contentinfo)

    Does NOT remove <nav>, <footer>, <header>, <aside> here — those are
    penalized during scoring and removed during post-clean. This avoids
    the data-loss bug in Kimi/DeepSeek where negative-class elements are
    removed before scoring, potentially losing content with confusingly-
    named wrappers.
    """
    # Remove HTML comments (often ad slots / tracking snippets).
    for comment in soup.find_all(string=lambda text: isinstance(text, Comment)):
        comment.extract()

    # Remove non-text/UI tags.
    for tag in soup.find_all(NON_TEXT_TAGS):
        tag.decompose()

    # Remove hidden elements.
    for tag in list(soup.find_all(True)):
        if isinstance(tag, Tag) and _is_hidden(tag):
            tag.decompose()

    # Remove ARIA noise roles (from DeepSeek).
    for el in soup.find_all(attrs={"role": True}):
        if el.parent is None:
            continue
        role = (el.get("role") or "").lower()
        if role in ("navigation", "banner", "complementary", "contentinfo"):
            el.decompose()


# ---------------------------------------------------------------------------
# Stage 3 & 4: Feature extraction, scoring, and propagation
# ---------------------------------------------------------------------------

def _element_features(tag, text=None, chars=None):
    """
    Compute heuristic features for one candidate container.

    Signals (from Qwen's comprehensive approach):
      - chars: punctuation-stripped character count
      - density: chars / descendant tag count
      - p_ratio: how much text is inside <p>
      - link_density: how much text is inside <a>
      - long_run_ratio: long consecutive text chunks (≥60 chars)
      - short_string_ratio: many short UI-like strings (≤30 chars)
      - avg_p: average paragraph length
      - attr hints: positive/negative class/id patterns
    """
    if text is None:
        text = _norm(tag.get_text(" ", strip=True))
    if chars is None:
        chars = _scoring_len(text)

    all_tags = tag.find_all(True)
    tag_count = len(all_tags) + 1

    # Link text is usually navigation/recommendations, not prose.
    link_chars = 0
    for a in tag.find_all("a"):
        link_chars += _scoring_len(_norm(a.get_text(" ", strip=True)))
    link_density = link_chars / chars if chars else 1.0

    # Paragraphs are a strong prose signal.
    p_tags = tag.find_all("p")
    p_chars = 0
    for p in p_tags:
        p_chars += _scoring_len(_norm(p.get_text(" ", strip=True)))
    p_ratio = p_chars / chars if chars else 0.0
    p_count = len(p_tags)
    avg_p = p_chars / p_count if p_count else 0.0

    # From Z-AI: count <br> as half-weight paragraphs for CJK sites
    # that use <br> inside <div> instead of <p>.
    br_count = len(tag.find_all("br"))
    effective_p_count = p_count + (br_count // 2)

    # Text-node/run statistics.
    string_lens = []
    for s in tag.stripped_strings:
        string_lens.append(_scoring_len(s))

    total_strings = len(string_lens)
    short_strings = sum(1 for length in string_lens if 0 < length <= 30)
    long_run_chars = sum(length for length in string_lens if length >= 60)

    short_string_ratio = short_strings / total_strings if total_strings else 0.0
    long_run_ratio = long_run_chars / chars if chars else 0.0

    # Attribute hints.
    attrs = _attr_text(tag)
    hint = bool(HINT_ATTR_RE.search(attrs))
    negative = bool(NEGATIVE_ATTR_RE.search(attrs))

    # A parent hint can help when the real container is an unclassed inner div.
    parent_hint = False
    if tag.parent is not None and isinstance(tag.parent, Tag):
        parent_hint = bool(HINT_ATTR_RE.search(_attr_text(tag.parent)))

    density = chars / tag_count if tag_count else 0.0

    return {
        "chars": chars,
        "tag_count": tag_count,
        "density": density,
        "link_chars": link_chars,
        "link_density": link_density,
        "p_chars": p_chars,
        "p_ratio": p_ratio,
        "p_count": p_count,
        "effective_p_count": effective_p_count,
        "avg_p": avg_p,
        "long_run_chars": long_run_chars,
        "long_run_ratio": long_run_ratio,
        "short_string_ratio": short_string_ratio,
        "hint": hint,
        "negative": negative,
        "parent_hint": parent_hint,
    }


def _score_tag(tag, text=None, chars=None):
    """
    Score a candidate container using density heuristics.

    Higher score = more likely to be the main chapter content.
    Combines Qwen's comprehensive formula with Claude's quadratic
    link penalty.
    """
    feat = _element_features(tag, text=text, chars=chars)

    # Ignore very small blocks as main content candidates.
    if feat["chars"] < 60:
        return 0.0, feat

    score = feat["density"]

    # Reward larger blocks, but sub-linearly (from Qwen).
    score *= math.log10(feat["chars"] + 10)

    # Reward paragraph-heavy blocks.
    score *= 1.0 + min(feat["p_ratio"], 1.0) * 1.5

    # Reward long average paragraph length.
    if feat["avg_p"] >= 80:
        score *= 1.15
    if feat["avg_p"] >= 200:
        score *= 1.15

    # Reward having multiple paragraphs (including br-counted), capped.
    score *= 1.0 + min(feat["effective_p_count"], 30) * 0.02

    # Reward long text runs even when <p> is not used (CJK <br> layouts).
    score *= 1.0 + min(feat["long_run_ratio"], 1.0) * 0.6

    # Penalize link-heavy blocks hard using quadratic suppression (Claude).
    # Nav bars, recommended lists, prev/next clusters are link-heavy.
    link_density = min(1.0, feat["link_density"])
    if link_density > 0:
        score *= max(0.05, (1.0 - link_density) ** 2)

    # Penalize many short strings (comments, menus, labels, UI widgets).
    short_ratio = min(1.0, feat["short_string_ratio"])
    if feat["p_ratio"] < 0.4:
        score *= max(0.3, 1.0 - short_ratio * 0.7)
    else:
        # If it is very paragraph-heavy, be gentler.
        score *= max(0.6, 1.0 - short_ratio * 0.3)

    # Structural tag bonuses/penalties.
    if tag.name in ("article", "main"):
        score *= 1.6
    elif tag.name == "section":
        score *= 1.05
    elif tag.name == "div":
        score *= 1.0
    elif tag.name == "td":
        score *= 0.85
    elif tag.name == "body":
        score *= 0.75
    else:
        score *= 0.6

    # Attribute hints.
    if feat["hint"]:
        score *= 1.5
    if feat["parent_hint"]:
        score *= 1.1
    if feat["negative"]:
        score *= 0.25

    # Extra structural penalties.
    if tag.name in ("nav", "footer", "aside"):
        score *= 0.2
    if tag.name == "header":
        score *= 0.5

    return score, feat


def _collect_density_candidates(soup):
    """
    Collect and score candidate containers using density heuristics.
    """
    candidates = []
    root = soup.body or soup.find("html") or soup
    tags = soup.find_all(CANDIDATE_TAGS)

    # Always consider the root/body as a fallback candidate.
    if not any(tag is root for tag in tags):
        tags.append(root)

    for tag in tags:
        if not isinstance(tag, Tag):
            continue

        text = _norm(tag.get_text(" ", strip=True))
        chars = _scoring_len(text)
        if chars < 60:
            continue

        score, feat = _score_tag(tag, text=text, chars=chars)
        if score > 0:
            candidates.append(Candidate(score=score, tag=tag, feat=feat))

    candidates.sort(key=lambda c: c.score, reverse=True)
    return candidates


def _fast_path_candidates(soup):
    """
    Fast-path semantic checks (from Qwen).

    Look for <article>, <main>, role="main". These are strong structural
    signals, but are still scored and compared against density candidates.
    NOT short-circuited like Kimi does — that causes missed detections.
    """
    tags = soup.find_all(["article", "main"])

    for tag in soup.find_all(attrs={"role": "main"}):
        if not any(tag is existing for existing in tags):
            tags.append(tag)

    out = []
    for tag in tags:
        if not isinstance(tag, Tag):
            continue

        text = _norm(tag.get_text(" ", strip=True))
        chars = _scoring_len(text)
        if chars < 120:
            continue

        score, feat = _score_tag(tag, text=text, chars=chars)

        # Only fast-path semantic containers that don't look link-heavy.
        if feat["link_density"] <= 0.35 and not feat["negative"] and score > 0:
            out.append(Candidate(score=score, tag=tag, feat=feat))

    out.sort(key=lambda c: c.score, reverse=True)
    return out


# Readability-style leaf scoring + propagation (from Claude).
# This is a second scoring system that complements the density approach.
# The leaf→parent propagation finds content containers even when the
# container itself has no class hints — the <p> children carry the signal.

def _score_leaf(tag):
    """
    Score a single text-bearing leaf (p/pre/blockquote/div-with-text).
    From Claude: base + length + punctuation - link_density - noise.
    """
    text = tag.get_text()
    n_chars = _content_char_count(text)
    if n_chars < 5:
        return 0.0

    score = 1.0  # base score for existing as a text node
    # Reward length with diminishing returns.
    score += min(n_chars / 100.0, 6.0)
    # Reward sentence-ish punctuation (weak prose signal).
    punct = len(re.findall(r"[,\uff0c.\u3002;\uff1b]", text))
    score += min(punct / 5.0, 3.0)

    # Link density penalty — quadratic (from Claude).
    total = _content_char_count(tag.get_text())
    if total > 0:
        link_chars = sum(_content_char_count(a.get_text()) for a in tag.find_all("a"))
        ld = min(1.0, link_chars / total)
        score *= (1.0 - ld) ** 2

    # Penalize very short text disguised as "paragraph" (UI labels).
    if n_chars < MIN_PARAGRAPH_CHARS:
        score *= 0.3

    # Penalize if class/id matches noise keywords.
    attrs = _attr_text(tag)
    if NEGATIVE_ATTR_RE.search(attrs):
        score *= 0.15

    return score


def _collect_leaves(root):
    """
    Find candidate leaf nodes for Readability-style propagation.

    From Claude: <p>/<pre>/<blockquote>, plus <div>/<span> that carry
    direct text — some novel sites dump chapter text into <div> separated
    by <br> instead of using <p>.
    """
    leaves = []
    for tag in root.find_all(True):
        if tag.name in LEAF_TAGS:
            leaves.append(tag)
        elif tag.name in ("div", "span"):
            # Direct text = at least one NavigableString child with
            # non-trivial content.
            direct_text = "".join(
                c for c in tag.contents if isinstance(c, NavigableString)
            )
            if _content_char_count(direct_text) >= MIN_PARAGRAPH_CHARS:
                leaves.append(tag)
    return leaves


def _propagation_candidates(root):
    """
    Readability-style score propagation: score leaves, propagate up to
    parent (1.0×) and grandparent (0.5×).

    From Claude: this is the classic Readability trick for finding the
    container that wraps the bulk of real content.
    """
    scores = {}
    tag_by_id = {}

    structural_skip = {"nav", "footer", "header", "aside"}

    leaves = _collect_leaves(root)
    for leaf in leaves:
        s = _score_leaf(leaf)
        if s <= 0:
            continue

        parent = leaf.parent
        grandparent = parent.parent if parent else None

        for node, weight in ((parent, 1.0), (grandparent, 0.5)):
            if node is None or not isinstance(node, Tag):
                continue
            if node.name in structural_skip:
                continue
            key = id(node)
            tag_by_id[key] = node
            scores[key] = scores.get(key, 0.0) + s * weight

    # Apply container-level bonuses/penalties on propagated scores.
    for key, node in tag_by_id.items():
        attrs = _attr_text(node)
        if HINT_ATTR_RE.search(attrs):
            scores[key] *= 1.4
        if NEGATIVE_ATTR_RE.search(attrs):
            scores[key] *= 0.2
        if node.name == "article" or node.get("role") == "main":
            scores[key] *= 1.5

    # Convert to Candidate objects with feature extraction.
    candidates = []
    for key, node in tag_by_id.items():
        prop_score = scores[key]
        if prop_score <= 0:
            continue
        text = _norm(node.get_text(" ", strip=True))
        chars = _scoring_len(text)
        if chars < 60:
            continue

        _, feat = _score_tag(node, text=text, chars=chars)
        candidates.append(Candidate(score=prop_score, tag=node, feat=feat))

    candidates.sort(key=lambda c: c.score, reverse=True)
    return candidates


# ---------------------------------------------------------------------------
# Stage 5: Candidate selection
# ---------------------------------------------------------------------------

def _is_ancestor(node, possible_ancestor):
    """Return True if possible_ancestor is an ancestor of node.

    Uses Python `is` identity — not BS4 .find() which can be unreliable
    across parser backends (from Z-AI).
    """
    parent = getattr(node, "parent", None)
    while parent is not None:
        if parent is possible_ancestor:
            return True
        parent = parent.parent
    return False


def _is_ancestor_or_descendant(a, b):
    """Return True if a is b, or one contains the other.

    From Z-AI: identity-based ancestor filtering for accurate runner-up
    calculation. Prevents wrapper elements from crushing confidence margins.
    """
    if a is b:
        return True
    return _is_ancestor(a, b) or _is_ancestor(b, a)


def _structurally_adjacent(tags):
    """
    Adjacency check for merge fallback (from Qwen).

    Allow merge if selected blocks share a parent or grandparent.
    This avoids merging random blocks from unrelated page regions.
    """
    if len(tags) < 2:
        return True

    parents = {tag.parent for tag in tags if tag.parent is not None}
    if len(parents) == 1:
        return True

    grandparents = {
        tag.parent.parent
        for tag in tags
        if tag.parent is not None and tag.parent.parent is not None
    }
    return len(grandparents) == 1


def _choose_selection(candidates):
    """
    Choose either:
      - one dominant candidate (with specificity refinement)
      - a merged set of adjacent strong candidates

    Returns:
        (selection_list, mode, margin)
        mode is "single", "merged", or "none"
    """
    if not candidates:
        return [], "none", 1.0

    best = candidates[0]

    # Specificity refinement (from Qwen):
    # Prefer a more specific descendant if it is almost as strong.
    # Example: <div class="content"> (high score via hint) contains
    #          <div id="chaptertext"> (actually cleaner, fewer noise siblings)
    for cand in candidates[1:12]:
        if cand.score >= best.score * 0.90 and _is_ancestor(cand.tag, best.tag):
            if cand.feat["chars"] >= best.feat["chars"] * 0.65:
                best = cand
                break

    remaining = [c for c in candidates if c.tag is not best.tag]

    # For margin, compare against an unrelated candidate (from Z-AI).
    # This prevents wrapper elements from artificially depressing confidence.
    unrelated = [
        c for c in candidates
        if not _is_ancestor_or_descendant(c.tag, best.tag)
    ]
    second = unrelated[0] if unrelated else (remaining[0] if remaining else None)

    if second and best.score:
        margin = (best.score - second.score) / best.score
        margin = max(0.0, min(1.0, margin))
    else:
        margin = 1.0

    # Merge fallback (from Qwen + Z-AI):
    # If no single block is dominant, merge multiple strong adjacent blocks.
    if best.feat["chars"] < 300 or margin < 0.25:
        strong = [
            c for c in candidates[:10]
            if c.score >= best.score * 0.35 and c.feat["chars"] >= 80
        ]

        specific = []
        for cand in strong:
            if not any(_is_ancestor_or_descendant(cand.tag, s.tag) for s in specific):
                specific.append(cand)

        if len(specific) >= 2:
            total_chars = sum(c.feat["chars"] for c in specific)
            tags = [c.tag for c in specific]

            if total_chars > best.feat["chars"] * 1.25 and _structurally_adjacent(tags):
                specific.sort(key=lambda c: (getattr(c.tag, "sourceline", 0) or 0))
                return specific, "merged", margin

    return [best], "single", margin


def _select_content(soup):
    """
    Main selection logic combining both scoring systems.

    Uses BOTH density scoring and Readability-style propagation,
    picking the winner from the combined pool.
    """
    # Density-scored candidates.
    density_cands = _collect_density_candidates(soup)

    # Readability-style propagated candidates.
    root = soup.body or soup.find("html") or soup
    prop_cands = _propagation_candidates(root)

    # Fast-path semantic candidates.
    fast_cands = _fast_path_candidates(soup)

    # Combine all candidates, de-duplicated by tag identity.
    all_cands = fast_cands + density_cands + prop_cands
    seen_tags = []
    unique = []
    for cand in all_cands:
        if not any(cand.tag is existing for existing in seen_tags):
            seen_tags.append(cand.tag)
            unique.append(cand)

    # Sort by score descending.
    unique.sort(key=lambda c: c.score, reverse=True)

    return _choose_selection(unique)


# ---------------------------------------------------------------------------
# Stage 6: Title detection
# ---------------------------------------------------------------------------

def _host_tokens(url):
    """
    Extract useful host tokens for title cleanup (from Qwen).
    Example: https://read.novelsite.com/chapter.html → ["read", "novelsite"]
    """
    if not url:
        return []
    try:
        host = urlparse(url).netloc.lower()
    except Exception:
        return []
    if not host:
        return []

    tokens = re.split(r"[^a-z0-9\u4e00-\u9fff]+", host)
    return [
        t for t in tokens
        if t and t not in GENERIC_HOST_TOKENS and len(t) > 2
    ]


def _is_siteish_title_part(part, host_tokens):
    """
    Return True if a title segment looks like a site suffix.

    From Qwen: dynamic host-token matching + generic suffix regexes.
    """
    low = part.lower()
    chars = _scoring_len(part)

    for token in host_tokens:
        if not token:
            continue
        if len(token) >= 4 and re.search(r"\b" + re.escape(token) + r"\b", low):
            return True
        if token in low and chars <= 12:
            return True

    if chars <= 25 and SITEISH_END_RE.search(low):
        return True
    if chars <= 12 and SITEISH_CN_END_RE.search(part):
        return True

    return False


def _clean_title(raw, url=None):
    """
    Clean a raw title by removing site-name suffixes.

    From Qwen: split on separators, strip site-like parts from ends.
    Fixed from Claude: prefer first segment (not longest) since chapter
    titles are often shorter than site descriptions.
    """
    raw = _norm(raw)
    if not raw:
        return ""

    parts = [p.strip() for p in TITLE_SEP_RE.split(raw) if p.strip()]
    if len(parts) <= 1:
        return raw

    host_tokens = _host_tokens(url)
    work = parts[:]
    changed = False

    # Remove site-like suffixes from the end.
    while work and _is_siteish_title_part(work[-1], host_tokens):
        work.pop()
        changed = True

    # Remove site-like prefixes from the beginning, if still leaving a title.
    while work and len(work) > 1 and _is_siteish_title_part(work[0], host_tokens):
        work.pop(0)
        changed = True

    if not changed:
        return raw

    # If everything looked site-like, keep the longest original segment.
    if not work:
        work = [max(parts, key=_scoring_len)]

    return " - ".join(work)


def _in_negative_container(tag):
    """
    Used by heading scoring to reject headings inside nav/footer/aside
    or strongly negative containers (from Qwen).
    """
    parent = tag.parent
    steps = 0

    while parent is not None and parent.name != "body" and steps < 6:
        if isinstance(parent, Tag):
            if parent.name in ("nav", "footer", "aside"):
                return True
            attrs = _attr_text(parent)
            if NEGATIVE_ATTR_RE.search(attrs) and not HINT_ATTR_RE.search(attrs):
                return True
        parent = parent.parent
        steps += 1

    return False


def _heading_score(tag):
    """Score a possible title heading (from Qwen)."""
    text = _norm(tag.get_text(" ", strip=True))
    chars = _scoring_len(text)

    if chars < 2 or chars > 150:
        return -1.0

    score = float(chars)

    if tag.name == "h1":
        score += 25
    elif tag.name == "h2":
        score += 15
    elif tag.name == "h3":
        score += 8

    attrs = _attr_text(tag)
    if HINT_ATTR_RE.search(attrs):
        score += 20
    if NEGATIVE_ATTR_RE.search(attrs):
        score -= 30

    if CHAPTER_TITLE_RE.search(text):
        score += 40

    if _in_negative_container(tag):
        score -= 60

    return score


def _best_heading(tags):
    """Return best heading tag and its score from an iterable of tags."""
    best = None
    best_score = 0.0
    for tag in tags:
        score = _heading_score(tag)
        if score > best_score:
            best_score = score
            best = tag
    return best, best_score


def _title_from_candidate(candidate, url):
    """
    Try to find a title near the selected content candidate.

    Checks headings inside the candidate AND immediately preceding it
    (many sites put <h1>Chapter Title</h1> just above the content div).
    """
    if not candidate:
        return ""

    tag = candidate.tag

    # Headings inside the candidate.
    inside_best, inside_score = _best_heading(tag.find_all(["h1", "h2", "h3"]))

    # Headings immediately before the candidate.
    prev_best, prev_score = _best_heading(
        tag.find_all_previous(["h1", "h2", "h3"], limit=5)
    )

    # Also check direct children of ancestors (from Z-AI):
    # Only direct children, not deep subtree, to avoid grabbing headers
    # from unrelated sibling branches.
    ancestor_best = None
    ancestor_score = 0.0
    parent = tag.parent
    for _ in range(3):
        if not isinstance(parent, Tag):
            break
        for child in parent.children:
            if isinstance(child, Tag) and child.name in ("h1", "h2") and child is not tag:
                s = _heading_score(child)
                if s > ancestor_score:
                    ancestor_score = s
                    ancestor_best = child
        parent = parent.parent

    # Pick the best among all sources.
    options = [
        (inside_score, inside_best),
        (prev_score, prev_best),
        (ancestor_score, ancestor_best),
    ]
    options.sort(key=lambda x: x[0], reverse=True)
    best_score, best = options[0]

    if best is not None and best_score > 15:
        return _clean_title(best.get_text(" ", strip=True), url)

    return ""


def _title_from_meta(soup, url):
    """Use og:title / twitter:title if present (from Qwen)."""
    for prop in ("og:title", "twitter:title"):
        tag = soup.find("meta", attrs={"property": prop}) or soup.find(
            "meta", attrs={"name": prop}
        )
        if tag and tag.get("content"):
            title = _clean_title(tag.get("content"), url)
            if title and _scoring_len(title) >= 3:
                return title
    return ""


def _title_from_page_headings(soup, url):
    """Fallback to the best h1/h2 on the page."""
    best, score = _best_heading(soup.find_all(["h1", "h2"]))
    if best is not None and score > 10:
        return _clean_title(best.get_text(" ", strip=True), url)
    return ""


def _detect_title(soup, selection, url):
    """
    Title detection cascade (from Qwen, enhanced):
      1. Heading near selected candidate
      2. Meta og:title / twitter:title
      3. Best page h1/h2
      4. <title> tag cleanup
    """
    if selection:
        title = _title_from_candidate(selection[0], url)
        if title and _scoring_len(title) >= 2:
            return title

    title = _title_from_meta(soup, url)
    if title and _scoring_len(title) >= 3:
        return title

    title = _title_from_page_headings(soup, url)
    if title and _scoring_len(title) >= 2:
        return title

    if soup.title and soup.title.string:
        title = _clean_title(soup.title.string, url)
        if title and _scoring_len(title) >= 2:
            return title

    return ""


# ---------------------------------------------------------------------------
# Stage 7: Post-clean
# ---------------------------------------------------------------------------

def _clean_content_root(root):
    """
    Clean the selected candidate in-place before final text extraction.

    Removes:
      - nav/footer/header/aside descendants
      - Hidden elements
      - Negative class/id containers (with 60% safety guard from Z-AI)
      - Link-heavy blocks
      - Small UI labels
      - Text-prefix noise elements (溫馨提示: etc. from Z-AI)
      - Author note trailing paragraphs (from Kimi)
      - Navigation anchors (contextual handling from Qwen)
    """
    # Total text for safety-guard calculations.
    total_text_len = _scoring_len(root.get_text(" ", strip=True))

    # Remove comments.
    for comment in root.find_all(string=lambda text: isinstance(text, Comment)):
        comment.extract()

    # Remove obvious non-content tags.
    for tag in root.find_all(CLEAN_REMOVE_TAGS):
        if tag is root:
            continue
        tag.decompose()

    # Remove hidden elements.
    for tag in list(root.find_all(True)):
        if tag is root or tag.parent is None:
            continue
        if _is_hidden(tag):
            tag.decompose()

    # Remove negative attribute containers with safety guard.
    # From Z-AI: never prune an element holding >60% of the container's text.
    for tag in list(root.find_all(True)):
        if tag is root or tag.parent is None:
            continue

        attrs = _attr_text(tag)
        if not NEGATIVE_ATTR_RE.search(attrs):
            # Also check text-prefix noise (from Z-AI).
            if not _looks_like_noise_text(tag):
                continue

        text = _norm(tag.get_text(" ", strip=True))
        chars = _scoring_len(text)

        # Safety guard: skip if this element holds most of the content.
        if total_text_len > 0 and chars / total_text_len > 0.6:
            continue

        p_chars = 0
        for p in tag.find_all("p"):
            p_chars += _scoring_len(_norm(p.get_text(" ", strip=True)))
        p_ratio = p_chars / max(chars, 1)

        # Keep only if it is large, paragraph-heavy, and has positive hints.
        if not (chars > 800 and p_ratio > 0.6 and HINT_ATTR_RE.search(attrs)):
            tag.decompose()

    # Remove link-heavy blocks (recommended novels, chapter lists, etc.).
    for tag in list(root.find_all(
        ["div", "section", "ul", "ol", "table", "aside", "footer", "nav", "p", "li"]
    )):
        if tag is root or tag.parent is None:
            continue

        text = _norm(tag.get_text(" ", strip=True))
        chars = _scoring_len(text)

        if chars == 0:
            tag.decompose()
            continue

        link_chars = 0
        for a in tag.find_all("a"):
            link_chars += _scoring_len(_norm(a.get_text(" ", strip=True)))

        link_density = link_chars / chars if chars else 1.0

        if (link_density > 0.5 and chars < 500) or link_density > 0.8:
            tag.decompose()

    # Remove author note trailing paragraphs (from Kimi).
    for p in list(root.find_all("p")):
        if p.parent is None:
            continue
        txt = p.get_text(strip=True).lower()
        if any(k in txt for k in AUTHOR_NOTE_PATTERNS):
            # Remove this paragraph and all subsequent siblings.
            for sibling in list(p.find_next_siblings()):
                if sibling.parent is None:
                    continue
                sibling.decompose()
            p.decompose()
            break  # Only the first author note boundary.

    # Remove small UI-like labels.
    for tag in list(root.find_all(True)):
        if tag is root or tag.parent is None:
            continue

        text = _norm(tag.get_text(" ", strip=True))
        chars = _scoring_len(text)

        if 0 < chars <= 80 and NEGATIVE_LABEL_RE.match(text):
            tag.decompose()

    # Handle anchors contextually (from Qwen).
    # If anchor text looks like UI/navigation, remove it.
    # Otherwise unwrap the link but keep the text (prose links).
    for a in list(root.find_all("a")):
        if a.parent is None:
            continue

        text = _norm(a.get_text(" ", strip=True))
        chars = _scoring_len(text)

        if NEGATIVE_LABEL_RE.match(text) or chars < 40:
            a.decompose()
        else:
            a.unwrap()

    # Remove empty tags.
    for tag in list(root.find_all(True)):
        if tag is root or tag.parent is None:
            continue
        if tag.name == "br":
            continue
        text = _norm(tag.get_text(" ", strip=True))
        if not text:
            tag.decompose()


# ---------------------------------------------------------------------------
# Stage 8: Text serialization
# ---------------------------------------------------------------------------

def _extract_text_from_root(root, title):
    """
    Convert cleaned selected root into final TTS-friendly text.

    From Qwen: leaf-node-only block traversal to avoid parent/child
    text duplication. Handles both <p> and <br>-separated layouts.
    """
    title_norm = _norm(title) if title else ""

    # Remove heading(s) that are the detected title (prevent duplication).
    # From Z-AI: decompose them from DOM so they don't appear in body.
    if title_norm:
        for h in root.find_all(["h1", "h2", "h3", "h4", "h5", "h6"]):
            h_text = _norm(h.get_text(" ", strip=True))
            if not h_text:
                continue

            same = h_text == title_norm
            similar = (
                title_norm
                and (h_text in title_norm or title_norm in h_text)
                and abs(_scoring_len(h_text) - _scoring_len(title_norm)) <= 20
            )

            if same or similar:
                h.decompose()

    # Convert <br> into line breaks before text serialization.
    # From Claude: CJK novel sites separate paragraphs with <br><br>.
    for br in root.find_all("br"):
        br.replace_with("\n")

    blocks = []

    def add_text(txt):
        t = _norm(txt)
        if not t:
            return
        if _scoring_len(t) == 0:
            return

        # Skip exact title lines.
        if title_norm:
            if t == title_norm:
                return
            if t.startswith(title_norm) and _scoring_len(t) <= _scoring_len(title_norm) + 30:
                return

        # Skip remaining obvious UI labels.
        if _scoring_len(t) <= 80 and NEGATIVE_LABEL_RE.match(t):
            return

        # Avoid consecutive duplicates.
        if blocks and blocks[-1] == t:
            return

        blocks.append(t)

    def add_raw(raw):
        for line in str(raw).split("\n"):
            add_text(line)

    # If there are no block-level descendants, treat the whole root as prose.
    # This handles simple <div>...<br>...<br>...</div> chapter layouts.
    if not root.find(TEXT_BLOCK_TAGS):
        add_raw(root.get_text(" ", strip=False))
    else:
        # Preserve long direct text outside block children.
        direct_parts = []
        for child in root.children:
            if isinstance(child, NavigableString) and not isinstance(child, Comment):
                direct_parts.append(str(child))

        if direct_parts:
            direct = " ".join(direct_parts)
            if _scoring_len(direct) > 60:
                add_raw(direct)

        # Emit leaf block nodes only (from Qwen).
        # This avoids duplicating text from a <div> that also contains <p>.
        for el in root.find_all(TEXT_BLOCK_TAGS):
            if el.parent is None:
                continue
            if el.find(TEXT_BLOCK_TAGS):
                continue  # has block children; let those emit
            add_raw(el.get_text(" ", strip=False))

    # Last resort.
    if not blocks:
        add_raw(root.get_text(" ", strip=False))

    # If the first block starts with the title, strip that prefix.
    if blocks and title_norm:
        first = blocks[0]
        if first.startswith(title_norm):
            rest = first[len(title_norm):].lstrip(" \t\r\n-—:：.。,，")
            if rest and _scoring_len(rest) > 0:
                blocks[0] = rest
            else:
                blocks.pop(0)

    return "\n\n".join(blocks).strip()


# ---------------------------------------------------------------------------
# Stage 9: Confidence estimation
# ---------------------------------------------------------------------------

def _estimate_confidence(selection, mode, margin, page_chars=0):
    """
    Estimate extraction confidence (from Qwen, enhanced with Claude's
    page-ratio component and Z-AI's identity-filtered margin).

    Multi-factor product: length × text_quality × link × margin × mode × hints
    """
    if not selection:
        return 0.0

    total_chars = sum(c.feat["chars"] for c in selection)
    if total_chars <= 0:
        return 0.0

    total_link_chars = sum(c.feat["link_chars"] for c in selection)
    total_p_chars = sum(c.feat["p_chars"] for c in selection)
    total_long_chars = sum(c.feat["long_run_chars"] for c in selection)

    link_density = min(1.0, total_link_chars / total_chars)
    p_ratio = min(1.0, total_p_chars / total_chars)
    long_ratio = min(1.0, total_long_chars / total_chars)

    # Length component: reaches 1.0 around 1200 scoring chars.
    length_comp = min(1.0, total_chars / 1200.0)

    # Text quality component: paragraph or long-run prose.
    text_comp = 0.35 + 0.65 * min(1.0, max(p_ratio, long_ratio * 0.8))

    # Link penalty.
    link_comp = max(0.0, 1.0 - link_density * 1.5)

    # Dominance/margin component (using identity-filtered margin from Z-AI).
    margin_comp = 0.55 + 0.45 * min(1.0, margin * 2.0)

    # Page-ratio component (from Claude).
    # Real chapter text is usually 5-70% of total page characters.
    # If extracted text is >95% of the page, we probably grabbed <body>.
    if page_chars > 0:
        ratio = total_chars / max(page_chars, 1)
        if ratio > 0.95:
            ratio_comp = 0.3  # suspicious: probably grabbed the whole page
        else:
            ratio_comp = 1.0 - abs(ratio - 0.35) / 0.65
            ratio_comp = max(0.3, min(1.0, ratio_comp))
    else:
        ratio_comp = 0.8  # no page-level info available

    # Mode multiplier.
    mode_mult = 1.0 if mode == "single" else 0.75

    # Hint/negative multipliers.
    negative_mult = 0.7 if any(c.feat["negative"] for c in selection) else 1.0
    hint_mult = 1.05 if any(c.feat["hint"] for c in selection) else 1.0

    # Weighted blend of core quality components (from Claude & Z-AI).
    # A weighted sum prevents multiple ~0.8 factors from artificially crushing
    # confidence to ~0.3 when multiplying them together.
    core_score = (
        0.30 * length_comp
        + 0.35 * text_comp
        + 0.20 * margin_comp
        + 0.15 * ratio_comp
    )

    confidence = (
        core_score
        * link_comp
        * mode_mult
        * negative_mult
        * hint_mult
    )

    return max(0.0, min(0.95, confidence))  # Cap at 0.95 (from Z-AI)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def extract_chapter(html, url=None):
    """
    Extract chapter title/body from raw HTML.

    Args:
        html: str or bytes containing HTML.
        url:  optional source URL, used only for title cleanup.

    Returns:
        {
            "title": str,
            "content": str,
            "confidence": float,
        }
    """
    soup = _parse_soup(html)

    # Compute page-level character count before pre-cleaning.
    page_chars = _scoring_len(soup.get_text()) if soup.body else 0

    # Stage 2: Pre-clean for scoring.
    _pre_clean(soup)

    # Stages 3-5: Candidate scoring and selection.
    selection, mode, margin = _select_content(soup)

    # If nothing reasonable was found, fall back to body with low confidence.
    if not selection:
        root = soup.body or soup.find("html") or soup
        title = _detect_title(soup, [], url)

        _clean_content_root(root)
        content = _extract_text_from_root(root, title)

        final_chars = _scoring_len(content)
        confidence = min(0.2, final_chars / 1000.0) if final_chars else 0.0

        return {
            "title": title,
            "content": content,
            "confidence": round(max(0.0, min(1.0, confidence)), 3),
        }

    # Stage 6: Detect title before destructive final cleaning.
    title = _detect_title(soup, selection, url)

    # Stages 7-8: Clean and extract text from each selected block.
    parts = []
    for cand in selection:
        root = cand.tag

        # Skip if a previous cleaning step removed this root.
        if (
            root.parent is None
            and getattr(root, "name", "") != "body"
            and root is not soup
        ):
            continue

        _clean_content_root(root)
        part = _extract_text_from_root(root, title)
        if part:
            parts.append(part)

    content = "\n\n".join(parts).strip()

    # Stage 9: Confidence estimation.
    confidence = _estimate_confidence(selection, mode, margin, page_chars)

    # Adjust confidence based on final extracted length.
    final_chars = _scoring_len(content)
    if final_chars < 150:
        confidence *= max(0.0, final_chars / 150.0)
    if final_chars == 0:
        confidence = 0.0

    confidence = max(0.0, min(0.95, confidence))

    return {
        "title": title,
        "content": content,
        "confidence": round(confidence, 3),
    }


# ---------------------------------------------------------------------------
# Test harness and CLI
# ---------------------------------------------------------------------------

# Built-in test URLs — a mix of English and Chinese novel sites.
TEST_URLS = [
    # English
    "https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother",
    # Chinese
    "https://www.69shu.com/txt/12345/12345678.html",
]


def _load_source(source, timeout):
    """
    Load HTML from a local file or URL.
    Returns (html_bytes_or_str, url_or_none).
    """
    if source.startswith("http://") or source.startswith("https://"):
        if requests is None:
            raise RuntimeError(
                "requests is required for URL input. Install with: pip install requests"
            )
        resp = requests.get(
            source,
            timeout=timeout,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/126.0.0.0 Safari/537.36"
                ),
                "Accept": (
                    "text/html,application/xhtml+xml,application/xml;"
                    "q=0.9,image/webp,*/*;q=0.8"
                ),
                "Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7",
            },
        )
        resp.raise_for_status()
        # Let BeautifulSoup/UnicodeDammit handle encoding detection.
        # But fix common ISO-8859-1 misdetection for Chinese sites.
        if resp.encoding and resp.encoding.lower() == "iso-8859-1":
            resp.encoding = resp.apparent_encoding
        return resp.content, source

    path = Path(source)
    return path.read_bytes(), None


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="Generic novel chapter extractor for arbitrary HTML pages.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s chapter.html
  %(prog)s https://example.com/chapter-1.html
  %(prog)s file1.html file2.html --preview 800
  %(prog)s --test
  %(prog)s chapter.html --json
        """,
    )
    parser.add_argument(
        "sources",
        nargs="*",
        help="HTML files or http(s) URLs to extract.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print full JSON result for each source.",
    )
    parser.add_argument(
        "--preview",
        type=int,
        default=500,
        help="Number of content characters to preview in non-JSON mode.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=20.0,
        help="HTTP timeout in seconds.",
    )
    parser.add_argument(
        "--test",
        nargs="?",
        const="__builtin__",
        metavar="FILE",
        help=(
            "Run test harness. With no argument, uses built-in URL list. "
            "With a file argument, reads one URL/path per line."
        ),
    )

    args = parser.parse_args()

    sources = list(args.sources or [])

    if args.test:
        if args.test == "__builtin__":
            sources.extend(TEST_URLS)
        else:
            with open(args.test, "r", encoding="utf-8") as f:
                lines = [line.strip() for line in f if line.strip() and not line.startswith("#")]
            sources.extend(lines)

    if not sources:
        parser.print_help()
        sys.exit(1)

    for source in sources:
        try:
            html, url = _load_source(source, args.timeout)
            result = extract_chapter(html, url=url)
        except Exception as exc:
            print(f"ERROR extracting {source}: {exc}", file=sys.stderr)
            continue

        if args.json:
            print(json.dumps(result, ensure_ascii=False, indent=2))
            continue

        print("=" * 100)
        print("SOURCE    :", source)
        print("TITLE     :", result["title"])
        print("CONFIDENCE:", result["confidence"])
        print("CONTENT   :")

        preview = result["content"][: args.preview]
        if len(result["content"]) > args.preview:
            preview += " ..."

        print(preview)
        print()


if __name__ == "__main__":
    main()
