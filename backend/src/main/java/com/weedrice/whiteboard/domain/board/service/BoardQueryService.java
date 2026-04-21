package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class BoardQueryService {
    private static final int TOP_BOARD_LIMIT = 15;
    private static final int TOP_BOARD_OVERFETCH_LIMIT = 100;

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserRepository userRepository;
    private final BoardResponseAssembler boardResponseAssembler;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardQueryService(BoardRepository boardRepository,
                      BoardCategoryRepository boardCategoryRepository,
                      BoardSubscriptionRepository boardSubscriptionRepository,
                      UserRepository userRepository,
                      BoardResponseAssembler boardResponseAssembler,
                      BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userRepository = userRepository;
        this.boardResponseAssembler = boardResponseAssembler;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    List<BoardResponse> getActiveBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        List<Board> boards = boardRepository.findByIsActiveOrderBySortOrderAsc(true);
        List<Board> visibleBoards = boards.stream()
                .filter(board -> !boardAccessPolicy.isInquiryBoard(board))
                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                .collect(Collectors.toList());
        return boardResponseAssembler.assembleAll(visibleBoards, currentUser);
    }

    List<BoardResponse> getTopBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        if (currentUser == null || !boardAccessPolicy.hasElevatedBoardVisibility(currentUser)) {
            List<Board> boards = boardRepository.findTopPublicBoardsByPostCount(PageRequest.of(0, TOP_BOARD_LIMIT));
            return boardResponseAssembler.assembleAll(boards, currentUser);
        }

        List<Board> visibleBoards = boardRepository.findTopBoardsByPostCount(PageRequest.of(0, TOP_BOARD_OVERFETCH_LIMIT))
                .stream()
                .filter(board -> !boardAccessPolicy.isInquiryBoard(board))
                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                .limit(TOP_BOARD_LIMIT)
                .toList();
        return boardResponseAssembler.assembleAll(visibleBoards, currentUser);
    }

    List<BoardResponse> getAllBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        List<Board> boards = boardRepository.findAllByOrderBySortOrderAsc();
        return boardResponseAssembler.assembleAll(boards, currentUser);
    }

    BoardResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserOrNull(userDetails);
        boardAccessPolicy.validateReadable(board, currentUser);

        return boardResponseAssembler.assemble(board, currentUser);
    }

    List<CategoryResponse> getActiveCategories(String boardUrl, UserDetails userDetails) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserOrNull(userDetails);
        boardAccessPolicy.validateReadable(board, currentUser);
        return boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)
                .stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());
    }

    Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return getMySubscriptions(userId, pageable, false);
    }

    Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (includeUnavailable) {
            Page<BoardSubscription> subscriptions = boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, pageable);
            List<Board> readableBoards = subscriptions.getContent().stream()
                    .map(BoardSubscription::getBoard)
                    .filter(board -> boardAccessPolicy.canReadBoard(board, user))
                    .toList();
            Map<Long, BoardResponse> readableResponsesByBoardId = boardResponseAssembler.assembleAll(readableBoards, user)
                    .stream()
                    .collect(Collectors.toMap(BoardResponse::getBoardId, Function.identity()));
            List<BoardResponse> responses = subscriptions.getContent().stream()
                    .map(subscription -> toSubscriptionResponse(subscription, readableResponsesByBoardId))
                    .toList();
            return new PageImpl<>(responses, pageable, subscriptions.getTotalElements());
        }
        Page<BoardSubscription> visibleSubscriptions = boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user,
                Boolean.TRUE.equals(user.getIsSuperAdmin()),
                pageable);
        List<Board> visibleBoards = visibleSubscriptions.getContent().stream()
                .map(BoardSubscription::getBoard)
                .toList();
        List<BoardResponse> responses = boardResponseAssembler.assembleAll(visibleBoards, user);
        return new PageImpl<>(responses, pageable, visibleSubscriptions.getTotalElements());
    }

    private BoardResponse toSubscriptionResponse(BoardSubscription subscription,
            Map<Long, BoardResponse> readableResponsesByBoardId) {
        Board board = subscription.getBoard();
        BoardResponse readableResponse = readableResponsesByBoardId.get(board.getBoardId());
        if (readableResponse != null) {
            return readableResponse;
        }
        return BoardResponse.unavailableSubscription(board);
    }

    private User getCurrentUserOrNull(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByLoginId(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
