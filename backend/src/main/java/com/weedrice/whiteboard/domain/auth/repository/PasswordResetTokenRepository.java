package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    boolean existsByVerificationCodeVerificationId(Long verificationId);

    Optional<PasswordResetToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.token = :token
            """)
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.tokenId = :tokenId
            """)
    Optional<PasswordResetToken> findByIdForUpdate(@Param("tokenId") Long tokenId);

    default Optional<PasswordResetToken> findLatestSentByUser(User user) {
        return findSentByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.user = :user
              AND (token.deliveryStatus IS NULL OR token.deliveryStatus = 'SENT')
            ORDER BY token.createdAt DESC, token.tokenId DESC
            """)
    List<PasswordResetToken> findSentByUserOrderByCreatedAtDesc(
            @Param("user") User user,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE PasswordResetToken token
            SET token.isUsed = true
            WHERE token.user = :user
              AND token.isUsed = false
              AND (token.deliveryStatus IS NULL OR token.deliveryStatus = 'SENT')
              AND (:excludeTokenId IS NULL OR token.tokenId <> :excludeTokenId)
            """)
    int invalidatePreviousSentUnusedTokens(
            @Param("user") User user,
            @Param("excludeTokenId") Long excludeTokenId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken token
            SET token.isUsed = true
            WHERE token.user = :user
              AND token.isUsed = false
            """)
    int invalidateAllUnusedTokens(@Param("user") User user);

    @Modifying
    @Query(value = """
            DELETE FROM password_reset_tokens
            WHERE token_id IN (
                SELECT token_id
                FROM password_reset_tokens
                WHERE expiry_date < :cutoff
                  AND modified_at < :cutoff
                ORDER BY expiry_date, token_id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);
}
