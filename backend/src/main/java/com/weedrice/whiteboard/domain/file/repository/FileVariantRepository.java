package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileVariantRepository extends JpaRepository<FileVariant, Long> {

    Optional<FileVariant> findByFileFileIdAndVariantType(Long fileId, FileVariantType variantType);

    List<FileVariant> findByFileFileId(Long fileId);

    void deleteByFileFileId(Long fileId);
}
