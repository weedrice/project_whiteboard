package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);
    List<VerificationCode> findByEmailOrderByCreatedAtDesc(String email);

    @Query(value = """
            SELECT *
            FROM verification_codes
            WHERE email = :email
              AND (delivery_status = 'SENT' OR delivery_status IS NULL)
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<VerificationCode> findLatestSentByEmail(@Param("email") String email);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE VerificationCode vc
            SET vc.isVerified = false
            WHERE vc.email = :email
              AND vc.isVerified = true
              AND (vc.deliveryStatus = 'SENT' OR vc.deliveryStatus IS NULL)
            """)
    int clearVerifiedSentCodes(@Param("email") String email);

    void deleteByExpiryDateBefore(LocalDateTime now);
}
