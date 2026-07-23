package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmoticonMasterRepository extends JpaRepository<EmoticonMaster, Long>, EmoticonMasterSearchRepository {

    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.createdAt DESC, e.emoticonId DESC")
    Page<EmoticonMaster> findAllActive(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.createdAt ASC, e.emoticonId ASC")
    Page<EmoticonMaster> findAllActiveOrderByCreatedAtAsc(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.purchaseCount DESC, e.createdAt DESC, e.emoticonId DESC")
    Page<EmoticonMaster> findAllActiveOrderByPurchaseCount(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.creator.userId = :creatorId ORDER BY e.createdAt DESC, e.emoticonId DESC")
    Page<EmoticonMaster> findByCreatorId(@Param("creatorId") Long creatorId, Pageable pageable);

    @Query("SELECT e FROM EmoticonMaster e LEFT JOIN FETCH e.images LEFT JOIN FETCH e.creator WHERE e.emoticonId = :emoticonId")
    Optional<EmoticonMaster> findByIdWithImages(@Param("emoticonId") Long emoticonId);

    boolean existsByThumbnailUrlIn(List<String> thumbnailUrls);

    @Query("SELECT DISTINCT e.thumbnailUrl FROM EmoticonMaster e WHERE e.thumbnailUrl IN :thumbnailUrls")
    List<String> findReferencedThumbnailUrls(@Param("thumbnailUrls") List<String> thumbnailUrls);

    @Query("""
            SELECT DISTINCT e
            FROM EmoticonMaster e
            LEFT JOIN FETCH e.creator
            LEFT JOIN e.images i
            WHERE e.emoticonId = :emoticonId
               OR e.thumbnailUrl IN :fileUrls
               OR i.imageUrl IN :fileUrls
            """)
    List<EmoticonMaster> findFileAccessTargets(
            @Param("emoticonId") Long emoticonId,
            @Param("fileUrls") List<String> fileUrls);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmoticonMaster e WHERE e.emoticonId = :emoticonId")
    Optional<EmoticonMaster> findByIdForUpdate(@Param("emoticonId") Long emoticonId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE EmoticonMaster e
            SET e.purchaseCount = e.purchaseCount + 1
            WHERE e.emoticonId = :emoticonId
            AND e.isActive = 'Y'
            """)
    int incrementPurchaseCount(@Param("emoticonId") Long emoticonId);

    @Query(value = """
            SELECT em.* FROM emoticon_masters em
            LEFT JOIN (
                SELECT ep.emoticon_id, COUNT(*) as cnt
                FROM emoticon_purchases ep
                WHERE ep.created_at >= :startDate
                GROUP BY ep.emoticon_id
            ) purchase_stats ON em.emoticon_id = purchase_stats.emoticon_id
            WHERE em.is_active = 'Y'
            ORDER BY COALESCE(purchase_stats.cnt, 0) DESC, em.created_at DESC, em.emoticon_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EmoticonMaster> findPopularEmoticons(@Param("startDate") LocalDateTime startDate, @Param("limit") int limit);

    @Query(value = """
            SELECT DISTINCT em.* FROM emoticon_masters em
            JOIN emoticon_purchases ep ON em.emoticon_id = ep.emoticon_id AND ep.user_id = :userId
            WHERE ep.purchase_id IS NOT NULL
            ORDER BY em.created_at DESC, em.emoticon_id DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT em.emoticon_id) FROM emoticon_masters em
            JOIN emoticon_purchases ep ON em.emoticon_id = ep.emoticon_id AND ep.user_id = :userId
            WHERE ep.purchase_id IS NOT NULL
            """,
            nativeQuery = true)
    Page<EmoticonMaster> findPurchasedEmoticons(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM EmoticonMaster e
            LEFT JOIN EmoticonPurchase ep ON ep.emoticon = e AND ep.user.userId = :userId
            WHERE e.emoticonId = :emoticonId
            AND (ep.purchaseId IS NOT NULL OR e.creator.userId = :userId)
            """)
    boolean canUseEmoticon(@Param("userId") Long userId, @Param("emoticonId") Long emoticonId);

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM EmoticonMaster e
            LEFT JOIN EmoticonPurchase ep ON ep.emoticon = e AND ep.user.userId = :userId
            WHERE e.emoticonId IN :emoticonIds
            AND (ep.purchaseId IS NOT NULL OR e.creator.userId = :userId)
            """)
    boolean canUseAnyEmoticon(@Param("userId") Long userId, @Param("emoticonIds") List<Long> emoticonIds);

    @Query("""
            SELECT CASE WHEN COUNT(DISTINCT e.emoticonId) = :requiredCount THEN true ELSE false END
            FROM EmoticonMaster e
            LEFT JOIN EmoticonPurchase ep ON ep.emoticon = e AND ep.user.userId = :userId
            WHERE e.emoticonId IN :emoticonIds
            AND (ep.purchaseId IS NOT NULL OR e.creator.userId = :userId)
            """)
    boolean canUseAllEmoticons(
            @Param("userId") Long userId,
            @Param("emoticonIds") List<Long> emoticonIds,
            @Param("requiredCount") long requiredCount);
}
