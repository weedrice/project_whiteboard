package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class BoardSubscriptionService {
    private final BoardRepository boardRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;
    private final BoardAccessPolicy boardAccessPolicy;
    private final BoardSubscriptionWritePolicy boardSubscriptionWritePolicy;

    BoardSubscriptionService(BoardRepository boardRepository,
                              BoardSubscriptionRepository boardSubscriptionRepository,
                              UserWritableResolver userWritableResolver,
                              BoardAccessPolicy boardAccessPolicy,
                              BoardSubscriptionWritePolicy boardSubscriptionWritePolicy) {
        this.boardRepository = boardRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userWritableResolver = userWritableResolver;
        this.boardAccessPolicy = boardAccessPolicy;
        this.boardSubscriptionWritePolicy = boardSubscriptionWritePolicy;
    }

    void subscribeBoard(Long userId, String boardUrl) {
        User user = userWritableResolver.resolveForUpdate(userId);
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        boardAccessPolicy.validateReadable(board, user);
        boardSubscriptionWritePolicy.validateNotSubscribed(userId, board.getBoardId());

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
            throw boardSubscriptionWritePolicy.resolveSubscriptionConflict(board, ex);
        }
    }

    void unsubscribeBoard(Long userId, String boardUrl) {
        User user = userWritableResolver.resolveForUpdate(userId);
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        BoardSubscription subscription = boardSubscriptionRepository
                .findByIdForUpdate(userId, board.getBoardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUBSCRIBED));
        boardSubscriptionRepository.delete(subscription);
    }

    void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        User user = userWritableResolver.resolveForUpdate(userId);
        if (boardUrls == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<String> normalizedBoardUrls = BoardUrlNormalizer.normalizeOrderList(boardUrls);
        Set<String> requestedBoardUrls = new LinkedHashSet<>(normalizedBoardUrls);
        if (requestedBoardUrls.isEmpty() || requestedBoardUrls.size() != normalizedBoardUrls.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<BoardSubscription> subscriptions = boardSubscriptionRepository.findReorderableByUser(
                user,
                user.isUsableSuperAdmin());

        Set<String> currentBoardUrls = subscriptions.stream()
                .map(sub -> sub.getBoard().getBoardUrl())
                .collect(Collectors.toSet());
        if (currentBoardUrls.size() != subscriptions.size()
                || !currentBoardUrls.equals(requestedBoardUrls)) {
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
            for (int i = 0; i < normalizedBoardUrls.size(); i++) {
                subscriptionByBoardUrl.get(normalizedBoardUrls.get(i)).updateSortOrder(temporarySortOrderBase + i + 1);
            }
            boardSubscriptionRepository.saveAll(subscriptions);
            boardSubscriptionRepository.flush();

            for (int i = 0; i < normalizedBoardUrls.size(); i++) {
                subscriptionByBoardUrl.get(normalizedBoardUrls.get(i)).updateSortOrder(requestedSortOrders.get(i));
            }
            boardSubscriptionRepository.saveAll(subscriptions);
            boardSubscriptionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw boardSubscriptionWritePolicy.resolveSubscriptionConflict(null, ex);
        }
    }
}
