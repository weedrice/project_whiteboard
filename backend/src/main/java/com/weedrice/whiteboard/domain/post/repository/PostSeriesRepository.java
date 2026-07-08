package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostSeriesRepository extends JpaRepository<PostSeries, Long> {
    List<PostSeries> findByOwner_UserIdOrderBySeriesIdDesc(Long ownerUserId);

    Optional<PostSeries> findBySeriesIdAndOwner_UserId(Long seriesId, Long ownerUserId);
}
