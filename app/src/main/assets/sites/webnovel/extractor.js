/**
 * WebNovel (m.webnovel.com) Custom Extractor
 *
 * Extracts chapter text and navigation from m.webnovel.com.
 * The mobile site is Next.js SSR with clean JSON in `window.__NEXT_DATA__`.
 *
 * Key feature: Extracts preChapterId/nextChapterId from __NEXT_DATA__
 * (the prev chapter has NO DOM link — only next chapter is in the DOM).
 *
 * The standard CSS-based extraction also works for paragraph text.
 * This extractor adds navigation URL extraction on top.
 */
(function() {
    'use strict';
    var utils = window.WtrExtractorUtils;

    window.__siteExtractor = function(opts) {
        // First, try standard extraction (CSS selectors work for m.webnovel.com)
        var result = utils.extractStandardParagraphs(opts);

        // Also extract navigation from __NEXT_DATA__ and store on window
        // for the Kotlin side to use for auto-advance
        try {
            var ndScript = document.getElementById('__NEXT_DATA__');
            if (ndScript) {
                var nd = JSON.parse(ndScript.textContent);
                var entities = nd && nd.props && nd.props.pageProps &&
                    nd.props.pageProps.initialState && nd.props.pageProps.initialState.entities;
                if (entities && entities.chapter) {
                    var chapters = entities.chapter;
                    var chapterKeys = Object.keys(chapters);
                    for (var i = 0; i < chapterKeys.length; i++) {
                        var ch = chapters[chapterKeys[i]];
                        if (ch && ch.contents && ch.contents.length > 0) {
                            var bookId = null;
                            var bookKeys = Object.keys(entities.books || {});
                            if (bookKeys.length > 0) bookId = bookKeys[0];

                            // Store navigation info for Kotlin to pick up
                            window.__wtrNavInfo = {
                                bookId: bookId,
                                chapterId: ch.chapterId || chapterKeys[i],
                                title: ch.chapterName || document.title,
                                nextChapterId: ch.nextChapterId || null,
                                nextChapterName: ch.nextChapterName || null,
                                prevChapterId: ch.preChapterId || null,
                                prevChapterName: ch.preChapterName || null,
                                isLocked: (ch.vipStatus === 1 || ch.price > 0),
                                isEncrypted: (ch.encryptType > 0),
                                base: 'https://m.webnovel.com/book/'
                            };

                            // Build full URLs
                            if (bookId) {
                                if (window.__wtrNavInfo.nextChapterId) {
                                    window.__wtrNavInfo.nextUrl = window.__wtrNavInfo.base + bookId + '/' + window.__wtrNavInfo.nextChapterId;
                                }
                                if (window.__wtrNavInfo.prevChapterId) {
                                    window.__wtrNavInfo.prevUrl = window.__wtrNavInfo.base + bookId + '/' + window.__wtrNavInfo.prevChapterId;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (e) {
            // Navigation extraction failed — paragraph extraction still works
        }

        var startIdx = 0;
        // Find the .cha-content container for scroll position
        var contentDiv = document.querySelector('.cha-content, .cha-words');
        if (contentDiv) {
            startIdx = utils.findScrollStartIndex(contentDiv, 'div.cha-paragraph p, p');
        }

        return { paragraphs: result, startIndex: startIdx };
    };
})();