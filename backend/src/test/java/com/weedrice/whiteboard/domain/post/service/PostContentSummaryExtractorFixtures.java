package com.weedrice.whiteboard.domain.post.service;

public final class PostContentSummaryExtractorFixtures {

    private PostContentSummaryExtractorFixtures() {
    }

    public static PostContentSummaryExtractor withNoviisCdn() {
        return new PostContentSummaryExtractor(
                "https://noviis.kr",
                "cdn.noviis.kr");
    }
}
