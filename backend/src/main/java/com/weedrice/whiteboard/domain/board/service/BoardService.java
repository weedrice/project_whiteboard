package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public List<BoardResponse> getActiveBoards() {
        List<Board> boards = boardRepository.findByIsActiveOrderBySortOrderAsc("Y");
        return boards.stream()
                .map(this::createBoardResponse)
                .collect(Collectors.toList());
    }

    public List<BoardResponse> getTopBoards() {
        List<Board> boards = boardRepository.findTopBoardsByPostCount(PageRequest.of(0, 15));
        return boards.stream()
                .map(this::createBoardResponse)
                .collect(Collectors.toList());
    }

    public BoardResponse getBoardDetails(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createBoardResponse(board);
    }

    private BoardResponse createBoardResponse(Board board) {
        long subscriberCount = boardSubscriptionRepository.countByBoard(board);
        String adminDisplayName = adminRepository.findByBoardAndRole(board, "BOARD_ADMIN")
                .map(admin -> admin.getUser().getDisplayName())
                .orElse(board.getCreator().getDisplayName());
        return new BoardResponse(board, subscriberCount, adminDisplayName);
    }

    public List<BoardCategory> getActiveCategories(Long boardId) {
        return boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(boardId, "Y");
    }

    @Transactional
    public void subscribeBoard(Long userId, Long boardId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        boardSubscriptionRepository.findById(new BoardSubscriptionId(userId, boardId))
                .ifPresent(subscription -> {
                    throw new BusinessException(ErrorCode.ALREADY_SUBSCRIBED);
                });

        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .build();
        boardSubscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribeBoard(Long userId, Long boardId) {
        BoardSubscription subscription = boardSubscriptionRepository.findById(new BoardSubscriptionId(userId, boardId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUBSCRIBED));
        boardSubscriptionRepository.delete(subscription);
    }

    public Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Board> boardPage = boardSubscriptionRepository.findByUser(user, pageable).map(BoardSubscription::getBoard);
        return boardPage.map(this::createBoardResponse);
    }

    @Transactional
    public Board createBoard(Long creatorId, BoardCreateRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (boardRepository.existsByBoardName(request.getBoardName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }

        Board board = Board.builder()
                .boardName(request.getBoardName())
                .description(request.getDescription())
                .creator(creator)
                .iconUrl(request.getIconUrl())
                .bannerUrl(request.getBannerUrl())
                .sortOrder(request.getSortOrder())
                .build();
        
        Board savedBoard = boardRepository.save(board);

        BoardCategory defaultCategory = BoardCategory.builder()
                .board(savedBoard)
                .name("일반")
                .sortOrder(1)
                .build();
        boardCategoryRepository.save(defaultCategory);

        Admin boardAdmin = Admin.builder()
                .user(creator)
                .board(savedBoard)
                .role("BOARD_ADMIN")
                .build();
        adminRepository.save(boardAdmin);

        return savedBoard;
    }

    @Transactional
    public Board updateBoard(Long boardId, BoardUpdateRequest request, UserDetails userDetails) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getCreator().getLoginId().equals(userDetails.getUsername()) &&
                !userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        board.update(request.getDescription(), request.getIconUrl(), request.getBannerUrl(), request.getSortOrder(), request.getAllowNsfw());
        return board;
    }

    @Transactional
    public void deleteBoard(Long boardId, UserDetails userDetails) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getCreator().getLoginId().equals(userDetails.getUsername()) &&
                !userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boardRepository.deleteById(boardId);
    }

    @Transactional
    public BoardCategory createCategory(Long boardId, CategoryRequest request) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .build();
        return boardCategoryRepository.save(category);
    }

    @Transactional
    public BoardCategory updateCategory(Long categoryId, CategoryRequest request) {
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        category.update(request.getName(), request.getSortOrder());
        return category;
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        BoardCategory category = boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        category.deactivate();
    }
}
