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
import com.weedrice.whiteboard.domain.file.service.FileService;
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

    private static final int EMOTICON_PRICE = 100;
    private static final String EMOTICON_THUMBNAIL = "EMOTICON_THUMBNAIL";
    private static final String EMOTICON_IMAGE = "EMOTICON_IMAGE";

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonPurchaseRepository emoticonPurchaseRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final FileService fileService;

    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable) {
        return emoticonMasterRepository.findAllActive(pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    public Page<EmoticonMasterDto> searchByTag(String tag, Pageable pageable) {
        return emoticonMasterRepository.findByTag(tag, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    public Page<EmoticonMasterDto> searchByKeyword(String keyword, Pageable pageable) {
        return emoticonMasterRepository.findByKeyword(keyword, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    public Page<EmoticonMasterDto> getMyEmoticons(Long userId, Pageable pageable) {
        return emoticonMasterRepository.findByCreatorId(userId, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    public EmoticonMasterDto getEmoticonDetail(Long emoticonId, Long userId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!"Y".equals(master.getIsActive()) && userId != null) {
            if (!master.isOwner(userId) && !emoticonMasterRepository.canUseEmoticon(userId, emoticonId)) {
                throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
            }
        } else if (!"Y".equals(master.getIsActive()) && userId == null) {
            throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
        }

        return EmoticonMasterDto.from(master);
    }

    @Transactional
    public EmoticonMasterDto createEmoticon(Long userId, EmoticonCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        EmoticonMaster master = EmoticonMaster.builder()
                .name(request.getName())
                .thumbnailUrl(null)
                .tags(request.getTags())
                .creator(user)
                .build();

        emoticonMasterRepository.save(master);

        if (request.getThumbnailFileId() != null) {
            master.update(
                    master.getName(),
                    attachFile(request.getThumbnailFileId(), userId, master.getEmoticonId(), EMOTICON_THUMBNAIL),
                    master.getTags());
        }

        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            AtomicInteger sortOrder = new AtomicInteger(0);
            request.getImageFileIds().forEach(fileId -> {
                EmoticonImage image = EmoticonImage.builder()
                        .emoticonMaster(master)
                        .imageUrl(attachFile(fileId, userId, master.getEmoticonId(), EMOTICON_IMAGE))
                        .sortOrder(sortOrder.getAndIncrement())
                        .build();
                master.addImage(image);
            });
        }

        return EmoticonMasterDto.from(master);
    }

    @Transactional
    public EmoticonMasterDto updateEmoticon(Long userId, Long emoticonId, EmoticonUpdateRequest request) {
        EmoticonMaster master = emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "수정 권한이 없습니다.");
        }

        master.update(
                request.getName() != null ? request.getName() : master.getName(),
                request.getThumbnailFileId() != null
                        ? attachFile(request.getThumbnailFileId(), userId, master.getEmoticonId(), EMOTICON_THUMBNAIL)
                        : master.getThumbnailUrl(),
                request.getTags() != null ? request.getTags() : master.getTags());

        return EmoticonMasterDto.from(master);
    }

    @Transactional
    public EmoticonMasterDto toggleVisibility(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "공개 여부를 변경할 권한이 없습니다.");
        }

        if ("Y".equals(master.getIsActive())) {
            master.deactivate();
        } else {
            master.activate();
        }

        return EmoticonMasterDto.from(master);
    }

    @Transactional
    public void deleteEmoticon(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다.");
        }

        if (master.getThumbnailUrl() != null) {
            Long fileId = FileService.extractFileIdFromUrl(master.getThumbnailUrl());
            if (fileId != null) {
                fileService.deleteFileWithStorageIfAssociated(fileId, master.getEmoticonId(), EMOTICON_THUMBNAIL);
            }
        }

        if (master.getImages() != null) {
            for (EmoticonImage image : master.getImages()) {
                Long fileId = FileService.extractFileIdFromUrl(image.getImageUrl());
                if (fileId != null) {
                    fileService.deleteFileWithStorageIfAssociated(fileId, master.getEmoticonId(), EMOTICON_IMAGE);
                }
            }
        }

        emoticonMasterRepository.delete(master);
    }

    @Transactional
    public EmoticonMasterDto addImage(Long userId, Long emoticonId, Long fileId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "수정 권한이 없습니다.");
        }

        int nextSortOrder = master.getImages().size();
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl(attachFile(fileId, userId, master.getEmoticonId(), EMOTICON_IMAGE))
                .sortOrder(nextSortOrder)
                .build();
        master.addImage(image);

        return EmoticonMasterDto.from(master);
    }

    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        EmoticonImage image = emoticonImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_IMAGE_NOT_FOUND));

        if (!image.getEmoticonMaster().isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다.");
        }

        Long fileId = FileService.extractFileIdFromUrl(image.getImageUrl());
        if (fileId != null) {
            fileService.deleteFileWithStorageIfAssociated(
                    fileId,
                    image.getEmoticonMaster().getEmoticonId(),
                    EMOTICON_IMAGE);
        }

        emoticonImageRepository.delete(image);
    }

    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable, String sortBy) {
        Page<EmoticonMaster> result;
        if ("popular".equalsIgnoreCase(sortBy)) {
            result = emoticonMasterRepository.findAllActiveOrderByPurchaseCount(pageable);
        } else {
            result = emoticonMasterRepository.findAllActiveOrderByCreatedAtAsc(pageable);
        }
        return result.map(EmoticonMasterDto::fromWithoutImages);
    }

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

    @Transactional
    public EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        EmoticonMaster emoticon = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(userId, emoticonId)) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        if (emoticon.isOwner(userId)) {
            throw new BusinessException(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN);
        }

        if (!"Y".equals(emoticon.getIsActive())) {
            throw new BusinessException(ErrorCode.EMOTICON_HIDDEN);
        }

        pointService.spendPoint(userId, EMOTICON_PRICE,
                "이모티콘 구매: " + emoticon.getName(), emoticonId, "EMOTICON");

        EmoticonPurchase purchase = EmoticonPurchase.builder()
                .user(user)
                .emoticon(emoticon)
                .purchasedPrice(EMOTICON_PRICE)
                .build();
        emoticonPurchaseRepository.save(purchase);

        emoticon.incrementPurchaseCount();

        return EmoticonMasterDto.from(emoticon);
    }

    public Page<EmoticonMasterDto> getPurchasedEmoticons(Long userId, Pageable pageable) {
        return emoticonMasterRepository.findUsableEmoticons(userId, pageable)
                .map(EmoticonMasterDto::fromWithoutImages);
    }

    public boolean hasPurchased(Long userId, Long emoticonId) {
        return emoticonMasterRepository.canUseEmoticon(userId, emoticonId);
    }

    public int getEmoticonPrice() {
        return EMOTICON_PRICE;
    }

    private String attachFile(Long fileId, Long userId, Long emoticonId, String relatedType) {
        fileService.associateFileWithEntity(fileId, userId, emoticonId, relatedType);
        return "/api/v1/files/" + fileId;
    }
}
