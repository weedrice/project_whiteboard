package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordResponse;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ApiResponse<IntegratedSearchResponse> integratedSearch(
            @RequestParam String q,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails != null) {
            searchService.recordSearch(userDetails.getUserId(), q);
        } else {
            searchService.recordSearch(null, q);
        }

        IntegratedSearchResponse response = searchService.integratedSearch(q);
        return ApiResponse.success(response);
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostSummary>> searchPosts(
            @RequestParam String q,
            @RequestParam(required = false) Long boardId,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails != null) {
            searchService.recordSearch(userDetails.getUserId(), q);
        } else {
            searchService.recordSearch(null, q);
        }

        Page<PostSummary> response = searchService.searchPosts(q, boardId, pageable);
        return ApiResponse.success(new PageResponse<>(response));
    }

    @GetMapping("/popular")
    public ApiResponse<PopularKeywordResponse> getPopularKeywords(
            @RequestParam(defaultValue = "DAILY") String period,
            @RequestParam(defaultValue = "10") int limit) {
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords(period, limit);
        return ApiResponse.success(PopularKeywordResponse.from(popularKeywords));
    }

    @GetMapping("/recent")
    public ApiResponse<SearchPersonalizationResponse> getRecentSearches(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(SearchPersonalizationResponse.from(searchService.getRecentSearches(userDetails.getUserId(), pageable)));
    }

    @DeleteMapping("/recent/{logId}")
    public ApiResponse<Void> deleteRecentSearch(@PathVariable Long logId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        searchService.deleteRecentSearch(userDetails.getUserId(), logId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/recent")
    public ApiResponse<Void> deleteAllRecentSearches(@AuthenticationPrincipal CustomUserDetails userDetails) {
        searchService.deleteAllRecentSearches(userDetails.getUserId());
        return ApiResponse.success(null);
    }
}
