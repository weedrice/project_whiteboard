package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
class BoardProvisioningService {

    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";
    private static final String DEFAULT_INQUIRY_BOARD_NAME = "문의";
    private static final String DEFAULT_INQUIRY_BOARD_DESCRIPTION = "운영진에게 문의를 남기는 비공개 게시판입니다.";
    private static final String DEFAULT_CATEGORY_NAME = "일반";
    private static final String BOARD_NAME_CONSTRAINT = "uk_boards_board_name";
    private static final String BOARD_URL_CONSTRAINT = "uk_boards_board_url";
    private static final String LEGACY_BOARD_NAME_CONSTRAINT = "boards_board_name_key";
    private static final String LEGACY_BOARD_URL_CONSTRAINT = "boards_board_url_key";
    private static final String BOARD_NAME_COLUMN = "board_name";
    private static final String BOARD_URL_COLUMN = "board_url";
    private static final String BOARD_CATEGORY_ACTIVE_CONSTRAINT = "uq_board_categories_active_name";
    private static final String ORM_BOARD_CATEGORY_ACTIVE_CONSTRAINT = "uk_board_categories_board_name_active";
    private static final String LEGACY_BOARD_CATEGORY_ACTIVE_CONSTRAINT = "board_categories_board_id_name_is_active_key";
    private static final String ADMIN_ACTIVE_CONSTRAINT = "uq_admins_active_user_board_role";
    private static final String BOARD_ADMIN_ACTIVE_CONSTRAINT = "uq_admins_active_board_admin";
    private static final String ORM_ADMIN_ACTIVE_CONSTRAINT = "uk_admins_user_board_role_active";
    private static final String LEGACY_ADMIN_ACTIVE_CONSTRAINT = "admins_user_id_board_id_role_is_active_key";

    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PointService pointService;
    private final GlobalConfigService globalConfigService;

    BoardProvisioningService(BoardRepository boardRepository,
                             BoardAiInfoRepository boardAiInfoRepository,
                             BoardCategoryRepository boardCategoryRepository,
                             UserRepository userRepository,
                             AdminRepository adminRepository,
                             PointService pointService,
                             GlobalConfigService globalConfigService) {
        this.boardRepository = boardRepository;
        this.boardAiInfoRepository = boardAiInfoRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.pointService = pointService;
        this.globalConfigService = globalConfigService;
    }

    void ensureInquiryBoard(UserDetails userDetails, String requestedBoardUrl) {
        User currentUser = getCurrentUserOrNull(userDetails);
        String inquiryBoardUrl = normalizeInquiryBoardUrl(requestedBoardUrl);

        Board board = boardRepository.findByBoardUrlForUpdate(inquiryBoardUrl)
                .orElseGet(() -> createInquiryBoard(currentUser, inquiryBoardUrl));

        ensureInquiryBoardIsPrivate(board);
        ensureInquiryBoardCategory(board);
        ensureInquiryBoardManager(board, currentUser);
    }

    Board createBoard(Long creatorId, BoardCreateRequest request) {
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

        Board savedBoard;
        try {
            savedBoard = boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardCreateConflict(ex);
        }
        pointService.spendPoint(
                creatorId,
                resolveBoardCreateCost(),
                "寃뚯떆???앹꽦 (" + savedBoard.getBoardName() + ")",
                savedBoard.getBoardId(),
                "BOARD_CREATE");
        upsertBoardAiInfoIfEnabled(savedBoard, request.getGuidePrompt(), true);

        BoardCategory defaultCategory = BoardCategory.builder()
                .board(savedBoard)
                .name("?쇰컲")
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

    private BusinessException resolveBoardCreateConflict(DataIntegrityViolationException ex) {
        if (containsBoardUrlConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
        }
        if (containsBoardNameConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    Board updateBoard(String boardUrl, BoardUpdateRequest request, UserDetails userDetails) {
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

    void transferBoardManager(String boardUrl, String loginId, UserDetails userDetails) {
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

    void deleteBoard(String boardUrl, UserDetails userDetails) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        SecurityUtils.validateBoardAdminPermission(board);

        board.deactivate();
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
            savedBoard = boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            if (!containsBoardUrlConstraint(ex) && !containsBoardNameConstraint(ex)) {
                throw ex;
            }
            return boardRepository.findByBoardUrlForUpdate(inquiryBoardUrl)
                    .orElseThrow(() -> ex);
        }

        ensureInquiryBoardCategory(savedBoard);
        ensureInquiryBoardManager(savedBoard, creator);

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
        List<BoardCategory> activeCategories = boardCategoryRepository
                .findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true);
        List<BoardCategory> defaultCategories = activeCategories.stream()
                .filter(category -> DEFAULT_CATEGORY_NAME.equals(category.getName()))
                .sorted(Comparator.comparing(BoardCategory::getSortOrder)
                        .thenComparing(BoardCategory::getCategoryId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        if (defaultCategories.isEmpty()) {
            createDefaultInquiryCategory(board);
            return;
        }

        BoardCategory canonicalCategory = defaultCategories.get(0);
        if (!Objects.equals(canonicalCategory.getSortOrder(), 1)) {
            canonicalCategory.update(canonicalCategory.getName(), 1, canonicalCategory.getMinWriteRole());
        }
        defaultCategories.stream()
                .skip(1)
                .forEach(BoardCategory::deactivate);
    }

    private void createDefaultInquiryCategory(Board board) {
        try {
            boardCategoryRepository.saveAndFlush(BoardCategory.builder()
                    .board(board)
                    .name(DEFAULT_CATEGORY_NAME)
                    .sortOrder(1)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (!containsBoardCategoryConstraint(ex)) {
                throw ex;
            }
        }
    }

    private void ensureInquiryBoardManager(Board board, User requester) {
        User manager = resolveInquiryBoardCreator(requester);
        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true)
                .stream()
                .sorted(Comparator.comparing(Admin::getAdminId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();

        Admin canonicalManager = activeManagers.stream()
                .filter(admin -> Objects.equals(admin.getUser().getUserId(), manager.getUserId()))
                .findFirst()
                .orElse(null);

        if (canonicalManager != null) {
            activeManagers.stream()
                    .filter(admin -> !Objects.equals(admin.getAdminId(), canonicalManager.getAdminId()))
                    .forEach(Admin::deactivate);
            return;
        }

        activeManagers.stream()
                .forEach(Admin::deactivate);

        activateOrCreateInquiryBoardManager(board, manager);
    }

    private Admin activateOrCreateInquiryBoardManager(Board board, User manager) {
        Admin reusableManager = adminRepository.findByUserAndBoardAndRole(manager, board, Role.BOARD_ADMIN)
                .orElse(null);
        if (reusableManager != null) {
            reusableManager.activate();
            return reusableManager;
        }

        try {
            return adminRepository.saveAndFlush(Admin.builder()
                    .user(manager)
                    .board(board)
                    .role(Role.BOARD_ADMIN)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (!containsAdminActiveConstraint(ex)) {
                throw ex;
            }
            return adminRepository.findByUserAndBoardAndRoleAndIsActive(manager, board, Role.BOARD_ADMIN, true)
                    .orElseThrow(() -> ex);
        }
    }

    private User resolveInquiryBoardCreator(User fallbackUser) {
        User creator = userRepository.findByIsSuperAdminTrueAndDeletedAtIsNull().stream()
                .filter(Objects::nonNull)
                .filter(superAdmin -> superAdmin.getDeletedAt() == null)
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

    private boolean containsBoardNameConstraint(Throwable throwable) {
        return containsConstraint(throwable, BOARD_NAME_CONSTRAINT, LEGACY_BOARD_NAME_CONSTRAINT, BOARD_NAME_COLUMN);
    }

    private boolean containsBoardUrlConstraint(Throwable throwable) {
        return containsConstraint(throwable, BOARD_URL_CONSTRAINT, LEGACY_BOARD_URL_CONSTRAINT, BOARD_URL_COLUMN);
    }

    private boolean containsBoardCategoryConstraint(Throwable throwable) {
        return containsConstraint(
                throwable,
                BOARD_CATEGORY_ACTIVE_CONSTRAINT,
                ORM_BOARD_CATEGORY_ACTIVE_CONSTRAINT,
                LEGACY_BOARD_CATEGORY_ACTIVE_CONSTRAINT);
    }

    private boolean containsAdminActiveConstraint(Throwable throwable) {
        return containsConstraint(
                throwable,
                ADMIN_ACTIVE_CONSTRAINT,
                BOARD_ADMIN_ACTIVE_CONSTRAINT,
                ORM_ADMIN_ACTIVE_CONSTRAINT,
                LEGACY_ADMIN_ACTIVE_CONSTRAINT);
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

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
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
                    .guidePrompt(resolveInitialGuidePrompt(board, requestedGuidePrompt, initializeFromDescription))
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
}
