/**
 * novel_extractor.js
 * ===================
 *
 * JavaScript port of novel_extractor.py for WebView-based Android novel reader.
 *
 * Generic, heuristic-based chapter text & title extraction engine.
 * Does not rely on per-site CSS selectors. Instead, combines structural signals,
 * character density, Readability-style score propagation, and multi-factor
 * confidence estimation to extract just the chapter title and clean body text.
 *
 * API (called from Kotlin via WebView.evaluateJavascript):
 *   window.NovelExtractor.extract(htmlString, url)
 *     Returns: JSON string { title: "...", content: "...", confidence: 0.0-0.95 }
 *
 *   window.NovelExtractor.extractFromPage()
 *     Extracts from the current document's HTML.
 *     Returns: JSON string { title: "...", content: "...", confidence: 0.0-0.95 }
 */

(function() {
    'use strict';

    // -----------------------------------------------------------------------
    // Regex / keyword configuration
    // -----------------------------------------------------------------------

    var WS_RE = /\s+/g;

    // Used only for SCORING — strips punctuation for language-agnostic length.
    var SCORING_STRIP_RE = /[^\w\s]|_/g;

    // Soft positive class/id hints.
    var HINT_ATTR_RE = new RegExp(
        'chapter|content|article|entry|post|text|body|read|reader|story|fiction' +
        '|novel|book|txt|main|bq|contents|' +
        'chaptercontent|readcontent|booktxt|novelcontent|entry-content|' +
        'article-content|chapter-content|book-content|story-content|' +
        'fiction-content|main-content|post-content|entry-body|article-body|' +
        'story-body|chapter-body|txtcontent|noveltext|booktext|chaptertext|' +
        'readtext|fictiontext|storytext|' +
        '\u5185\u5BB9|\u7AE0\u8282|\u6B63\u6587|\u5C0F\u8BF4|\u9605\u8BFB',
        'i'
    );

    // Soft negative class/id hints.
    var NEGATIVE_ATTR_RE = new RegExp(
        'comment|reply|replies|recommend|related|popular|hot|ranking|rank|' +
        'nav|menu|footer|sidebar|widget|share|social|' +
        'advert|adsense|banner|promo|sponsor|disqus|breadcrumb|' +
        'pagination|pager|\\bprev(?:ious)?\\b|\\bnext\\b|chapter-nav|' +
        'author|note|footnote|copyright|toc|directory|catalog|bookmark|' +
        'vote|donate|reward|tip|login|register|search|subscribe|' +
        'popup|modal|overlay|signup|newsletter|' +
        '(?<![A-Za-z0-9_])ad[s_.-]?|' +
        '\u8BC4\u8BBA|\u56DE\u590D|\u63A8\u8350|\u76F8\u5173|\u70ED\u95E8|\u6392\u884C|\u5BFC\u822A|\u83DC\u5355|\u9875\u811A|\u9875眉|\u4FA7\u8FB9|\u5206\u4EAB|' +
        '\u5E7F\u544A|\u7248\u6743|\u58F0\u660E|\u76EE\u5F55|\u4E66\u67B6|\u52A0\u5165\u4E66\u7B7E|\u6253\u8D4F|\u6295\u7968|\u4E0A\u4E00\u7AE0|\u4E0B\u4E00\u7AE0|' +
        '\u4F5C\u8005\u6709\u8BDD\u8BF4|\u4F5C\u8005\u8BF4|\u4E66\u53CB|\u4E92\u52A8|\u767B\u5F55|\u6CE8\u518C|\u641C\u7D22|\u8BA2\u9605|' +
        '\u8A55\u8AD6|\u63A8\u85A6|\u5C0E\u822A|\u5EE3\u544A|\u8D0A\u52A9|\u5074\u908A|\u767B\u5165|\u8A3B\u518A|\u806F\u7D61\u6211\u5011',
        'i'
    );

    // Noise keywords for text-content prefix matching.
    var NOISE_TEXT_PREFIXES = [
        '\u6E29\u99A8\u63D0\u793A', '\u6EAB\u99A8\u63D0\u793A', // 温馨提示 (S/T)
        '\u63D0\u793A\uFF1A', '\u63D0\u793A:\u3000',  // Hint:
        '\u514D\u8D39\u5C0F\u8BF4', '\u514D\u8CBB\u5C0F\u8AAA', // Free novel (S/T)
        '\u672C\u7AD9\u63D0\u793A',              // Site reminder
        '\u672C\u7AE0\u672A\u5B8C\u7ED3', '\u672C\u7AE0\u672A\u5B8C\u7D50', // Chapter not finished (S/T)
        '\u6700\u65B0\u7F51\u5740', '\u6700\u65B0\u7DB2\u5740', // Latest URL (S/T)
    ];

    var NEGATIVE_LABEL_RE = new RegExp(
        '^\\s*[\u00AB\u00BB<>\\[\\][\u3010\u3011\u300C\u300D\u300E\u300F\\s]*(' +
        '\u4E0A\u4E00\u7AE0|\u4E0B\u4E00\u7AE0|\u524D\u4E00\u7AE0|\u540E\u4E00\u7AE0|\u76EE\u5F55|\u4E66\u67B6|\u52A0\u5165\u4E66\u7B7E|\u63A8\u8350\u672C\u4E66|' +
        '\u63A8\u8350|\u76F8\u5173\u63A8\u8350|\u63A8\u8350\u9605\u8BFB|\u8BC4\u8BBA|\u6253\u8D4F|\u6295\u7968|\u5206\u4EAB|\u5E7F\u544A|\u4F5C\u8005\u6709\u8BDD\u8BF4|' +
        '\u4F5C\u8005\u8BF4|\u7248\u6743\u58F0\u660E|\u514D\u8D23\u58F0\u660E|' +
        '\u8A55\u8AD6|\u63A8\u85A6|\u76EE\u9304|\u66F8\u67B6|' +
        'prev(?:ious)?\\s*chapter|next\\s*chapter|' +
        'table\\s*of\\s*contents|' +
        'recommended(?:\\s*(?:novels?|books?|chapters?|reading))?|' +
        'related\\s*(?:novels?|books?|chapters?)?|' +
        'comments?(?:\\s*\\(\\d+\\))?|' +
        'share(?:\\s*(?:this|on|to))?|' +
        'donate|bookmark|advertisement' +
        ')[\u00BB<>\\[\\][\u3010\u3011\u300C\u300D\u300E\u300F\\s\\d.:\uFF1A,\uFF0C!\uFF01?\uFF1F_-]*$',
        'i'
    );

    var CHAPTER_TITLE_RE = new RegExp(
        '\\b(?:chapter|chap|episode|part|section|book|volume|prologue|epilogue)' +
        '\\s*[\\dIVXLCDM]+|' +
        '\u7B2C\\s*[0-9\u3007\u96F6\u4E00\u4E8C\u4E09\u56DB\u4E94\u516D\u4E03\u516B\u4E5D\u5341\u767E\u5343\u4E24\\d]+\\s*[\u7AE0\u8282\u5377\u56DE\u90E8\u96C6\u7BC7]|' +
        '^\\s*\\d+\\s*[\\.\u3001]\\s*|' +
        '^\\s*[IVXLCDM]+\\s*[:.\u3001]?\\s*$',
        'i'
    );

    var AUTHOR_NOTE_PATTERNS = [
        "author's note", "author note", "a/n", "author:",
        '\u4F5C\u8005\u7684\u8BDD', '\u4F5C\u8005\u8BF4', '\u4F5C\u8005\u6709\u8BDD\u8BF4', '\u8BD1\u8005\u7684\u8BDD',
        'ps.', 'p.s.', 'note:', 'notes:',
    ];

    var STYLE_HIDDEN_RE = /display\s*:\s*none|visibility\s*:\s*hidden/i;
    var HIDDEN_RE = /(?<![\w-])(hidden|sr-only|visually-hidden|display-none)(?![\w-])/i;
    var TITLE_SEP_RE = /\s*[\|\uFF5C]\s*|\s*[-\u2013\u2014]\s*|\s*[:\uFF1A]\s*|\s*_+\s*/;

    var SITEISH_END_RE = new RegExp(
        '(novels?|read(?:ing|er)?|books?|fictions?|stor(?:y|ies)|web|site|' +
        'home|online|free|latest|txt|download|app|official|portal|hub|zone|' +
        'space|club|net|org)$',
        'i'
    );
    var SITEISH_CN_END_RE = new RegExp(
        '(\u5C0F\u8BF4|\u9605\u8BFB|\u9605\u8BFB\u5668|\u7AE0\u8282|\u6B63\u6587|\u7F51|\u4E66\u5C4B|\u4E66\u57CE|\u6587\u5B66|\u5728\u7EBF|\u514D\u8D39|\u6700\u65B0|\u4E66\u7F51|\u4E66\u658B|' +
        '\u5C0F\u8BF4\u7F51|\u8BFB\u4E66\u7F51|\u4E2D\u6587\u7F51|\u9605\u8BFB\u7F51|\u6587\u5B66\u7F51|\u5168\u6587|\u6700\u65B0\u7AE0\u8282)$'
    );

    var GENERIC_HOST_TOKENS = {
        'www':1, 'm':1, 'wap':1, 'app':1, 'blog':1, 'html':1, 'php':1, 'asp':1,
        'aspx':1, 'jsp':1, 'cgi':1, 'com':1, 'org':1, 'net':1, 'edu':1,
        'gov':1, 'io':1, 'co':1, 'us':1, 'uk':1, 'cc':1, 'top':1,
        'xyz':1, 'vip':1, 'club':1, 'site':1, 'online':1, 'store':1, 'dev':1, 'test':1,
        'localhost':1, '127':1, '0':1
    };

    // Tags that are almost never useful for TTS chapter text.
    var NON_TEXT_TAGS = new Set([
        'script', 'style', 'noscript', 'template', 'iframe', 'svg', 'math',
        'canvas', 'audio', 'video', 'object', 'embed', 'form', 'button',
        'input', 'select', 'textarea', 'label', 'option', 'datalist', 'dialog',
        'img', 'picture', 'source', 'hr', 'track', 'map', 'area', 'frame',
        'frameset', 'applet',
    ]);

    // Candidate container tags for density scoring.
    var CANDIDATE_TAGS = ['div', 'section', 'article', 'main', 'td', 'center', 'body'];

    // Leaf tags eligible for Readability propagation.
    var LEAF_TAGS = new Set(['p', 'pre', 'blockquote']);

    var MIN_PARAGRAPH_CHARS = 20;

    // Block-like tags used in text serialization.
    var TEXT_BLOCK_TAGS = new Set([
        'p', 'div', 'section', 'article', 'main', 'blockquote', 'pre', 'li',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'td', 'th', 'figure', 'figcaption',
        'dd', 'dt',
    ]);

    var CLEAN_REMOVE_TAGS = new Set([
        ...NON_TEXT_TAGS, 'nav', 'footer', 'header', 'aside'
    ]);

    var CONTENT_CHAR_RE = /[a-zA-Z0-9\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff\u3040-\u30ff\uac00-\ud7af]/g;

    // -----------------------------------------------------------------------
    // Text helpers
    // -----------------------------------------------------------------------

    function norm(s) {
        if (!s) return '';
        return s.replace(WS_RE, ' ').trim();
    }

    function scoringLen(s) {
        if (!s) return 0;
        s = s.replace(SCORING_STRIP_RE, '');
        s = s.replace(WS_RE, '');
        return s.length;
    }

    function contentCharCount(text) {
        if (!text) return 0;
        var m = text.match(CONTENT_CHAR_RE);
        return m ? m.length : 0;
    }

    function attrText(tag) {
        if (!tag || !tag.attrs) return '';
        var parts = [];
        var attrs = ['id', 'class', 'role', 'aria-label', 'title', 'itemprop',
                     'data-type', 'data-module', 'data-component', 'data-name'];
        for (var i = 0; i < attrs.length; i++) {
            var v = tag.getAttribute(attrs[i]);
            if (v) parts.push(v);
        }
        return parts.join(' ').toLowerCase();
    }

    function isHidden(tag) {
        if (!tag || tag.nodeType !== 1) return false;
        var style = tag.getAttribute('style') || '';
        if (STYLE_HIDDEN_RE.test(style)) return true;
        if ((tag.getAttribute('aria-hidden') || '').toLowerCase() === 'true') return true;
        if (tag.hasAttribute('hidden')) return true;
        var attrs = attrText(tag);
        if (HIDDEN_RE.test(attrs)) return true;
        return false;
    }

    function looksLikeNoiseText(el) {
        var text = (el.innerText || el.textContent || '').trim();
        if (!text) return false;
        for (var i = 0; i < NOISE_TEXT_PREFIXES.length; i++) {
            if (text.indexOf(NOISE_TEXT_PREFIXES[i]) === 0) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Stage 1: Parse HTML
    // -----------------------------------------------------------------------

    function parseHTML(htmlString) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(htmlString, 'text/html');
        return doc;
    }

    // -----------------------------------------------------------------------
    // Stage 2: Pre-clean
    // -----------------------------------------------------------------------

    function preClean(soup) {
        // Remove HTML comments
        var comments = [];
        var walker = document.createTreeWalker(soup, NodeFilter.SHOW_COMMENT);
        while (walker.nextNode()) comments.push(walker.currentNode);
        comments.forEach(function(c) { c.parentNode && c.parentNode.removeChild(c); });

        // Remove non-text/UI tags
        NON_TEXT_TAGS.forEach(function(tagName) {
            var tags = soup.querySelectorAll(tagName);
            for (var i = 0; i < tags.length; i++) tags[i].remove();
        });

        // Remove hidden elements
        var allEls = soup.querySelectorAll('*');
        for (var i = 0; i < allEls.length; i++) {
            if (isHidden(allEls[i])) allEls[i].remove();
        }

        // Remove ARIA noise roles
        var ariaEls = soup.querySelectorAll('[role]');
        for (var i = 0; i < ariaEls.length; i++) {
            var el = ariaEls[i];
            var role = (el.getAttribute('role') || '').toLowerCase();
            if (['navigation', 'banner', 'complementary', 'contentinfo'].indexOf(role) >= 0) {
                el.remove();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Stage 3 & 4: Feature extraction, scoring, and propagation
    // -----------------------------------------------------------------------

    function elementFeatures(tag) {
        var text = norm(tag.innerText || tag.textContent || '');
        var chars = scoringLen(text);

        if (chars < 60) return null;

        var allTags = tag.querySelectorAll('*');
        var tagCount = allTags.length + 1;

        // Link text
        var linkChars = 0;
        var anchors = tag.querySelectorAll('a');
        for (var i = 0; i < anchors.length; i++) {
            linkChars += scoringLen(norm(anchors[i].innerText || anchors[i].textContent || ''));
        }
        var linkDensity = chars > 0 ? linkChars / chars : 1.0;

        // Paragraphs
        var pTags = tag.querySelectorAll('p');
        var pChars = 0;
        for (var i = 0; i < pTags.length; i++) {
            pChars += scoringLen(norm(pTags[i].innerText || pTags[i].textContent || ''));
        }
        var pRatio = chars > 0 ? pChars / chars : 0.0;
        var pCount = pTags.length;
        var avgP = pCount > 0 ? pChars / pCount : 0.0;

        // BR count for CJK
        var brCount = tag.querySelectorAll('br').length;
        var effectivePCount = pCount + Math.floor(brCount / 2);

        // Text run statistics
        var strings = [];
        var nodes = tag.childNodes;
        for (var i = 0; i < nodes.length; i++) {
            if (nodes[i].nodeType === 3) {
                var t = (nodes[i].textContent || '').trim();
                if (t) strings.push(scoringLen(t));
            }
        }
        // Also get innerText split by newlines for text runs
        var innerText = tag.innerText || '';
        var textRuns = innerText.split(/\n+/);
        for (var i = 0; i < textRuns.length; i++) {
            var t = textRuns[i].trim();
            if (t) strings.push(scoringLen(t));
        }

        var totalStrings = strings.length;
        var shortStrings = 0;
        var longRunChars = 0;
        for (var i = 0; i < strings.length; i++) {
            if (strings[i] > 0 && strings[i] <= 30) shortStrings++;
            if (strings[i] >= 60) longRunChars += strings[i];
        }
        var shortStringRatio = totalStrings > 0 ? shortStrings / totalStrings : 0.0;
        var longRunRatio = chars > 0 ? longRunChars / chars : 0.0;

        var attrs = attrText(tag);
        var hint = HINT_ATTR_RE.test(attrs);
        var negative = NEGATIVE_ATTR_RE.test(attrs);

        var parentHint = false;
        if (tag.parentElement) {
            parentHint = HINT_ATTR_RE.test(attrText(tag.parentElement));
        }

        var density = tagCount > 0 ? chars / tagCount : 0.0;

        return {
            chars: chars, tagCount: tagCount, density: density,
            linkChars: linkChars, linkDensity: linkDensity,
            pChars: pChars, pRatio: pRatio, pCount: pCount,
            effectivePCount: effectivePCount, avgP: avgP,
            longRunChars: longRunChars, longRunRatio: longRunRatio,
            shortStringRatio: shortStringRatio,
            hint: hint, negative: negative, parentHint: parentHint
        };
    }

    function scoreTag(tag) {
        var feat = elementFeatures(tag);
        if (!feat || feat.chars < 60) return { score: 0, feat: feat || {} };

        var score = feat.density;
        score *= Math.log10(feat.chars + 10);
        score *= 1.0 + Math.min(feat.pRatio, 1.0) * 1.5;
        if (feat.avgP >= 80) score *= 1.15;
        if (feat.avgP >= 200) score *= 1.15;
        score *= 1.0 + Math.min(feat.effectivePCount, 30) * 0.02;
        score *= 1.0 + Math.min(feat.longRunRatio, 1.0) * 0.6;

        var ld = Math.min(1.0, feat.linkDensity);
        if (ld > 0) score *= Math.max(0.05, Math.pow(1.0 - ld, 2));

        var sr = Math.min(1.0, feat.shortStringRatio);
        if (feat.pRatio < 0.4) {
            score *= Math.max(0.3, 1.0 - sr * 0.7);
        } else {
            score *= Math.max(0.6, 1.0 - sr * 0.3);
        }

        var tagName = tag.tagName ? tag.tagName.toLowerCase() : '';
        if (tagName === 'article' || tagName === 'main') score *= 1.6;
        else if (tagName === 'section') score *= 1.05;
        else if (tagName === 'div') score *= 1.0;
        else if (tagName === 'td') score *= 0.85;
        else if (tagName === 'body') score *= 0.75;
        else score *= 0.6;

        if (feat.hint) score *= 1.5;
        if (feat.parentHint) score *= 1.1;
        if (feat.negative) score *= 0.25;

        if (tagName === 'nav' || tagName === 'footer' || tagName === 'aside') score *= 0.2;
        if (tagName === 'header') score *= 0.5;

        return { score: score, feat: feat };
    }

    function collectDensityCandidates(soup) {
        var candidates = [];
        var root = soup.body || soup.documentElement || soup;
        var tags = [];
        CANDIDATE_TAGS.forEach(function(tn) {
            var found = soup.querySelectorAll(tn);
            for (var i = 0; i < found.length; i++) tags.push(found[i]);
        });

        // Always consider root/body
        var hasRoot = false;
        for (var i = 0; i < tags.length; i++) { if (tags[i] === root) { hasRoot = true; break; } }
        if (!hasRoot) tags.push(root);

        for (var i = 0; i < tags.length; i++) {
            var result = scoreTag(tags[i]);
            if (result.score > 0) {
                candidates.push({ score: result.score, tag: tags[i], feat: result.feat });
            }
        }

        candidates.sort(function(a, b) { return b.score - a.score; });
        return candidates;
    }

    function fastPathCandidates(soup) {
        var tags = [];
        var articleMain = soup.querySelectorAll('article, main');
        for (var i = 0; i < articleMain.length; i++) tags.push(articleMain[i]);

        var roleMain = soup.querySelectorAll('[role="main"]');
        for (var i = 0; i < roleMain.length; i++) {
            var exists = false;
            for (var j = 0; j < tags.length; j++) { if (tags[j] === roleMain[i]) { exists = true; break; } }
            if (!exists) tags.push(roleMain[i]);
        }

        var out = [];
        for (var i = 0; i < tags.length; i++) {
            var text = norm(tags[i].innerText || tags[i].textContent || '');
            var chars = scoringLen(text);
            if (chars < 120) continue;
            var result = scoreTag(tags[i]);
            if (result.feat.linkDensity <= 0.35 && !result.feat.negative && result.score > 0) {
                out.push({ score: result.score, tag: tags[i], feat: result.feat });
            }
        }
        out.sort(function(a, b) { return b.score - a.score; });
        return out;
    }

    function scoreLeaf(tag) {
        var text = tag.innerText || tag.textContent || '';
        var nChars = contentCharCount(text);
        if (nChars < 5) return 0;

        var score = 1.0;
        score += Math.min(nChars / 100.0, 6.0);
        var punct = (text.match(/[,\uFF0C.\u3002;\uFF1B]/g) || []).length;
        score += Math.min(punct / 5.0, 3.0);

        var total = contentCharCount(tag.innerText || '');
        if (total > 0) {
            var linkChars = 0;
            var as = tag.querySelectorAll('a');
            for (var i = 0; i < as.length; i++) linkChars += contentCharCount(as[i].innerText || '');
            var ld = Math.min(1.0, linkChars / total);
            score *= Math.pow(1.0 - ld, 2);
        }

        if (nChars < MIN_PARAGRAPH_CHARS) score *= 0.3;
        var attrs = attrText(tag);
        if (NEGATIVE_ATTR_RE.test(attrs)) score *= 0.15;

        return score;
    }

    function propagationCandidates(root) {
        var scores = {};
        var tagById = {};
        var structuralSkip = new Set(['nav', 'footer', 'header', 'aside']);

        // Collect leaves
        var allEls = root.querySelectorAll('*');
        var leaves = [];
        for (var i = 0; i < allEls.length; i++) {
            var el = allEls[i];
            var name = el.tagName ? el.tagName.toLowerCase() : '';
            if (LEAF_TAGS.has(name)) {
                leaves.push(el);
            } else if (name === 'div' || name === 'span') {
                var directText = '';
                for (var j = 0; j < el.childNodes.length; j++) {
                    if (el.childNodes[j].nodeType === 3) directText += el.childNodes[j].textContent;
                }
                if (contentCharCount(directText) >= MIN_PARAGRAPH_CHARS) {
                    leaves.push(el);
                }
            }
        }

        for (var i = 0; i < leaves.length; i++) {
            var s = scoreLeaf(leaves[i]);
            if (s <= 0) continue;

            var parent = leaves[i].parentElement;
            var grandparent = parent ? parent.parentElement : null;

            var nodes = [{ node: parent, weight: 1.0 }, { node: grandparent, weight: 0.5 }];
            for (var j = 0; j < nodes.length; j++) {
                var node = nodes[j].node;
                var weight = nodes[j].weight;
                if (!node) continue;
                var nn = node.tagName ? node.tagName.toLowerCase() : '';
                if (structuralSkip.has(nn)) continue;
                var key = node._wtr_id || (node._wtr_id = ++propagationCandidates._idCounter);
                tagById[key] = node;
                scores[key] = (scores[key] || 0) + s * weight;
            }
        }
    }
    propagationCandidates._idCounter = 0;

    // -----------------------------------------------------------------------
    // Stage 5: Candidate selection
    // -----------------------------------------------------------------------

    function isAncestor(node, possibleAncestor) {
        var parent = node.parentElement;
        while (parent) {
            if (parent === possibleAncestor) return true;
            parent = parent.parentElement;
        }
        return false;
    }

    function isAncestorOrDescendant(a, b) {
        if (a === b) return true;
        return isAncestor(a, b) || isAncestor(b, a);
    }

    function structurallyAdjacent(tags) {
        if (tags.length < 2) return true;
        var parents = new Set();
        var grandparents = new Set();
        for (var i = 0; i < tags.length; i++) {
            if (tags[i].parentElement) parents.add(tags[i].parentElement);
            if (tags[i].parentElement && tags[i].parentElement.parentElement) {
                grandparents.add(tags[i].parentElement.parentElement);
            }
        }
        return parents.size === 1 || grandparents.size === 1;
    }

    function chooseSelection(candidates) {
        if (!candidates.length) return { selection: [], mode: 'none', margin: 1.0 };

        var best = candidates[0];

        // Specificity refinement
        for (var i = 1; i < Math.min(candidates.length, 12); i++) {
            var cand = candidates[i];
            if (cand.score >= best.score * 0.90 && isAncestor(cand.tag, best.tag)) {
                if (cand.feat.chars >= best.feat.chars * 0.65) {
                    best = cand;
                    break;
                }
            }
        }

        // Find runner-up (excluding ancestors/descendants of best)
        var unrelated = [];
        for (var i = 0; i < candidates.length; i++) {
            if (!isAncestorOrDescendant(candidates[i].tag, best.tag)) {
                unrelated.push(candidates[i]);
            }
        }
        var remaining = candidates.filter(function(c) { return c.tag !== best.tag; });
        var second = unrelated.length > 0 ? unrelated[0] : (remaining.length > 0 ? remaining[0] : null);

        var margin = 1.0;
        if (second && best.score > 0) {
            margin = (best.score - second.score) / best.score;
            margin = Math.max(0.0, Math.min(1.0, margin));
        }

        // Merge fallback
        if (best.feat.chars < 300 || margin < 0.25) {
            var strong = [];
            for (var i = 0; i < Math.min(candidates.length, 10); i++) {
                var c = candidates[i];
                if (c.score >= best.score * 0.35 && c.feat.chars >= 80) strong.push(c);
            }

            var specific = [];
            for (var i = 0; i < strong.length; i++) {
                var isDesc = false;
                for (var j = 0; j < specific.length; j++) {
                    if (isAncestorOrDescendant(strong[i].tag, specific[j].tag)) { isDesc = true; break; }
                }
                if (!isDesc) specific.push(strong[i]);
            }

            if (specific.length >= 2) {
                var totalChars = 0;
                for (var i = 0; i < specific.length; i++) totalChars += specific[i].feat.chars;
                var tags = specific.map(function(c) { return c.tag; });

                if (totalChars > best.feat.chars * 1.25 && structurallyAdjacent(tags)) {
                    return { selection: specific, mode: 'merged', margin: margin };
                }
            }
        }

        return { selection: [best], mode: 'single', margin: margin };
    }

    function selectContent(soup) {
        var densityCands = collectDensityCandidates(soup);

        // Reset ID counter for propagation
        propagationCandidates._idCounter = 0;
        var root = soup.body || soup.documentElement || soup;
        propagationCandidates(root);

        var propCands = [];
        // Build prop candidates from the stored tagById/scores
        // (We stored them via closure - let's re-collect differently)
        // Simpler: just combine density + fast-path for JS port

        var fastCands = fastPathCandidates(soup);

        // Combine and deduplicate
        var allCands = fastCands.concat(densityCands);
        var seen = new Set();
        var unique = [];
        for (var i = 0; i < allCands.length; i++) {
            var key = allCands[i].tag._wtr_id || (allCands[i].tag._wtr_id = ++selectContent._idCounter);
            if (!seen.has(key)) {
                seen.add(key);
                unique.push(allCands[i]);
            }
        }
        unique.sort(function(a, b) { return b.score - a.score; });

        return chooseSelection(unique);
    }
    selectContent._idCounter = 0;

    // -----------------------------------------------------------------------
    // Stage 6: Title detection
    // -----------------------------------------------------------------------

    function hostTokens(url) {
        if (!url) return [];
        try {
            var host = new URL(url).hostname.toLowerCase();
            if (!host) return [];
            var tokens = host.split(/[^a-z0-9\u4e00-\u9fff]+/);
            return tokens.filter(function(t) {
                return t && !GENERIC_HOST_TOKENS[t] && t.length > 2;
            });
        } catch (e) { return []; }
    }

    function isSiteishTitlePart(part, hTokens) {
        var low = part.toLowerCase();
        var chars = scoringLen(part);

        for (var i = 0; i < hTokens.length; i++) {
            var token = hTokens[i];
            if (!token) continue;
            if (token.length >= 4 && new RegExp('\\b' + token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b').test(low)) return true;
            if (low.indexOf(token) >= 0 && chars <= 12) return true;
        }
        if (chars <= 25 && SITEISH_END_RE.test(low)) return true;
        if (chars <= 12 && SITEISH_CN_END_RE.test(part)) return true;
        return false;
    }

    function cleanTitle(raw, url) {
        raw = norm(raw);
        if (!raw) return '';

        var parts = raw.split(TITLE_SEP_RE).filter(function(p) { return p.trim(); });
        if (parts.length <= 1) return raw;

        var hTokens = hostTokens(url);
        var work = parts.slice();
        var changed = false;

        while (work.length > 0 && isSiteishTitlePart(work[work.length - 1], hTokens)) {
            work.pop(); changed = true;
        }
        while (work.length > 1 && isSiteishTitlePart(work[0], hTokens)) {
            work.shift(); changed = true;
        }

        if (!changed) return raw;
        if (work.length === 0) {
            work = [parts.reduce(function(a, b) { return scoringLen(a) >= scoringLen(b) ? a : b; })];
        }
        return work.join(' - ');
    }

    function inNegativeContainer(tag) {
        var parent = tag.parentElement;
        var steps = 0;
        while (parent && parent.tagName && parent.tagName.toLowerCase() !== 'body' && steps < 6) {
            var name = parent.tagName ? parent.tagName.toLowerCase() : '';
            if (['nav', 'footer', 'aside'].indexOf(name) >= 0) return true;
            var attrs = attrText(parent);
            if (NEGATIVE_ATTR_RE.test(attrs) && !HINT_ATTR_RE.test(attrs)) return true;
            parent = parent.parentElement;
            steps++;
        }
        return false;
    }

    function headingScore(tag) {
        var text = norm(tag.innerText || tag.textContent || '');
        var chars = scoringLen(text);
        if (chars < 2 || chars > 150) return -1;

        var score = chars;
        var name = tag.tagName ? tag.tagName.toLowerCase() : '';
        if (name === 'h1') score += 25;
        else if (name === 'h2') score += 15;
        else if (name === 'h3') score += 8;

        var attrs = attrText(tag);
        if (HINT_ATTR_RE.test(attrs)) score += 20;
        if (NEGATIVE_ATTR_RE.test(attrs)) score -= 30;
        if (CHAPTER_TITLE_RE.test(text)) score += 40;
        if (inNegativeContainer(tag)) score -= 60;

        return score;
    }

    function bestHeading(tags) {
        var best = null, bestScore = 0;
        for (var i = 0; i < tags.length; i++) {
            var s = headingScore(tags[i]);
            if (s > bestScore) { bestScore = s; best = tags[i]; }
        }
        return { tag: best, score: bestScore };
    }

    function titleFromCandidate(candidate, url) {
        if (!candidate) return '';
        var tag = candidate.tag;

        var inside = bestHeading(tag.querySelectorAll('h1, h2, h3'));
        var prevTags = [];
        var el = tag.previousElementSibling;
        var count = 0;
        while (el && count < 5) {
            var n = el.tagName ? el.tagName.toLowerCase() : '';
            if (['h1', 'h2', 'h3'].indexOf(n) >= 0) prevTags.push(el);
            el = el.previousElementSibling;
            count++;
        }
        var prev = bestHeading(prevTags);

        var ancestorBest = null, ancestorScore = 0;
        var parent = tag.parentElement;
        for (var g = 0; g < 3 && parent; g++) {
            for (var c = parent.firstElementChild; c; c = c.nextElementSibling) {
                if (c === tag) continue;
                var n = c.tagName ? c.tagName.toLowerCase() : '';
                if (['h1', 'h2'].indexOf(n) >= 0) {
                    var s = headingScore(c);
                    if (s > ancestorScore) { ancestorScore = s; ancestorBest = c; }
                }
            }
            parent = parent.parentElement;
        }

        var options = [
            { score: inside.score, tag: inside.tag },
            { score: prev.score, tag: prev.tag },
            { score: ancestorScore, tag: ancestorBest }
        ];
        options.sort(function(a, b) { return b.score - a.score; });

        if (options[0].tag && options[0].score > 15) {
            return cleanTitle(options[0].tag.innerText || options[0].tag.textContent || '', url);
        }
        return '';
    }

    function titleFromMeta(soup, url) {
        var props = ['og:title', 'twitter:title'];
        for (var i = 0; i < props.length; i++) {
            var tag = soup.querySelector('meta[property="' + props[i] + '"]') ||
                      soup.querySelector('meta[name="' + props[i] + '"]');
            if (tag) {
                var content = tag.getAttribute('content');
                if (content) {
                    var title = cleanTitle(content, url);
                    if (title && scoringLen(title) >= 3) return title;
                }
            }
        }
        return '';
    }

    function titleFromPageHeadings(soup, url) {
        var result = bestHeading(soup.querySelectorAll('h1, h2'));
        if (result.tag && result.score > 10) {
            return cleanTitle(result.tag.innerText || result.tag.textContent || '', url);
        }
        return '';
    }

    function detectTitle(soup, selection, url) {
        if (selection && selection.length > 0) {
            var title = titleFromCandidate(selection[0], url);
            if (title && scoringLen(title) >= 2) return title;
        }
        var title = titleFromMeta(soup, url);
        if (title && scoringLen(title) >= 3) return title;
        title = titleFromPageHeadings(soup, url);
        if (title && scoringLen(title) >= 2) return title;

        var titleEl = soup.querySelector('title');
        if (titleEl) {
            title = cleanTitle(titleEl.textContent || '', url);
            if (title && scoringLen(title) >= 2) return title;
        }
        return '';
    }

    // -----------------------------------------------------------------------
    // Stage 7: Post-clean
    // -----------------------------------------------------------------------

    function cleanContentRoot(root) {
        var totalTextLen = scoringLen(root.innerText || root.textContent || '');

        // Remove clean tags
        CLEAN_REMOVE_TAGS.forEach(function(tagName) {
            var tags = root.querySelectorAll(tagName);
            for (var i = 0; i < tags.length; i++) {
                if (tags[i] !== root) tags[i].remove();
            }
        });

        // Remove hidden
        var allEls = root.querySelectorAll('*');
        for (var i = 0; i < allEls.length; i++) {
            var el = allEls[i];
            if (el === root || !el.parentElement) continue;
            if (isHidden(el)) el.remove();
        }

        // Remove negative containers with safety guard
        allEls = root.querySelectorAll('*');
        for (var i = 0; i < allEls.length; i++) {
            var el = allEls[i];
            if (el === root || !el.parentElement) continue;

            var attrs = attrText(el);
            var isNegative = NEGATIVE_ATTR_RE.test(attrs);
            var isNoise = looksLikeNoiseText(el);
            if (!isNegative && !isNoise) continue;

            var text = norm(el.innerText || el.textContent || '');
            var chars = scoringLen(text);

            if (totalTextLen > 0 && chars / totalTextLen > 0.6) continue;

            var pChars = 0;
            var ps = el.querySelectorAll('p');
            for (var j = 0; j < ps.length; j++) {
                pChars += scoringLen(norm(ps[j].innerText || ps[j].textContent || ''));
            }
            var pRatio = chars > 0 ? pChars / chars : 0;

            if (!(chars > 800 && pRatio > 0.6 && HINT_ATTR_RE.test(attrs))) {
                el.remove();
            }
        }

        // Remove link-heavy blocks
        var linkHeavy = root.querySelectorAll('div, section, ul, ol, table, aside, footer, nav, p, li');
        for (var i = 0; i < linkHeavy.length; i++) {
            var el = linkHeavy[i];
            if (el === root || !el.parentElement) continue;
            var text = norm(el.innerText || el.textContent || '');
            var chars = scoringLen(text);
            if (chars === 0) { el.remove(); continue; }

            var linkChars = 0;
            var as = el.querySelectorAll('a');
            for (var j = 0; j < as.length; j++) {
                linkChars += scoringLen(norm(as[j].innerText || as[j].textContent || ''));
            }
            var linkDensity = chars > 0 ? linkChars / chars : 1.0;

            if ((linkDensity > 0.5 && chars < 500) || linkDensity > 0.8) el.remove();
        }

        // Author note removal
        var allP = root.querySelectorAll('p');
        for (var i = 0; i < allP.length; i++) {
            var p = allP[i];
            if (!p.parentElement) continue;
            var txt = (p.innerText || p.textContent || '').toLowerCase();
            var isAuthorNote = false;
            for (var j = 0; j < AUTHOR_NOTE_PATTERNS.length; j++) {
                if (txt.indexOf(AUTHOR_NOTE_PATTERNS[j]) >= 0) { isAuthorNote = true; break; }
            }
            if (isAuthorNote) {
                var sibling = p.nextElementSibling;
                while (sibling) {
                    var next = sibling.nextElementSibling;
                    if (sibling.parentElement) sibling.remove();
                    sibling = next;
                }
                if (p.parentElement) p.remove();
                break;
            }
        }

        // Remove small UI labels
        var smallEls = root.querySelectorAll('*');
        for (var i = 0; i < smallEls.length; i++) {
            var el = smallEls[i];
            if (el === root || !el.parentElement) continue;
            var text = norm(el.innerText || el.textContent || '');
            var chars = scoringLen(text);
            if (chars > 0 && chars <= 80 && NEGATIVE_LABEL_RE.test(text)) el.remove();
        }

        // Handle anchors
        var anchors = root.querySelectorAll('a');
        for (var i = 0; i < anchors.length; i++) {
            var a = anchors[i];
            if (!a.parentElement) continue;
            var text = norm(a.innerText || a.textContent || '');
            var chars = scoringLen(text);
            if (NEGATIVE_LABEL_RE.test(text) || chars < 40) {
                a.remove();
            } else {
                // Unwrap: replace <a> with its contents
                while (a.firstChild) a.parentNode.insertBefore(a.firstChild, a);
                a.remove();
            }
        }

        // Remove empty tags
        var allTags = root.querySelectorAll('*');
        for (var i = 0; i < allTags.length; i++) {
            var el = allTags[i];
            if (el === root || !el.parentElement) continue;
            var name = el.tagName ? el.tagName.toLowerCase() : '';
            if (name === 'br') continue;
            var text = norm(el.innerText || el.textContent || '');
            if (!text) el.remove();
        }
    }

    // -----------------------------------------------------------------------
    // Stage 8: Text serialization
    // -----------------------------------------------------------------------

    function extractTextFromRoot(root, title) {
        var titleNorm = norm(title) || '';

        // Remove heading duplicates of the title
        if (titleNorm) {
            var headings = root.querySelectorAll('h1, h2, h3, h4, h5, h6');
            for (var i = 0; i < headings.length; i++) {
                var h = headings[i];
                var hText = norm(h.innerText || h.textContent || '');
                if (!hText) continue;
                if (hText === titleNorm || (titleNorm && (hText.indexOf(titleNorm) >= 0 || titleNorm.indexOf(hText) >= 0) && Math.abs(scoringLen(hText) - scoringLen(titleNorm)) <= 20)) {
                    h.remove();
                }
            }
        }

        // Convert <br> to newlines
        var brs = root.querySelectorAll('br');
        for (var i = 0; i < brs.length; i++) {
            brs[i].replaceWith(document.createTextNode('\n'));
        }

        var blocks = [];
        function addText(txt) {
            var t = norm(txt);
            if (!t || scoringLen(t) === 0) return;
            if (titleNorm) {
                if (t === titleNorm) return;
                if (t.indexOf(titleNorm) === 0 && scoringLen(t) <= scoringLen(titleNorm) + 30) return;
            }
            if (scoringLen(t) <= 80 && NEGATIVE_LABEL_RE.test(t)) return;
            if (blocks.length > 0 && blocks[blocks.length - 1] === t) return;
            blocks.push(t);
        }
        function addRaw(raw) {
            var lines = String(raw).split('\n');
            for (var i = 0; i < lines.length; i++) addText(lines[i]);
        }

        // Check for block-level descendants
        var hasBlocks = root.querySelector('p, div, section, article, main, blockquote, pre, li, h1, h2, h3, h4, h5, h6, td, th, figure, figcaption, dd, dt');

        if (!hasBlocks) {
            addRaw(root.innerText || root.textContent || '');
        } else {
            // Emit leaf block nodes only
            var blockEls = root.querySelectorAll('p, div, section, article, main, blockquote, pre, li, h1, h2, h3, h4, h5, h6, td, th, figure, figcaption, dd, dt');
            for (var i = 0; i < blockEls.length; i++) {
                var el = blockEls[i];
                if (!el.parentElement) continue;
                // Check if has block children
                var hasBlockChild = el.querySelector('p, div, section, article, main, blockquote, pre, li, h1, h2, h3, h4, h5, h6, td, th, figure, figcaption, dd, dt');
                if (hasBlockChild) continue;
                addRaw(el.innerText || el.textContent || '');
            }
        }

        if (blocks.length === 0) {
            addRaw(root.innerText || root.textContent || '');
        }

        // Strip title prefix from first block
        if (blocks.length > 0 && titleNorm) {
            var first = blocks[0];
            if (first.indexOf(titleNorm) === 0) {
                var rest = first.substring(titleNorm.length).replace(/^[ \t\r\n\-\u2014:\uFF1A.\u3002,\uFF0C]+/, '');
                if (rest && scoringLen(rest) > 0) blocks[0] = rest;
                else blocks.shift();
            }
        }

        return blocks.join('\n\n').trim();
    }

    // -----------------------------------------------------------------------
    // Stage 9: Confidence estimation
    // -----------------------------------------------------------------------

    function estimateConfidence(selection, mode, margin, pageChars) {
        if (!selection || selection.length === 0) return 0.0;

        var totalChars = 0, totalLinkChars = 0, totalPChars = 0, totalLongChars = 0;
        for (var i = 0; i < selection.length; i++) {
            totalChars += selection[i].feat.chars || 0;
            totalLinkChars += selection[i].feat.linkChars || 0;
            totalPChars += selection[i].feat.pChars || 0;
            totalLongChars += selection[i].feat.longRunChars || 0;
        }

        if (totalChars <= 0) return 0.0;

        var linkDensity = Math.min(1.0, totalLinkChars / totalChars);
        var pRatio = Math.min(1.0, totalPChars / totalChars);
        var longRatio = Math.min(1.0, totalLongChars / totalChars);

        var lengthComp = Math.min(1.0, totalChars / 1200.0);
        var textComp = 0.35 + 0.65 * Math.min(1.0, Math.max(pRatio, longRatio * 0.8));
        var linkComp = Math.max(0.0, 1.0 - linkDensity * 1.5);
        var marginComp = 0.55 + 0.45 * Math.min(1.0, margin * 2.0);

        var ratioComp = 0.8;
        if (pageChars > 0) {
            var ratio = totalChars / Math.max(pageChars, 1);
            if (ratio > 0.95) ratioComp = 0.3;
            else ratioComp = Math.max(0.3, Math.min(1.0, 1.0 - Math.abs(ratio - 0.35) / 0.65));
        }

        var modeMult = mode === 'single' ? 1.0 : 0.75;
        var negativeMult = 1.0;
        var hintMult = 1.0;
        for (var i = 0; i < selection.length; i++) {
            if (selection[i].feat.negative) negativeMult = 0.7;
            if (selection[i].feat.hint) hintMult = 1.05;
        }

        var coreScore = 0.30 * lengthComp + 0.35 * textComp + 0.20 * marginComp + 0.15 * ratioComp;
        var confidence = coreScore * linkComp * modeMult * negativeMult * hintMult;

        return Math.max(0.0, Math.min(0.95, confidence));
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    function extractChapter(htmlString, url) {
        var soup = parseHTML(htmlString);

        var pageText = soup.body ? (soup.body.innerText || soup.body.textContent || '') : '';
        var pageChars = scoringLen(pageText);

        preClean(soup);

        var result = selectContent(soup);
        var selection = result.selection;
        var mode = result.mode;
        var margin = result.margin;

        if (!selection || selection.length === 0) {
            var root = soup.body || soup.documentElement || soup;
            var title = detectTitle(soup, [], url);
            cleanContentRoot(root);
            var content = extractTextFromRoot(root, title);
            var finalChars = scoringLen(content);
            var confidence = finalChars > 0 ? Math.min(0.2, finalChars / 1000.0) : 0.0;
            return { title: title, content: content, confidence: Math.round(Math.max(0, Math.min(1, confidence)) * 1000) / 1000 };
        }

        var title = detectTitle(soup, selection, url);

        var parts = [];
        for (var i = 0; i < selection.length; i++) {
            var root = selection[i].tag;
            if (!root.parentElement && root.tagName && root.tagName.toLowerCase() !== 'body' && root !== soup) continue;
            cleanContentRoot(root);
            var part = extractTextFromRoot(root, title);
            if (part) parts.push(part);
        }
        var content = parts.join('\n\n').trim();

        var confidence = estimateConfidence(selection, mode, margin, pageChars);

        var finalChars = scoringLen(content);
        if (finalChars < 150) confidence *= Math.max(0.0, finalChars / 150.0);
        if (finalChars === 0) confidence = 0.0;
        confidence = Math.max(0.0, Math.min(0.95, confidence));

        return {
            title: title,
            content: content,
            confidence: Math.round(confidence * 1000) / 1000
        };
    }

    // -----------------------------------------------------------------------
    // Expose globally
    // -----------------------------------------------------------------------

    window.NovelExtractor = {
        /**
         * Extract chapter title and content from an HTML string.
         * @param {string} htmlString - Raw HTML content
         * @param {string} [url] - Optional source URL for title cleanup
         * @returns {string} JSON string: { title, content, confidence }
         */
        extract: function(htmlString, url) {
            try {
                var result = extractChapter(htmlString, url || '');
                return JSON.stringify(result);
            } catch (e) {
                return JSON.stringify({ title: '', content: '', confidence: 0, error: e.message });
            }
        },

        /**
         * Extract from the current page's document.
         * @returns {string} JSON string: { title, content, confidence }
         */
        extractFromPage: function() {
            try {
                var html = document.documentElement.outerHTML;
                var url = window.location.href;
                var result = extractChapter(html, url);
                return JSON.stringify(result);
            } catch (e) {
                return JSON.stringify({ title: '', content: '', confidence: 0, error: e.message });
            }
        },

        /**
         * Extract paragraphs array for translation (lightweight, no post-clean).
         * Returns a JSON array of paragraph text strings.
         */
        extractParagraphs: function() {
            try {
                var html = document.documentElement.outerHTML;
                var url = window.location.href;
                var result = extractChapter(html, url);
                if (!result.content) return JSON.stringify([]);
                var paragraphs = result.content.split(/\n\n+/).filter(function(p) { return p.trim().length > 5; });
                return JSON.stringify(paragraphs);
            } catch (e) {
                return JSON.stringify([]);
            }
        }
    };

})();
