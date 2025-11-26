package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

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
