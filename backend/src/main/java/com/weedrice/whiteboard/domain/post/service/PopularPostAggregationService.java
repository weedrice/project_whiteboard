package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PopularPostAggregationLockRepository;
import com.weedrice.whiteboard.domain.post.repository.PopularPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PopularPostAggregationService {

    private static final int RANKING_LIMIT = 100;

    private final PostRepository postRepository;
    private final PopularPostRepository popularPostRepository;
    private final PopularPostAggregationLockRepository lockRepository;
    private final Clock clock;

    @Transactional
    public boolean aggregate() {
        if (!lockRepository.tryAcquireTransactionLock()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<PostRepository.PopularRankingProjection> candidates =
                postRepository.findPopularRankingCandidates(
                        now.minusDays(1),
                        now.minusWeeks(1),
                        RANKING_LIMIT);
        List<PopularPost> rankings = createRankings(candidates);

        popularPostRepository.deleteAllInBatch();
        popularPostRepository.saveAll(rankings);
        return true;
    }

    private List<PopularPost> createRankings(List<PostRepository.PopularRankingProjection> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = candidates.stream()
                .map(PostRepository.PopularRankingProjection::getPostId)
                .distinct()
                .toList();
        Map<Long, Post> postsById = new LinkedHashMap<>();
        postRepository.findByPostIdIn(postIds)
                .forEach(post -> postsById.put(post.getPostId(), post));
        return candidates.stream()
                .filter(candidate -> postsById.containsKey(candidate.getPostId()))
                .map(candidate -> PopularPost.builder()
                        .rankingType(candidate.getRankingType())
                        .post(postsById.get(candidate.getPostId()))
                        .score(candidate.getScore())
                        .rank(Math.toIntExact(candidate.getRankingPosition()))
                        .build())
                .toList();
    }
}
