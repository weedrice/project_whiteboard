package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByTagName(String tagName);

    java.util.List<Tag> findTop10ByPostCountGreaterThanOrderByPostCountDesc(Integer postCount);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tag t SET t.postCount = t.postCount + 1 WHERE t.tagId = :tagId")
    int incrementPostCount(@Param("tagId") Long tagId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Tag t
            SET t.postCount = CASE WHEN t.postCount > 0 THEN t.postCount - 1 ELSE 0 END
            WHERE t.tagId = :tagId
            """)
    int decrementPostCount(@Param("tagId") Long tagId);
}
