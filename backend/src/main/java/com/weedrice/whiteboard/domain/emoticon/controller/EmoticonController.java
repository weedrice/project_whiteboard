package com.weedrice.whiteboard.domain.emoticon.controller;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonImageAddRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonPurchaseStatusResponse;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.service.EmoticonService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/emoticons")
@RequiredArgsConstructor
public class EmoticonController {

    private final EmoticonService emoticonService;

    /**
     * 이모티콘 목록 조회
     * @param sortBy "latest" (최신순), "oldest" (오래된순), "popular" (판매순)
     */
    @GetMapping
    public ApiResponse<PageResponse<EmoticonMasterDto>> getEmoticons(
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(new PageResponse<>(
                emoticonService.getActiveEmoticons(pageable(page, size), normalizeSortBy(sortBy))));
    }

    /**
     * 인기 이모티콘 조회 (일간/주간/월간)
     * @param period "daily", "weekly", "monthly"
     */
    @GetMapping("/popular")
    public ApiResponse<List<EmoticonMasterDto>> getPopularEmoticons(
            @RequestParam(defaultValue = "daily") String period) {
        return ApiResponse.success(emoticonService.getPopularEmoticons(period));
    }

    /**
     * 통합 검색 (태그, 등록자명, 이모티콘 이름)
     * @param searchType ALL(전체), NAME(이름), CREATOR(등록자), TAG(태그)
     */
    @GetMapping("/search/all")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String searchType,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(new PageResponse<>(
                emoticonService.searchAll(keyword, searchType, pageable(page, size), normalizeSortBy(sortBy))));
    }

    /**
     * 태그로 이모티콘 검색
     */
    @GetMapping("/search/tag")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(new PageResponse<>(emoticonService.searchByTag(tag, pageable(page, size))));
    }

    /**
     * 키워드로 이모티콘 검색
     */
    @GetMapping("/search")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(new PageResponse<>(emoticonService.searchByKeyword(keyword, pageable(page, size))));
    }

    /**
     * 내 이모티콘 목록
     */
    @GetMapping("/my")
    public ApiResponse<PageResponse<EmoticonMasterDto>> getMyEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomUserDetails authenticatedUser = requireAuthenticated(userDetails);
        return ApiResponse.success(new PageResponse<>(
                emoticonService.getMyEmoticons(authenticatedUser.getUserId(), pageable(page, size))));
    }

    /**
     * 이모티콘 상세 조회
     * 숨김 처리된 경우: 등록자 또는 구매자만 조회 가능
     */
    @GetMapping("/{emoticonId}")
    public ApiResponse<EmoticonMasterDto> getEmoticonDetail(
            @PathVariable Long emoticonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        return ApiResponse.success(emoticonService.getEmoticonDetail(emoticonId, userId));
    }

    /**
     * 이모티콘 생성
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmoticonMasterDto> createEmoticon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody EmoticonCreateRequest request) {
        return ApiResponse.success(emoticonService.createEmoticon(userDetails.getUserId(), request));
    }

    /**
     * 이모티콘 수정
     */
    @PutMapping("/{emoticonId}")
    public ApiResponse<EmoticonMasterDto> updateEmoticon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId,
            @Valid @RequestBody EmoticonUpdateRequest request) {
        return ApiResponse.success(emoticonService.updateEmoticon(userDetails.getUserId(), emoticonId, request));
    }

    /**
     * 노비콘 숨김/표시 전환 (판매 중단 시 사용)
     */
    @PatchMapping("/{emoticonId}/visibility")
    public ApiResponse<EmoticonMasterDto> toggleVisibility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.toggleVisibility(userDetails.getUserId(), emoticonId));
    }

    /**
     * 이모티콘 삭제
     */
    @DeleteMapping("/{emoticonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteEmoticon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId) {
        emoticonService.deleteEmoticon(userDetails.getUserId(), emoticonId);
        return ApiResponse.success(null);
    }

    /**
     * 이미지 추가
     */
    @PostMapping("/{emoticonId}/images")
    public ApiResponse<EmoticonMasterDto> addImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId,
            @Valid @RequestBody EmoticonImageAddRequest request) {
        return ApiResponse.success(emoticonService.addImage(userDetails.getUserId(), emoticonId, request.getFileId()));
    }

    /**
     * 이미지 삭제
     */
    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long imageId) {
        emoticonService.deleteImage(userDetails.getUserId(), imageId);
        return ApiResponse.success(null);
    }

    /**
     * 이모티콘 구매
     */
    @PostMapping("/{emoticonId}/purchase")
    public ApiResponse<EmoticonMasterDto> purchaseEmoticon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.purchaseEmoticon(userDetails.getUserId(), emoticonId));
    }

    /**
     * 구매한 이모티콘 목록
     */
    @GetMapping("/purchased")
    public ApiResponse<PageResponse<EmoticonMasterDto>> getPurchasedEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomUserDetails authenticatedUser = requireAuthenticated(userDetails);
        return ApiResponse.success(new PageResponse<>(
                emoticonService.getPurchasedEmoticons(authenticatedUser.getUserId(), pageable(page, size))));
    }

    /**
     * 이모티콘 구매 여부 확인
     */
    @GetMapping("/{emoticonId}/purchased")
    public ApiResponse<EmoticonPurchaseStatusResponse> hasPurchased(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId) {
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        return ApiResponse.success(emoticonService.getPurchaseStatus(userId, emoticonId));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtils.of(page, size);
    }

    private CustomUserDetails requireAuthenticated(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails;
    }

    private String normalizeSortBy(String sortBy) {
        if ("popular".equalsIgnoreCase(sortBy)) {
            return "popular";
        }
        if ("oldest".equalsIgnoreCase(sortBy)) {
            return "oldest";
        }
        return "latest";
    }
}
