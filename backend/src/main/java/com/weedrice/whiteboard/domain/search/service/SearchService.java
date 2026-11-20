package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final SearchPreviewReadService searchPreviewReadService;
    private final SearchRecordEventPublisher searchRecordEventPublisher;
    private final SearchUserLookupPolicy searchUserLookupPolicy;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<PostSummary> searchPosts(String keyword, String searchType, String boardUrl, String author,
            String from, String to, String period, int page, int size, Sort sort,
            Long currentUserId) {
        Page<PostSummary> response = searchPreviewReadService.readSearchPosts(
                keyword, searchType, boardUrl, author, from, to, period, page, size, sort, currentUserId);
        searchRecordEventPublisher.publish(currentUserId, SearchRequestNormalizer.canonicalizeKeyword(keyword));
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
            case "MONTHLY" -> endDate.minusMonths(1).plusDays(1);
            case "WEEKLY" -> endDate.minusWeeks(1).plusDays(1);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

}
