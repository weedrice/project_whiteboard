package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByRelatedIdIsNullAndCreatedAtBefore(LocalDateTime dateTime);

    List<File> findByRelatedIdAndRelatedType(Long relatedId, String relatedType);
}
