package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);

    /** 이메일 중복 검사 시 사용 - 인증 완료(is_email_verified=Y)인 사용자만 대상 */
    boolean existsByEmailAndIsEmailVerifiedTrue(String email);

    Optional<User> findByEmail(String email);
    long countByLastLoginAtAfter(java.time.LocalDateTime lastLoginAt);
    Page<User> findByDisplayNameContainingIgnoreCaseAndStatus(String displayName, String status, Pageable pageable);
    Page<User> findByDisplayNameContainingIgnoreCase(String displayName, Pageable pageable); // Added for IntegratedSearch

    List<User> findByIsSuperAdminTrue();
    List<User> findByIsSuperAdminTrueAndDeletedAtIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.isSuperAdmin = true
              AND u.deletedAt IS NULL
            """)
    List<User> findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Object findByIsSuperAdmin(boolean isSuperAdmin);
}
