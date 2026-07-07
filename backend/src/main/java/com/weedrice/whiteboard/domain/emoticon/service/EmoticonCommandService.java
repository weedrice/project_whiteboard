package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class EmoticonCommandService {

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonImageRepository emoticonImageRepository;
    private final UserWritableResolver userWritableResolver;
    private final EmoticonAttachmentHelper attachmentHelper;
    private final EmoticonDeletePolicy deletePolicy;
    private final String emoticonThumbnailType;
    private final String emoticonImageType;

    EmoticonCommandService(EmoticonMasterRepository emoticonMasterRepository,
                            EmoticonImageRepository emoticonImageRepository,
                            UserWritableResolver userWritableResolver,
                            EmoticonAttachmentHelper attachmentHelper,
                            EmoticonDeletePolicy deletePolicy,
                            String emoticonThumbnailType,
                            String emoticonImageType) {
        this.emoticonMasterRepository = emoticonMasterRepository;
        this.emoticonImageRepository = emoticonImageRepository;
        this.userWritableResolver = userWritableResolver;
        this.attachmentHelper = attachmentHelper;
        this.deletePolicy = deletePolicy;
        this.emoticonThumbnailType = emoticonThumbnailType;
        this.emoticonImageType = emoticonImageType;
    }

    EmoticonMasterDto createEmoticon(Long userId, EmoticonCreateRequest request) {
        validateDistinctImageFileIds(request.getImageFileIds());
        User user = userWritableResolver.resolve(userId);

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
                    attachmentHelper.attachFile(
                            request.getThumbnailFileId(),
                            userId,
                            master.getEmoticonId(),
                            emoticonThumbnailType),
                    master.getTags());
        }

        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            List<String> imageUrls = attachmentHelper.attachFiles(
                    request.getImageFileIds(),
                    userId,
                    master.getEmoticonId(),
                    emoticonImageType);
            for (int sortOrder = 0; sortOrder < imageUrls.size(); sortOrder++) {
                EmoticonImage image = EmoticonImage.builder()
                        .emoticonMaster(master)
                        .imageUrl(imageUrls.get(sortOrder))
                        .sortOrder(sortOrder)
                        .build();
                master.addImage(image);
            }
        }

        return EmoticonMasterDto.from(master);
    }

    private void validateDistinctImageFileIds(List<Long> imageFileIds) {
        if (imageFileIds == null || imageFileIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueImageFileIds = new HashSet<>();
        for (Long imageFileId : imageFileIds) {
            if (!uniqueImageFileIds.add(imageFileId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    EmoticonMasterDto updateEmoticon(Long userId, Long emoticonId, EmoticonUpdateRequest request) {
        EmoticonMaster master = emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateWritableOwner(master, userId);

        String thumbnailUrl = master.getThumbnailUrl();
        if (request.getThumbnailFileId() != null) {
            thumbnailUrl = attachmentHelper.attachFile(
                    request.getThumbnailFileId(),
                    userId,
                    master.getEmoticonId(),
                    emoticonThumbnailType);
            attachmentHelper.deleteAssociatedFileIfDifferent(
                    master.getThumbnailUrl(),
                    request.getThumbnailFileId(),
                    master.getEmoticonId(),
                    emoticonThumbnailType);
        }

        master.update(
                normalizeUpdateName(request.getName(), master.getName()),
                thumbnailUrl,
                request.getTags() != null ? request.getTags() : master.getTags());

        return EmoticonMasterDto.from(master);
    }

    private String normalizeUpdateName(String requestedName, String currentName) {
        if (requestedName == null) {
            return currentName;
        }
        String normalizedName = requestedName.trim();
        if (normalizedName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedName;
    }

    EmoticonMasterDto toggleVisibility(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateWritableOwner(master, userId);

        if ("Y".equals(master.getIsActive())) {
            master.deactivate();
        } else {
            master.activate();
        }

        return EmoticonMasterDto.from(master);
    }

    void deleteEmoticon(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdForUpdate(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateWritableOwner(master, userId);

        deletePolicy.validateDeletable(emoticonId);

        Long masterId = master.getEmoticonId();
        String thumbnailUrl = master.getThumbnailUrl();
        List<String> imageUrls = master.getImages() == null
                ? List.of()
                : master.getImages().stream()
                        .map(EmoticonImage::getImageUrl)
                        .toList();

        try {
            emoticonMasterRepository.delete(master);
            emoticonMasterRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw deletePolicy.purchaseHistoryDeleteBlocked();
        }

        attachmentHelper.deleteAssociatedFile(thumbnailUrl, masterId, emoticonThumbnailType);

        for (String imageUrl : imageUrls) {
            attachmentHelper.deleteAssociatedFile(imageUrl, masterId, emoticonImageType);
        }
    }

    EmoticonMasterDto addImage(Long userId, Long emoticonId, Long fileId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdForUpdate(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateWritableOwner(master, userId);
        validateImageLimit(master);

        int nextSortOrder = resolveNextSortOrder(master);
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl(attachmentHelper.attachFile(fileId, userId, master.getEmoticonId(), emoticonImageType))
                .sortOrder(nextSortOrder)
                .build();
        master.addImage(image);

        return EmoticonMasterDto.from(master);
    }

    void deleteImage(Long userId, Long imageId) {
        EmoticonImage image = emoticonImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_IMAGE_NOT_FOUND));

        validateWritableOwner(image.getEmoticonMaster(), userId);
        attachmentHelper.deleteAssociatedFile(
                image.getImageUrl(),
                image.getEmoticonMaster().getEmoticonId(),
                emoticonImageType);

        emoticonImageRepository.delete(image);
    }

    private void validateOwner(EmoticonMaster master, Long userId) {
        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateWritableOwner(EmoticonMaster master, Long userId) {
        validateOwner(master, userId);
        userWritableResolver.resolve(userId);
    }

    private void validateImageLimit(EmoticonMaster master) {
        int imageCount = master.getImages() == null ? 0 : master.getImages().size();
        if (imageCount >= FileAssociationConstraints.MAX_EMOTICON_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private int resolveNextSortOrder(EmoticonMaster master) {
        return master.getImages().stream()
                .map(EmoticonImage::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(-1) + 1;
    }
}
