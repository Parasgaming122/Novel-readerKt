/**
 * NovelHubApp Custom Extractor
 *
 * NovelHubApp is a pure SPA (Nuxt.js). Content is rendered client-side.
 * This extractor waits for the content to appear in the DOM.
 * The WebScripts.kt TTS bridge already handles SPA URL tracking via hash injection.
 *
 * Content structure (after JS render): standard <p> tags inside a content container.
 */
(function() {
    'use strict';
    var utils = window.WtrExtractorUtils;

    window.__siteExtractor = function(opts) {
        // For SPAs, try multiple possible content containers
        var possibleSelectors = [
            '#chapter-content',
            '.chapter-content',
            '.reader-content',
            '.content-wrapper p',
            'main article',
            '.nuxt-page-container p'
        ];

        var paragraphs = [];

        for (var si = 0; si < possibleSelectors.length; si++) {
            var container = document.querySelector(possibleSelectors[si]);
            if (container) {
                paragraphs = utils.extractStandardParagraphs({
                    containerSelectors: [possibleSelectors[si]],
                    paragraphSelector: opts.paragraphSelector || 'p, .wtr-line-segment',
                    excludeSelectors: opts.excludeSelectors || [],
                    siteJunkKeywords: opts.siteJunkKeywords || []
                });
                if (paragraphs.length > 3) break;
            }
        }

        // Ultimate fallback: find the element with the most <p> children
        if (paragraphs.length === 0) {
            var allDivs = document.querySelectorAll('div, article, section, main');
            var bestDiv = null;
            var bestCount = 0;
            for (var d = 0; d < allDivs.length; d++) {
                var pCount = allDivs[d].querySelectorAll('p').length;
                if (pCount > bestCount) {
                    bestCount = pCount;
                    bestDiv = allDivs[d];
                }
            }
            if (bestDiv && bestCount > 3) {
                paragraphs = utils.extractStandardParagraphs({
                    containerSelectors: [],
                    paragraphSelector: 'p',
                    excludeSelectors: opts.excludeSelectors || [],
                    siteJunkKeywords: opts.siteJunkKeywords || []
                });
            }
        }

        return { paragraphs: paragraphs, startIndex: 0 };
    };
})();