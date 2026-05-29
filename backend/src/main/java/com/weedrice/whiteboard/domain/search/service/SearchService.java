package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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
    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchStatisticCommandService searchStatisticCommandService;
    private final RecentSearchCommandService recentSearchCommandService;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final BoardAccessPolicy boardAccessPolicy;
    private final SearchRecordEventPublisher searchRecordEventPublisher;
    private final SearchUserLookupPolicy searchUserLookupPolicy;

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

    public Page<PostSummary> searchPosts(String keyword, String searchType, String boardUrl, int page, int size,
            Sort sort,
            Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable normalizedPageable = SearchRequestNormalizer.normalizePostSearchPageable(page, size, sort);
        boolean includeSecret = false;
        User currentUser = null;
        if (boardUrl != null && !boardUrl.trim().isEmpty()) {
            Board board = boardRepository.findByBoardUrl(boardUrl)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
            currentUser = searchUserLookupPolicy.resolveOptional(currentUserId);
            if (!boardAccessPolicy.canReadBoard(board, currentUser)) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
            includeSecret = boardAccessPolicy.canViewSecretPosts(board, currentUser);
        }

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = currentUser == null
                    ? userBlockService.getBlockedUserIdsEitherDirection(currentUserId)
                    : userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(currentUserId);
        }
        Page<Post> postPage = postRepository.searchPosts(canonicalKeyword, searchType,
                boardUrl, blockedUserIds, includeSecret, currentUserId, normalizedPageable);

        Page<PostSummary> response = postSummaryAssembler.assembleSearchPage(postPage);
        searchRecordEventPublisher.publish(currentUserId, canonicalKeyword);
        return response;
    }

    public SearchPersonalizationResponse getRecentSearches(Long userId, Pageable pageable) {
        Pageable normalizedPageable = SearchRequestNormalizer.normalizeRecentSearchPageable(pageable);
        Page<SearchPersonalization> recentSearches =
                searchPersonalizationRepository.findRecentSearchesByUserId(userId, normalizedPageable);
        if (recentSearches.isEmpty()) {
            searchUserLookupPolicy.validateExists(userId);
        }
        return SearchPersonalizationResponse.from(recentSearches);
    }

    @Transactional
    public void deleteRecentSearch(Long userId, Long logId) {
        int deletedCount = searchPersonalizationRepository.deleteByLogIdAndUserId(logId, userId);
        if (deletedCount == 0) {
            searchUserLookupPolicy.validateExists(userId);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void deleteAllRecentSearches(Long userId) {
        int deletedCount = searchPersonalizationRepository.deleteAllByUserId(userId);
        if (deletedCount == 0) {
            searchUserLookupPolicy.validateExists(userId);
        }
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
