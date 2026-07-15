package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;

record FileAccessResult(File file, CacheScope cacheScope) {

    enum CacheScope {
        PUBLIC_EMOTICON,
        RESTRICTED_EMOTICON,
        OTHER
    }
}
