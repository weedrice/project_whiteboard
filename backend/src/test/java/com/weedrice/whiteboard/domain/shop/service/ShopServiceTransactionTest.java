package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.PurchaseHistoryRepository;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import({
        QuerydslConfig.class,
        ShopService.class,
        PointService.class,
        ShopEntitlementCapabilityRegistry.class,
        UserReadableResolver.class,
        ShopServiceTransactionTest.TestDataService.class,
        ShopServiceTransactionTest.TestShopConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShopServiceTransactionTest {

    @Autowired
    private ShopService shopService;
    @Autowired
    private UserPointRepository userPointRepository;
    @Autowired
    private PointHistoryRepository pointHistoryRepository;
    @Autowired
    private ShopItemRepository shopItemRepository;
    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;
    @Autowired
    private TestDataService testDataService;

    @Test
    @DisplayName("grant 실패 시 포인트 차감과 구매 이력을 롤백한다")
    void purchaseItem_grantFailure_rollsBackPointSpendAndHistory() {
        SeedData seedData = testDataService.seed();

        assertThatThrownBy(() -> shopService.purchaseItem(seedData.userId(), seedData.itemId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMOTICON_ALREADY_PURCHASED);

        assertThat(userPointRepository.findByUserId(seedData.userId()))
                .get()
                .extracting(UserPoint::getCurrentPoint)
                .isEqualTo(500);
        assertThat(pointHistoryRepository.findAll()).isEmpty();
        assertThat(purchaseHistoryRepository.findAll()).isEmpty();
    }

    private record SeedData(Long userId, Long itemId) {
    }

    static class TestDataService {

        private final UserRepository userRepository;
        private final UserPointRepository userPointRepository;
        private final ShopItemRepository shopItemRepository;

        TestDataService(
                UserRepository userRepository,
                UserPointRepository userPointRepository,
                ShopItemRepository shopItemRepository) {
            this.userRepository = userRepository;
            this.userPointRepository = userPointRepository;
            this.shopItemRepository = shopItemRepository;
        }

        @Transactional
        SeedData seed() {
            User user = userRepository.save(User.builder()
                    .loginId("rollback-user")
                    .email("rollback@test.com")
                    .password("password")
                    .displayName("Rollback User")
                    .build());
            UserPoint userPoint = UserPoint.builder()
                    .user(user)
                    .build();
            userPoint.addPoint(500);
            userPointRepository.save(userPoint);
            ShopItem item = shopItemRepository.save(ShopItem.builder()
                    .itemName("Rollback emoticon")
                    .description("Rollback item")
                    .price(100)
                    .itemType("EMOTICON")
                    .targetId(10L)
                    .imageUrl("https://example.com/item.png")
                    .build());
            return new SeedData(user.getUserId(), item.getItemId());
        }
    }

    @TestConfiguration
    static class TestShopConfig {

        @Bean
        ShopEntitlementHandler failingShopEntitlementHandler() {
            return new FailingShopEntitlementHandler();
        }

        @Bean
        SanctionService sanctionService() {
            return mock(SanctionService.class);
        }
    }

    private static class FailingShopEntitlementHandler implements ShopEntitlementHandler {

        @Override
        public Set<String> getSupportedItemTypes() {
            return Set.of("EMOTICON");
        }

        @Override
        public void validateConfiguration(ShopItem item) {
        }

        @Override
        public PurchasePreparation preparePurchase(Long userId, ShopItem item) {
            return TestPurchasePreparation.INSTANCE;
        }

        @Override
        public void grant(PurchasePreparation preparation) {
            throw new BusinessException(ErrorCode.EMOTICON_ALREADY_PURCHASED);
        }
    }

    private enum TestPurchasePreparation implements ShopEntitlementHandler.PurchasePreparation {
        INSTANCE
    }
}
