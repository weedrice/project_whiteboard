package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_email_verified", nullable = false, length = 1)
    private Boolean isEmailVerified;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_super_admin", nullable = false, length = 1, columnDefinition = "varchar(1) default 'N'")
    private Boolean isSuperAdmin;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String loginId, String password, String email, String displayName) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.displayName = displayName;
        this.status = STATUS_ACTIVE;
        this.isEmailVerified = false;
        this.isSuperAdmin = false;
    }

    public void updateLastLogin(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public void verifyEmail() {
        this.isEmailVerified = true;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    public void delete(LocalDateTime deletedAt) {
        this.status = STATUS_DELETED;
        this.deletedAt = deletedAt;
    }

    public void suspend() {
        this.status = STATUS_SUSPENDED;
    }

    public void activate() {
        this.status = STATUS_ACTIVE;
        this.deletedAt = null;
    }

    public void grantSuperAdminRole() {
        this.isSuperAdmin = true;
    }

    public void revokeSuperAdminRole() {
        this.isSuperAdmin = false;
    }

    public boolean isActiveAccount() {
        return STATUS_ACTIVE.equals(this.status) && this.deletedAt == null;
    }

    public boolean isUsableSuperAdmin() {
        return Boolean.TRUE.equals(this.isSuperAdmin) && isActiveAccount();
    }
}
