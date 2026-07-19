package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostSeriesItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostSeriesItemRepository extends JpaRepository<PostSeriesItem, Long> {
    @EntityGraph(attributePaths = { "series", "post", "post.board" })
    Optional<PostSeriesItem> findByPost_PostId(Long postId);

    Optional<PostSeriesItem> findByPost_PostIdAndSeries_Owner_UserId(Long postId, Long ownerUserId);

    @EntityGraph(attributePaths = { "post", "post.board", "post.user" })
    List<PostSeriesItem> findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(Long seriesId);

    @Query("SELECT COALESCE(MAX(i.sortOrder), -1) FROM PostSeriesItem i WHERE i.series.seriesId = :seriesId")
    int findMaxSortOrder(@Param("seriesId") Long seriesId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostSeriesItem i WHERE i.series.seriesId = :seriesId")
    int deleteAllBySeriesId(@Param("seriesId") Long seriesId);
}
