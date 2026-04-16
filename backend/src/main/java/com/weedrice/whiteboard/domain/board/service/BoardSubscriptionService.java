package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class BoardSubscriptionService {

    private final BoardRepository boardRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardSubscriptionService(BoardRepository boardRepository,
                             BoardSubscriptionRepository boardSubscriptionRepository,
                             UserRepository userRepository,
                             BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userRepository = userRepository;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    void subscribeBoard(Long userId, String boardUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
            throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
        }
    }

    void unsubscribeBoard(Long userId, String boardUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boardAccessPolicy.validateReadable(board, user);

        BoardSubscription subscription = boardSubscriptionRepository
                .findById(new BoardSubscriptionId(userId, board.getBoardId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUBSCRIBED));
        boardSubscriptionRepository.delete(subscription);
    }

    void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (boardUrls == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<BoardSubscription> subscriptions = boardSubscriptionRepository
                .findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user)
                .stream()
                .filter(subscription -> boardAccessPolicy.canReadBoard(subscription.getBoard(), user))
                .toList();
        Set<String> requestedBoardUrls = new LinkedHashSet<>(boardUrls);
        if (requestedBoardUrls.size() != boardUrls.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Set<String> currentBoardUrls = subscriptions.stream()
                .map(sub -> sub.getBoard().getBoardUrl())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currentBoardUrls.size() != subscriptions.size()
                || subscriptions.size() != boardUrls.size()
                || !currentBoardUrls.equals(requestedBoardUrls)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<String, BoardSubscription> subscriptionByBoardUrl = subscriptions.stream()
                .collect(Collectors.toMap(
                        sub -> sub.getBoard().getBoardUrl(),
                        sub -> sub));
        for (int i = 0; i < boardUrls.size(); i++) {
            subscriptionByBoardUrl.get(boardUrls.get(i)).updateSortOrder(i + 1);
        }

        boardSubscriptionRepository.saveAll(subscriptions);
    }
}
