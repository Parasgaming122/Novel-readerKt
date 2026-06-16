/**
 * Twkan/Ttkan Custom Extractor
 *
 * Content is in #txtcontent0 with BR-split text nodes and <font> tags
 * wrapping Google Translate output. No standard <p> tags.
 *
 * This replaces the old inline twkan fast-path logic that was duplicated
 * 5 times in BrowserAppScreen.kt (and the dead TwkanReader.js).
 */
(function() {
    'use strict';
    var utils = window.WtrExtractorUtils;

    window.__siteExtractor = function(opts) {
        var contentEl = document.querySelector('#txtcontent0');
        if (!contentEl) {
            contentEl = document.querySelector('[id^="txtcontent"]') || document.querySelector('.txtcontent');
        }
        if (!contentEl) {
            // Fallback to standard extraction
            return { paragraphs: utils.extractStandardParagraphs(opts), startIndex: 0 };
        }

        // Use the shared BR preparation utility (twkan mode)
        var paragraphs = utils.prepareBrParagraphs(contentEl, true);

        // Find scroll start index
        var startIdx = utils.findScrollStartIndex(contentEl, 'p, span.wtr-line-segment');

        return { paragraphs: paragraphs, startIndex: startIdx };
    };
})();