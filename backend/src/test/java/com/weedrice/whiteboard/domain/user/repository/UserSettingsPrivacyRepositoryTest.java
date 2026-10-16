package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.dto.UserSettingsResponse;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.ProfilePrivacyPolicy;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserSettingsPrivacyRepositoryTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private UserSettingsRepository repository;

    @Test
    void roundTripUsesYNConverterAndProjectionReflectsSavedPrivacy() {
        User user = User.builder().loginId("privacy").email("privacy@example.com")
                .displayName("Privacy").password("password").build();
        entityManager.persist(user);
        UserSettings settings = UserSettings.builder().user(user).build();
        settings.updateProfilePrivacy(false, true, false);
        entityManager.persistAndFlush(settings);
        entityManager.clear();

        Object[] columns = (Object[]) entityManager.getEntityManager().createNativeQuery(
                "select profile_overview_public, profile_posts_public, profile_comments_public from user_settings where user_id = :id")
                .setParameter("id", user.getUserId()).getSingleResult();
        assertThat(columns).extracting(String::valueOf).containsExactly("N", "Y", "N");
        UserSettings reloaded = repository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getProfileOverviewPublic()).isFalse();
        assertThat(reloaded.getProfilePostsPublic()).isTrue();
        assertThat(reloaded.getProfileCommentsPublic()).isFalse();
        UserSettingsRepository.SettingsReadProjection projection = repository.findSettingsReadByUserId(user.getUserId()).orElseThrow();
        assertThat(projection.getProfileOverviewPublic()).isFalse();
        assertThat(projection.getProfilePostsPublic()).isTrue();
        assertThat(projection.getProfileCommentsPublic()).isFalse();
    }

    @Test
    void userWithoutSettingsStillHasReadProjection() {
        User user = User.builder().loginId("missing-settings").email("missing-settings@example.com")
                .displayName("Missing").password("password").build();
        entityManager.persistAndFlush(user);
        UserSettingsRepository.SettingsReadProjection projection = repository.findSettingsReadByUserId(user.getUserId()).orElseThrow();
        // The Y/N converter maps joined NULL flags to false; theme identifies the missing row.
        assertThat(projection.getTheme()).isNull();
        assertThat(repository.findById(user.getUserId())).isEmpty();
        UserSettingsResponse response = new UserSettingsService(
                repository, null, null, mock(GlobalConfigService.class), java.time.Clock.systemUTC())
                .getSettings(user.getUserId());
        assertThat(response.isProfilePrivacyEnabled()).isFalse();
        assertThat(response.isProfileOverviewPublic()).isTrue();
        assertThat(response.isProfilePostsPublic()).isTrue();
        assertThat(response.isProfileCommentsPublic()).isTrue();
        assertThat(new ProfilePrivacyPolicy(repository).resolve(user.getUserId(), null)).isEqualTo(
                new ProfilePrivacyPolicy.Visibility(true, true, true, true, true, true));
    }
}
