package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            JOIN FETCH token.user
            WHERE token.token = :token
            """)
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") String token);

    List<PasswordResetToken> findByUserOrderByCreatedAtDesc(User user);
}
