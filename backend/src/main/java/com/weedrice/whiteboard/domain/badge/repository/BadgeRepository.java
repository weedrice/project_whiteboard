package com.weedrice.whiteboard.domain.badge.repository;

import com.weedrice.whiteboard.domain.badge.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, String> {
    List<Badge> findAllByOrderBySortOrderAscBadgeCodeAsc();
}
