package com.weedrice.whiteboard.domain.emoticon.controller;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.service.EmoticonService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/emoticons")
@RequiredArgsConstructor
public class EmoticonController {

    private final EmoticonService emoticonService;

    /**
     * 이모티콘 목록 조회
     * @param sortBy "latest" (등록순 오름차순) 또는 "popular" (판매순)
     */
    @GetMapping
    public ApiResponse<Page<EmoticonMasterDto>> getEmoticons(
            @RequestParam(defaultValue = "latest") String sortBy,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.getActiveEmoticons(pageable, sortBy));
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
    public ApiResponse<Page<EmoticonMasterDto>> searchAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String searchType,
            @RequestParam(defaultValue = "latest") String sortBy,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.searchAll(keyword, searchType, pageable, sortBy));
    }

    /**
     * 태그로 이모티콘 검색
     */
    @GetMapping("/search/tag")
    public ApiResponse<Page<EmoticonMasterDto>> searchByTag(
            @RequestParam String tag,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.searchByTag(tag, pageable));
    }

    /**
     * 키워드로 이모티콘 검색
     */
    @GetMapping("/search")
    public ApiResponse<Page<EmoticonMasterDto>> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.searchByKeyword(keyword, pageable));
    }

    /**
     * 내 이모티콘 목록
     */
    @GetMapping("/my")
    public ApiResponse<Page<EmoticonMasterDto>> getMyEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.getMyEmoticons(userDetails.getUserId(), pageable));
    }

    /**
     * 이모티콘 상세 조회
     */
    @GetMapping("/{emoticonId}")
    public ApiResponse<EmoticonMasterDto> getEmoticonDetail(@PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.getEmoticonDetail(emoticonId));
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
            @RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");
        return ApiResponse.success(emoticonService.addImage(userDetails.getUserId(), emoticonId, imageUrl));
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
    public ApiResponse<Page<EmoticonMasterDto>> getPurchasedEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(emoticonService.getPurchasedEmoticons(userDetails.getUserId(), pageable));
    }

    /**
     * 이모티콘 구매 여부 확인
     */
    @GetMapping("/{emoticonId}/purchased")
    public ApiResponse<Map<String, Object>> hasPurchased(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId) {
        boolean purchased = userDetails != null && 
                emoticonService.hasPurchased(userDetails.getUserId(), emoticonId);
        return ApiResponse.success(Map.of(
                "purchased", purchased,
                "price", emoticonService.getEmoticonPrice()
        ));
    }
}
