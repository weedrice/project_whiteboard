package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardActivitySummary;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardRecentUpdateResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository.TopBoardPostCountProjection;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class BoardQueryService {
    private static final int TOP_BOARD_LIMIT = 15;
    private static final int MANAGER_CANDIDATE_KEYWORD_MAX_LENGTH = 100;
    private static final char LIKE_ESCAPE = '!';

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final BoardResponseAssembler boardResponseAssembler;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostService postService;
    private final BoardVisitService boardVisitService;

    BoardQueryService(BoardRepository boardRepository,
                      BoardCategoryRepository boardCategoryRepository,
                      BoardSubscriptionRepository boardSubscriptionRepository,
                      AdminRepository adminRepository,
                      UserRepository userRepository,
                      BoardResponseAssembler boardResponseAssembler,
                      BoardAccessPolicy boardAccessPolicy,
                      PostService postService,
                      BoardVisitService boardVisitService) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.boardResponseAssembler = boardResponseAssembler;
        this.boardAccessPolicy = boardAccessPolicy;
        this.postService = postService;
        this.boardVisitService = boardVisitService;
    }

    List<BoardListResponse> getActiveBoards(Long userId) {
        User currentUser = getCurrentUserByIdOrNull(userId);
        List<Board> boards = boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(
                currentUser,
                currentUser != null && currentUser.isUsableSuperAdmin(),
                boardAccessPolicy.getInquiryBoardUrl());
        return boardResponseAssembler.assembleListAll(boards, currentUser);
    }

    List<BoardListResponse> getTopBoardsByUserId(Long userId) {
        return getTopBoardsByUserId(userId, TOP_BOARD_LIMIT);
    }

    List<BoardListResponse> getTopBoardsByUserId(Long userId, int limit) {
        User currentUser = getCurrentUserByIdOrNull(userId);
        return getTopBoardsForUser(currentUser, limit);
    }

    private List<BoardListResponse> getTopBoardsForUser(User currentUser, int limit) {
        if (currentUser == null || !boardAccessPolicy.hasElevatedBoardVisibility(currentUser)) {
            List<TopBoardPostCountProjection> topBoardCounts = boardRepository.findTopPublicBoardPostCounts(
                    boardAccessPolicy.getInquiryBoardUrl(),
                    PageRequest.of(0, limit));
            return assembleTopBoards(topBoardCounts, currentUser);
        }

        List<TopBoardPostCountProjection> topBoardCounts = boardRepository.findTopReadableBoardPostCounts(
                currentUser,
                currentUser.isUsableSuperAdmin(),
                boardAccessPolicy.getInquiryBoardUrl(),
                PageRequest.of(0, limit));
        return assembleTopBoards(topBoardCounts, currentUser);
    }

    private List<BoardListResponse> assembleTopBoards(List<TopBoardPostCountProjection> topBoardCounts,
                                                      User currentUser) {
        List<Long> boardIds = topBoardCounts.stream()
                .map(TopBoardPostCountProjection::getBoardId)
                .toList();
        List<Board> boards = findBoardsByIdsInOrder(boardIds);
        Map<Long, Long> postCounts = topBoardCounts.stream()
                .collect(Collectors.toMap(
                        TopBoardPostCountProjection::getBoardId,
                        TopBoardPostCountProjection::getPostCount,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));
        return boardResponseAssembler.assembleListAll(boards, currentUser, postCounts);
    }

    List<AdminBoardResponse> getAllBoards(Long userId) {
        User currentUser = getCurrentUserByIdOrNull(userId);
        if (currentUser == null || !currentUser.isUsableSuperAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Board> boards = boardRepository.findAllByOrderBySortOrderAscBoardIdAsc();
        return boardResponseAssembler.assembleAdminAll(boards);
    }

    BoardDetailResponse getBoardDetails(String boardUrl, Long userId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserByIdOrNull(userId);
        boardAccessPolicy.validateReadable(board, currentUser);

        return boardResponseAssembler.assembleDetail(board, currentUser);
    }

    List<CategoryResponse> getActiveCategories(String boardUrl, Long userId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserByIdOrNull(userId);
        boardAccessPolicy.validateReadable(board, currentUser);
        return boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)
                .stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());
    }

    List<PostSummary> getNoticeSummaries(String boardUrl, Long currentUserId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (boardAccessPolicy.isInquiryBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        User currentUser = getCurrentUserByIdOrNull(currentUserId);
        boardAccessPolicy.validateReadable(board, currentUser);
        boolean includeSecret = boardAccessPolicy.canViewSecretPosts(board, currentUser);
        return postService.getNoticeSummaries(board.getBoardId(), currentUserId, includeSecret);
    }

    Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable) {
        return getMySubscriptions(userId, pageable, false);
    }

    Page<SubscriptionBoardResponse> getMySubscriptions(Long userId, Pageable pageable, boolean includeUnavailable) {
        User user = getCurrentUserByIdOrThrow(userId);
        Pageable fixedOrderPageable = fixedSubscriptionOrderPageable(pageable);
        if (includeUnavailable) {
            Page<BoardSubscription> subscriptions = boardSubscriptionRepository.findByUserOrderBySortOrderAsc(
                    user,
                    fixedOrderPageable);
            Set<Long> activeAdminBoardIds = resolveActiveAdminBoardIds(user, subscriptions.getContent());
            List<Board> readableBoards = subscriptions.getContent().stream()
                    .map(BoardSubscription::getBoard)
                    .filter(board -> boardAccessPolicy.canReadBoard(board, user, activeAdminBoardIds))
                    .toList();
            Set<Long> subscribedBoardIds = resolveSubscribedBoardIds(subscriptions.getContent());
            Map<Long, BoardActivitySummary> activityByBoardId = boardVisitService.getActivitySummaries(
                    subscribedBoardIds,
                    user);
            Map<Long, BoardListResponse> readableResponsesByBoardId = boardResponseAssembler
                    .assembleListAllWithSubscribedBoardIds(readableBoards, user, subscribedBoardIds)
                    .stream()
                    .collect(Collectors.toMap(BoardListResponse::getBoardId, Function.identity()));
            List<SubscriptionBoardResponse> responses = subscriptions.getContent().stream()
                    .map(subscription -> toSubscriptionResponse(subscription, readableResponsesByBoardId, activityByBoardId))
                    .toList();
            return new PageImpl<>(responses, fixedOrderPageable, subscriptions.getTotalElements());
        }
        Page<BoardSubscription> visibleSubscriptions = boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user,
                user.isUsableSuperAdmin(),
                fixedOrderPageable);
        List<Board> visibleBoards = visibleSubscriptions.getContent().stream()
                .map(BoardSubscription::getBoard)
                .toList();
        Set<Long> subscribedBoardIds = resolveSubscribedBoardIds(visibleSubscriptions.getContent());
        Map<Long, BoardActivitySummary> activityByBoardId = boardVisitService.getActivitySummaries(
                subscribedBoardIds,
                user);
        List<SubscriptionBoardResponse> responses = boardResponseAssembler
                .assembleListAllWithSubscribedBoardIds(visibleBoards, user, subscribedBoardIds)
                .stream()
                .map(board -> SubscriptionBoardResponse.accessible(board, activityByBoardId.get(board.getBoardId())))
                .toList();
        return new PageImpl<>(responses, fixedOrderPageable, visibleSubscriptions.getTotalElements());
    }

    List<BoardRecentUpdateResponse> getRecentBoardUpdates(List<String> boardUrls, Long userId) {
        List<String> normalizedUrls = boardUrls == null ? List.of() : boardUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(BoardUrlNormalizer::normalizeLookup)
                .filter(url -> !url.isBlank())
                .distinct()
                .limit(20)
                .toList();
        if (normalizedUrls.isEmpty()) {
            return List.of();
        }

        User currentUser = getCurrentUserByIdOrNull(userId);
        Map<String, Integer> orderByUrl = new LinkedHashMap<>();
        for (int index = 0; index < normalizedUrls.size(); index++) {
            orderByUrl.put(normalizedUrls.get(index), index);
        }
        List<Board> readableBoards = boardRepository.findByBoardUrlIn(normalizedUrls).stream()
                .filter(board -> boardAccessPolicy.canReadBoard(board, currentUser))
                .sorted((left, right) -> Integer.compare(
                        orderByUrl.getOrDefault(left.getBoardUrl(), Integer.MAX_VALUE),
                        orderByUrl.getOrDefault(right.getBoardUrl(), Integer.MAX_VALUE)))
                .toList();
        Map<Long, BoardActivitySummary> activityByBoardId = boardVisitService.getActivitySummaries(
                readableBoards.stream().map(Board::getBoardId).toList(),
                currentUser);

        return readableBoards.stream()
                .map(board -> BoardRecentUpdateResponse.builder()
                        .boardUrl(board.getBoardUrl())
                        .latestPostAt(activityByBoardId
                                .getOrDefault(board.getBoardId(), BoardActivitySummary.empty(board.getBoardId()))
                                .latestPostAt())
                        .build())
                .toList();
    }

    private Set<Long> resolveSubscribedBoardIds(List<BoardSubscription> subscriptions) {
        return subscriptions.stream()
                .map(BoardSubscription::getBoard)
                .map(Board::getBoardId)
                .collect(Collectors.toSet());
    }

    Page<BoardManagerCandidateResponse> getBoardManagerCandidates(String boardUrl, Long currentUserId,
            String keyword, Pageable pageable) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUserByIdOrNull(currentUserId);
        boardAccessPolicy.validateBoardAdmin(board, currentUser);

        Pageable fixedOrderPageable = fixedSubscriptionOrderPageable(pageable);
        String keywordPattern = toKeywordPattern(keyword);
        Page<BoardSubscription> candidates = keywordPattern == null
                ? boardSubscriptionRepository.findManagerCandidatesByBoard(board, fixedOrderPageable)
                : boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(
                        board,
                        keywordPattern,
                        fixedOrderPageable);
        Long currentManagerUserId = adminRepository
                .findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true)
                .map(Admin::getUser)
                .map(User::getUserId)
                .orElse(null);

        return candidates.map(subscription -> {
            User user = subscription.getUser();
            return BoardManagerCandidateResponse.from(
                    user,
                    currentManagerUserId != null && currentManagerUserId.equals(user.getUserId()));
        });
    }

    private String toKeywordPattern(String keyword) {
        String normalizedKeyword = TextInputNormalizer.normalizeOptional(
                keyword,
                MANAGER_CANDIDATE_KEYWORD_MAX_LENGTH);
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return null;
        }
        return "%" + escapeLikePattern(normalizedKeyword.toLowerCase(Locale.ROOT)) + "%";
    }

    private String escapeLikePattern(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') {
                builder.append(LIKE_ESCAPE);
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private Pageable fixedSubscriptionOrderPageable(Pageable pageable) {
        return PageRequestUtils.of(pageable, 20);
    }

    private Set<Long> resolveActiveAdminBoardIds(User user, List<BoardSubscription> subscriptions) {
        List<Long> boardIds = subscriptions.stream()
                .map(BoardSubscription::getBoard)
                .filter(board -> !isPublicActive(board))
                .filter(board -> !user.isUsableSuperAdmin())
                .map(Board::getBoardId)
                .toList();
        if (boardIds.isEmpty()) {
            return Set.of();
        }
        return adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, boardIds, true)
                .stream()
                .map(Admin::getBoard)
                .map(Board::getBoardId)
                .collect(Collectors.toSet());
    }

    private boolean isPublicActive(Board board) {
        return Boolean.TRUE.equals(board.getIsActive()) && Boolean.TRUE.equals(board.getIsPublic());
    }

    private SubscriptionBoardResponse toSubscriptionResponse(BoardSubscription subscription,
            Map<Long, BoardListResponse> readableResponsesByBoardId,
            Map<Long, BoardActivitySummary> activityByBoardId) {
        Board board = subscription.getBoard();
        BoardListResponse readableResponse = readableResponsesByBoardId.get(board.getBoardId());
        if (readableResponse != null) {
            return SubscriptionBoardResponse.accessible(readableResponse, activityByBoardId.get(board.getBoardId()));
        }
        return SubscriptionBoardResponse.inaccessible(board);
    }

    private User getCurrentUserByIdOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return getCurrentUserByIdOrThrow(userId);
    }

    private User getCurrentUserByIdOrThrow(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findById(userId)
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
