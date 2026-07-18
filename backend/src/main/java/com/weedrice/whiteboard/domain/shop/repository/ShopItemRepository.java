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

    Page<ShopItem> findByIsActiveAndItemType(Boolean isActive, String itemType, Pageable pageable);
    Page<ShopItem> findByIsActiveAndItemTypeIn(Boolean isActive, Collection<String> itemTypes, Pageable pageable);
    Page<ShopItem> findByIsActive(Boolean isActive, Pageable pageable);
    List<ShopItem> findByIsActive(Boolean isActive);
    List<ShopItem> findTop2ByIsActiveAndItemTypeAndTargetId(
            Boolean isActive,
            String itemType,
            Long targetId);
    List<ShopItem> findByItemTypeAndTargetId(String itemType, Long targetId);

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
