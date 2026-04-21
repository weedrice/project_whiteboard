package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    @Query(value = """
            SELECT *
            FROM verification_codes
            WHERE email = :email
              AND purpose = :purpose
              AND (delivery_status = 'SENT' OR delivery_status IS NULL)
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<VerificationCode> findLatestSentByEmailAndPurpose(
            @Param("email") String email,
            @Param("purpose") String purpose);

    @Query(value = """
            SELECT *
            FROM verification_codes
            WHERE email = :email
              AND purpose = :purpose
              AND (delivery_status = 'SENT' OR delivery_status IS NULL)
            ORDER BY created_at DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<VerificationCode> findLatestSentByEmailAndPurposeForUpdate(
            @Param("email") String email,
            @Param("purpose") String purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationCode> findByEmailAndPurposeAndVerificationTicket(
            String email,
            VerificationPurpose purpose,
            String verificationTicket);

    List<VerificationCode> findAllByEmailAndPurpose(String email, VerificationPurpose purpose);

    void deleteByExpiryDateBefore(LocalDateTime now);
}
