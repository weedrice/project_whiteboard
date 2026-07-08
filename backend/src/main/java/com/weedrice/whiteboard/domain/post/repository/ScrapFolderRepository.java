package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapFolderRepository extends JpaRepository<ScrapFolder, Long> {
    List<ScrapFolder> findByUser_UserIdOrderBySortOrderAscFolderIdAsc(Long userId);

    Optional<ScrapFolder> findByFolderIdAndUser_UserId(Long folderId, Long userId);

    boolean existsByUser_UserIdAndName(Long userId, String name);
}
