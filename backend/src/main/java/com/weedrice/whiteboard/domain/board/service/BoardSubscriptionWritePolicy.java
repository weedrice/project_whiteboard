package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BoardSubscriptionWritePolicy {
    private static final String USER_SORT_ORDER_CONSTRAINT = "uk_board_subscriptions_user_sort_order";

    private final BoardSubscriptionRepository boardSubscriptionRepository;

    void validateNotSubscribed(Long userId, Long boardId) {
        boardSubscriptionRepository.findById(new BoardSubscriptionId(userId, boardId))
                .ifPresent(subscription -> {
                    throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
                });
    }

    BusinessException resolveSubscriptionConflict(Board board, DataIntegrityViolationException ex) {
        if (containsConstraint(ex, USER_SORT_ORDER_CONSTRAINT)) {
            return new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Duplicate board subscription sort order");
        }
        if (board != null) {
            return new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private boolean containsConstraint(Throwable throwable, String... candidates) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (containsAny(message, candidates)) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (containsAny(constraintName != null ? constraintName.toLowerCase() : "", candidates)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && source.contains(candidate.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
