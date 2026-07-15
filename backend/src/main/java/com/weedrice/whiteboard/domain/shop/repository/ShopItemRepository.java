package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {
    Page<ShopItem> findByIsActiveAndItemType(Boolean isActive, String itemType, Pageable pageable);
    Page<ShopItem> findByIsActiveAndItemTypeIn(Boolean isActive, Collection<String> itemTypes, Pageable pageable);
    Page<ShopItem> findByIsActive(Boolean isActive, Pageable pageable);
    List<ShopItem> findByIsActive(Boolean isActive);
    List<ShopItem> findTop2ByIsActiveAndItemTypeAndTargetId(
            Boolean isActive,
            String itemType,
            Long targetId);
    List<ShopItem> findByItemTypeAndTargetId(String itemType, Long targetId);
}
