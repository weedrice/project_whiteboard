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
    List<ScrapFolder> findByUser_UserIdOrderBySortOrderAscFolderIdAsc(Long userId);

    Optional<ScrapFolder> findByFolderIdAndUser_UserId(Long folderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT folder FROM ScrapFolder folder WHERE folder.folderId = :folderId AND folder.user.userId = :userId")
    Optional<ScrapFolder> findOwnedByIdForUpdate(@Param("folderId") Long folderId, @Param("userId") Long userId);

    boolean existsByUser_UserIdAndName(Long userId, String name);
}
