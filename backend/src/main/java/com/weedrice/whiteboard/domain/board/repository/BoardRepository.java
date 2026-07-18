package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    interface TopBoardPostCountProjection {
        Long getBoardId();

        Long getPostCount();
    }

    long countByIsActiveTrueAndIsPublicTrue();

    @Query("""
            SELECT COUNT(b)
            FROM Board b
            WHERE b.isActive = true
              AND b.isPublic = true
              AND LOWER(b.boardUrl) <> :inquiryBoardUrl
            """)
    long countPublicLandingVisibleBoards(@Param("inquiryBoardUrl") String inquiryBoardUrl);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByIsActiveOrderBySortOrderAscBoardIdAsc(Boolean isActive);

    @EntityGraph(attributePaths = "creator")
    @Query("""
            SELECT b
            FROM Board b
            WHERE b.isActive = true
              AND LOWER(b.boardUrl) <> :inquiryBoardUrl
              AND (
                    :isSuperAdmin = true
                    OR b.isPublic = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin a
                        WHERE a.board = b
                          AND a.user = :user
                          AND a.isActive = true
                    )
              )
            ORDER BY b.sortOrder ASC, b.boardId ASC
            """)
    List<Board> findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(
            @Param("user") User user,
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("inquiryBoardUrl") String inquiryBoardUrl);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(Boolean isActive, Boolean isPublic);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();

    List<Board> findByBoardNameContainingIgnoreCaseAndIsActiveTrue(String keyword);

    Page<Board> findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(String keyword,
            Pageable pageable);

    boolean existsByBoardName(String boardName);

    @Query("""
            SELECT b.boardName
            FROM Board b
            WHERE b.boardName IN :boardNames
            """)
    List<String> findExistingBoardNamesIn(@Param("boardNames") Collection<String> boardNames);

    boolean existsByBoardUrl(String boardUrl);

    @EntityGraph(attributePaths = "creator")
    Optional<Board> findByBoardUrl(String boardUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Board b JOIN FETCH b.creator WHERE b.boardUrl = :boardUrl")
    Optional<Board> findByBoardUrlForUpdate(@Param("boardUrl") String boardUrl);

    @Query("""
            SELECT b.boardId
            FROM Post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND p.isBlinded = false
              AND b.isActive = true
            GROUP BY b.boardId, b.sortOrder
            ORDER BY COUNT(p) DESC, b.sortOrder ASC, b.boardId ASC
            """)
    List<Long> findTopBoardIdsByPostCount(Pageable pageable);

    @Query("""
            SELECT b.boardId AS boardId, COUNT(p) AS postCount
            FROM Post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND p.isBlinded = false
              AND p.isSecret = false
              AND b.isActive = true
              AND b.isPublic = true
              AND LOWER(b.boardUrl) <> :inquiryBoardUrl
            GROUP BY b.boardId, b.sortOrder
            ORDER BY COUNT(p) DESC, b.sortOrder ASC, b.boardId ASC
            """)
    List<TopBoardPostCountProjection> findTopPublicBoardPostCounts(
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            Pageable pageable);

    @Query("""
            SELECT b.boardId AS boardId, COUNT(p) AS postCount
            FROM Post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND p.isBlinded = false
              AND (
                    p.isSecret = false
                    OR :isSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin secretAdmin
                        WHERE secretAdmin.board = b
                          AND secretAdmin.user = :user
                          AND secretAdmin.isActive = true
                    )
              )
              AND b.isActive = true
              AND LOWER(b.boardUrl) <> :inquiryBoardUrl
              AND (
                    :isSuperAdmin = true
                    OR b.isPublic = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin a
                        WHERE a.board = b
                          AND a.user = :user
                          AND a.isActive = true
                    )
              )
            GROUP BY b.boardId, b.sortOrder
            ORDER BY COUNT(p) DESC, b.sortOrder ASC, b.boardId ASC
            """)
    List<TopBoardPostCountProjection> findTopReadableBoardPostCounts(
            @Param("user") User user,
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByBoardIdIn(List<Long> boardIds);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByBoardUrlIn(Collection<String> boardUrls);

    @EntityGraph(attributePaths = "creator")
    List<Board> findAllByOrderBySortOrderAscBoardIdAsc();

    @Query("SELECT COALESCE(MAX(b.sortOrder), 0) FROM Board b")
    Integer findMaxSortOrder();

    @EntityGraph(attributePaths = "creator")
    Optional<Board> findByBoardId(Long boardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Board b WHERE b.boardId = :boardId")
    Optional<Board> findByIdForUpdate(@Param("boardId") Long boardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM Board b
            WHERE b.boardId IN :boardIds
            ORDER BY b.boardId ASC
            """)
    List<Board> findByBoardIdInForUpdate(@Param("boardIds") Collection<Long> boardIds);
}
