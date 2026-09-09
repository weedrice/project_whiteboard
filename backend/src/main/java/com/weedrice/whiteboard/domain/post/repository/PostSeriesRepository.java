package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostSeriesRepository extends JpaRepository<PostSeries, Long> {
    List<PostSeries> findByOwnerUserIdOrderBySeriesIdDesc(Long ownerUserId);

    Optional<PostSeries> findBySeriesIdAndOwnerUserId(Long seriesId, Long ownerUserId);
    default Optional<PostSeries> findBySeriesIdAndOwner_UserId(Long seriesId, Long ownerUserId) {
        return findBySeriesIdAndOwnerUserId(seriesId, ownerUserId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PostSeries s WHERE s.seriesId = :seriesId AND s.ownerUserId = :ownerUserId")
    Optional<PostSeries> findBySeriesIdAndOwnerUserIdForUpdate(
            @Param("seriesId") Long seriesId,
            @Param("ownerUserId") Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM PostSeries s
            WHERE s.ownerUserId = :ownerUserId
              AND s.seriesId IN :seriesIds
            ORDER BY s.seriesId ASC
            """)
    List<PostSeries> findAllOwnedByIdsForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("seriesIds") Collection<Long> seriesIds);
}
