package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordResponse;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchRecordEventPublisher;
import com.weedrice.whiteboard.domain.search.service.SearchRequestNormalizer;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchRecordEventPublisher searchRecordEventPublisher;

    @GetMapping
    public ApiResponse<IntegratedSearchResponse> integratedSearch(
            @RequestParam String q,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String keyword = SearchRequestNormalizer.canonicalizeKeyword(q);
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        IntegratedSearchResponse response = searchService.integratedSearch(keyword, userId);
        searchRecordEventPublisher.publish(userId, keyword);
        return ApiResponse.success(response);
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostSummary>> searchPosts(
            @RequestParam String q,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String boardUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String keyword = SearchRequestNormalizer.canonicalizeKeyword(q);
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        Pageable pageable = SearchRequestNormalizer.normalizePostSearchPageable(page, size, sort);
        Page<PostSummary> response = searchService.searchPosts(keyword, searchType, boardUrl, pageable, userId);

        searchRecordEventPublisher.publish(userId, keyword);
        return ApiResponse.success(new PageResponse<>(response));
    }

    @GetMapping("/popular")
    public ApiResponse<PopularKeywordResponse> getPopularKeywords(
            @RequestParam(defaultValue = "DAILY") String period,
            @RequestParam(defaultValue = "10") int limit) {
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords(
                SearchRequestNormalizer.normalizePopularKeywordPeriod(period),
                SearchRequestNormalizer.normalizePopularKeywordLimit(limit));
        return ApiResponse.success(PopularKeywordResponse.from(popularKeywords));
    }

    @GetMapping("/recent")
    public ApiResponse<SearchPersonalizationResponse> getRecentSearches(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(searchService.getRecentSearches(userDetails.getUserId(), pageable));
    }

    @DeleteMapping("/recent/{logId}")
    public ApiResponse<Void> deleteRecentSearch(@PathVariable Long logId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        searchService.deleteRecentSearch(userDetails.getUserId(), logId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/recent")
    public ApiResponse<Void> deleteAllRecentSearches(@AuthenticationPrincipal CustomUserDetails userDetails) {
        searchService.deleteAllRecentSearches(userDetails.getUserId());
        return ApiResponse.success(null);
    }
}
