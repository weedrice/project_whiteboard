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

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_verified", nullable = false, length = 1)
    private Boolean isVerified;

    @Column(name = "delivery_status", length = 20)
    private String deliveryStatus;

    @Builder
    public VerificationCode(String email, String code, LocalDateTime expiryDate) {
        this.email = email;
        this.code = code;
        this.expiryDate = expiryDate;
        this.isVerified = false;
        this.deliveryStatus = DELIVERY_STATUS_PENDING;
    }

    public void verify() {
        this.isVerified = true;
    }

    public void clearVerification() {
        this.isVerified = false;
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
}
