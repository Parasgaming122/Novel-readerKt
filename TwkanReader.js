/**
 * TwkanReader.js
 * Dedicated content scraper specialized for twkan.com / ttkan.co.
 * Designed to scrape #txtcontent0, safely clean up script tags,
 * and preserve translated text from wrapped <font> tags.
 */

function extractTwkanContent() {
    try {
        let contentEl = document.querySelector('#txtcontent0');
        if (!contentEl) {
            contentEl = document.querySelector('[id^="txtcontent"]') || document.querySelector('.txtcontent');
        }
        if (!contentEl) {
            return JSON.stringify({ paragraphs: [], error: "Content container #txtcontent0 not found" });
        }

        // Clone the content element to perform transformations without altering layout
        let clone = contentEl.cloneNode(true);

        // Remove scripts, style blocks, non-script embeds, ad placements, etc.
        clone.querySelectorAll("script, style, noscript, iframe, ins, .ad, .txtad, .txtcenter, .ad-placement, #ad-container").forEach(el => el.remove());

        let paragraphs = [];
        let currentPart = [];

        function flushPart() {
            if (currentPart.length > 0) {
                let joined = currentPart.join(" ").trim();
                // Clean unicode indentations
                joined = joined.replace(/^[\u2003\u3000\t ]+/g, "").trim();
                if (joined.length > 3) {
                    paragraphs.push(joined);
                }
                currentPart = [];
            }
        }

        let children = Array.from(clone.childNodes);
        children.forEach(node => {
            if (node.nodeType === 3) { // Text Node
                let txt = node.textContent.trim();
                if (txt) {
                    currentPart.push(txt);
                }
            } else if (node.nodeType === 1) { // Element Node
                let tagName = node.tagName.toLowerCase();
                if (tagName === 'br') {
                    flushPart();
                } else if (tagName === 'font' || tagName === 'span' || tagName === 'b' || tagName === 'i' || tagName === 'strong' || tagName === 'em') {
                    // Extract text inside inline elements, especially <font> tags containing translations from Google Translate
                    let txt = node.innerText || node.textContent;
                    txt = txt.trim();
                    if (txt) {
                        currentPart.push(txt);
                    }
                } else {
                    flushPart();
                    let txt = node.innerText || node.textContent;
                    txt = txt.trim();
                    if (txt.length > 3) {
                        paragraphs.push(txt);
                    }
                }
            }
        });
        flushPart();

        // Exclude site signatures or marketing junk
        paragraphs = paragraphs.filter(p => {
            let t = p.toLowerCase();
            if (t.includes("twkan") || t.includes("ttkan")) return false;
            return p.length > 3;
        });

        // Determine scroll-based active index targeting the closest middle element
        let originalElements = Array.from(contentEl.querySelectorAll('p, span.wtr-line-segment') || []);
        let bestIndex = 0;
        let minDistance = Infinity;
        for (let i = 0; i < originalElements.length; i++) {
            let rect = originalElements[i].getBoundingClientRect();
            let dist = Math.abs(rect.top - 100);
            if (dist < minDistance) {
                minDistance = dist;
                bestIndex = i;
            }
        }

        return JSON.stringify({
            paragraphs: paragraphs,
            startIndex: bestIndex
        });
    } catch (e) {
        return JSON.stringify({
            error: e.toString(),
            paragraphs: []
        });
    }
}
