package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.shop.dto.AdminShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminShopServiceTest {

    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private SuperAdminPolicy superAdminPolicy;
    @Mock
    private UserReadableResolver userReadableResolver;
    @Mock
    private ModerationAuditLogService moderationAuditLogService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private User actor;

    private AdminShopService adminShopService;
    private ShopItem item;

    @BeforeEach
    void setUp() {
        adminShopService = new AdminShopService(
                shopItemRepository,
                superAdminPolicy,
                userReadableResolver,
                moderationAuditLogService,
                applicationEventPublisher);
        item = ShopItem.builder()
                .itemName("Premium emoticon")
                .price(100)
                .itemType("EMOTICON")
                .targetId(10L)
                .build();
        ReflectionTestUtils.setField(item, "itemId", 2L);
    }

    @Test
    void getItems_normalizesFiltersAndUsesSafeSort() {
        Pageable requested = PageRequest.of(0, 20, Sort.by("unsupported"));
        Pageable expected = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("itemId")));
        when(shopItemRepository.searchAdminItems(
                "premium", "EMOTICON", true, false, expected))
                .thenReturn(new PageImpl<>(List.of(item), expected, 1));

        var result = adminShopService.getItems(
                1L, " premium ", " emoticon ", true, false, requested);

        assertThat(result.getContent()).singleElement()
                .extracting(AdminShopItemResponse::getItemId)
                .isEqualTo(2L);
        verify(superAdminPolicy).requireUsableSuperAdmin(1L);
        verify(shopItemRepository).searchAdminItems(
                "premium", "EMOTICON", true, false, expected);
    }

    @Test
    void updateSaleStatus_suspendsSaleAndRecordsAuditAfterLock() {
        when(shopItemRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(item));
        when(userReadableResolver.resolve(1L)).thenReturn(actor);

        AdminShopItemResponse response = adminShopService.updateSaleStatus(
                1L, 2L, false, " temporary review ");

        assertThat(response.getIsSaleEnabled()).isFalse();
        assertThat(response.isPurchasable()).isFalse();
        InOrder order = inOrder(superAdminPolicy, shopItemRepository, userReadableResolver, moderationAuditLogService);
        order.verify(superAdminPolicy).requireUsableSuperAdmin(1L);
        order.verify(shopItemRepository).findByIdForUpdate(2L);
        order.verify(userReadableResolver).resolve(1L);
        order.verify(moderationAuditLogService).recordUserAction(
                actor,
                ModerationAuditLogService.ACTION_SHOP_ITEM_SALE_SUSPEND,
                ModerationAuditLogService.TARGET_TYPE_SHOP_ITEM,
                2L,
                null,
                "temporary review");
        verify(applicationEventPublisher).publishEvent(new ShopItemSaleStatusChangedEvent(
                2L, "EMOTICON", 10L, false));
    }

    @Test
    void updateSaleStatus_resumesSaleWithoutChangingSourceState() {
        item.suspendSale();
        item.deactivate();
        when(shopItemRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(item));
        when(userReadableResolver.resolve(1L)).thenReturn(actor);

        AdminShopItemResponse response = adminShopService.updateSaleStatus(1L, 2L, true, "review complete");

        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getIsSaleEnabled()).isTrue();
        assertThat(response.isPurchasable()).isFalse();
        verify(moderationAuditLogService).recordUserAction(
                actor,
                ModerationAuditLogService.ACTION_SHOP_ITEM_SALE_RESUME,
                ModerationAuditLogService.TARGET_TYPE_SHOP_ITEM,
                2L,
                null,
                "review complete");
        verify(applicationEventPublisher).publishEvent(new ShopItemSaleStatusChangedEvent(
                2L, "EMOTICON", 10L, true));
    }

    @Test
    void updateSaleStatus_sameStateIsIdempotentWithoutAudit() {
        when(shopItemRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(item));

        AdminShopItemResponse response = adminShopService.updateSaleStatus(1L, 2L, true, "no change");

        assertThat(response.getIsSaleEnabled()).isTrue();
        verifyNoInteractions(userReadableResolver, moderationAuditLogService, applicationEventPublisher);
    }

    @Test
    void updateSaleStatus_cannotResumeRetiredItemWithoutTarget() {
        item.retire();
        when(shopItemRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminShopService.updateSaleStatus(1L, 2L, true, "restore"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_AVAILABLE);

        verifyNoInteractions(userReadableResolver, moderationAuditLogService, applicationEventPublisher);
    }

    @Test
    void updateSaleStatus_rejectsBlankReasonBeforeLockingItem() {
        assertThatThrownBy(() -> adminShopService.updateSaleStatus(1L, 2L, false, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(shopItemRepository, never()).findByIdForUpdate(2L);
        verifyNoInteractions(userReadableResolver, moderationAuditLogService, applicationEventPublisher);
    }

    @Test
    void updateSaleStatus_doesNotAccessItemWhenActorIsRejected() {
        BusinessException forbidden = new BusinessException(ErrorCode.FORBIDDEN);
        org.mockito.Mockito.doThrow(forbidden)
                .when(superAdminPolicy)
                .requireUsableSuperAdmin(1L);

        assertThatThrownBy(() -> adminShopService.updateSaleStatus(1L, 2L, false, "reason"))
                .isSameAs(forbidden);

        verifyNoInteractions(
                shopItemRepository,
                userReadableResolver,
                moderationAuditLogService,
                applicationEventPublisher);
    }
}
