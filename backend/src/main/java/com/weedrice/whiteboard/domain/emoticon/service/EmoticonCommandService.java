package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.concurrent.atomic.AtomicInteger;

class EmoticonCommandService {

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonImageRepository emoticonImageRepository;
    private final UserRepository userRepository;
    private final EmoticonAttachmentHelper attachmentHelper;
    private final String emoticonThumbnailType;
    private final String emoticonImageType;

    EmoticonCommandService(EmoticonMasterRepository emoticonMasterRepository,
                           EmoticonImageRepository emoticonImageRepository,
                           UserRepository userRepository,
                           EmoticonAttachmentHelper attachmentHelper,
                           String emoticonThumbnailType,
                           String emoticonImageType) {
        this.emoticonMasterRepository = emoticonMasterRepository;
        this.emoticonImageRepository = emoticonImageRepository;
        this.userRepository = userRepository;
        this.attachmentHelper = attachmentHelper;
        this.emoticonThumbnailType = emoticonThumbnailType;
        this.emoticonImageType = emoticonImageType;
    }

    EmoticonMasterDto createEmoticon(Long userId, EmoticonCreateRequest request) {
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
                    attachmentHelper.attachFile(
                            request.getThumbnailFileId(),
                            userId,
                            master.getEmoticonId(),
                            emoticonThumbnailType),
                    master.getTags());
        }

        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            AtomicInteger sortOrder = new AtomicInteger(0);
            request.getImageFileIds().forEach(fileId -> {
                EmoticonImage image = EmoticonImage.builder()
                        .emoticonMaster(master)
                        .imageUrl(attachmentHelper.attachFile(fileId, userId, master.getEmoticonId(), emoticonImageType))
                        .sortOrder(sortOrder.getAndIncrement())
                        .build();
                master.addImage(image);
            });
        }

        return EmoticonMasterDto.from(master);
    }

    EmoticonMasterDto updateEmoticon(Long userId, Long emoticonId, EmoticonUpdateRequest request) {
        EmoticonMaster master = emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateOwner(master, userId, "?섏젙 沅뚰븳???놁뒿?덈떎.");

        master.update(
                request.getName() != null ? request.getName() : master.getName(),
                request.getThumbnailFileId() != null
                        ? attachmentHelper.attachFile(
                                request.getThumbnailFileId(),
                                userId,
                                master.getEmoticonId(),
                                emoticonThumbnailType)
                        : master.getThumbnailUrl(),
                request.getTags() != null ? request.getTags() : master.getTags());

        return EmoticonMasterDto.from(master);
    }

    EmoticonMasterDto toggleVisibility(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateOwner(master, userId, "怨듦컻 ?щ?瑜?蹂寃쏀븷 沅뚰븳???놁뒿?덈떎.");

        if ("Y".equals(master.getIsActive())) {
            master.deactivate();
        } else {
            master.activate();
        }

        return EmoticonMasterDto.from(master);
    }

    void deleteEmoticon(Long userId, Long emoticonId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateOwner(master, userId, "??젣 沅뚰븳???놁뒿?덈떎.");

        attachmentHelper.deleteAssociatedFile(master.getThumbnailUrl(), master.getEmoticonId(), emoticonThumbnailType);

        if (master.getImages() != null) {
            for (EmoticonImage image : master.getImages()) {
                attachmentHelper.deleteAssociatedFile(image.getImageUrl(), master.getEmoticonId(), emoticonImageType);
            }
        }

        emoticonMasterRepository.delete(master);
    }

    EmoticonMasterDto addImage(Long userId, Long emoticonId, Long fileId) {
        EmoticonMaster master = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validateOwner(master, userId, "?섏젙 沅뚰븳???놁뒿?덈떎.");

        int nextSortOrder = master.getImages().size();
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

        validateOwner(image.getEmoticonMaster(), userId, "??젣 沅뚰븳???놁뒿?덈떎.");
        attachmentHelper.deleteAssociatedFile(
                image.getImageUrl(),
                image.getEmoticonMaster().getEmoticonId(),
                emoticonImageType);

        emoticonImageRepository.delete(image);
    }

    private void validateOwner(EmoticonMaster master, Long userId, String message) {
        if (!master.isOwner(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
