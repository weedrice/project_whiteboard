package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfilePrivacyPolicyTest {
    private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
    private final ProfilePrivacyPolicy policy = new ProfilePrivacyPolicy(settingsRepository);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void enforcesAllSectionsForEveryCombinationAndViewer(int mask) {
        UserSettings settings = UserSettings.builder().user(User.builder().build()).build();
        boolean overview = (mask & 1) != 0;
        boolean posts = (mask & 2) != 0;
        boolean comments = (mask & 4) != 0;
        settings.updateProfilePrivacy(overview, posts, comments);
        when(settingsRepository.findById(2L)).thenReturn(Optional.of(settings));
        for (Long viewer : new Long[]{null, 1L, 2L}) {
            boolean owner = Long.valueOf(2L).equals(viewer);
            check(owner || overview, () -> policy.requireOverviewVisible(2L, viewer));
            check(owner || posts, () -> policy.requirePostsVisible(2L, viewer));
            check(owner || comments, () -> policy.requireCommentsVisible(2L, viewer));
        }
    }

    @Test
    void missingSettingsDefaultsToPublic() {
        when(settingsRepository.findById(2L)).thenReturn(Optional.empty());
        assertThat(policy.resolve(2L, null)).isEqualTo(
                new ProfilePrivacyPolicy.Visibility(true, true, true, true, true, true));
    }

    private void check(boolean allowed, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        if (allowed) {
            assertThatCode(action).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFILE_SECTION_PRIVATE);
        }
    }
}
