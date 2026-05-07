package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFeedCleanupService {

    private final UserFeedRepository userFeedRepository;

    @Transactional
    public int cleanupHardStalePostFeeds() {
        int deletedCount = userFeedRepository.deleteHardStalePostFeeds(FeedGenerationService.CONTENT_TYPE_POST);
        if (deletedCount > 0) {
            log.info("Deleted {} hard stale POST user feed(s)", deletedCount);
        }
        return deletedCount;
    }
}
