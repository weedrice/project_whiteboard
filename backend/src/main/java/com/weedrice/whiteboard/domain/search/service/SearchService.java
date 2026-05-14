package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private static final int SEARCH_PREVIEW_LIMIT = 5;
    private static final Sort COMMENT_PREVIEW_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("commentId"));

    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchStatisticCommandService searchStatisticCommandService;
    private final RecentSearchCommandService recentSearchCommandService;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final BoardAccessPolicy boardAccessPolicy;
    private final SearchRecordEventPublisher searchRecordEventPublisher;

    @Transactional
    public void recordSearch(Long userId, String keyword, LocalDate searchDate) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }

        searchStatisticCommandService.recordSearchStatistic(canonicalKeyword, searchDate);

        if (userId != null) {
            try {
                recentSearchCommandService.recordRecentSearch(userId, canonicalKeyword);
            } catch (RuntimeException e) {
                log.warn("Failed to record recent search. userId={}, keyword={}", userId, canonicalKeyword, e);
            }
        }
    }

    public IntegratedSearchResponse integratedSearch(String keyword, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable previewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT);
        Pageable commentPreviewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT, COMMENT_PREVIEW_SORT);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
        }

        Page<Post> postPage = postRepository.searchPostsByKeyword(canonicalKeyword,
                blockedUserIds, currentUserId, previewPageable);
        Page<PostSummary> posts = postSummaryAssembler.assembleSearchPage(postPage);

        Page<CommentResponse> comments = commentRepository
                .searchCommentsByKeyword(canonicalKeyword, blockedUserIds, currentUserId, commentPreviewPageable)
                .map(CommentResponse::from);

        Page<UserSummary> users = userRepository.searchUsersVisibleTo(canonicalKeyword, blockedUserIds, previewPageable)
                .map(UserSummary::from);

        List<BoardSummary> boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                        canonicalKeyword,
                        previewPageable)
                .stream()
                .map(BoardSummary::from)
                .collect(Collectors.toList());

        IntegratedSearchResponse response = IntegratedSearchResponse.from(posts, comments, users, boards, canonicalKeyword);
        searchRecordEventPublisher.publish(currentUserId, canonicalKeyword);
        return response;
    }

    public Page<PostSummary> searchPosts(String keyword, String searchType, String boardUrl, Pageable pageable,
            Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable normalizedPageable = SearchRequestNormalizer.normalizePostSearchPageable(pageable);
        boolean includeSecret = false;
        if (boardUrl != null && !boardUrl.trim().isEmpty()) {
            Board board = boardRepository.findByBoardUrl(boardUrl)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
            User currentUser = null;
            if (currentUserId != null) {
                currentUser = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            }
            if (!boardAccessPolicy.canReadBoard(board, currentUser)) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
            includeSecret = boardAccessPolicy.canViewSecretPosts(board, currentUser);
        }

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
        }
        Page<Post> postPage = postRepository.searchPosts(canonicalKeyword, searchType,
                boardUrl, blockedUserIds, includeSecret, currentUserId, normalizedPageable);

        Page<PostSummary> response = postSummaryAssembler.assembleSearchPage(postPage);
        searchRecordEventPublisher.publish(currentUserId, canonicalKeyword);
        return response;
    }

    public SearchPersonalizationResponse getRecentSearches(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable normalizedPageable = SearchRequestNormalizer.normalizeRecentSearchPageable(pageable);
        return SearchPersonalizationResponse
                .from(searchPersonalizationRepository.findByUserOrderBySearchedAtDesc(user, normalizedPageable));
    }

    @Transactional
    public void deleteRecentSearch(Long userId, Long logId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        int deletedCount = searchPersonalizationRepository.deleteByLogIdAndUserId(logId, userId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void deleteAllRecentSearches(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        searchPersonalizationRepository.deleteByUser(user);
    }

    public List<PopularKeywordDto> getPopularKeywords(String period, int limit) {
        LocalDate endDate = DateTimeUtils.nowKST().toLocalDate();
        LocalDate startDate = resolvePopularKeywordStartDate(
                SearchRequestNormalizer.normalizePopularKeywordPeriod(period),
                endDate);
        int normalizedLimit = SearchRequestNormalizer.normalizePopularKeywordLimit(limit);

        return searchStatisticRepository.findPopularKeywords(startDate, endDate, PageRequest.of(0, normalizedLimit))
                .stream()
                .map(result -> new PopularKeywordDto(result.getKeyword(), result.getCount()))
                .collect(Collectors.toList());
    }

    private LocalDate resolvePopularKeywordStartDate(String period, LocalDate endDate) {
        return switch (period) {
            case "DAILY" -> endDate;
            case "MONTHLY" -> endDate.minusMonths(1);
            case "WEEKLY" -> endDate.minusWeeks(1);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

}
