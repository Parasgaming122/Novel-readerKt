/**
 * WTR Extractor Utilities — Shared helpers for per-site extractors.
 *
 * This file is loaded BEFORE site-specific extractor.js files.
 * It defines window.WtrExtractorUtils with common functions.
 *
 * Usage in site extractors:
 *   var utils = window.WtrExtractorUtils;
 *   var clean = utils.isJunk(text, window.location.href, ["spam", "ad"]);
 *   var paragraphs = utils.prepareBrParagraphs(container);
 */
(function() {
    'use strict';

    var CommonJunkPatterns = [
        'join our discord', 'join discord', 'patreon', 'support me', 'support the author',
        'rate this', 'please review', 'please rate', "author's note", 'author note',
        "editor's note", 'editor note',
        'find any errors', 'broken links', 'report us', 'if you find any',
        'next chapter', 'previous chapter',
        'table of contents', 'read online free', 'read online for free',
        'unlocked chapters', 'bonus chapters', 'sign up', 'sign in', 'subscribe to',
        'follow my page', 'download our app', 'read this novel', 'other novel', 'like this book',
        'stop your ad blocker', 'ad blocker detected',
        '\u672C\u7AE0\u672A\u5B8C', '\u70B9\u51FB\u4E0B\u4E00\u9875', '\u7EE7\u7EED\u9605\u8BFB',
        '\u672C\u7AE0\u5B8C', '\uFF08\u672C\u7AE0\u672A\u5B8C\uFF09', '(\u672C\u7AE0\u5B8C)',
        '\u6700\u65B0\u7F51\u5740', '\u624B\u673A\u7528\u6237\u8BF7\u6D4F\u89C8',
        '\u66F4\u591A\u7CBE\u5F69\u5185\u5BB9', '\u6295\u63A8\u8350\u7968',
        '\u4E0A\u4E00\u7AE0', '\u4E0B\u4E00\u7AE0', '\u76EE\u5F55', '\u4E66\u67B6',
        '\u52A0\u5165\u4E66\u67B6', '\u8FD4\u56DE\u5C01\u9762'
    ];

    /**
     * Check if a paragraph text is junk (too short or matches known promo patterns).
     */
    function isJunk(text, url, siteJunkKeywords) {
        if (!text || text.length <= 3) return true;
        var t = text.toLowerCase();
        var urlLower = (url || '').toLowerCase();
        // Ad-blocker warning text
        if (t.includes('ad blocker') || t.includes('adblocker') || t.includes('disable ad')) return true;
        for (var i = 0; i < CommonJunkPatterns.length; i++) {
            if (t.includes(CommonJunkPatterns[i])) return true;
        }
        if (siteJunkKeywords && Array.isArray(siteJunkKeywords)) {
            for (var j = 0; j < siteJunkKeywords.length; j++) {
                if (t.includes(siteJunkKeywords[j].toLowerCase())) return true;
            }
        }
        return false;
    }

    /**
     * Check if an element matches any of the given CSS selectors.
     */
    function matchesExclude(el, excludeSelectors) {
        if (!el || !excludeSelectors) return false;
        try {
            return el.matches && el.matches(excludeSelectors.join(', '));
        } catch (e) {
            return false;
        }
    }

    /**
     * Prepare BR-split content into paragraph spans.
     * Used by sites like Twkan, TimoTxt, Novel543, NovelHall that use
     * <br> tags instead of <p> tags to separate paragraphs.
     */
    function prepareBrParagraphs(container, isTwkan) {
        if (!container) return [];

        if (isTwkan) {
            // Twkan-specific: remove ad containers, split childNodes on <br>,
            // extract text from <font>/<span>/<b>/<i> tags
            container.querySelectorAll('script, style, noscript, iframe, ins, .ad, .txtad, .txtcenter, .ad-placement, #ad-container').forEach(function(el) { el.remove(); });
            var paragraphs = [];
            var currentPart = [];

            function flushPart() {
                if (currentPart.length > 0) {
                    var joined = currentPart.join(' ').trim();
                    joined = joined.replace(/^[\u2003\u3000\t ]+/g, '').trim();
                    if (joined.length > 3) paragraphs.push(joined);
                    currentPart = [];
                }
            }

            Array.from(container.childNodes).forEach(function(node) {
                if (node.nodeType === 3) {
                    var txt = node.textContent.trim();
                    if (txt) currentPart.push(txt);
                } else if (node.nodeType === 1) {
                    var tag = node.tagName.toLowerCase();
                    if (tag === 'br') {
                        flushPart();
                    } else if (tag === 'font' || tag === 'span' || tag === 'b' || tag === 'i' || tag === 'strong' || tag === 'em') {
                        var txt = (node.innerText || node.textContent || '').trim();
                        if (txt) currentPart.push(txt);
                    } else {
                        flushPart();
                        var txt = (node.innerText || node.textContent || '').trim();
                        if (txt.length > 3) paragraphs.push(txt);
                    }
                }
            });
            flushPart();

            // Filter out twkan/ttkan self-promo
            paragraphs = paragraphs.filter(function(p) {
                var t = p.toLowerCase();
                return !(t.includes('twkan') || t.includes('ttkan'));
            });

            // Wrap each paragraph in a span for consistency with the TTS engine
            var newHtml = '';
            for (var i = 0; i < paragraphs.length; i++) {
                newHtml += '<span class="wtr-line-segment" data-wtr-index="' + i + '">' + paragraphs[i] + '</span>\n';
            }
            container.innerHTML = newHtml;
            return paragraphs;
        }

        // Generic BR preparation for non-twkan sites
        var children = Array.from(container.childNodes);
        var result = [];
        var currentText = [];

        function flush() {
            if (currentText.length > 0) {
                var joined = currentText.join(' ').trim().replace(/\s+/g, ' ');
                if (joined.length > 3) result.push(joined);
                currentText = [];
            }
        }

        children.forEach(function(node) {
            if (node.nodeType === 3) {
                var txt = node.textContent.trim();
                if (txt) currentText.push(txt);
            } else if (node.nodeType === 1) {
                var tag = node.tagName.toLowerCase();
                if (tag === 'br') {
                    flush();
                } else if (tag === 'p' || tag === 'div') {
                    flush();
                    var txt = (node.innerText || node.textContent || '').trim();
                    if (txt.length > 3) result.push(txt);
                } else {
                    var txt = (node.innerText || node.textContent || '').trim();
                    if (txt) currentText.push(txt);
                }
            }
        });
        flush();

        // Wrap in spans
        var html = '';
        for (var i = 0; i < result.length; i++) {
            html += '<span class="wtr-line-segment" data-wtr-index="' + i + '">' + result[i] + '</span>\n';
        }
        container.innerHTML = html;
        return result;
    }

    /**
     * Find the paragraph closest to the top of the viewport (for auto-focus).
     * Returns the index of the paragraph that is nearest to 100px from top.
     */
    function findScrollStartIndex(container, selector) {
        var elements = Array.from(container.querySelectorAll(selector || 'p, .wtr-line-segment') || []);
        if (elements.length === 0) return 0;
        var bestIndex = 0;
        var minDist = Infinity;
        for (var i = 0; i < elements.length; i++) {
            try {
                var rect = elements[i].getBoundingClientRect();
                var dist = Math.abs(rect.top - 100);
                if (dist < minDist) {
                    minDist = dist;
                    bestIndex = i;
                }
            } catch (e) {}
        }
        return bestIndex;
    }

    /**
     * Standard extraction: find containers, find paragraphs, filter junk.
     * Used by most sites that use standard <p> tags.
     */
    function extractStandardParagraphs(opts) {
        var containerSelectors = opts.containerSelectors || [];
        var paragraphSelector = opts.paragraphSelector || 'p, .wtr-line-segment';
        var excludeSelectors = opts.excludeSelectors || [];
        var siteJunkKeywords = opts.siteJunkKeywords || [];

        var paragraphs = [];
        var seen = new Set();

        for (var ci = 0; ci < containerSelectors.length; ci++) {
            var containers = document.querySelectorAll(containerSelectors[ci]);
            for (var c = 0; c < containers.length; c++) {
                var container = containers[c];
                if (matchesExclude(container, excludeSelectors)) continue;

                var elements = container.querySelectorAll(paragraphSelector);
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    if (matchesExclude(el, excludeSelectors)) continue;

                    var text = (el.innerText || el.textContent || '').trim();
                    if (isJunk(text, window.location.href, siteJunkKeywords)) continue;

                    // De-duplicate
                    var key = text.substring(0, 80);
                    if (seen.has(key)) continue;
                    seen.add(key);

                    el.setAttribute('data-wtr-index', String(paragraphs.length));
                    paragraphs.push(text);
                }
            }
            if (paragraphs.length > 0) break; // Use first matching container
        }

        return paragraphs;
    }

    // Expose globally
    window.WtrExtractorUtils = {
        isJunk: isJunk,
        matchesExclude: matchesExclude,
        prepareBrParagraphs: prepareBrParagraphs,
        findScrollStartIndex: findScrollStartIndex,
        extractStandardParagraphs: extractStandardParagraphs,
        CommonJunkPatterns: CommonJunkPatterns
    };
})();