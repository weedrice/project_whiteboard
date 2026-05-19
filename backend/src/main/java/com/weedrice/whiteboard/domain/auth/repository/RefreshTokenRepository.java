package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("""
            SELECT rt.tokenId AS tokenId, rt.user.userId AS userId
            FROM RefreshToken rt
            WHERE rt.tokenHash = :tokenHash
            """)
    Optional<RefreshTokenRenewalCandidate> findRenewalCandidateByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenId = :tokenId")
    Optional<RefreshToken> findByTokenIdForUpdate(@Param("tokenId") Long tokenId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.isRevoked = true
            WHERE rt.user.userId = :userId
              AND rt.isRevoked = false
              AND rt.expiresAt >= :now
            """)
    int revokeActiveTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<RefreshToken> findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(
            com.weedrice.whiteboard.domain.user.entity.User user,
            Boolean isRevoked,
            LocalDateTime now);

    interface RefreshTokenRenewalCandidate {
        Long getTokenId();

        Long getUserId();
    }
}
