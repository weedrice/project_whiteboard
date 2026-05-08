package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class BoardSubscriptionService {
    private static final String USER_SORT_ORDER_CONSTRAINT = "uk_board_subscriptions_user_sort_order";


    private final BoardRepository boardRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardSubscriptionService(BoardRepository boardRepository,
                             BoardSubscriptionRepository boardSubscriptionRepository,
                             UserWritableResolver userWritableResolver,
                             BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userWritableResolver = userWritableResolver;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    void subscribeBoard(Long userId, String boardUrl) {
        User user = userWritableResolver.resolveForUpdate(userId);
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        boardAccessPolicy.validateReadable(board, user);

        boardSubscriptionRepository.findById(new BoardSubscriptionId(userId, board.getBoardId()))
                .ifPresent(subscription -> {
                    throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
                });

        Integer maxSortOrder = boardSubscriptionRepository.findMaxSortOrder(user);

        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(maxSortOrder + 1)
                .build();
        try {
            boardSubscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException ex) {
            throw resolveSubscriptionConflict(board, ex);
        }
    }

    void unsubscribeBoard(Long userId, String boardUrl) {
        User user = userWritableResolver.resolve(userId);
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        BoardSubscription subscription = boardSubscriptionRepository
                .findById(new BoardSubscriptionId(userId, board.getBoardId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUBSCRIBED));
        boardSubscriptionRepository.delete(subscription);
    }

    void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        User user = userWritableResolver.resolveForUpdate(userId);
        if (boardUrls == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Set<String> requestedBoardUrls = new LinkedHashSet<>(boardUrls);
        if (requestedBoardUrls.isEmpty() || requestedBoardUrls.size() != boardUrls.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<BoardSubscription> subscriptions = boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                requestedBoardUrls,
                user.isUsableSuperAdmin());

        Set<String> currentBoardUrls = subscriptions.stream()
                .map(sub -> sub.getBoard().getBoardUrl())
                .collect(Collectors.toSet());
        if (currentBoardUrls.size() != subscriptions.size()
                || !currentBoardUrls.equals(new HashSet<>(requestedBoardUrls))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<Integer> requestedSortOrders = subscriptions.stream()
                .map(BoardSubscription::getSortOrder)
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<String, BoardSubscription> subscriptionByBoardUrl = subscriptions.stream()
                .collect(Collectors.toMap(
                        sub -> sub.getBoard().getBoardUrl(),
                        sub -> sub));
        int temporarySortOrderBase = boardSubscriptionRepository.findMaxSortOrder(user) + subscriptions.size();
        try {
            for (int i = 0; i < boardUrls.size(); i++) {
                subscriptionByBoardUrl.get(boardUrls.get(i)).updateSortOrder(temporarySortOrderBase + i + 1);
            }
            boardSubscriptionRepository.saveAll(subscriptions);
            boardSubscriptionRepository.flush();

            for (int i = 0; i < boardUrls.size(); i++) {
                subscriptionByBoardUrl.get(boardUrls.get(i)).updateSortOrder(requestedSortOrders.get(i));
            }
            boardSubscriptionRepository.saveAll(subscriptions);
            boardSubscriptionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw resolveSubscriptionConflict(null, ex);
        }
    }

    private BusinessException resolveSubscriptionConflict(Board board, DataIntegrityViolationException ex) {
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
