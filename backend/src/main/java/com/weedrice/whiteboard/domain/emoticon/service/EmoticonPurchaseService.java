package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.List;

class EmoticonPurchaseService {

    private static final String EMOTICON_ITEM_TYPE = "EMOTICON";

    private final ShopItemRepository shopItemRepository;
    private final ShopService shopService;
    private final EmoticonCatalogService catalogService;

    EmoticonPurchaseService(ShopItemRepository shopItemRepository,
                            ShopService shopService,
                            EmoticonCatalogService catalogService) {
        this.shopItemRepository = shopItemRepository;
        this.shopService = shopService;
        this.catalogService = catalogService;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        shopService.purchaseActiveItemByTarget(userId, EMOTICON_ITEM_TYPE, emoticonId);

        return catalogService.getEmoticonDetail(emoticonId, userId);
    }

    int getEmoticonPrice(Long emoticonId) {
        return getActiveEmoticonShopItem(emoticonId).getPrice();
    }

    private ShopItem getActiveEmoticonShopItem(Long emoticonId) {
        List<ShopItem> items = shopItemRepository.findByIsActiveAndItemTypeAndTargetId(
                true,
                EMOTICON_ITEM_TYPE,
                emoticonId);
        if (items.size() != 1) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        return items.get(0);
    }
}
