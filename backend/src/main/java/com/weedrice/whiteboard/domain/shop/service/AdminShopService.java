package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.shop.dto.AdminShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShopService {

    private static final int QUERY_MAX_LENGTH = 100;
    private static final int ITEM_TYPE_MAX_LENGTH = 50;
    private static final int REASON_MAX_LENGTH = 500;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("itemId"));
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "itemId", "itemName", "price", "itemType", "isActive", "isSaleEnabled", "modifiedAt");

    private final ShopItemRepository shopItemRepository;
    private final SuperAdminPolicy superAdminPolicy;
    private final UserReadableResolver userReadableResolver;
    private final ModerationAuditLogService moderationAuditLogService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public Page<AdminShopItemResponse> getItems(
            Long actorUserId,
            String query,
            String itemType,
            Boolean isActive,
            Boolean isSaleEnabled,
            Pageable pageable) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        Pageable effectivePageable = PageRequestUtils.of(
                pageable,
                20,
                DEFAULT_SORT,
                ALLOWED_SORT_PROPERTIES);
        return shopItemRepository.searchAdminItems(
                        normalizeQuery(query),
                        normalizeItemType(itemType),
                        isActive,
                        isSaleEnabled,
                        effectivePageable)
                .map(AdminShopItemResponse::from);
    }

    @Transactional
    public AdminShopItemResponse updateSaleStatus(
            Long actorUserId,
            Long itemId,
            boolean saleEnabled,
            String reason) {
        superAdminPolicy.requireUsableSuperAdmin(actorUserId);
        String normalizedReason = normalizeReason(reason);
        ShopItem item = shopItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (saleEnabled && item.getTargetId() == null) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        if (Boolean.valueOf(saleEnabled).equals(item.getIsSaleEnabled())) {
            return AdminShopItemResponse.from(item);
        }

        if (saleEnabled) {
            item.resumeSale();
        } else {
            item.suspendSale();
        }

        User actor = userReadableResolver.resolve(actorUserId);
        moderationAuditLogService.recordUserAction(
                actor,
                saleEnabled
                        ? ModerationAuditLogService.ACTION_SHOP_ITEM_SALE_RESUME
                        : ModerationAuditLogService.ACTION_SHOP_ITEM_SALE_SUSPEND,
                ModerationAuditLogService.TARGET_TYPE_SHOP_ITEM,
                item.getItemId(),
                null,
                normalizedReason);
        applicationEventPublisher.publishEvent(new ShopItemSaleStatusChangedEvent(
                item.getItemId(),
                item.getItemType(),
                item.getTargetId(),
                saleEnabled));
        return AdminShopItemResponse.from(item);
    }

    private String normalizeQuery(String query) {
        String normalized = TextInputNormalizer.normalizeNullable(query);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > QUERY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private String normalizeItemType(String itemType) {
        String normalized = TextInputNormalizer.normalizeNullable(itemType);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > ITEM_TYPE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeReason(String reason) {
        String normalized = TextInputNormalizer.normalizeNullable(reason);
        if (normalized == null || normalized.isBlank() || normalized.length() > REASON_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }
}
