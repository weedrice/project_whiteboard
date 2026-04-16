package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.*;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {
        private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";
        private static final String DEFAULT_INQUIRY_BOARD_NAME = "문의";
        private static final String DEFAULT_INQUIRY_BOARD_DESCRIPTION = "운영진에게 문의를 남기는 게시판입니다.";
        private static final String DEFAULT_CATEGORY_NAME = "일반";


        private final BoardRepository boardRepository;
        private final BoardAiInfoRepository boardAiInfoRepository;
        private final BoardCategoryRepository boardCategoryRepository;
        private final BoardSubscriptionRepository boardSubscriptionRepository;
        private final UserRepository userRepository;
        private final AdminRepository adminRepository;
        private final PointService pointService;
        private final GlobalConfigService globalConfigService;
        private final BoardResponseAssembler boardResponseAssembler;
        private final BoardAccessPolicy boardAccessPolicy;

        public List<BoardResponse> getActiveBoards(UserDetails userDetails) {
                User currentUser = getCurrentUserOrNull(userDetails);
                List<Board> boards = boardRepository.findByIsActiveOrderBySortOrderAsc(true);
                List<Board> visibleBoards = boards.stream()
                                .filter(board -> !boardAccessPolicy.isInquiryBoard(board))
                                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                                .collect(Collectors.toList());
                return boardResponseAssembler.assembleAll(visibleBoards, currentUser);
        }

        public List<BoardResponse> getTopBoards(UserDetails userDetails) {
                User currentUser = getCurrentUserOrNull(userDetails);
                List<Board> boards = boardRepository.findTopBoardsByPostCount(PageRequest.of(0, 15));
                List<Board> visibleBoards = boards.stream()
                                .filter(board -> !boardAccessPolicy.isInquiryBoard(board))
                                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                                .collect(Collectors.toList());
                return boardResponseAssembler.assembleAll(visibleBoards, currentUser);
        }

        public List<BoardResponse> getAllBoards(UserDetails userDetails) {
                User currentUser = getCurrentUserOrNull(userDetails);
                List<Board> boards = boardRepository.findAllByOrderBySortOrderAsc();
                return boardResponseAssembler.assembleAll(boards, currentUser);
        }

        public BoardResponse getBoardDetails(String boardUrl, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                User currentUser = getCurrentUserOrNull(userDetails);
                boardAccessPolicy.validateReadable(board, currentUser);

                return boardResponseAssembler.assemble(board, currentUser);
        }

        public List<CategoryResponse> getActiveCategories(String boardUrl, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
                User currentUser = getCurrentUserOrNull(userDetails);
                boardAccessPolicy.validateReadable(board, currentUser);
                return boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(),
                                true).stream()
                                .map(CategoryResponse::new)
                                .collect(Collectors.toList());
        }

        @Transactional
        public void ensureInquiryBoard(UserDetails userDetails, String requestedBoardUrl) {
                User currentUser = getCurrentUserOrNull(userDetails);
                String inquiryBoardUrl = normalizeInquiryBoardUrl(requestedBoardUrl);

                Board board = boardRepository.findByBoardUrl(inquiryBoardUrl)
                                .orElseGet(() -> createInquiryBoard(currentUser, inquiryBoardUrl));

                ensureInquiryBoardIsPrivate(board);
                ensureInquiryBoardCategory(board);
        }

        @Transactional
        public void subscribeBoard(Long userId, String boardUrl) {
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

        @Transactional
        public void unsubscribeBoard(Long userId, String boardUrl) {
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

        public Page<BoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
                if (userId == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Page<BoardSubscription> subscriptions = boardSubscriptionRepository
                                .findByUserAndBoard_IsActiveOrderBySortOrderAsc(user, true, pageable);
                List<Board> visibleBoards = subscriptions
                                .stream()
                                .map(BoardSubscription::getBoard)
                                .filter(board -> boardAccessPolicy.canReadBoard(board, user))
                                .collect(Collectors.toList());
                List<BoardResponse> responses = boardResponseAssembler.assembleAll(visibleBoards, user);
                return new org.springframework.data.domain.PageImpl<>(responses, pageable, subscriptions.getTotalElements());
        }

        @Transactional
        public Board createBoard(Long creatorId, BoardCreateRequest request) {
                User creator = userRepository.findById(creatorId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                validateCreatableBoardUrl(request.getBoardUrl());

                if (boardRepository.existsByBoardName(request.getBoardName())) {
                        throw new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
                }
                if (boardRepository.existsByBoardUrl(request.getBoardUrl())) {
                        throw new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
                }

                Integer maxSortOrder = boardRepository.findMaxSortOrder();

                Board board = Board.builder()
                                .boardName(request.getBoardName())
                                .boardUrl(request.getBoardUrl())
                                .description(normalizeDescription(request.getDescription()))
                                .creator(creator)
                                .iconUrl(request.getIconUrl())
                                .sortOrder(maxSortOrder + 1)
                                .isPublic(request.getIsPublic())
                                .agentUseYn(request.getAgentUseYn())
                                .build();

                Board savedBoard = boardRepository.save(board);
                pointService.spendPoint(
                                creatorId,
                                resolveBoardCreateCost(),
                                "게시판 생성 (" + savedBoard.getBoardName() + ")",
                                savedBoard.getBoardId(),
                                "BOARD_CREATE");
                upsertBoardAiInfoIfEnabled(savedBoard, request.getGuidePrompt(), true);

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

                if (request.getBoardUrl() != null && !board.getBoardUrl().equals(request.getBoardUrl())) {
                        validateCreatableBoardUrl(request.getBoardUrl());
                        if (boardRepository.existsByBoardUrl(request.getBoardUrl())) {
                                throw new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
                        }
                }

                if (request.getBoardUrl() != null && !board.getBoardUrl().equals(request.getBoardUrl())) {
                        board.updateBoardUrl(request.getBoardUrl());
                }

                board.update(request.getBoardName(), normalizeDescription(request.getDescription()), request.getIconUrl(),
                                request.getSortOrder(),
                                request.getAllowNsfw() != null ? request.getAllowNsfw() : board.getAllowNsfw(),
                                request.getIsActive(),
                                request.getIsPublic(),
                                request.getAgentUseYn());
                upsertBoardAiInfoIfEnabled(board, request.getGuidePrompt(), false);
                return board;
        }

        @Transactional
        public void transferBoardManager(String boardUrl, String loginId, UserDetails userDetails) {
                Board board = boardRepository.findByBoardUrl(boardUrl)
                                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

                SecurityUtils.validateBoardAdminPermission(board);

                User nextManager = userRepository.findByLoginId(loginId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                Admin currentManager = adminRepository
                                .findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true)
                                .orElse(null);

                if (currentManager != null && currentManager.getUser().getUserId().equals(nextManager.getUserId())) {
                        return;
                }

                List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
                activeManagers.forEach(Admin::deactivate);

                Admin reusableManager = adminRepository.findByUserAndBoardAndRole(nextManager, board, Role.BOARD_ADMIN)
                                .orElse(null);
                if (reusableManager != null) {
                        reusableManager.activate();
                        return;
                }

                Admin boardManager = Admin.builder()
                                .user(nextManager)
                                .board(board)
                                .role(Role.BOARD_ADMIN)
                                .build();
                adminRepository.save(boardManager);
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

        private User getCurrentUserOrNull(UserDetails userDetails) {
                if (userDetails == null) {
                        return null;
                }
                return userRepository.findByLoginId(userDetails.getUsername())
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        }

        private String normalizeInquiryBoardUrl(String requestedBoardUrl) {
                return DEFAULT_INQUIRY_BOARD_URL;
        }

        private Board createInquiryBoard(User requester, String inquiryBoardUrl) {
                User creator = resolveInquiryBoardCreator(requester);
                Integer maxSortOrder = boardRepository.findMaxSortOrder();

                String boardName = resolveInquiryBoardName(inquiryBoardUrl);

                Board board = Board.builder()
                                .boardName(boardName)
                                .boardUrl(inquiryBoardUrl)
                                .description(DEFAULT_INQUIRY_BOARD_DESCRIPTION)
                                .creator(creator)
                                .iconUrl(null)
                                .sortOrder((maxSortOrder != null ? maxSortOrder : 0) + 1)
                                .isPublic(false)
                                .build();

                Board savedBoard;
                try {
                        savedBoard = boardRepository.save(board);
                } catch (DataIntegrityViolationException ex) {
                        return boardRepository.findByBoardUrl(inquiryBoardUrl)
                                        .orElseThrow(() -> ex);
                }

                boardCategoryRepository.save(BoardCategory.builder()
                                .board(savedBoard)
                                .name(DEFAULT_CATEGORY_NAME)
                                .sortOrder(1)
                                .build());

                adminRepository.findByUserAndBoardAndRole(creator, savedBoard, Role.BOARD_ADMIN)
                                .orElseGet(() -> adminRepository.save(Admin.builder()
                                                .user(creator)
                                                .board(savedBoard)
                                                .role(Role.BOARD_ADMIN)
                                                .build()));

                return savedBoard;
        }

        private void ensureInquiryBoardIsPrivate(Board board) {
                if (board == null) {
                        return;
                }
                if (Boolean.TRUE.equals(board.getIsPublic())) {
                        board.update(
                                        board.getBoardName(),
                                        board.getDescription(),
                                        board.getIconUrl(),
                                        board.getSortOrder(),
                                        board.getAllowNsfw(),
                                        board.getIsActive(),
                                        false,
                                        board.getAgentUseYn());
                }
        }

        private void ensureInquiryBoardCategory(Board board) {
                if (boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)
                                .isEmpty()) {
                        boardCategoryRepository.save(BoardCategory.builder()
                                        .board(board)
                                        .name(DEFAULT_CATEGORY_NAME)
                                        .sortOrder(1)
                                        .build());
                }
        }

        private User resolveInquiryBoardCreator(User fallbackUser) {
                User creator = userRepository.findByIsSuperAdminTrue().stream()
                                .min(Comparator.comparing(User::getUserId))
                                .orElse(fallbackUser);
                if (creator == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                return creator;
        }

        private String resolveInquiryBoardName(String inquiryBoardUrl) {
                String base = DEFAULT_INQUIRY_BOARD_NAME;
                if (!boardRepository.existsByBoardName(base)) {
                        return base;
                }

                String candidate = trimToMaxLength(base + "-" + inquiryBoardUrl, 100);
                if (!boardRepository.existsByBoardName(candidate)) {
                        return candidate;
                }

                for (int i = 2; i <= 999; i++) {
                        String withSuffix = trimToMaxLength(candidate + "-" + i, 100);
                        if (!boardRepository.existsByBoardName(withSuffix)) {
                                return withSuffix;
                        }
                }

                return trimToMaxLength(base + "-" + System.currentTimeMillis(), 100);
        }

        private String trimToMaxLength(String value, int maxLength) {
                return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }

        private void validateCreatableBoardUrl(String boardUrl) {
                if (boardUrl == null) {
                        return;
                }
                if (DEFAULT_INQUIRY_BOARD_URL.equalsIgnoreCase(boardUrl.trim())) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reserved board URL");
                }
        }

        private void upsertBoardAiInfoIfEnabled(Board board, String requestedGuidePrompt, boolean initializeFromDescription) {
                if (board == null || !board.isAgentEnabled()) {
                        return;
                }

                BoardAiInfo boardAiInfo = boardAiInfoRepository.findByBoard_BoardId(board.getBoardId())
                                .orElse(null);

                if (boardAiInfo == null) {
                        boardAiInfoRepository.save(BoardAiInfo.builder()
                                        .board(board)
                                        .guidePrompt(resolveInitialGuidePrompt(board, requestedGuidePrompt,
                                                        initializeFromDescription))
                                        .build());
                        return;
                }

                if (requestedGuidePrompt != null) {
                        boardAiInfo.updateGuidePrompt(normalizeGuidePrompt(requestedGuidePrompt));
                }
        }

        private String resolveInitialGuidePrompt(Board board, String requestedGuidePrompt, boolean initializeFromDescription) {
                if (requestedGuidePrompt != null && !requestedGuidePrompt.isBlank()) {
                        return normalizeGuidePrompt(requestedGuidePrompt);
                }
                if (initializeFromDescription || requestedGuidePrompt == null || requestedGuidePrompt.isBlank()) {
                        return normalizeDescription(board.getDescription());
                }
                return normalizeGuidePrompt(requestedGuidePrompt);
        }

        private int resolveBoardCreateCost() {
                String boardCreateCostStr = globalConfigService.getConfig("POINT_BOARD_CREATE_COST");
                return boardCreateCostStr != null ? Integer.parseInt(boardCreateCostStr) : 500;
        }

        private String normalizeGuidePrompt(String guidePrompt) {
                return guidePrompt == null || guidePrompt.isBlank() ? "" : guidePrompt;
        }

        private String normalizeDescription(String description) {
                return description == null || description.isBlank() ? "" : description;
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
