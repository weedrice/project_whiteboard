package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;

class EmoticonPurchaseService {

    private final EmoticonEntitlementGrantService emoticonEntitlementGrantService;
    private final PointService pointService;
    private final SanctionService sanctionService;
    private final int emoticonPrice;

    EmoticonPurchaseService(EmoticonEntitlementGrantService emoticonEntitlementGrantService,
                            PointService pointService,
                            SanctionService sanctionService,
                            int emoticonPrice) {
        this.emoticonEntitlementGrantService = emoticonEntitlementGrantService;
        this.pointService = pointService;
        this.sanctionService = sanctionService;
        this.emoticonPrice = emoticonPrice;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        EmoticonEntitlementGrantService.EmoticonGrantContext grantContext =
                emoticonEntitlementGrantService.prepareGrant(userId, emoticonId);
        sanctionService.validateNotBanned(grantContext.user());

        pointService.spendPoint(userId, emoticonPrice,
                "Emoticon purchase: " + grantContext.emoticon().getName(), emoticonId, "EMOTICON");

        return EmoticonMasterDto.from(emoticonEntitlementGrantService.grant(grantContext, emoticonPrice));
    }
}
