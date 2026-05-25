package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseHistoryRepository extends JpaRepository<PurchaseHistory, Long> {
    @EntityGraph(attributePaths = "item")
    Page<PurchaseHistory> findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(Long userId, Pageable pageable);
}
