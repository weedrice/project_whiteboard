package com.weedrice.whiteboard.domain.post.scheduler;

import com.weedrice.whiteboard.domain.post.service.PopularPostAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularPostScheduler {

    private final PopularPostAggregationService aggregationService;

    @Scheduled(cron = "0 0 * * * ?")
    public void aggregatePopularPosts() {
        if (aggregationService.aggregate()) {
            log.info("인기글 집계 작업 완료");
            return;
        }
        log.debug("다른 인스턴스가 인기글 집계를 실행 중이므로 건너뜁니다");
    }
}
