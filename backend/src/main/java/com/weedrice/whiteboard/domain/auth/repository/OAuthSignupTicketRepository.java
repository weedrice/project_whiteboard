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

    @Modifying
    @Query("DELETE FROM OAuthSignupTicketEntity ticket WHERE ticket.expiresAt <= :now")
    int deleteExpiredAtOrBefore(@Param("now") LocalDateTime now);
}
