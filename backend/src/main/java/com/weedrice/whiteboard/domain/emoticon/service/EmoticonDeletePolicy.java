package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

class EmoticonDeletePolicy {

    private static final String PURCHASE_HISTORY_DELETE_BLOCKED_MESSAGE_KEY = "validation.emoticon.delete.purchaseHistory";

    private final EmoticonPurchaseRepository emoticonPurchaseRepository;

    EmoticonDeletePolicy(EmoticonPurchaseRepository emoticonPurchaseRepository) {
        this.emoticonPurchaseRepository = emoticonPurchaseRepository;
    }

    void validateDeletable(Long emoticonId) {
        if (emoticonPurchaseRepository.existsByEmoticon_EmoticonId(emoticonId)) {
            throw purchaseHistoryDeleteBlocked();
        }
    }

    BusinessException purchaseHistoryDeleteBlocked() {
        return BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, PURCHASE_HISTORY_DELETE_BLOCKED_MESSAGE_KEY);
    }
}
