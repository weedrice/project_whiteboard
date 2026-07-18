package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    interface AdminUserDetailStatsProjection {
        Long getPostCount();

        Long getCommentCount();

        Long getSubscriptionCount();

        Long getSanctionCount();

        Long getReportTotalCount();

        Long getReportPendingCount();
    }

    interface PublicLandingUserStatsProjection {
        Long getNewMembersLast24Hours();

        Long getOnlineCount();
    }

    interface AdminDashboardUserStatsProjection {
        Long getTotalUsers();

        Long getActiveUsers();
    }

    interface ReportTargetMetadataProjection {
        Long getTargetId();

        Long getTargetUserId();

        String getTargetDisplayName();

        String getTargetLoginId();
    }

    Optional<User> findByLoginId(String loginId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.loginId = :loginId")
    Optional<User> findByLoginIdForUpdate(@Param("loginId") String loginId);
    @Query("""
            SELECT u.userId AS targetId,
                   u.userId AS targetUserId,
                   u.displayName AS targetDisplayName,
                   u.loginId AS targetLoginId
            FROM User u
            WHERE u.userId IN :userIds
            """)
    List<ReportTargetMetadataProjection> findReportTargetMetadataByUserIds(@Param("userIds") List<Long> userIds);
    Optional<User> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, String status);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIsEmailVerifiedTrue(String email);
    Optional<User> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
    long countByLastLoginAtAfter(LocalDateTime lastLoginAt);
    long countByStatusAndDeletedAtIsNullAndCreatedAtAfter(String status, LocalDateTime createdAt);
    Page<User> findByDisplayNameContainingIgnoreCaseAndStatus(String displayName, String status, Pageable pageable);
    Page<User> findByDisplayNameContainingIgnoreCase(String displayName, Pageable pageable);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
              AND LOWER(u.displayName) LIKE LOWER(CONCAT(
                    REPLACE(REPLACE(REPLACE(:keyword, '!', '!!'), '%', '!%'), '_', '!_'),
                    '%')) ESCAPE '!'
              AND (:excludedUserIdsEmpty = true OR u.userId NOT IN :excludedUserIds)
            ORDER BY u.displayName ASC, u.userId ASC
            """)
    List<User> findMentionCandidates(
            @Param("keyword") String keyword,
            @Param("excludedUserIdsEmpty") boolean excludedUserIdsEmpty,
            @Param("excludedUserIds") List<Long> excludedUserIds,
            Pageable pageable);

    List<User> findByIsSuperAdminTrue();
    List<User> findByIsSuperAdminTrueAndDeletedAtIsNull();
    List<User> findByIsSuperAdminTrueAndStatusAndDeletedAtIsNull(String status);

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    long countActiveUsersForAdminDashboard();

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
              AND u.lastLoginAt > :since
            """)
    long countRecentlyLoggedInActiveUsersForAdminDashboard(@Param("since") LocalDateTime since);

    @Query("""
            SELECT
                COUNT(u) AS totalUsers,
                COALESCE(SUM(CASE WHEN u.lastLoginAt > :since THEN 1L ELSE 0L END), 0L) AS activeUsers
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    AdminDashboardUserStatsProjection countAdminDashboardUserStats(@Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
              AND u.lastLoginAt > :since
            """)
    long countRecentlyLoggedInActiveUsersForPublicLanding(@Param("since") LocalDateTime since);

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN u.createdAt > :since THEN 1L ELSE 0L END), 0L) AS newMembersLast24Hours,
                COALESCE(SUM(CASE WHEN u.lastLoginAt > :since THEN 1L ELSE 0L END), 0L) AS onlineCount
            FROM User u
            WHERE u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    PublicLandingUserStatsProjection countPublicLandingUserStats(@Param("since") LocalDateTime since);

    @Query("""
            SELECT
                (SELECT COUNT(p) FROM Post p WHERE p.user = u AND p.isDeleted = false) AS postCount,
                (SELECT COUNT(c) FROM Comment c WHERE c.user = u AND c.isDeleted = false) AS commentCount,
                (SELECT COUNT(bs) FROM BoardSubscription bs WHERE bs.user = u) AS subscriptionCount,
                (SELECT COUNT(s) FROM Sanction s WHERE s.targetUser = u) AS sanctionCount,
                (SELECT COUNT(r)
                 FROM Report r
                 WHERE r.targetType = :reportTargetType
                   AND r.targetId = u.userId) AS reportTotalCount,
                (SELECT COUNT(r)
                 FROM Report r
                 WHERE r.targetType = :reportTargetType
                   AND r.targetId = u.userId
                   AND r.status = :reportPendingStatus) AS reportPendingCount
            FROM User u
            WHERE u = :user
            """)
    AdminUserDetailStatsProjection countAdminUserDetailStats(
            @Param("user") User user,
            @Param("reportTargetType") String reportTargetType,
            @Param("reportPendingStatus") String reportPendingStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.isSuperAdmin = true
              AND u.deletedAt IS NULL
            """)
    List<User> findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate();

    @Query("""
            SELECT u
            FROM User u
            WHERE u.isSuperAdmin = true
              AND u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    List<User> findUsableSuperAdmins();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.isSuperAdmin = true
              AND u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    List<User> findUsableSuperAdminsForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId IN :userIds ORDER BY u.userId ASC")
    List<User> findAllByIdForUpdate(@Param("userIds") Collection<Long> userIds);

    Object findByIsSuperAdmin(boolean isSuperAdmin);
}
