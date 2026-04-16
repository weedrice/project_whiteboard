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

import java.util.List;
import java.util.stream.Collectors;

class BoardQueryService {

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
        List<Board> boards = boardRepository.findTopBoardsByPostCount(PageRequest.of(0, 15));
        List<Board> visibleBoards = boards.stream()
                .filter(board -> !boardAccessPolicy.isInquiryBoard(board))
                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                .collect(Collectors.toList());
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
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<BoardSubscription> subscriptions = boardSubscriptionRepository
                .findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user);
        List<Board> visibleBoards = subscriptions
                .stream()
                .map(BoardSubscription::getBoard)
                .filter(board -> boardAccessPolicy.canReadBoard(board, user))
                .collect(Collectors.toList());
        int start = Math.min((int) pageable.getOffset(), visibleBoards.size());
        int end = Math.min(start + pageable.getPageSize(), visibleBoards.size());
        List<BoardResponse> responses = boardResponseAssembler.assembleAll(visibleBoards.subList(start, end), user);
        return new PageImpl<>(responses, pageable, visibleBoards.size());
    }

    private User getCurrentUserOrNull(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByLoginId(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
