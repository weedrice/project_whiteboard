package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PurchaseHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    private User user;
    private ShopItem item;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("shop-user")
                .email("shop-user@test.com")
                .password("password")
                .displayName("Shop User")
                .build();
        entityManager.persist(user);

        item = ShopItem.builder()
                .itemName("Premium item")
                .description("Shop item")
                .price(100)
                .itemType("EMOTICON")
                .targetId(10L)
                .imageUrl("https://example.com/item.png")
                .build();
        entityManager.persist(item);
    }

    @Test
    @DisplayName("구매 이력 조회는 생성 시각이 같으면 purchaseId 내림차순으로 정렬한다")
    void findByUserOrderByCreatedAtDescPurchaseIdDesc_ordersByPurchaseIdWhenCreatedAtTies() {
        PurchaseHistory first = persistPurchaseHistory(100);
        PurchaseHistory second = persistPurchaseHistory(200);
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<PurchaseHistory> result = purchaseHistoryRepository.findByUserOrderByCreatedAtDescPurchaseIdDesc(
                user,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(PurchaseHistory::getPurchaseId)
                .containsExactly(second.getPurchaseId(), first.getPurchaseId());
    }

    private PurchaseHistory persistPurchaseHistory(int purchasedPrice) {
        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(item)
                .purchasedPrice(purchasedPrice)
                .build();
        entityManager.persist(purchaseHistory);
        return purchaseHistory;
    }

    private void updateCreatedAt(PurchaseHistory purchaseHistory, LocalDateTime createdAt) {
        String sql = "UPDATE purchase_history SET created_at = :createdAt WHERE purchase_id = :purchaseId";
        entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("createdAt", createdAt)
                .setParameter("purchaseId", purchaseHistory.getPurchaseId())
                .executeUpdate();
    }
}
