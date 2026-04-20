package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.point.service.PointService;

class EmoticonPurchaseService {

    private final EmoticonEntitlementGrantService emoticonEntitlementGrantService;
    private final PointService pointService;
    private final int emoticonPrice;

    EmoticonPurchaseService(EmoticonEntitlementGrantService emoticonEntitlementGrantService,
                            PointService pointService,
                            int emoticonPrice) {
        this.emoticonEntitlementGrantService = emoticonEntitlementGrantService;
        this.pointService = pointService;
        this.emoticonPrice = emoticonPrice;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        EmoticonEntitlementGrantService.EmoticonGrantContext grantContext =
                emoticonEntitlementGrantService.prepareGrant(userId, emoticonId);

        pointService.spendPoint(userId, emoticonPrice,
                "Emoticon purchase: " + grantContext.emoticon().getName(), emoticonId, "EMOTICON");

        return EmoticonMasterDto.from(emoticonEntitlementGrantService.grant(grantContext, emoticonPrice));
    }
}
