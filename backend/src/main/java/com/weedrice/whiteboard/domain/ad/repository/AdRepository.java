package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {
    List<Ad> findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(
            String placement, String isActive, LocalDateTime now, LocalDateTime now2);
}
