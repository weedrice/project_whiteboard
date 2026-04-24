package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
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

    List<BoardListResponse> getActiveBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        List<Board> boards = boardRepository.findReadableActiveBoardsOrderBySortOrderAsc(
                currentUser,
                currentUser != null && Boolean.TRUE.equals(currentUser.getIsSuperAdmin()));
        return boardResponseAssembler.assembleListAll(boards, currentUser);
    }

    List<BoardListResponse> getTopBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        if (currentUser == null || !boardAccessPolicy.hasElevatedBoardVisibility(currentUser)) {
            List<Long> boardIds = boardRepository.findTopPublicBoardIdsByPostCount(PageRequest.of(0, TOP_BOARD_LIMIT));
            List<Board> boards = findBoardsByIdsInOrder(boardIds);
            return boardResponseAssembler.assembleListAll(boards, currentUser);
        }

        List<Long> boardIds = boardRepository.findTopReadableBoardIdsByPostCount(
                currentUser,
                Boolean.TRUE.equals(currentUser.getIsSuperAdmin()),
                PageRequest.of(0, TOP_BOARD_LIMIT));
        List<Board> boards = findBoardsByIdsInOrder(boardIds);
        return boardResponseAssembler.assembleListAll(boards, currentUser);
    }

    List<AdminBoardResponse> getAllBoards(UserDetails userDetails) {
        User currentUser = getCurrentUserOrNull(userDetails);
        List<Board> boards = boardRepository.findAllByOrderBySortOrderAsc();
        return boardResponseAssembler.assembleAdminAll(boards);
    }

    BoardDetailResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserOrNull(userDetails);
        boardAccessPolicy.validateReadable(board, currentUser);

        return boardResponseAssembler.assembleDetail(board, currentUser);
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

    Page<BoardListResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return getMySubscriptions(userId, pageable, false);
    }

    Page<BoardListResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
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
            Map<Long, BoardListResponse> readableResponsesByBoardId = boardResponseAssembler.assembleListAll(readableBoards, user)
                    .stream()
                    .collect(Collectors.toMap(BoardListResponse::getBoardId, Function.identity()));
            List<BoardListResponse> responses = subscriptions.getContent().stream()
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
        List<BoardListResponse> responses = boardResponseAssembler.assembleListAll(visibleBoards, user);
        return new PageImpl<>(responses, pageable, visibleSubscriptions.getTotalElements());
    }

    private BoardListResponse toSubscriptionResponse(BoardSubscription subscription,
            Map<Long, BoardListResponse> readableResponsesByBoardId) {
        Board board = subscription.getBoard();
        BoardListResponse readableResponse = readableResponsesByBoardId.get(board.getBoardId());
        if (readableResponse != null) {
            return readableResponse;
        }
        return BoardListResponse.unavailableSubscription(board);
    }

    private User getCurrentUserOrNull(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByLoginId(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private List<Board> findBoardsByIdsInOrder(List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Board> boardsById = boardRepository.findByBoardIdIn(boardIds)
                .stream()
                .collect(Collectors.toMap(Board::getBoardId, Function.identity()));

        return boardIds.stream()
                .map(boardsById::get)
                .filter(board -> board != null)
                .toList();
    }
}
