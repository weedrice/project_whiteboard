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
public interface EmoticonMasterRepository extends JpaRepository<EmoticonMaster, Long> {

    // 활성화된 이모티콘 목록 조회
    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.createdAt DESC")
    Page<EmoticonMaster> findAllActive(Pageable pageable);

    // 활성화된 이모티콘 목록 조회 (등록순 - 오름차순)
    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.createdAt ASC")
    Page<EmoticonMaster> findAllActiveOrderByCreatedAtAsc(Pageable pageable);

    // 활성화된 이모티콘 목록 조회 (판매순)
    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' ORDER BY e.purchaseCount DESC, e.createdAt DESC")
    Page<EmoticonMaster> findAllActiveOrderByPurchaseCount(Pageable pageable);

    // 특정 사용자의 이모티콘 목록 조회
    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.creator.userId = :creatorId ORDER BY e.createdAt DESC")
    Page<EmoticonMaster> findByCreatorId(@Param("creatorId") Long creatorId, Pageable pageable);

    // 태그로 검색 (PostgreSQL 배열 함수 사용)
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags) ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags)",
            nativeQuery = true)
    Page<EmoticonMaster> findByTag(@Param("tag") String tag, Pageable pageable);

    // 이름으로 검색
    @EntityGraph(attributePaths = "creator")
    @Query("SELECT e FROM EmoticonMaster e WHERE e.isActive = 'Y' AND LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY e.createdAt DESC")
    Page<EmoticonMaster> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 이모티콘 상세 조회 (이미지 포함)
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

    // 인기 이모티콘 조회 (특정 기간 내 구매 횟수 기준) - 상위 5개
    @Query(value = """
            SELECT em.* FROM emoticon_masters em
            LEFT JOIN (
                SELECT ep.emoticon_id, COUNT(*) as cnt
                FROM emoticon_purchases ep
                WHERE ep.created_at >= :startDate
                GROUP BY ep.emoticon_id
            ) purchase_stats ON em.emoticon_id = purchase_stats.emoticon_id
            WHERE em.is_active = 'Y'
            ORDER BY COALESCE(purchase_stats.cnt, 0) DESC, em.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EmoticonMaster> findPopularEmoticons(@Param("startDate") LocalDateTime startDate, @Param("limit") int limit);

    // 통합 검색 (태그, 등록자명, 이모티콘 이름) - 오래된순
    @Query(value = """
            SELECT DISTINCT em.* FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            ORDER BY em.created_at ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT em.emoticon_id) FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByKeywordAllOrderByCreatedAtAsc(@Param("keyword") String keyword, Pageable pageable);

    // 통합 검색 (태그, 등록자명, 이모티콘 이름) - 최신순
    @Query(value = """
            SELECT DISTINCT em.* FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            ORDER BY em.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT em.emoticon_id) FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByKeywordAllOrderByCreatedAtDesc(@Param("keyword") String keyword, Pageable pageable);

    // 통합 검색 (태그, 등록자명, 이모티콘 이름) - 판매순
    @Query(value = """
            SELECT DISTINCT em.* FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            ORDER BY em.purchase_count DESC, em.created_at DESC
            """, 
            countQuery = """
            SELECT COUNT(DISTINCT em.emoticon_id) FROM emoticon_masters em
            LEFT JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y'
            AND (
                LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR :keyword = ANY(em.tags)
            )
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByKeywordAllOrderByPurchase(@Param("keyword") String keyword, Pageable pageable);

    // 이름으로 검색 - 오래된순
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY created_at ASC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<EmoticonMaster> searchByNameOrderByCreatedAtAsc(@Param("keyword") String keyword, Pageable pageable);

    // 이름으로 검색 - 최신순
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<EmoticonMaster> searchByNameOrderByCreatedAtDesc(@Param("keyword") String keyword, Pageable pageable);

    // 이름으로 검색 - 판매순
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY purchase_count DESC, created_at DESC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<EmoticonMaster> searchByNameOrderByPurchase(@Param("keyword") String keyword, Pageable pageable);

    // 등록자명으로 검색 - 오래된순
    @Query(value = """
            SELECT em.* FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY em.created_at ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByCreatorOrderByCreatedAtAsc(@Param("keyword") String keyword, Pageable pageable);

    // 등록자명으로 검색 - 최신순
    @Query(value = """
            SELECT em.* FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY em.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByCreatorOrderByCreatedAtDesc(@Param("keyword") String keyword, Pageable pageable);

    // 등록자명으로 검색 - 판매순
    @Query(value = """
            SELECT em.* FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY em.purchase_count DESC, em.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM emoticon_masters em
            JOIN users u ON em.creator_id = u.user_id
            WHERE em.is_active = 'Y' AND LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<EmoticonMaster> searchByCreatorOrderByPurchase(@Param("keyword") String keyword, Pageable pageable);

    // 태그로 검색 - 오래된순
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags) ORDER BY created_at ASC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags)",
            nativeQuery = true)
    Page<EmoticonMaster> searchByTagOrderByCreatedAtAsc(@Param("tag") String tag, Pageable pageable);

    // 태그로 검색 - 판매순
    @Query(value = "SELECT * FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags) ORDER BY purchase_count DESC, created_at DESC",
            countQuery = "SELECT COUNT(*) FROM emoticon_masters WHERE is_active = 'Y' AND :tag = ANY(tags)",
            nativeQuery = true)
    Page<EmoticonMaster> searchByTagOrderByPurchase(@Param("tag") String tag, Pageable pageable);

    // 사용자가 사용 가능한 이모티콘 목록 (구매한 것 + 내가 등록한 것)
    // 숨김 처리된 노비콘도 구매자/등록자는 계속 사용 가능
    @Query(value = """
            SELECT DISTINCT em.* FROM emoticon_masters em
            LEFT JOIN emoticon_purchases ep ON em.emoticon_id = ep.emoticon_id AND ep.user_id = :userId
            WHERE (ep.purchase_id IS NOT NULL OR em.creator_id = :userId)
            ORDER BY em.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT em.emoticon_id) FROM emoticon_masters em
            LEFT JOIN emoticon_purchases ep ON em.emoticon_id = ep.emoticon_id AND ep.user_id = :userId
            WHERE (ep.purchase_id IS NOT NULL OR em.creator_id = :userId)
            """,
            nativeQuery = true)
    Page<EmoticonMaster> findUsableEmoticons(@Param("userId") Long userId, Pageable pageable);

    // 사용자가 해당 이모티콘을 사용할 수 있는지 확인 (구매했거나 본인이 등록한 경우)
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
}
