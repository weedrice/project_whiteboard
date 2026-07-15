package com.weedrice.whiteboard.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_signup_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthSignupTicketEntity {

    @Id
    @Column(name = "ticket_hash", length = 64, nullable = false, updatable = false)
    private String ticketHash;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "provider", length = 255, nullable = false)
    private String provider;

    @Column(name = "provider_id", length = 255, nullable = false)
    private String providerId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public OAuthSignupTicketEntity(
            String ticketHash,
            String email,
            String displayName,
            String provider,
            String providerId,
            LocalDateTime expiresAt) {
        this.ticketHash = ticketHash;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.providerId = providerId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
