package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonPurchaseStatusResponse;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmoticonService {

    private final EmoticonCatalogService catalogService;
    private final EmoticonCommandService commandService;
    private final EmoticonPurchaseService purchaseService;

    public EmoticonService(EmoticonCatalogService catalogService,
                           EmoticonCommandService commandService,
                           EmoticonPurchaseService purchaseService) {
        this.catalogService = catalogService;
        this.commandService = commandService;
        this.purchaseService = purchaseService;
    }

    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable) {
        return catalogService.getActiveEmoticons(pageable);
    }

    public Page<EmoticonMasterDto> searchByTag(String tag, Pageable pageable) {
        return catalogService.searchByTag(tag, pageable);
    }

    public Page<EmoticonMasterDto> searchByKeyword(String keyword, Pageable pageable) {
        return catalogService.searchByKeyword(keyword, pageable);
    }

    public Page<EmoticonMasterDto> getMyEmoticons(Long userId, Pageable pageable) {
        return catalogService.getMyEmoticons(userId, pageable);
    }

    public EmoticonMasterDto getEmoticonDetail(Long emoticonId, Long userId) {
        return catalogService.getEmoticonDetail(emoticonId, userId);
    }

    @Transactional
    public EmoticonMasterDto createEmoticon(Long userId, EmoticonCreateRequest request) {
        return commandService.createEmoticon(userId, request);
    }

    @Transactional
    public EmoticonMasterDto updateEmoticon(Long userId, Long emoticonId, EmoticonUpdateRequest request) {
        return commandService.updateEmoticon(userId, emoticonId, request);
    }

    @Transactional
    public EmoticonMasterDto toggleVisibility(Long userId, Long emoticonId) {
        return commandService.toggleVisibility(userId, emoticonId);
    }

    @Transactional
    public void deleteEmoticon(Long userId, Long emoticonId) {
        commandService.deleteEmoticon(userId, emoticonId);
    }

    @Transactional
    public EmoticonMasterDto addImage(Long userId, Long emoticonId, Long fileId) {
        return commandService.addImage(userId, emoticonId, fileId);
    }

    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        commandService.deleteImage(userId, imageId);
    }

    public Page<EmoticonMasterDto> getActiveEmoticons(Pageable pageable, String sortBy) {
        return catalogService.getActiveEmoticons(pageable, sortBy);
    }

    public List<EmoticonMasterDto> getPopularEmoticons(String period) {
        return catalogService.getPopularEmoticons(period);
    }

    public Page<EmoticonMasterDto> searchAll(String keyword, String searchType, Pageable pageable, String sortBy) {
        return catalogService.searchAll(keyword, searchType, pageable, sortBy);
    }

    @Transactional
    public EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        return purchaseService.purchaseEmoticon(userId, emoticonId);
    }

    public Page<EmoticonMasterDto> getPurchasedEmoticons(Long userId, Pageable pageable) {
        return catalogService.getPurchasedEmoticons(userId, pageable);
    }

    public boolean hasPurchased(Long userId, Long emoticonId) {
        return catalogService.hasPurchased(userId, emoticonId);
    }

    public int getEmoticonPrice(Long emoticonId) {
        return purchaseService.getEmoticonPrice(emoticonId);
    }

    public EmoticonPurchaseStatusResponse getPurchaseStatus(Long userId, Long emoticonId) {
        boolean purchased = userId != null && catalogService.hasPurchased(userId, emoticonId);
        EmoticonShopItemLifecycleService.PurchaseOffer offer = purchaseService.getPurchaseOffer(emoticonId);
        return EmoticonPurchaseStatusResponse.builder()
                .purchased(purchased)
                .available(offer.available())
                .price(offer.price())
                .build();
    }
}
