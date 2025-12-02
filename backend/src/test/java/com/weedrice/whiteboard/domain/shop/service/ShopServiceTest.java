package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.PurchaseHistoryRepository;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PointService pointService;

    @InjectMocks
    private ShopService shopService;

    private User user;
    private ShopItem shopItem;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        shopItem = ShopItem.builder()
                .itemName("Test Item")
                .price(100)
                .build();
    }

    @Test
    @DisplayName("아이템 구매 성공")
    void purchaseItem_success() {
        // given
        Long userId = 1L;
        Long itemId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(shopItem));
        when(purchaseHistoryRepository.save(any(PurchaseHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PurchaseHistory purchaseHistory = shopService.purchaseItem(userId, itemId);

        // then
        verify(pointService).forceSubtractPoint(anyLong(), any(Integer.class), anyString(), anyLong(), anyString());
        assertThat(purchaseHistory.getItem().getItemName()).isEqualTo("Test Item");
        assertThat(purchaseHistory.getPurchasedPrice()).isEqualTo(100);
    }
}
