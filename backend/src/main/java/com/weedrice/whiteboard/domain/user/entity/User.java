package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
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
    private String status; // ACTIVE, SUSPENDED, DELETED

    @Column(name = "is_email_verified", nullable = false, length = 1)
    private String isEmailVerified; // Y, N

    @Column(name = "is_super_admin", nullable = false, length = 1, columnDefinition = "varchar(1) default 'N'")
    private String isSuperAdmin; // Y, N

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
        this.status = "ACTIVE";
        this.isEmailVerified = "N";
        this.isSuperAdmin = "N"; // 기본값은 일반 사용자
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        this.isEmailVerified = "Y";
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    public void updateIsSuperAdmin(String isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
    }

    public void delete() {
        this.status = "DELETED";
        this.deletedAt = LocalDateTime.now();
    }

    public void suspend() {
        this.status = "SUSPENDED";
    }

    public void activate() {
        this.status = "ACTIVE";
    }

    public void grantSuperAdminRole() {
        this.isSuperAdmin = "Y";
    }

    public void revokeSuperAdminRole() {
        this.isSuperAdmin = "N";
    }
}
