package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ScrapFolderRepository extends JpaRepository<ScrapFolder, Long> {
    List<ScrapFolder> findByUserIdOrderBySortOrderAscFolderIdAsc(Long userId);

    Optional<ScrapFolder> findByFolderIdAndUserId(Long folderId, Long userId);
    default Optional<ScrapFolder> findByFolderIdAndUser_UserId(Long folderId, Long userId) {
        return findByFolderIdAndUserId(folderId, userId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT folder FROM ScrapFolder folder WHERE folder.folderId = :folderId AND folder.userId = :userId")
    Optional<ScrapFolder> findOwnedByIdForUpdate(@Param("folderId") Long folderId, @Param("userId") Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
