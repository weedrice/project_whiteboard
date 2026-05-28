package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeLandingSectionAssemblerTest {

    private final HomeLandingSectionAssembler assembler = new HomeLandingSectionAssembler();

    @Test
    void assemble_splitsCuratedAndLatestPostsIntoLandingSections() {
        List<FeedPostSummary> curatedPosts = List.of(
                post(1L),
                post(2L),
                post(3L),
                post(4L),
                post(5L),
                post(6L),
                post(7L),
                post(8L),
                post(9L),
                post(10L),
                post(11L));
        List<FeedPostSummary> latestPosts = List.of(
                post(21L),
                post(22L),
                post(23L),
                post(24L),
                post(25L),
                post(26L),
                post(27L));

        HomeLandingResponse response = assembler.assemble(curatedPosts, latestPosts, List.of(), null);

        assertThat(response.getSections()).extracting(HomeLandingResponse.Section::getSectionType)
                .containsExactly("FEATURED", "EDITOR_PICKS", "TRENDING", "LIVE_ACTIVITY");
        assertThat(response.getSections()).extracting(HomeLandingResponse.Section::getLimit)
                .containsExactly(1, 3, 9, 6);
        assertThat(response.getSections().get(0).getItems()).extracting(FeedPostSummary::getPostId)
                .containsExactly(1L);
        assertThat(response.getSections().get(1).getItems()).extracting(FeedPostSummary::getPostId)
                .containsExactly(2L, 3L, 4L);
        assertThat(response.getSections().get(2).getItems()).extracting(FeedPostSummary::getPostId)
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(response.getSections().get(3).getItems()).extracting(FeedPostSummary::getPostId)
                .containsExactly(21L, 22L, 23L, 24L, 25L, 26L);
    }

    @Test
    void assemble_returnsEmptySectionsWhenSourcePostsAreMissing() {
        HomeLandingResponse response = assembler.assemble(null, null, List.of(), null);

        assertThat(response.getSections()).extracting(HomeLandingResponse.Section::getSectionType)
                .containsExactly("FEATURED", "EDITOR_PICKS", "TRENDING", "LIVE_ACTIVITY");
        assertThat(response.getSections()).allSatisfy(section -> assertThat(section.getItems()).isEmpty());
    }

    private FeedPostSummary post(Long postId) {
        return FeedPostSummary.builder()
                .postId(postId)
                .title("post-" + postId)
                .build();
    }
}
