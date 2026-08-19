package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM ShopItem si WHERE si.itemId = :itemId")
    Optional<ShopItem> findByIdForUpdate(@Param("itemId") Long itemId);

    Page<ShopItem> findByIsActiveAndIsSaleEnabledAndItemType(
            Boolean isActive,
            Boolean isSaleEnabled,
            String itemType,
            Pageable pageable);
    Page<ShopItem> findByIsActiveAndIsSaleEnabledAndItemTypeIn(
            Boolean isActive,
            Boolean isSaleEnabled,
            Collection<String> itemTypes,
            Pageable pageable);
    List<ShopItem> findByIsActive(Boolean isActive);
    List<ShopItem> findTop2ByIsActiveAndIsSaleEnabledAndItemTypeAndTargetId(
            Boolean isActive,
            Boolean isSaleEnabled,
            String itemType,
            Long targetId);
    List<ShopItem> findByItemTypeAndTargetId(String itemType, Long targetId);

    @Query("""
            SELECT si
            FROM ShopItem si
            WHERE (:query IS NULL OR LOCATE(LOWER(:query), LOWER(si.itemName)) > 0)
              AND (:itemType IS NULL OR si.itemType = :itemType)
              AND (:isActive IS NULL OR si.isActive = :isActive)
              AND (:isSaleEnabled IS NULL OR si.isSaleEnabled = :isSaleEnabled)
            """)
    Page<ShopItem> searchAdminItems(
            @Param("query") String query,
            @Param("itemType") String itemType,
            @Param("isActive") Boolean isActive,
            @Param("isSaleEnabled") Boolean isSaleEnabled,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT si
            FROM ShopItem si
            WHERE si.itemType = :itemType
              AND si.targetId = :targetId
            ORDER BY si.itemId ASC
            """)
    List<ShopItem> findByItemTypeAndTargetIdForUpdate(
            @Param("itemType") String itemType,
            @Param("targetId") Long targetId);
}
