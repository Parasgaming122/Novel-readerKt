/**
 * Novel543 Custom Extractor
 *
 * Same pattern as TimoTxt — <p> tags or BR-split content.
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

        var pCount = container.querySelectorAll('p').length;
        var paragraphs;

        if (pCount > 3) {
            paragraphs = utils.extractStandardParagraphs(opts);
        } else {
            paragraphs = utils.prepareBrParagraphs(container, false);
        }

        var startIdx = utils.findScrollStartIndex(container, 'p, .wtr-line-segment');
        return { paragraphs: paragraphs, startIndex: startIdx };
    };
})();