package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonPurchase;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

class EmoticonPurchaseService {

    private final EmoticonMasterRepository emoticonMasterRepository;
    private final EmoticonPurchaseRepository emoticonPurchaseRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final int emoticonPrice;

    EmoticonPurchaseService(EmoticonMasterRepository emoticonMasterRepository,
                            EmoticonPurchaseRepository emoticonPurchaseRepository,
                            UserRepository userRepository,
                            PointService pointService,
                            int emoticonPrice) {
        this.emoticonMasterRepository = emoticonMasterRepository;
        this.emoticonPurchaseRepository = emoticonPurchaseRepository;
        this.userRepository = userRepository;
        this.pointService = pointService;
        this.emoticonPrice = emoticonPrice;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        EmoticonMaster emoticon = emoticonMasterRepository.findByIdWithImages(emoticonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

        if (emoticon.isOwner(userId)) {
            throw new BusinessException(ErrorCode.EMOTICON_CANNOT_PURCHASE_OWN);
        }

        if (!"Y".equals(emoticon.getIsActive())) {
            throw new BusinessException(ErrorCode.EMOTICON_HIDDEN);
        }

        EmoticonPurchase purchase = EmoticonPurchase.builder()
                .user(user)
                .emoticon(emoticon)
                .purchasedPrice(emoticonPrice)
                .build();
        try {
            emoticonPurchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }

        pointService.spendPoint(userId, emoticonPrice,
                "?대え?곗퐯 援щℓ: " + emoticon.getName(), emoticonId, "EMOTICON");

        emoticon.incrementPurchaseCount();

        return EmoticonMasterDto.from(emoticon);
    }
}
