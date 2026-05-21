package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdRepository extends JpaRepository<Ad, Long> {
    @Query("""
            select count(a)
            from Ad a
            where a.placement = :placement
              and a.isActive = true
              and a.startDate <= :now
              and (a.endDate is null or a.endDate > :now)
            """)
    long countActiveByPlacement(@Param("placement") String placement, @Param("now") LocalDateTime now);

    @Query("""
            select a
            from Ad a
            where a.placement = :placement
              and a.isActive = true
              and a.startDate <= :now
              and (a.endDate is null or a.endDate > :now)
            order by a.adId asc
            """)
    List<Ad> findActiveByPlacement(
            @Param("placement") String placement,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select a
            from Ad a
            where a.adId = :adId
              and a.isActive = true
              and a.startDate <= :now
              and (a.endDate is null or a.endDate > :now)
            """)
    Optional<Ad> findActiveById(@Param("adId") Long adId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ad a
            set a.impressionCount = a.impressionCount + 1
            where a.adId = :adId
              and a.isActive = true
              and a.startDate <= :now
              and (a.endDate is null or a.endDate > :now)
            """)
    int incrementImpressionCountForActive(@Param("adId") Long adId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ad a
            set a.clickCount = a.clickCount + 1
            where a.adId = :adId
              and a.isActive = true
              and a.startDate <= :now
              and (a.endDate is null or a.endDate > :now)
            """)
    int incrementClickCountForActive(@Param("adId") Long adId, @Param("now") LocalDateTime now);
}
