package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.OAuthSignupTicketEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OAuthSignupTicketRepository extends JpaRepository<OAuthSignupTicketEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ticket FROM OAuthSignupTicketEntity ticket WHERE ticket.ticketHash = :ticketHash")
    Optional<OAuthSignupTicketEntity> findByTicketHashForUpdate(@Param("ticketHash") String ticketHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM oauth_signup_tickets
            WHERE ticket_hash IN (
                SELECT ticket_hash
                FROM oauth_signup_tickets
                WHERE expires_at <= :now
                ORDER BY expires_at ASC, ticket_hash ASC
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query("DELETE FROM OAuthSignupTicketEntity ticket WHERE ticket.ticketHash = :ticketHash")
    int deleteByTicketHash(@Param("ticketHash") String ticketHash);
}
