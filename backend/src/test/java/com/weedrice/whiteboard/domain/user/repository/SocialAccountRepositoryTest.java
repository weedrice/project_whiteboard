package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class SocialAccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Test
    @DisplayName("Provider and provider id normalized lookup trims legacy values")
    void findAllByNormalizedProviderAndProviderId_trimsLegacyValues() {
        User user = persistUser("social-user-1");
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(" Google ")
                .providerId(" Provider-User-1 ")
                .build();
        entityManager.persist(socialAccount);
        entityManager.flush();
        entityManager.clear();

        var result = socialAccountRepository.findAllByNormalizedProviderAndProviderId(
                "google",
                "Provider-User-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProvider()).isEqualTo(" Google ");
        assertThat(result.get(0).getProviderId()).isEqualTo(" Provider-User-1 ");
    }

    @Test
    @DisplayName("User provider normalized lookup lower-cases and trims provider")
    void findAllByUserAndNormalizedProvider_matchesUserAndNormalizedProvider() {
        User user = persistUser("social-user-2");
        User anotherUser = persistUser("social-user-3");
        SocialAccount userSocialAccount = SocialAccount.builder()
                .user(user)
                .provider(" Google ")
                .providerId("provider-user-2")
                .build();
        SocialAccount anotherUserSocialAccount = SocialAccount.builder()
                .user(anotherUser)
                .provider("google")
                .providerId("provider-user-3")
                .build();
        entityManager.persist(userSocialAccount);
        entityManager.persist(anotherUserSocialAccount);
        entityManager.flush();

        var result = socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google");

        assertThat(result)
                .hasSize(1)
                .first()
                .extracting(SocialAccount::getProviderId)
                .isEqualTo("provider-user-2");
    }

    private User persistUser(String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }
}
