package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordResponse;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
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
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        }
        searchService.recordSearch(userId, q); // 검색어 기록

        Pageable pageable = PageRequest.of(page, size);
        IntegratedSearchResponse response = searchService.integratedSearch(q, type, pageable);

        return ApiResponse.success(response);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(SearchPersonalizationResponse.from(searchService.getRecentSearches(userId, pageable)));
    }

    @DeleteMapping("/recent/{logId}")
    public ApiResponse<Void> deleteRecentSearch(@PathVariable Long logId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        searchService.deleteRecentSearch(userId, logId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/recent")
    public ApiResponse<Void> deleteAllRecentSearches(Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        searchService.deleteAllRecentSearches(userId);
        return ApiResponse.success(null);
    }
}
