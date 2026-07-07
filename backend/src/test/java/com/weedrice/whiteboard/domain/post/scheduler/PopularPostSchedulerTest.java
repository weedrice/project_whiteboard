package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PopularPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularPostSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T03:00:00Z"), KST);

    @Mock
    private PostRepository postRepository;

    @Mock
    private PopularPostRepository popularPostRepository;

    private PopularPostScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PopularPostScheduler(postRepository, popularPostRepository, FIXED_CLOCK);
    }

    @Test
    void aggregatePopularPosts_usesFixedClockWindowsAndRanksByScore() {
        Post highScorePost = post(1L, 20, 3, 2);
        Post lowScorePost = post(2L, 5, 0, 1);
        Post weeklyPost = post(3L, 7, 1, 0);
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        when(postRepository.findByCreatedAtAfterAndIsDeleted(now.minusDays(1), false))
                .thenReturn(List.of(lowScorePost, highScorePost));
        when(postRepository.findByCreatedAtAfterAndIsDeleted(now.minusWeeks(1), false))
                .thenReturn(List.of(weeklyPost));

        scheduler.aggregatePopularPosts();

        verify(popularPostRepository).deleteAll();
        verify(postRepository).findByCreatedAtAfterAndIsDeleted(now.minusDays(1), false);
        verify(postRepository).findByCreatedAtAfterAndIsDeleted(now.minusWeeks(1), false);

        ArgumentCaptor<PopularPost> popularPostCaptor = ArgumentCaptor.forClass(PopularPost.class);
        verify(popularPostRepository, org.mockito.Mockito.times(3)).save(popularPostCaptor.capture());
        List<PopularPost> saved = popularPostCaptor.getAllValues();
        assertThat(saved)
                .extracting(PopularPost::getRankingType)
                .containsExactly("DAILY", "DAILY", "WEEKLY");
        assertThat(saved)
                .extracting(popularPost -> popularPost.getPost().getPostId())
                .containsExactly(1L, 2L, 3L);
        assertThat(saved)
                .extracting(PopularPost::getRank)
                .containsExactly(1, 2, 1);
        assertThat(saved.get(0).getScore()).isEqualTo(60.0);
        assertThat(saved.get(1).getScore()).isEqualTo(10.0);
    }

    private Post post(Long postId, int viewCount, int likeCount, int commentCount) {
        Post post = Post.builder()
                .title("Post " + postId)
                .contents("content")
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        ReflectionTestUtils.setField(post, "viewCount", viewCount);
        ReflectionTestUtils.setField(post, "likeCount", likeCount);
        ReflectionTestUtils.setField(post, "commentCount", commentCount);
        return post;
    }
}
