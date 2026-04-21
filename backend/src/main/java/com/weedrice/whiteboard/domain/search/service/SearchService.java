package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private static final int SEARCH_PREVIEW_LIMIT = 5;

    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchStatisticCommandService searchStatisticCommandService;
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
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            searchPersonalizationRepository.deleteByUserAndKeyword(user, keyword);
            SearchPersonalization personalization = SearchPersonalization.builder()
                    .user(user)
                    .keyword(keyword)
                    .build();
            searchPersonalizationRepository.save(personalization);
        }
    }

    public IntegratedSearchResponse integratedSearch(String keyword, Long currentUserId) {
        Pageable previewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        Page<com.weedrice.whiteboard.domain.post.entity.Post> postPage = postRepository.searchPostsByKeyword(keyword,
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
        Page<com.weedrice.whiteboard.domain.post.entity.Post> postPage = postRepository.searchPosts(keyword, searchType,
                boardUrl, blockedUserIds, includeSecret, currentUserId, pageable);

        return mapPostSummaries(postPage);
    }

    public SearchPersonalizationResponse getRecentSearches(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return SearchPersonalizationResponse
                .from(searchPersonalizationRepository.findByUserOrderByCreatedAtDesc(user, pageable));
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
            return Collections.emptyList();
        }

        LocalDate endDate = DateTimeUtils.nowKST().toLocalDate();
        LocalDate startDate = resolvePopularKeywordStartDate(period, endDate);

        return searchStatisticRepository.findPopularKeywords(startDate, endDate, PageRequest.of(0, limit))
                .stream()
                .map(result -> new PopularKeywordDto(result.getKeyword(), result.getCount()))
                .collect(Collectors.toList());
    }

    private LocalDate resolvePopularKeywordStartDate(String period, LocalDate endDate) {
        return switch (period.toUpperCase()) {
            case "DAILY" -> endDate;
            case "MONTHLY" -> endDate.minusMonths(1);
            case "WEEKLY" -> endDate.minusWeeks(1);
            default -> endDate.minusWeeks(1);
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

    private Page<PostSummary> mapPostSummaries(Page<com.weedrice.whiteboard.domain.post.entity.Post> postPage) {
        List<Long> postIds = postPage.getContent().stream()
                .map(com.weedrice.whiteboard.domain.post.entity.Post::getPostId)
                .collect(Collectors.toList());
        Set<Long> postIdsWithImages = postIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(fileService.getRelatedIdsWithImages(postIds, "POST_CONTENT"));

        List<PostSummary> content = postPage.getContent().stream()
                .map(post -> {
                    PostSummary summary = PostSummary.from(post);
                    summary.setHasImage(postIdsWithImages.contains(post.getPostId()));
                    return summary;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(content, postPage.getPageable(), postPage.getTotalElements());
    }
}
