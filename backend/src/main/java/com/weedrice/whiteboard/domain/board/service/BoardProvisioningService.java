package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.admin.service.BoardManagerAssignmentService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
class BoardProvisioningService {

    private static final String DEFAULT_INQUIRY_BOARD_NAME = "문의";
    private static final String DEFAULT_INQUIRY_BOARD_DESCRIPTION = "운영진에게 문의를 남기는 비공개 스페이스입니다.";
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserRepository userRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final BoardManagerAssignmentService boardManagerAssignmentService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final Clock clock;

    BoardProvisioningService(BoardRepository boardRepository,
                             BoardCategoryRepository boardCategoryRepository,
                             BoardSubscriptionRepository boardSubscriptionRepository,
                             UserRepository userRepository,
                             AdminEligibleUserService adminEligibleUserService,
                             BoardManagerAssignmentService boardManagerAssignmentService,
                             BoardAccessPolicy boardAccessPolicy,
                             Clock clock) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.userRepository = userRepository;
        this.adminEligibleUserService = adminEligibleUserService;
        this.boardManagerAssignmentService = boardManagerAssignmentService;
        this.boardAccessPolicy = boardAccessPolicy;
        this.clock = clock;
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

    void transferBoardManager(String boardUrl, String loginId, Long userId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrlForUpdate(normalizedBoardUrl)
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
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrlForUpdate(normalizedBoardUrl)
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
            if (!ConstraintNameMatcher.containsBoardUrlConstraint(ex)
                    && !ConstraintNameMatcher.containsBoardNameConstraint(ex)) {
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
                .orElseGet(this::fallbackInquiryBoardName);
    }

    private String fallbackInquiryBoardName() {
        return trimToMaxLength(DEFAULT_INQUIRY_BOARD_NAME + "-" + clock.millis(), 100);
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

}
