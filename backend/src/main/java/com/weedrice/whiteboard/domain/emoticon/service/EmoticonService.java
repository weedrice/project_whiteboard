package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonPurchase;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmoticonService {

    private static final int EMOTICON_PRICE = 100; // 이모티콘 구매 가격

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonPurchaseRepository emoticonPurchaseRepository;
    private final UserRepository userRepository;
    private final PointService pointService;

    /**
     * 활성화된 이모티콘 목록 조회
     */
    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable) {
        return emoticonMasterRepository.findAllActive(pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 태그로 이모티콘 검색
     */
    public Page<EmoticonMasterDto> searchByTag(String tag, Pageable pageable) {
        return emoticonMasterRepository.findByTag(tag, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 키워드로 이모티콘 검색
     */
    public Page<EmoticonMasterDto> searchByKeyword(String keyword, Pageable pageable) {
        return emoticonMasterRepository.findByKeyword(keyword, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 내 이모티콘 목록 조회
     */
    public Page<EmoticonMasterDto> getMyEmoticons(Long userId, Pageable pageable) {
        return emoticonMasterRepository.findByCreatorId(userId, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 이모티콘 상세 조회 (이미지 포함)
     * 숨김 처리된 경우: 등록자 또는 구매자만 조회 가능
     */
    public EmoticonMasterDto getEmoticonDetail(Long emoticonId, Long userId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        // 숨김 처리된 경우, 등록자 또는 구매자만 접근 가능
        if (!"Y".equals(master.getIsActive()) && userId != null) {
            if (!master.isOwner(userId) && !emoticonMasterRepository.canUseEmoticon(userId, emoticonId)) {
                throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
            }
        } else if (!"Y".equals(master.getIsActive()) && userId == null) {
            throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
        }

        return EmoticonMasterDto.from(master);
    }

    /**
     * 이모티콘 생성
     */
    @Transactional
    public EmoticonMasterDto createEmoticon(Long userId, EmoticonCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        EmoticonMaster master = EmoticonMaster.builder()
                .name(request.getName())
                .thumbnailUrl(request.getThumbnailUrl())
                .tags(request.getTags())
                .creator(user)
                .build();

        emoticonMasterRepository.save(master);

        // 이미지 추가
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            AtomicInteger sortOrder = new AtomicInteger(0);
            request.getImageUrls().forEach(imageUrl -> {
                EmoticonImage image = EmoticonImage.builder()
                        .emoticonMaster(master)
                        .imageUrl(imageUrl)
                        .sortOrder(sortOrder.getAndIncrement())
                        .build();
                master.addImage(image);
            });
        }

        return EmoticonMasterDto.from(master);
    }

    /**
     * 이모티콘 수정
     */
    @Transactional
    public EmoticonMasterDto updateEmoticon(Long userId, Long emoticonId, EmoticonUpdateRequest request) {
        EmoticonMaster master = emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        // 소유자 확인
        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "수정 권한이 없습니다.");
        }

        master.update(
                request.getName() != null ? request.getName() : master.getName(),
                request.getThumbnailUrl() != null ? request.getThumbnailUrl() : master.getThumbnailUrl(),
                request.getTags() != null ? request.getTags() : master.getTags());

        return EmoticonMasterDto.from(master);
    }

    /**
     * 노비콘 숨김/표시 전환 (판매 중단 시 사용, 구매자는 계속 이용 가능)
     */
    @Transactional
    public EmoticonMasterDto toggleVisibility(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "숨김/표시 변경 권한이 없습니다.");
        }

        if ("Y".equals(master.getIsActive())) {
            master.deactivate();
        } else {
            master.activate();
        }

        return EmoticonMasterDto.from(master);
    }

    /**
     * 이모티콘 삭제
     */
    @Transactional
    public void deleteEmoticon(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        // 소유자 확인
        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다.");
        }

        emoticonMasterRepository.delete(master);
    }

    /**
     * 이미지 추가
     */
    @Transactional
    public EmoticonMasterDto addImage(Long userId, Long emoticonId, String imageUrl) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "수정 권한이 없습니다.");
        }

        int nextSortOrder = master.getImages().size();
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl(imageUrl)
                .sortOrder(nextSortOrder)
                .build();
        master.addImage(image);

        return EmoticonMasterDto.from(master);
    }

    /**
     * 이미지 삭제
     */
    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        EmoticonImage image = emoticonImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_IMAGE_NOT_FOUND));

        if (!image.getEmoticonMaster().isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다.");
        }

        emoticonImageRepository.delete(image);
    }

    /**
     * 이모티콘 목록 조회 (정렬 옵션 지원)
     * @param sortBy "latest" (등록순 오름차순) 또는 "popular" (판매순)
     */
    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable, String sortBy) {
        Page<EmoticonMaster> result;
        if ("popular".equalsIgnoreCase(sortBy)) {
            result = emoticonMasterRepository.findAllActiveOrderByPurchaseCount(pageable);
        } else {
            result = emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(pageable);
        }
        return result.map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 인기 이모티콘 조회 (일간/주간/월간)
     * @param period "daily", "weekly", "monthly"
     */
    public List<EmoticonMasterDto> getPopularEmoticons(String period) {
        LocalDateTime startDate;
        LocalDateTime now = LocalDateTime.now();

        switch (period.toLowerCase()) {
            case "weekly":
                startDate = now.minusDays(7);
                break;
            case "monthly":
                startDate = now.minusDays(30);
                break;
            case "daily":
            default:
                startDate = now.minusDays(1);
                break;
        }

        return emoticonMasterRepository.findPopularEmoticons(startDate, 5)
                .stream()
                .map(EmoticonMasterDto::fromWithoutImages)
                .collect(Collectors.toList());
    }

    /**
     * 통합 검색 (태그, 등록자명, 이모티콘 이름)
     * @param searchType ALL(전체), NAME(이름), CREATOR(등록자), TAG(태그)
     */
    public Page<EmoticonMasterDto> searchAll(String keyword, String searchType, Pageable pageable, String sortBy) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getActiveEmoticons(pageable, sortBy);
        }
        
        String trimmedKeyword = keyword.trim();
        Page<EmoticonMaster> result;
        boolean isPopular = "popular".equalsIgnoreCase(sortBy);

        switch (searchType.toUpperCase()) {
            case "NAME":
                result = isPopular 
                    ? emoticonMasterRepository.searchByNameOrderByPurchase(trimmedKeyword, pageable)
                    : emoticonMasterRepository.searchByName(trimmedKeyword, pageable);
                break;
            case "CREATOR":
                result = isPopular
                    ? emoticonMasterRepository.searchByCreatorOrderByPurchase(trimmedKeyword, pageable)
                    : emoticonMasterRepository.searchByCreator(trimmedKeyword, pageable);
                break;
            case "TAG":
                result = isPopular
                    ? emoticonMasterRepository.searchByTagOrderByPurchase(trimmedKeyword, pageable)
                    : emoticonMasterRepository.findByTag(trimmedKeyword, pageable);
                break;
            case "ALL":
            default:
                result = isPopular 
                    ? emoticonMasterRepository.searchByKeywordAllOrderByPurchase(trimmedKeyword, pageable)
                    : emoticonMasterRepository.searchByKeywordAll(trimmedKeyword, pageable);
                break;
        }
        return result.map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 이모티콘 구매
     * - ShopService.purchaseItem()과 동일한 패턴
     * - point_histories에 type='SPEND'로 기록
     * - emoticon_purchases에 구매 이력 저장
     */
    @Transactional
    public EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        EmoticonMaster emoticon = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        // 이미 구매한 이모티콘인지 확인
        if (emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(userId, emoticonId)) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        // 본인이 등록한 이모티콘인지 확인
        if (emoticon.isOwner(userId)) {
            throw new BusinessException(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN);
        }

        // 숨김 처리된 노비콘은 구매 불가
        if (!"Y".equals(emoticon.getIsActive())) {
            throw new BusinessException(ErrorCode.EMOTICON_HIDDEN);
        }

        // 포인트 차감 (포인트 부족 시 INSUFFICIENT_POINTS 예외 발생)
        pointService.spendPoint(userId, EMOTICON_PRICE, 
                "노비콘 구매: " + emoticon.getName(), emoticonId, "EMOTICON");

        // 구매 기록 저장 (PurchaseHistory와 동일한 구조)
        EmoticonPurchase purchase = EmoticonPurchase.builder()
                .user(user)
                .emoticon(emoticon)
                .purchasedPrice(EMOTICON_PRICE)
                .build();
        emoticonPurchaseRepository.save(purchase);

        // 이모티콘 구매 횟수 증가
        emoticon.incrementPurchaseCount();

        return EmoticonMasterDto.from(emoticon);
    }

    /**
     * 사용자가 사용 가능한 이모티콘 목록 (구매한 것 + 내가 등록한 것)
     */
    public Page<EmoticonMasterDto> getPurchasedEmoticons(Long userId, Pageable pageable) {
        return emoticonMasterRepository.findUsableEmoticons(userId, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    /**
     * 사용자가 해당 이모티콘을 사용할 수 있는지 확인 (구매했거나 본인이 등록한 경우)
     */
    public boolean hasPurchased(Long userId, Long emoticonId) {
        return emoticonMasterRepository.canUseEmoticon(userId, emoticonId);
    }

    /**
     * 이모티콘 가격 조회
     */
    public int getEmoticonPrice() {
        return EMOTICON_PRICE;
    }
}
