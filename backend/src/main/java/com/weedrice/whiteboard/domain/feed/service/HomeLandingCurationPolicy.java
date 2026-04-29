package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class HomeLandingCurationPolicy {

    HomeLandingSections curate(List<PostSummary> curatedPosts) {
        return new HomeLandingSections(
                curatedPosts.isEmpty() ? null : curatedPosts.getFirst(),
                slice(curatedPosts, 1, 4),
                slice(curatedPosts, 4, 10),
                slice(curatedPosts, 0, 6));
    }

    private List<PostSummary> slice(List<PostSummary> posts, int startInclusive, int endExclusive) {
        if (posts.isEmpty() || startInclusive >= posts.size()) {
            return List.of();
        }
        return posts.subList(startInclusive, Math.min(posts.size(), endExclusive));
    }

    record HomeLandingSections(
            PostSummary featuredPost,
            List<PostSummary> editorPicks,
            List<PostSummary> trendingPosts,
            List<PostSummary> liveActivity) {
    }
}
