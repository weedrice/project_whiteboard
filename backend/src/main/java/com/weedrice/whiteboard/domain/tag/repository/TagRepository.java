package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByTagName(String tagName);

    List<Tag> findByTagNameIn(Collection<String> tagNames);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tag t WHERE t.tagName IN :tagNames ORDER BY t.tagName ASC")
    List<Tag> findByTagNameInForUpdate(@Param("tagNames") Collection<String> tagNames);

    List<Tag> findTop10ByPostCountGreaterThanOrderByPostCountDesc(Integer postCount);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tag t SET t.postCount = t.postCount + 1 WHERE t.tagId = :tagId")
    int incrementPostCount(@Param("tagId") Long tagId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tag t SET t.postCount = t.postCount + 1 WHERE t.tagId IN :tagIds")
    int incrementPostCountIn(@Param("tagIds") Collection<Long> tagIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Tag t
            SET t.postCount = CASE WHEN t.postCount > 0 THEN t.postCount - 1 ELSE 0 END
            WHERE t.tagId = :tagId
            """)
    int decrementPostCount(@Param("tagId") Long tagId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Tag t
            SET t.postCount = CASE WHEN t.postCount > 0 THEN t.postCount - 1 ELSE 0 END
            WHERE t.tagId IN :tagIds
            """)
    int decrementPostCountIn(@Param("tagIds") Collection<Long> tagIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM Tag t
            WHERE t.postCount = 0
              AND t.createdAt < :cutoff
              AND NOT EXISTS (
                  SELECT 1
                  FROM PostTag pt
                  WHERE pt.tag = t
              )
            """)
    int deleteOrphanTagsCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
