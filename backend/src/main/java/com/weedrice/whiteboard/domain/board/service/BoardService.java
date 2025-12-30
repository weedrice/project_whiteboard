package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.*;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.global.security.CustomUserDetails;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

        private final BoardRepository boardRepository;
        private final BoardCategoryRepository boardCategoryRepository;
        private final BoardSubscriptionRepository boardSubscriptionRepository;
        private final UserRepository userRepository;
        private final AdminRepository adminRepository;
        private final PostService postService;
        private final PostRepository postRepository;
        private final DraftPostRepository draftPostRepository;
        private final UserPointRepository userPointRepository;
        private final PointHistoryRepository pointHistoryRepository;

        public List<BoardResponse> getActiveBoards(UserDetails userDetails) {
                List<Board> boards = boardRepository.findByIsActiveOrderBySortOrderAsc(true);
                return boards.stream()
                                .map(board -> createBoardResponse(board, userDetails))
                                .collect(Collectors.toList());
        }

        public List<BoardResponse> getTopBoards(UserDetails userDetails) {
                List<Board> boards = boardRepository.findTopBoardsByPostCount(PageRequest.of(0, 15));
                return boards.stream()
                                .map(board -> createBoardResponse(board, userDetails))
                                .collect(Collectors.toList());
        }

        public List<BoardResponse> getAllBoards(UserDetails userDetails) {
                List<Board> boards = boardRepository.findAll(org.springframework.data.domain.Sort
                                .by(org.springframework.data.domain.Sort.Direction.ASC, "sortOrder"));
                return boards.stream()
                                .map(board -> createBoardResponse(board, userDetails))
                                .collect(Collectors.toList());
        }

        public BoardResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                boolean isSuperAdmin = false;
                boolean isBoardAdmin = false;
                boolean isCreator = false;

                if (userDetails != null) {
                        User currentUser = userRepository.findByLoginId(userDetails.getUsername())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                        isSuperAdmin = currentUser.getIsSuperAdmin();
                        isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(currentUser, board, true)
                                        .isPresent();
                        isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());
                }

                if (!board.getIsActive() && !isSuperAdmin && !isBoardAdmin && !isCreator) {
                        throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
                }

                return createBoardResponse(board, userDetails);
        }

        private BoardResponse createBoardResponse(Board board, UserDetails userDetails) {
                long subscriberCount = boardSubscriptionRepository.countByBoard(board);
                User adminUser = adminRepository.findByBoardAndRole(board, Role.BOARD_ADMIN)
                                .map(Admin::getUser)
                                .orElse(board.getCreator());

                String adminDisplayName = adminUser.getDisplayName();
                Long adminUserId = adminUser.getUserId();

                boolean isAdmin = false;
                boolean isSubscribed = false;

                if (userDetails != null) {
                        User currentUser = userRepository.findByLoginId(userDetails.getUsername())
                                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                        boolean isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(currentUser, board, true)
                                        .isPresent();
                        boolean isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());

                        isAdmin = currentUser.getIsSuperAdmin() || isBoardAdmin || isCreator;
                        isSubscribed = boardSubscriptionRepository.existsByUserAndBoard(currentUser, board);
                }

                List<CategoryResponse> categories = getActiveCategories(board.getBoardUrl());

                Long currentUserId = (userDetails instanceof CustomUserDetails)
                                ? ((CustomUserDetails) userDetails).getUserId()
                                : null;
                List<PostSummary> latestPosts = postService.getLatestPostsByBoard(board.getBoardId(), 15,
                                currentUserId);

                return new BoardResponse(board, subscriberCount, adminDisplayName, adminUserId, isAdmin, isSubscribed,
                                categories,
                                latestPosts);
        }

        public List<CategoryResponse> getActiveCategories(String boardUrl) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                return boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(),
                                true).stream()
                                .map(CategoryResponse::new)
                                .collect(Collectors.toList());
        }

        @Transactional
        public void subscribeBoard(Long userId, String boardUrl) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                if (!board.getIsActive()) {
                        throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
                }

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
                boardSubscriptionRepository.save(subscription);
        }

        @Transactional
        public void unsubscribeBoard(Long userId, String boardUrl) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                BoardSubscription subscription = boardSubscriptionRepository
                                .findById(new BoardSubscriptionId(userId, board.getBoardId()))
                                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUBSCRIBED));
                boardSubscriptionRepository.delete(subscription);
        }

        public Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
                if (userId == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Page<BoardResponse> boardPage = boardSubscriptionRepository
                                .findByUserAndBoard_IsActiveOrderBySortOrderAsc(user, true, pageable)
                                .map(boardSubscription -> createBoardResponse(boardSubscription.getBoard(), null));
                return boardPage;
        }

        @Transactional
        public Board createBoard(Long creatorId, BoardCreateRequest request) {
                User creator = userRepository.findById(creatorId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                if (boardRepository.existsByBoardName(request.getBoardName())) {
                        throw new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
                }
                if (boardRepository.existsByBoardUrl(request.getBoardUrl())) {
                        throw new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
                }

                UserPoint userPoint = userPointRepository.findById(creatorId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                if (userPoint.getCurrentPoint() < 500) {
                        throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
                }

                userPoint.subtractPoint(500);
                // userPointRepository.save(userPoint); // Dirty checking

                Integer maxSortOrder = boardRepository.findMaxSortOrder();

                Board board = Board.builder()
                                .boardName(request.getBoardName())
                                .boardUrl(request.getBoardUrl())
                                .description(request.getDescription())
                                .creator(creator)
                                .iconUrl(request.getIconUrl())
                                .sortOrder(maxSortOrder + 1)
                                .build();

                Board savedBoard = boardRepository.save(board);

                pointHistoryRepository.save(PointHistory.builder()
                                .user(creator)
                                .type("SPEND")
                                .amount(-500)
                                .balanceAfter(userPoint.getCurrentPoint())
                                .description("게시판 생성 (" + savedBoard.getBoardName() + ")")
                                .relatedType("BOARD_CREATE")
                                .relatedId(savedBoard.getBoardId())
                                .build());

                BoardCategory defaultCategory = BoardCategory.builder()
                                .board(savedBoard)
                                .name("일반")
                                .sortOrder(1)
                                .build();
                boardCategoryRepository.save(defaultCategory);

                Admin boardAdmin = Admin.builder()
                                .user(creator)
                                .board(savedBoard)
                                .role(Role.BOARD_ADMIN)
                                .build();
                adminRepository.save(boardAdmin);

                return savedBoard;
        }

        @Transactional
        public Board updateBoard(String boardUrl, BoardUpdateRequest request, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(board);

                if (!board.getBoardName().equals(request.getBoardName())
                                && boardRepository.existsByBoardName(request.getBoardName())) {
                        throw new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
                }

                board.update(request.getBoardName(), request.getDescription(), request.getIconUrl(),
                                request.getSortOrder(),
                                board.getAllowNsfw(), request.getIsActive());
                return board;
        }

        @Transactional
        public void deleteBoard(String boardUrl, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(board);

                board.deactivate();
        }

        @Transactional
        public CategoryResponse createCategory(String boardUrl, CategoryRequest request) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(board);

                BoardCategory category = BoardCategory.builder()
                                .board(board)
                                .name(request.getName())
                                .sortOrder(request.getSortOrder())
                                .minWriteRole(request.getMinWriteRole())
                                .build();
                return new CategoryResponse(boardCategoryRepository.save(category));
        }

        @Transactional
        public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
                if (categoryId == null) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category ID cannot be null");
                }
                BoardCategory category = boardCategoryRepository.findById(categoryId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(category.getBoard());

                category.update(request.getName(), request.getSortOrder(), request.getMinWriteRole());
                return new CategoryResponse(category);
        }

        @Transactional
        public void deleteCategory(Long categoryId) {
                BoardCategory category = boardCategoryRepository.findById(categoryId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(category.getBoard());

                category.deactivate();
        }

        @Transactional
        public void updateSubscriptionOrder(Long userId, List<String> boardUrls) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                List<BoardSubscription> subscriptions = boardSubscriptionRepository.findAllByUser(user);

                for (int i = 0; i < boardUrls.size(); i++) {
                        String boardUrl = boardUrls.get(i);
                        int sortOrder = i + 1;

                        subscriptions.stream()
                                        .filter(sub -> sub.getBoard().getBoardUrl().equals(boardUrl))
                                        .findFirst()
                                        .ifPresent(sub -> sub.updateSortOrder(sortOrder));
                }
                boardSubscriptionRepository.saveAll(subscriptions);
        }
}
