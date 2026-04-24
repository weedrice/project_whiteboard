package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private static final int SEARCH_PREVIEW_LIMIT = 5;
    private static final int MAX_POPULAR_KEYWORD_LIMIT = 100;

    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchStatisticCommandService searchStatisticCommandService;
    private final RecentSearchCommandService recentSearchCommandService;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final AdminRepository adminRepository;
    private final UserBlockService userBlockService;
    private final FileService fileService;

    @Transactional
    public void recordSearch(Long userId, String keyword, LocalDate searchDate) {
        searchStatisticCommandService.recordSearchStatistic(keyword, searchDate);

        if (userId != null) {
            try {
                recentSearchCommandService.recordRecentSearch(userId, keyword);
            } catch (RuntimeException e) {
                log.warn("Failed to record recent search. userId={}, keyword={}", userId, keyword, e);
            }
        }
    }

    public IntegratedSearchResponse integratedSearch(String keyword, Long currentUserId) {
        Pageable previewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        Page<Post> postPage = postRepository.searchPostsByKeyword(keyword,
                blockedUserIds, currentUserId, previewPageable);
        Page<PostSummary> posts = mapPostSummaries(postPage);

        Page<CommentResponse> comments = commentRepository
                .searchCommentsByKeyword(keyword, blockedUserIds, currentUserId, previewPageable)
                .map(CommentResponse::from);

        Page<UserSummary> users = userRepository.searchUsersVisibleTo(keyword, blockedUserIds, previewPageable)
                .map(UserSummary::from);

        List<BoardSummary> boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(keyword,
                        previewPageable)
                .stream()
                .map(BoardSummary::from)
                .collect(Collectors.toList());

        return IntegratedSearchResponse.from(posts, comments, users, boards, keyword);
    }

    public Page<PostSummary> searchPosts(String keyword, String searchType, String boardUrl, Pageable pageable,
            Long currentUserId) {
        boolean includeSecret = false;
        if (boardUrl != null && !boardUrl.trim().isEmpty()) {
            Board board = boardRepository.findByBoardUrl(boardUrl)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
            User currentUser = null;
            if (currentUserId != null) {
                currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            }
            if ((!board.getIsActive() || !board.getIsPublic()) && !hasBoardAdminAccess(board, currentUser)) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
            includeSecret = hasBoardAdminAccess(board, currentUser);
        }

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        Page<Post> postPage = postRepository.searchPosts(keyword, searchType,
                boardUrl, blockedUserIds, includeSecret, currentUserId, pageable);

        return mapPostSummaries(postPage);
    }

    public SearchPersonalizationResponse getRecentSearches(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return SearchPersonalizationResponse
                .from(searchPersonalizationRepository.findByUserOrderBySearchedAtDesc(user, pageable));
    }

    @Transactional
    public void deleteRecentSearch(Long userId, Long logId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        SearchPersonalization personalization = searchPersonalizationRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!personalization.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        searchPersonalizationRepository.delete(personalization);
    }

    @Transactional
    public void deleteAllRecentSearches(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        searchPersonalizationRepository.deleteByUser(user);
    }

    public List<PopularKeywordDto> getPopularKeywords(String period, int limit) {
        if (limit < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDate endDate = DateTimeUtils.nowKST().toLocalDate();
        LocalDate startDate = resolvePopularKeywordStartDate(period, endDate);
        int normalizedLimit = Math.min(limit, MAX_POPULAR_KEYWORD_LIMIT);

        return searchStatisticRepository.findPopularKeywords(startDate, endDate, PageRequest.of(0, normalizedLimit))
                .stream()
                .map(result -> new PopularKeywordDto(result.getKeyword(), result.getCount()))
                .collect(Collectors.toList());
    }

    private LocalDate resolvePopularKeywordStartDate(String period, LocalDate endDate) {
        String normalizedPeriod = normalizePopularKeywordPeriod(period);
        return switch (normalizedPeriod) {
            case "DAILY" -> endDate;
            case "MONTHLY" -> endDate.minusMonths(1);
            case "WEEKLY" -> endDate.minusWeeks(1);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    private String normalizePopularKeywordPeriod(String period) {
        if (period == null || period.isBlank()) {
            return "DAILY";
        }
        String normalizedPeriod = period.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedPeriod) {
            case "DAILY", "WEEKLY", "MONTHLY" -> normalizedPeriod;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    private boolean hasBoardAdminAccess(Board board, User user) {
        if (board == null || user == null) {
            return false;
        }
        if (user.getIsSuperAdmin()) {
            return true;
        }
        if (board.getCreator().getUserId().equals(user.getUserId())) {
            return true;
        }
        return adminRepository.existsByUserAndBoardAndIsActive(user, board, true);
    }

    private Page<PostSummary> mapPostSummaries(Page<Post> postPage) {
        List<Long> postIds = postPage.getContent().stream()
                .map(Post::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = postIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(fileService.getRelatedIdsWithImages(postIds, "POST_CONTENT"));
        long totalElements = postPage.getTotalElements();
        int pageNumber = postPage.getNumber();
        int pageSize = postPage.getSize();
        boolean isAscending = isAscendingPostOrder(postPage.getPageable());

        List<PostSummary> content = postPage.getContent().stream()
                .map(post -> {
                    PostSummary summary = PostSummary.from(post);
                    summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
                    return summary;
                })
                .collect(Collectors.toList());

        for (int index = 0; index < content.size(); index++) {
            PostSummary summary = content.get(index);
            if (isAscending) {
                summary.setRowNum(((long) pageNumber * pageSize) + index + 1);
                continue;
            }
            summary.setRowNum(totalElements - ((long) pageNumber * pageSize) - index);
        }

        return new PageImpl<>(content, postPage.getPageable(), postPage.getTotalElements());
    }

    private boolean isAscendingPostOrder(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (("createdAt".equals(order.getProperty()) || "postId".equals(order.getProperty()))
                    && order.isAscending()) {
                return true;
            }
        }
        return false;
    }
}
