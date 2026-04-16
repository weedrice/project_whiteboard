package com.weedrice.whiteboard.domain.post.service;

import java.util.Collections;
import java.util.Set;

record PostUserInteractionContext(Set<Long> likedPostIds,
                                  Set<Long> scrappedPostIds,
                                  Set<String> subscribedBoardUrls) {

    static PostUserInteractionContext empty() {
        return new PostUserInteractionContext(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }
}
