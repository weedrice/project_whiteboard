package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardActivitySummary;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardVisit;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BoardVisitService {
    private final BoardVisitRepository boardVisitRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchVisit(Long userId, String boardUrl) {
        if (userId == null || boardUrl == null || boardUrl.isBlank()) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        Board board = boardRepository.findByBoardUrl(boardUrl).orElse(null);
        if (user == null || board == null) {
            return;
        }
        LocalDateTime now = DateTimeUtils.nowKST();
        BoardVisit visit = boardVisitRepository.findByUserAndBoard(user, board)
                .orElseGet(() -> new BoardVisit(user, board, now));
        visit.touch(now);
        boardVisitRepository.save(visit);
    }

    public Map<Long, BoardActivitySummary> getActivitySummaries(Collection<Long> boardIds, User user) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Map.of();
        }
        Long userId = user != null ? user.getUserId() : null;
        boolean isSuperAdmin = user != null && user.isUsableSuperAdmin();
        return boardVisitRepository.findActivityByBoardIds(boardIds, userId, isSuperAdmin)
                .stream()
                .collect(Collectors.toMap(
                        BoardVisitRepository.BoardActivityProjection::getBoardId,
                        projection -> new BoardActivitySummary(
                                projection.getBoardId(),
                                projection.getLatestPostAt(),
                                projection.getNewPostCount() != null ? projection.getNewPostCount() : 0L)));
    }
}
