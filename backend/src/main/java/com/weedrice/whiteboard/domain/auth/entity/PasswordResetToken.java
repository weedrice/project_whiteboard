package com.weedrice.whiteboard.domain.auth.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseTimeEntity {
    public static final String DELIVERY_STATUS_PENDING = "PENDING";
    public static final String DELIVERY_STATUS_SENT = "SENT";
    public static final String DELIVERY_STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token; // SHA-256 hash of the actual token

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_used", nullable = false, length = 1)
    private Boolean isUsed;

    @Column(name = "delivery_status", length = 20)
    private String deliveryStatus;

    @Builder
    public PasswordResetToken(String token, User user, LocalDateTime expiryDate) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.isUsed = false;
        this.deliveryStatus = DELIVERY_STATUS_PENDING;
    }

    public void useToken() {
        this.isUsed = true;
    }

    public void invalidate() {
        this.isUsed = true;
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

    public boolean isExpiredAt(LocalDateTime now) {
        return now.isAfter(this.expiryDate);
    }
}
