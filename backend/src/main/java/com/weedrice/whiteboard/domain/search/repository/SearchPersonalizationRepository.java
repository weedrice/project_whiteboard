package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchPersonalizationRepository extends JpaRepository<SearchPersonalization, Long> {
    @Query("""
            SELECT sp
            FROM SearchPersonalization sp
            WHERE sp.user = :user
            ORDER BY sp.searchedAt DESC, sp.logId DESC
            """)
    Page<SearchPersonalization> findByUserOrderBySearchedAtDesc(@Param("user") User user, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE SearchPersonalization sp
            SET sp.keyword = :keyword,
                sp.searchedAt = :searchedAt,
                sp.modifiedAt = :searchedAt
            WHERE sp.user.userId = :userId
              AND sp.normalizedKeyword = :normalizedKeyword
            """)
    int updateRecentSearch(@Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("searchedAt") LocalDateTime searchedAt);

    @Modifying
    @Query(value = """
            INSERT INTO search_personalization (
                user_id,
                keyword,
                normalized_keyword,
                searched_at,
                created_at,
                modified_at
            )
            VALUES (
                :userId,
                :keyword,
                :normalizedKeyword,
                :searchedAt,
                :searchedAt,
                :searchedAt
            )
            """, nativeQuery = true)
    int insertRecentSearch(@Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("searchedAt") LocalDateTime searchedAt);

    void deleteByUser(User user);
}
