package com.weedrice.whiteboard.domain.auth.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCode extends BaseTimeEntity {
    public static final String DELIVERY_STATUS_PENDING = "PENDING";
    public static final String DELIVERY_STATUS_SENT = "SENT";
    public static final String DELIVERY_STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_verified", nullable = false, length = 1)
    private Boolean isVerified;

    @Column(name = "delivery_status", length = 20)
    private String deliveryStatus;

    @Column(name = "verification_ticket", length = 64)
    private String verificationTicket;

    @Column(name = "ticket_expiry_date")
    private LocalDateTime ticketExpiryDate;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_ticket_consumed", nullable = false, length = 1)
    private Boolean isTicketConsumed;

    @Builder
    public VerificationCode(String email, VerificationPurpose purpose, String code, LocalDateTime expiryDate) {
        this.email = email;
        this.purpose = purpose;
        this.code = code;
        this.expiryDate = expiryDate;
        this.isVerified = false;
        this.deliveryStatus = DELIVERY_STATUS_PENDING;
        this.isTicketConsumed = false;
    }

    public void verify() {
        this.isVerified = true;
    }

    public void clearVerification() {
        this.isVerified = false;
        clearVerificationTicket();
    }

    public void markSent() {
        this.deliveryStatus = DELIVERY_STATUS_SENT;
    }

    public void markFailed() {
        this.deliveryStatus = DELIVERY_STATUS_FAILED;
    }

    public boolean isSent() {
        return this.deliveryStatus == null || DELIVERY_STATUS_SENT.equals(this.deliveryStatus);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    public void issueVerificationTicket(String ticket, LocalDateTime ticketExpiryDate) {
        this.isVerified = true;
        this.verificationTicket = ticket;
        this.ticketExpiryDate = ticketExpiryDate;
        this.isTicketConsumed = false;
    }

    public void consumeVerificationTicket() {
        this.isTicketConsumed = true;
        clearVerificationTicket();
    }

    public void invalidateVerificationTicket() {
        this.isTicketConsumed = true;
        clearVerificationTicket();
    }

    public boolean hasActiveVerificationTicket() {
        return this.verificationTicket != null
                && !Boolean.TRUE.equals(this.isTicketConsumed)
                && !isVerificationTicketExpired();
    }

    public boolean isVerificationTicketExpired() {
        return this.ticketExpiryDate == null || LocalDateTime.now().isAfter(this.ticketExpiryDate);
    }

    private void clearVerificationTicket() {
        this.verificationTicket = null;
        this.ticketExpiryDate = null;
    }
}
