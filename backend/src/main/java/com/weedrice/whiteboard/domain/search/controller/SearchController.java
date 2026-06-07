package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordResponse;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchResultResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchService;
import com.weedrice.whiteboard.domain.search.service.SearchPreviewReadService;
import com.weedrice.whiteboard.domain.search.service.SearchRecordFacade;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchPreviewReadService searchPreviewReadService;
    private final SearchRecordFacade searchRecordFacade;
    private final SemanticSearchService semanticSearchService;

    @GetMapping
    public ApiResponse<IntegratedSearchResponse> integratedSearch(
            @RequestParam String q,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = optionalUserId(userDetails);
        IntegratedSearchResponse response = searchPreviewReadService.integratedSearch(q, userId);
        searchRecordFacade.record(userId, response.getKeyword());
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

        Long userId = optionalUserId(userDetails);
        Page<PostSummary> response = searchService.searchPosts(q, searchType, boardUrl, page, size, sort, userId);

        return ApiResponses.page(response);
    }

    @GetMapping("/semantic")
    public ApiResponse<PageResponse<SemanticSearchResultResponse>> semanticSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String contentType,
            @RequestParam(required = false) String boardUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = optionalUserId(userDetails);
        Page<SemanticSearchResultResponse> response = semanticSearchService.search(
                q, contentType, boardUrl, page, size, userId);
        return ApiResponses.page(response);
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
            @CurrentUserId Long userId) {
        return ApiResponse.success(searchService.getRecentSearches(userId, pageable));
    }

    @DeleteMapping("/recent/{logId}")
    public ApiResponse<Void> deleteRecentSearch(@PathVariable Long logId,
            @CurrentUserId Long userId) {
        searchService.deleteRecentSearch(userId, logId);
        return ApiResponses.ok();
    }

    @DeleteMapping("/recent")
    public ApiResponse<Void> deleteAllRecentSearches(@CurrentUserId Long userId) {
        searchService.deleteAllRecentSearches(userId);
        return ApiResponses.ok();
    }
}
