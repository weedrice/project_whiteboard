package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {
    List<Ad> findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(
            String placement, Boolean isActive, LocalDateTime now, LocalDateTime now2);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ad a
            set a.impressionCount = a.impressionCount + 1
            where a.adId = :adId
            """)
    int incrementImpressionCount(@Param("adId") Long adId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ad a
            set a.clickCount = a.clickCount + 1
            where a.adId = :adId
            """)
    int incrementClickCount(@Param("adId") Long adId);
}
