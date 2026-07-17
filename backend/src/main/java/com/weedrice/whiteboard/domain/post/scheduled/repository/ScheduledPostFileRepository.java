package com.weedrice.whiteboard.domain.post.scheduled.repository;

import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFile;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFileId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledPostFileRepository extends JpaRepository<ScheduledPostFile, ScheduledPostFileId> {

    boolean existsByIdFileIdAndIdScheduledPostIdNot(Long fileId, Long scheduledPostId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ScheduledPostFile spf WHERE spf.id.scheduledPostId = :scheduledPostId")
    int deleteAllByScheduledPostId(@Param("scheduledPostId") Long scheduledPostId);
}
