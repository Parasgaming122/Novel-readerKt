/**
 * WTR-Lab Custom Extractor
 *
 * WTR-Lab uses <div class="wtr-line"> for each text line (NOT <p> tags).
 * Each chapter is in <div class="chapter-container" id="chapter-N">.
 * Multi-chapter infinite scroll — multiple chapters may be on one page.
 *
 * This extractor handles the non-standard div-based content structure.
 */
(function() {
    'use strict';
    var utils = window.WtrExtractorUtils;

    window.__siteExtractor = function(opts) {
        var paragraphs = [];
        var seen = new Set();
        var siteJunk = opts.siteJunkKeywords || [];
        var excludeSels = opts.excludeSelectors || [];

        // Find all chapter containers
        var chapters = document.querySelectorAll('.chapter-container, .chapter-body');
        if (chapters.length === 0) {
            // Fallback: try standard extraction
            return { paragraphs: utils.extractStandardParagraphs(opts), startIndex: 0 };
        }

        // Extract from ALL chapters on the page (infinite scroll may load many)
        for (var ci = 0; ci < chapters.length; ci++) {
            var chapter = chapters[ci];
            if (utils.matchesExclude(chapter, excludeSels)) continue;

            var lines = chapter.querySelectorAll('div.wtr-line, p, .wtr-line-segment');
            for (var i = 0; i < lines.length; i++) {
                var el = lines[i];
                if (utils.matchesExclude(el, excludeSels)) continue;

                var text = (el.innerText || el.textContent || '').trim();
                if (utils.isJunk(text, window.location.href, siteJunk)) continue;

                var key = text.substring(0, 80);
                if (seen.has(key)) continue;
                seen.add(key);

                el.setAttribute('data-wtr-index', String(paragraphs.length));
                paragraphs.push(text);
            }
        }

        // Find the chapter container that is currently in/near viewport
        var startIdx = 0;
        var minDist = Infinity;
        for (var c2 = 0; c2 < chapters.length; c2++) {
            try {
                var rect = chapters[c2].getBoundingClientRect();
                var dist = Math.abs(rect.top - 100);
                if (dist < minDist) {
                    minDist = dist;
                    // Count paragraphs in all previous chapters to get start index
                    startIdx = 0;
                    for (var prev = 0; prev < c2; prev++) {
                        var prevLines = chapters[prev].querySelectorAll('div.wtr-line, p, .wtr-line-segment');
                        startIdx += prevLines.length;
                    }
                }
            } catch (e) {}
        }

        return { paragraphs: paragraphs, startIndex: startIdx };
    };
})();