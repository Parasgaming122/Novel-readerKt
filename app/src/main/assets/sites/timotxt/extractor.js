/**
 * TimoTxt Custom Extractor
 *
 * Content uses <p> tags inside `.chapter-content .content` but some chapters
 * also use <br> separators. This extractor handles both cases.
 */
(function() {
    'use strict';
    var utils = window.WtrExtractorUtils;

    window.__siteExtractor = function(opts) {
        var container = document.querySelector('.chapter-content .content') ||
            document.querySelector('.chapter-content') ||
            document.querySelector('.content');
        if (!container) {
            return { paragraphs: utils.extractStandardParagraphs(opts), startIndex: 0 };
        }

        // Check if content has <p> tags
        var pCount = container.querySelectorAll('p').length;
        var paragraphs;

        if (pCount > 3) {
            // Standard <p> extraction
            paragraphs = utils.extractStandardParagraphs(opts);
        } else {
            // BR-split content — use preparation
            paragraphs = utils.prepareBrParagraphs(container, false);
        }

        var startIdx = utils.findScrollStartIndex(container, 'p, .wtr-line-segment');
        return { paragraphs: paragraphs, startIndex: startIdx };
    };
})();