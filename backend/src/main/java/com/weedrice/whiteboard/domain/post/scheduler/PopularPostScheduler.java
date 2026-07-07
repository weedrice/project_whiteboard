package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PopularPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularPostScheduler {

    private final PostRepository postRepository;
    private final PopularPostRepository popularPostRepository;
    private final Clock clock;

    // 매시간 실행
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void aggregatePopularPosts() {
        log.info("인기글 집계 스케줄러 시작");

        popularPostRepository.deleteAll();
        LocalDateTime now = LocalDateTime.now(clock);

        aggregateAndSave("DAILY", now.minusDays(1));
        aggregateAndSave("WEEKLY", now.minusWeeks(1));

        log.info("인기글 집계 스케줄러 완료");
    }

    private void aggregateAndSave(String rankingType, LocalDateTime since) {
        List<Post> posts = postRepository.findByCreatedAtAfterAndIsDeleted(since, false);

        List<PopularPost> popularPosts = posts.stream()
                .map(post -> new PopularPost(rankingType, post, calculateScore(post), 0))
                .sorted((p1, p2) -> Double.compare(p2.getScore(), p1.getScore()))
                .limit(100)
                .collect(Collectors.toList());

        for (int i = 0; i < popularPosts.size(); i++) {
            PopularPost popularPost = popularPosts.get(i);
            popularPost.setRank(i + 1);
            popularPostRepository.save(popularPost);
        }
    }

    private double calculateScore(Post post) {
        return post.getViewCount() + (post.getLikeCount() * 10.0) + (post.getCommentCount() * 5.0);
    }
}
