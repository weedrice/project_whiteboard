package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.admin.service.BoardManagerAssignmentService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
class BoardProvisioningService {

    private static final String DEFAULT_INQUIRY_BOARD_NAME = "문의";
    private static final String DEFAULT_INQUIRY_BOARD_DESCRIPTION = "운영진에게 문의를 남기는 비공개 게시판입니다.";
    private static final String BOARD_NAME_CONSTRAINT = "uk_boards_board_name";
    private static final String BOARD_URL_CONSTRAINT = "uk_boards_board_url";
    private static final String LEGACY_BOARD_NAME_CONSTRAINT = "boards_board_name_key";
    private static final String LEGACY_BOARD_URL_CONSTRAINT = "boards_board_url_key";
    private static final String BOARD_NAME_COLUMN = "board_name";
    private static final String BOARD_URL_COLUMN = "board_url";

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserRepository userRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final BoardManagerAssignmentService boardManagerAssignmentService;
    private final BoardIconAttachmentService boardIconAttachmentService;
    private final BoardCreationBillingService boardCreationBillingService;
    private final BoardCreationInitializer boardCreationInitializer;
    private final BoardAiInfoService boardAiInfoService;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardProvisioningService(BoardRepository boardRepository,
                             BoardCategoryRepository boardCategoryRepository,
                             BoardSubscriptionRepository boardSubscriptionRepository,
                             UserRepository userRepository,
                             AdminEligibleUserService adminEligibleUserService,
                             BoardManagerAssignmentService boardManagerAssignmentService,
                             BoardIconAttachmentService boardIconAttachmentService,
                             BoardCreationBillingService boardCreationBillingService,
                             BoardCreationInitializer boardCreationInitializer,
                             BoardAiInfoService boardAiInfoService,
                             BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userRepository = userRepository;
        this.adminEligibleUserService = adminEligibleUserService;
        this.boardManagerAssignmentService = boardManagerAssignmentService;
        this.boardIconAttachmentService = boardIconAttachmentService;
        this.boardCreationBillingService = boardCreationBillingService;
        this.boardCreationInitializer = boardCreationInitializer;
        this.boardAiInfoService = boardAiInfoService;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    void ensureInquiryBoard(Long userId, String requestedBoardUrl) {
        User currentUser = getCurrentUserOrNull(userId);
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
        adminEligibleUserService.validateActiveUser(creator);
        String iconUrl = normalizeIconUrl(request.getIconUrl());

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
                .iconUrl(iconUrl)
                .sortOrder(maxSortOrder + 1)
                .isPublic(request.getIsPublic())
                .agentUseYn(resolveAgentUseYn(request.getIsPublic(), request.getAgentUseYn()))
                .build();

        Board savedBoard;
        try {
            savedBoard = boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardConflict(ex);
        }
        boardIconAttachmentService.syncBoardIcon(creatorId, savedBoard, null);
        boardCreationBillingService.spendCreationCost(creatorId, savedBoard);
        boardCreationInitializer.initialize(savedBoard, creator, request.getGuidePrompt());

        return savedBoard;
    }

    private BusinessException resolveBoardConflict(DataIntegrityViolationException ex) {
        if (containsBoardUrlConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
        }
        if (containsBoardNameConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    Board updateBoard(String boardUrl, BoardUpdateRequest request, Long userId) {
        Board board = boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUser(userId);
        String previousIconUrl = board.getIconUrl();
        String iconUrl = normalizeIconUrl(request.getIconUrl());

        boardAccessPolicy.validateBoardAdmin(board, currentUser);

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

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : board.getSortOrder();
        board.update(request.getBoardName(), normalizeDescription(request.getDescription()), iconUrl,
                sortOrder,
                request.getAllowNsfw() != null ? request.getAllowNsfw() : board.getAllowNsfw(),
                request.getIsActive(),
                request.getIsPublic(),
                resolveAgentUseYn(
                        request.getIsPublic() != null ? request.getIsPublic() : board.getIsPublic(),
                        request.getAgentUseYn()));
        try {
            boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardConflict(ex);
        }
        boardIconAttachmentService.syncBoardIcon(currentUser.getUserId(), board, previousIconUrl);
        boardAiInfoService.upsertBoardAiInfoIfEnabled(board, request.getGuidePrompt(), false);
        return board;
    }

    private Boolean resolveAgentUseYn(Boolean isPublic, Boolean requestedAgentUseYn) {
        if (Boolean.FALSE.equals(isPublic)) {
            return false;
        }
        return requestedAgentUseYn;
    }

    void transferBoardManager(String boardUrl, String loginId, Long userId) {
        Board board = boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        User currentUser = getCurrentUser(userId);
        boardAccessPolicy.validateBoardAdmin(board, currentUser);

        User nextManager = adminEligibleUserService.getActiveUserByLoginId(loginId);
        if (!boardSubscriptionRepository.existsByUserAndBoard(nextManager, board)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        boardManagerAssignmentService.assignBoardManager(board, nextManager);
    }

    void deleteBoard(String boardUrl, Long userId) {
        Board board = boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        User currentUser = getCurrentUser(userId);
        boardAccessPolicy.validateBoardAdmin(board, currentUser);

        board.deactivate();
    }

    private User getCurrentUserOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User getCurrentUser(Long userId) {
        User user = getCurrentUserOrNull(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String normalizeInquiryBoardUrl(String requestedBoardUrl) {
        return BoardPolicyConstants.INQUIRY_BOARD_URL;
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
                    false);
        }
    }

    private void ensureInquiryBoardCategory(Board board) {
        List<BoardCategory> activeCategories = boardCategoryRepository
                .findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true);
        BoardCategory canonicalCategory = resolveInquiryDefaultCategory(activeCategories)
                .orElse(null);
        if (canonicalCategory == null) {
            createDefaultInquiryCategory(board);
            return;
        }

        canonicalCategory.setDefaultCategory(true);
        if (!Objects.equals(canonicalCategory.getSortOrder(), 1)) {
            canonicalCategory.update(canonicalCategory.getName(), 1, canonicalCategory.getMinWriteRole());
        }
        activeCategories.stream()
                .filter(category -> category.isDefaultCategory()
                        || BoardDefaultCategoryNames.DEFAULT_CATEGORY_NAME.equals(category.getName()))
                .filter(category -> !Objects.equals(category.getCategoryId(), canonicalCategory.getCategoryId()))
                .forEach(BoardCategory::deactivate);
    }

    private java.util.Optional<BoardCategory> resolveInquiryDefaultCategory(List<BoardCategory> activeCategories) {
        return activeCategories.stream()
                .filter(BoardCategory::isDefaultCategory)
                .min(Comparator.comparing(BoardCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BoardCategory::getCategoryId, Comparator.nullsLast(Long::compareTo)))
                .or(() -> activeCategories.stream()
                        .filter(category -> BoardDefaultCategoryNames.DEFAULT_CATEGORY_NAME.equals(category.getName()))
                        .min(Comparator.comparing(BoardCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(BoardCategory::getCategoryId, Comparator.nullsLast(Long::compareTo))))
                .or(() -> BoardDefaultCategoryResolver.resolveDefaultCategory(activeCategories));
    }

    private void createDefaultInquiryCategory(Board board) {
        try {
            boardCategoryRepository.saveAndFlush(BoardCategory.builder()
                    .board(board)
                    .name(BoardDefaultCategoryNames.DEFAULT_CATEGORY_NAME)
                    .sortOrder(1)
                    .isDefault(true)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (!BoardCategoryConstraintResolver.isActiveNameConstraint(ex)) {
                throw ex;
            }
        }
    }

    private void ensureInquiryBoardManager(Board board, User requester) {
        User manager = resolveInquiryBoardCreator(requester);
        boardManagerAssignmentService.assignBoardManager(board, manager);
    }

    private User resolveInquiryBoardCreator(User fallbackUser) {
        User creator = userRepository.findUsableSuperAdmins().stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(User::getUserId))
                .orElse(fallbackUser);
        if (creator == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        adminEligibleUserService.validateActiveUser(creator);
        return creator;
    }

    private String resolveInquiryBoardName(String inquiryBoardUrl) {
        LinkedHashSet<String> candidates = buildInquiryBoardNameCandidates(inquiryBoardUrl);
        Set<String> existingBoardNames = new HashSet<>(boardRepository.findExistingBoardNamesIn(candidates));
        return candidates.stream()
                .filter(candidate -> !existingBoardNames.contains(candidate))
                .findFirst()
                .orElseGet(() -> trimToMaxLength(DEFAULT_INQUIRY_BOARD_NAME + "-" + System.currentTimeMillis(), 100));
    }

    private LinkedHashSet<String> buildInquiryBoardNameCandidates(String inquiryBoardUrl) {
        String base = DEFAULT_INQUIRY_BOARD_NAME;
        String candidate = trimToMaxLength(base + "-" + inquiryBoardUrl, 100);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(base);
        candidates.add(candidate);

        for (int i = 2; i <= 999; i++) {
            candidates.add(trimToMaxLength(candidate + "-" + i, 100));
        }
        return candidates;
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
        if (BoardPolicyConstants.INQUIRY_BOARD_URL.equalsIgnoreCase(boardUrl.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reserved board URL");
        }
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }

    private String normalizeIconUrl(String iconUrl) {
        if (iconUrl == null) {
            return null;
        }
        String trimmedIconUrl = iconUrl.trim();
        return trimmedIconUrl.isEmpty() ? null : trimmedIconUrl;
    }
}
