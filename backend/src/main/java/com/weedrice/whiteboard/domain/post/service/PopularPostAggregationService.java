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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PopularPostAggregationService {

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
        List<PopularPost> rankings = new ArrayList<>();
        rankings.addAll(createRankings("DAILY", now.minusDays(1)));
        rankings.addAll(createRankings("WEEKLY", now.minusWeeks(1)));

        popularPostRepository.deleteAllInBatch();
        popularPostRepository.saveAll(rankings);
        return true;
    }

    private List<PopularPost> createRankings(String rankingType, LocalDateTime since) {
        List<PopularPost> rankings = postRepository.findByCreatedAtAfterAndIsDeleted(since, false).stream()
                .map(post -> new PopularPost(rankingType, post, calculateScore(post), 0))
                .sorted(Comparator.comparingDouble(PopularPost::getScore).reversed())
                .limit(100)
                .toList();

        for (int index = 0; index < rankings.size(); index++) {
            rankings.get(index).setRank(index + 1);
        }
        return rankings;
    }

    private double calculateScore(Post post) {
        return post.getViewCount() + (post.getLikeCount() * 10.0) + (post.getCommentCount() * 5.0);
    }
}
