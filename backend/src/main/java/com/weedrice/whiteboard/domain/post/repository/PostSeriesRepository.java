package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostSeriesRepository extends JpaRepository<PostSeries, Long> {
    List<PostSeries> findByOwner_UserIdOrderBySeriesIdDesc(Long ownerUserId);

    Optional<PostSeries> findBySeriesIdAndOwner_UserId(Long seriesId, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PostSeries s JOIN FETCH s.owner WHERE s.seriesId = :seriesId AND s.owner.userId = :ownerUserId")
    Optional<PostSeries> findBySeriesIdAndOwnerUserIdForUpdate(
            @Param("seriesId") Long seriesId,
            @Param("ownerUserId") Long ownerUserId);
}
