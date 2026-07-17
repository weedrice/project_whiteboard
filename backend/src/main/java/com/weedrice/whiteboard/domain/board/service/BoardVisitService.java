package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardActivitySummary;
import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
class BoardVisitService {
    private static final long ACTIVITY_LOOKBACK_DAYS = 30L;

    private final BoardVisitRepository boardVisitRepository;
    private final BoardVisitWriter boardVisitWriter;
    private final MeterRegistry meterRegistry;

    public void touchVisit(Long userId, String boardUrl) {
        if (userId == null || boardUrl == null || boardUrl.isBlank()) {
            return;
        }
        try {
            boardVisitWriter.upsert(userId, boardUrl, DateTimeUtils.nowKST());
        } catch (RuntimeException exception) {
            Counter.builder("noviis.board.visit.failures")
                    .description("Board visit persistence failures")
                    .register(meterRegistry)
                    .increment();
            log.warn("Failed to persist board visit: userId={}, boardUrl={}", userId, boardUrl, exception);
        }
    }

    public Map<Long, BoardActivitySummary> getActivitySummaries(Collection<Long> boardIds, User user) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Map.of();
        }
        Long userId = user != null ? user.getUserId() : null;
        boolean isSuperAdmin = user != null && user.isUsableSuperAdmin();
        LocalDateTime recentCutoff = DateTimeUtils.nowKST().minusDays(ACTIVITY_LOOKBACK_DAYS);
        return boardVisitRepository.findActivityByBoardIds(boardIds, userId, isSuperAdmin, recentCutoff)
                .stream()
                .collect(Collectors.toMap(
                        BoardVisitRepository.BoardActivityProjection::getBoardId,
                        projection -> new BoardActivitySummary(
                                projection.getBoardId(),
                                projection.getLatestPostAt(),
                                projection.getNewPostCount() != null ? projection.getNewPostCount() : 0L)));
    }
}
