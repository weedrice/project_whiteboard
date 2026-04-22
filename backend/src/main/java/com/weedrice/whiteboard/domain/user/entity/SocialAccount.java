package com.weedrice.whiteboard.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_accounts_provider_provider_id", columnNames = {"provider", "provider_id"}),
                @UniqueConstraint(name = "uk_social_accounts_user_provider", columnNames = {"user_id", "provider"})
        }
)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String provider; // google, github, discord

    @Column(name = "provider_id", nullable = false)
    private String providerId; // unique id from provider

    @Builder
    public SocialAccount(User user, String provider, String providerId) {
        this.user = user;
        this.provider = provider;
        this.providerId = providerId;
    }
}
