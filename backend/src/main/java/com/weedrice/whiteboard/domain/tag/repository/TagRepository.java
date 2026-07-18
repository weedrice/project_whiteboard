package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long>, TagRepositoryCustom {
    interface PublicTagCountProjection {
        Long getTagId();

        String getTagName();

        long getPostCount();
    }

    Optional<Tag> findByTagName(String tagName);

    List<Tag> findByTagNameIn(Collection<String> tagNames);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tag t WHERE t.tagName IN :tagNames ORDER BY t.tagName ASC")
    List<Tag> findByTagNameInForUpdate(@Param("tagNames") Collection<String> tagNames);

    List<Tag> findTop10ByPostCountGreaterThanOrderByPostCountDesc(Integer postCount);

    @Query("""
            SELECT t.tagId AS tagId,
                   t.tagName AS tagName,
                   COUNT(pt) AS postCount
            FROM PostTag pt
            JOIN pt.tag t
            JOIN pt.post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND p.isBlinded = false
              AND p.isSecret = false
              AND b.isActive = true
              AND b.isPublic = true
            GROUP BY t.tagId, t.tagName
            ORDER BY COUNT(pt) DESC, t.tagName ASC, t.tagId ASC
            """)
    List<PublicTagCountProjection> findPopularPublicTagCounts(Pageable pageable);

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

}
