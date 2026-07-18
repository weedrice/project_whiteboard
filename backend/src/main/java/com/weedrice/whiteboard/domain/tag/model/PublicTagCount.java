package com.weedrice.whiteboard.domain.tag.model;

public record PublicTagCount(
        Long tagId,
        String tagName,
        long postCount) {
}
