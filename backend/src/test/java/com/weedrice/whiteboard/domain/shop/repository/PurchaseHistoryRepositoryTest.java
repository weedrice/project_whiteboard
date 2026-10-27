package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.dto.PurchaseHistoryResponse;
import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
    void findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc_ordersByPurchaseIdWhenCreatedAtTies() {
        PurchaseHistory first = persistPurchaseHistory(100);
        PurchaseHistory second = persistPurchaseHistory(200);
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<PurchaseHistory> result = purchaseHistoryRepository.findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(
                user.getUserId(),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(PurchaseHistory::getPurchaseId)
                .containsExactly(second.getPurchaseId(), first.getPurchaseId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "/api/v1/files/20" })
    @DisplayName("기존 구매 이력 응답은 썸네일 교체·제거를 반영하고 구매 당시 이름·유형·가격을 유지한다")
    void existingPurchaseResponse_usesCurrentThumbnail(String currentImageUrl) {
        PurchaseHistory purchase = persistPurchaseHistory(75);
        Long purchaseId = purchase.getPurchaseId();
        Long itemId = item.getItemId();
        Long userId = user.getUserId();
        entityManager.flush();
        entityManager.clear();

        ShopItem managedItem = entityManager.find(ShopItem.class, itemId);
        managedItem.updatePresentation("Renamed item", currentImageUrl);
        managedItem.suspendSale();
        entityManager.flush();
        entityManager.clear();

        Page<PurchaseHistory> page = purchaseHistoryRepository
                .findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(userId, PageRequest.of(0, 10));
        // The old snapshot may remain in storage; it must not override the current thumbnail.
        assertThat(page.getContent().getFirst().getImageUrlSnapshot())
                .isEqualTo("https://example.com/item.png");
        PurchaseHistoryResponse.PurchaseSummary response = PurchaseHistoryResponse.from(page).getContent().getFirst();
        assertThat(response.getPurchaseId()).isEqualTo(purchaseId);
        assertThat(response.getItem().getImageUrl()).isEqualTo(currentImageUrl);
        assertThat(response.getItem().getItemName()).isEqualTo("Premium item");
        assertThat(response.getItem().getItemType()).isEqualTo("EMOTICON");
        assertThat(response.getPrice()).isEqualTo(75);
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
