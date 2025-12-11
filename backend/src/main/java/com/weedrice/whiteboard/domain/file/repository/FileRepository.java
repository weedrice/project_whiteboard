package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByRelatedIdIsNullAndCreatedAtBefore(LocalDateTime dateTime);

    List<File> findByRelatedIdAndRelatedType(Long relatedId, String relatedType);

    List<File> findByRelatedIdInAndRelatedType(List<Long> relatedIds, String relatedType);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT f.relatedId FROM File f WHERE f.relatedId IN :relatedIds AND f.relatedType = :relatedType AND f.mimeType LIKE 'image/%'")
    List<Long> findRelatedIdsWithImages(
            @org.springframework.data.repository.query.Param("relatedIds") List<Long> relatedIds,
            @org.springframework.data.repository.query.Param("relatedType") String relatedType);

    Optional<File> findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith(Long relatedId, String relatedType, String mimeTypePrefix);
}
