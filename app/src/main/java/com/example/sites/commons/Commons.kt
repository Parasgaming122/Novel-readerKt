package com.example.sites.commons

object CommonSelectors {
    const val STANDARD_PARAGRAPH = "p, .wtr-line-segment"

    val COMMON_EXCLUDE = listOf(
        ".author-note",
        ".gift-box",
        ".recommend-box",
        ".comment-area",
        ".m-comment",
        ".user-opinion",
        ".review-item",
        ".j_recommendation",
        ".book-recommend",
        ".cha-nav",
        ".chapter-control",
        ".nav",
        ".nav-btn",
        ".next_chap",
        ".prev_chap",
        ".next-page",
        ".prev-page",
        ".ads",
        ".adsbygoogle",
        ".btn-group",
        ".custom-control",
        ".category",
        ".desc",
        ".title-book",
        ".chapter-nav",
        ".bottem",
        ".bottem1",
        ".bottem2",
        ".txtnav",
        ".readnav",
        ".headnav",
        ".footer",
        ".header",
        "#header",
        "#footer"
    )
}

object CommonJunkKeywords {
    val GENERIC_PROMO = listOf(
        "join our discord", "join discord", "patreon", "support me", "support the author",
        "rate this", "please review", "please rate", "author's note", "author note",
        "editor's note", "editor note",
        "find any errors", "broken links", "report us", "if you find any",
        "next chapter", "previous chapter",
        "table of contents", "read online free", "read online for free",
        "unlocked chapters", "bonus chapters", "sign up", "sign in", "subscribe to",
        "follow my page", "download our app", "read this novel", "other novel", "like this book",
        "stop your ad blocker", "ad blocker detected", "本章未完", "点击下一页", "继续阅读", "本章完", "（本章未完）", "(本章完)",
        "最新网址", "手机用户请浏览", "更多精彩内容", "投推荐票", "上一章", "下一章", "目录", "书架", "加入书架", "返回封面"
    )
}

object CommonPatterns {
    val TITLE_PATTERNS = listOf(
        Regex("""(?i)(?:Chapter|Ch\.|Ch|Episode)\s*(\d+(:?\.\d+)?(-(\d+))?)"""),
        Regex("""(?i)\b(?:chapter|chap|ch|episode|ep)\.?\s*([ivxldcm]+)"""),
        Regex("""(第\s*[0-9一二三四五六七八九十百千万]+[章回节集卷折篇])"""),
        Regex("""(?i)Chapter\s*([a-zA-Z0-9]+)"""),
        Regex("""(?i)Ch\s*([a-zA-Z0-9]+)"""),
        Regex("""\b(\d+)\s*$""")
    )

    val URL_PATTERNS = listOf(
        Regex("""(?i)chapter[-_]?(\d+)"""),
        Regex("""(?i)ch[-_]?(\d+)"""),
        Regex("""/(\d+)\.html"""),
        Regex("""/(\d+)""")
    )
}
