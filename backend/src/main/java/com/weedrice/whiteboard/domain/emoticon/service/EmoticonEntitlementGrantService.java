package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonPurchase;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

class EmoticonEntitlementGrantService {

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonPurchaseRepository emoticonPurchaseRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    EmoticonEntitlementGrantService(EmoticonMasterRepository emoticonMasterRepository,
                                    EmoticonPurchaseRepository emoticonPurchaseRepository,
                                    UserRepository userRepository,
                                    EntityManager entityManager) {
        this.emoticonMasterRepository = emoticonMasterRepository;
        this.emoticonPurchaseRepository = emoticonPurchaseRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    EmoticonGrantContext prepareGrant(Long userId, Long emoticonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        EmoticonMaster emoticon = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        validatePurchasable(userId, emoticon);
        if (emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(user.getUserId(), emoticonId)) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        return new EmoticonGrantContext(user, emoticon);
    }

    EmoticonGrantContext prepareConfiguredGrant(Long userId, Long emoticonId, Runnable afterConfigurationValidation) {
        EmoticonMaster emoticon = resolveConfiguredTargetWithImages(emoticonId);
        afterConfigurationValidation.run();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validatePurchasable(userId, emoticon);
        if (emoticonPurchaseRepository.existsByUser_UserIdAndEmoticon_EmoticonId(user.getUserId(), emoticonId)) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        return new EmoticonGrantContext(user, emoticon);
    }

    void validateTargetConfiguration(Long emoticonId) {
        validateConfiguredTarget(resolveConfiguredTarget(emoticonId));
    }

    @Transactional
    EmoticonMaster grant(EmoticonGrantContext grantContext, int purchasedPrice) {
        int updatedCount = emoticonMasterRepository.incrementPurchaseCount(grantContext.emoticon().getEmoticonId());
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.EMOTICON_NOT_FOUND);
        }

        EmoticonPurchase purchase = EmoticonPurchase.builder()
                .user(grantContext.user())
                .emoticon(grantContext.emoticon())
                .purchasedPrice(purchasedPrice)
                .build();

        try {
            emoticonPurchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        entityManager.refresh(grantContext.emoticon());
        return grantContext.emoticon();
    }

    private void validatePurchasable(Long userId, EmoticonMaster emoticon) {
        if (emoticon.isOwner(userId)) {
            throw new BusinessException(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN);
        }

        if (!"Y".equals(emoticon.getIsActive())) {
            throw new BusinessException(ErrorCode.EMOTICON_HIDDEN);
        }
    }

    private EmoticonMaster resolveConfiguredTargetWithImages(Long emoticonId) {
        if (emoticonId == null) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        EmoticonMaster emoticon = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
        validateConfiguredTarget(emoticon);
        return emoticon;
    }

    private EmoticonMaster resolveConfiguredTarget(Long emoticonId) {
        if (emoticonId == null) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        return emoticonMasterRepository.findById(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
    }

    private void validateConfiguredTarget(EmoticonMaster emoticon) {
        if (!"Y".equals(emoticon.getIsActive())) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
    }

    record EmoticonGrantContext(User user, EmoticonMaster emoticon) {
    }
}
