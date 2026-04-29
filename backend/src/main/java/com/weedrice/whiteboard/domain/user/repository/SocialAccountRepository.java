package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);
    Optional<SocialAccount> findByUserAndProvider(User user, String provider);

    @Modifying
    @Query(value = """
            INSERT INTO social_accounts (
                user_id,
                provider,
                provider_id
            )
            VALUES (
                :userId,
                :provider,
                :providerId
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertSocialAccountIfAbsent(
            @Param("userId") Long userId,
            @Param("provider") String provider,
            @Param("providerId") String providerId);
}
