package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

class EmoticonDeletePolicy {

    private static final String PURCHASE_HISTORY_DELETE_BLOCKED_MESSAGE = "구매 이력이 있는 이모티콘은 삭제할 수 없습니다.";

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
        return new BusinessException(ErrorCode.VALIDATION_ERROR, PURCHASE_HISTORY_DELETE_BLOCKED_MESSAGE);
    }
}
