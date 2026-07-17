package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vc FROM VerificationCode vc WHERE vc.verificationId = :verificationId")
    Optional<VerificationCode> findByIdForUpdate(@Param("verificationId") Long verificationId);
    @Query(value = """
            SELECT *
            FROM verification_codes
            WHERE email = :email
              AND purpose = :purpose
              AND (delivery_status = 'SENT' OR delivery_status IS NULL)
            ORDER BY created_at DESC, verification_id DESC
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
            ORDER BY created_at DESC, verification_id DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<VerificationCode> findLatestSentByEmailAndPurposeForUpdate(
            @Param("email") String email,
            @Param("purpose") String purpose);

    @Query(value = """
            SELECT created_at
            FROM verification_codes
            WHERE email = :email
              AND purpose = :purpose
              AND (delivery_status IN ('PENDING', 'SENT', 'FAILED') OR delivery_status IS NULL)
            ORDER BY created_at DESC, verification_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<LocalDateTime> findLatestDeliveryAttemptCreatedAt(
            @Param("email") String email,
            @Param("purpose") String purpose);

    @Query(value = """
            SELECT COUNT(*)
            FROM verification_codes
            WHERE email = :email
              AND purpose = :purpose
              AND created_at >= :createdAtFrom
              AND (delivery_status IN ('PENDING', 'SENT', 'FAILED') OR delivery_status IS NULL)
            """, nativeQuery = true)
    long countDeliveryAttemptsSince(
            @Param("email") String email,
            @Param("purpose") String purpose,
            @Param("createdAtFrom") LocalDateTime createdAtFrom);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationCode> findByEmailAndPurposeAndVerificationTicket(
            String email,
            VerificationPurpose purpose,
            String verificationTicket);

    @Query("""
            SELECT vc
            FROM VerificationCode vc
            WHERE vc.email = :email
              AND vc.purpose = :purpose
              AND vc.verificationTicket = :verificationTicket
            """)
    Optional<VerificationCode> findByEmailAndPurposeAndVerificationTicketWithoutLock(
            @Param("email") String email,
            @Param("purpose") VerificationPurpose purpose,
            @Param("verificationTicket") String verificationTicket);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE VerificationCode vc
            SET vc.isTicketConsumed = true,
                vc.verificationTicket = null,
                vc.ticketExpiryDate = null
            WHERE vc.email = :email
              AND vc.purpose = :purpose
              AND (:excludeVerificationId IS NULL OR vc.verificationId <> :excludeVerificationId)
              AND vc.verificationTicket IS NOT NULL
              AND vc.isTicketConsumed = false
              AND vc.ticketExpiryDate > :now
            """)
    int invalidateActiveTickets(
            @Param("email") String email,
            @Param("purpose") VerificationPurpose purpose,
            @Param("excludeVerificationId") Long excludeVerificationId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE verification_codes
            SET delivery_status = 'FAILED',
                modified_at = :recoveredAt
            WHERE verification_id IN (
                SELECT verification_id
                FROM verification_codes
                WHERE delivery_status = 'PENDING'
                  AND expiry_date < :recoveredAt
                  AND created_at < :staleBefore
                  AND modified_at < :staleBefore
                ORDER BY created_at ASC, verification_id ASC
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int recoverStalePendingDeliveries(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT COUNT(*)
            FROM verification_codes
            WHERE delivery_status = 'PENDING'
              AND expiry_date < :now
              AND created_at < :staleBefore
              AND modified_at < :staleBefore
            """, nativeQuery = true)
    long countStalePendingDeliveries(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            DELETE FROM verification_codes
            WHERE verification_id IN (
                SELECT verification_id
                FROM verification_codes
                WHERE (delivery_status IN ('SENT', 'FAILED') OR delivery_status IS NULL)
                  AND expiry_date < :cutoff
                  AND (ticket_expiry_date IS NULL OR ticket_expiry_date < :cutoff)
                  AND modified_at < :cutoff
                ORDER BY expiry_date ASC, verification_id ASC
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredTerminalBatch(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);
}
