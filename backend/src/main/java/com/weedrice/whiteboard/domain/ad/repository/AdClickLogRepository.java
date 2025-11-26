package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.AdClickLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdClickLogRepository extends JpaRepository<AdClickLog, Long> {
}
