package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PopularPostAggregationLockRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularPostAggregationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private PostRepository postRepository;
    @Mock
    private PopularPostRepository popularPostRepository;
    @Mock
    private PopularPostAggregationLockRepository lockRepository;

    private PopularPostAggregationService service;

    @BeforeEach
    void setUp() {
        service = new PopularPostAggregationService(
                postRepository, popularPostRepository, lockRepository, FIXED_CLOCK);
    }

    @Test
    void aggregate_doesNotChangeDataWhenLockIsUnavailable() {
        when(lockRepository.tryAcquireTransactionLock()).thenReturn(false);

        assertThat(service.aggregate()).isFalse();

        verifyNoInteractions(postRepository, popularPostRepository);
    }

    @Test
    void aggregate_replacesDailyAndWeeklyRankingsInOneBatch() {
        Post highScorePost = post(1L, 20, 3, 2);
        Post lowScorePost = post(2L, 5, 0, 1);
        Post weeklyPost = post(3L, 7, 1, 0);
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        when(lockRepository.tryAcquireTransactionLock()).thenReturn(true);
        when(postRepository.findPopularRankingCandidates(now.minusDays(1), now.minusWeeks(1), 100))
                .thenReturn(List.of(
                        ranking("DAILY", 1L, 60.0, 1L),
                        ranking("DAILY", 2L, 10.0, 2L),
                        ranking("WEEKLY", 1L, 60.0, 1L),
                        ranking("WEEKLY", 3L, 17.0, 2L)));
        when(postRepository.findByPostIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(highScorePost, lowScorePost, weeklyPost));

        assertThat(service.aggregate()).isTrue();

        verify(popularPostRepository).deleteAllInBatch();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PopularPost>> captor = ArgumentCaptor.forClass(List.class);
        verify(popularPostRepository).saveAll(captor.capture());
        List<PopularPost> saved = captor.getValue();
        assertThat(saved).extracting(PopularPost::getRankingType)
                .containsExactly("DAILY", "DAILY", "WEEKLY", "WEEKLY");
        assertThat(saved).extracting(popularPost -> popularPost.getPost().getPostId())
                .containsExactly(1L, 2L, 1L, 3L);
        assertThat(saved).extracting(PopularPost::getRank).containsExactly(1, 2, 1, 2);
        assertThat(saved).extracting(PopularPost::getScore).containsExactly(60.0, 10.0, 60.0, 17.0);
        verify(postRepository).findPopularRankingCandidates(now.minusDays(1), now.minusWeeks(1), 100);
        verify(postRepository).findByPostIdIn(List.of(1L, 2L, 3L));
    }

    @Test
    void aggregate_doesNotDeleteExistingRankingsBeforeAllScoresAreCalculated() {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        when(lockRepository.tryAcquireTransactionLock()).thenReturn(true);
        when(postRepository.findPopularRankingCandidates(now.minusDays(1), now.minusWeeks(1), 100))
                .thenThrow(new IllegalStateException("aggregation failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(service::aggregate)
                .isInstanceOf(IllegalStateException.class);

        verify(popularPostRepository, never()).deleteAllInBatch();
        verify(popularPostRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    private Post post(Long postId, int viewCount, int likeCount, int commentCount) {
        Post post = Post.builder().title("Post " + postId).contents("content").build();
        ReflectionTestUtils.setField(post, "postId", postId);
        ReflectionTestUtils.setField(post, "viewCount", viewCount);
        ReflectionTestUtils.setField(post, "likeCount", likeCount);
        ReflectionTestUtils.setField(post, "commentCount", commentCount);
        return post;
    }

    private PostRepository.PopularRankingProjection ranking(
            String rankingType, Long postId, Double score, Long position) {
        return new PostRepository.PopularRankingProjection() {
            @Override
            public String getRankingType() {
                return rankingType;
            }

            @Override
            public Long getPostId() {
                return postId;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public Long getRankingPosition() {
                return position;
            }
        };
    }
}
