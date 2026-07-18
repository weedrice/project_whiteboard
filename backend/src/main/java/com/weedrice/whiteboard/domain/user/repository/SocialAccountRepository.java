package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);

    @Query("""
            SELECT socialAccount
            FROM SocialAccount socialAccount
            WHERE LOWER(TRIM(socialAccount.provider)) = :provider
              AND TRIM(socialAccount.providerId) = :providerId
            ORDER BY socialAccount.id ASC
            """)
    List<SocialAccount> findAllByNormalizedProviderAndProviderId(
            @Param("provider") String provider,
            @Param("providerId") String providerId);

    @Query("""
            SELECT socialAccount
            FROM SocialAccount socialAccount
            WHERE socialAccount.user = :user
              AND LOWER(TRIM(socialAccount.provider)) = :provider
            ORDER BY socialAccount.id ASC
            """)
    List<SocialAccount> findAllByUserAndNormalizedProvider(
            @Param("user") User user,
            @Param("provider") String provider);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SocialAccount socialAccount WHERE socialAccount.user = :user")
    int deleteAllByUser(@Param("user") User user);

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
