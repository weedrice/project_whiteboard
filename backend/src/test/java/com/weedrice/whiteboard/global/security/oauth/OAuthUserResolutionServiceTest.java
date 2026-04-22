package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthUserResolutionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialAccountLinkService socialAccountLinkService;

    @Mock
    private SanctionService sanctionService;

    @InjectMocks
    private OAuthUserResolutionService oauthUserResolutionService;

    private User user;
    private OAuthAttributes oauthAttributes;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("oauth-user")
                .password("encoded")
                .email("oauth@example.com")
                .displayName("OAuth User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        oauthAttributes = OAuthAttributes.of(
                "google",
                "sub",
                Map.of(
                        "sub", "provider-user-id",
                        "email", "oauth@example.com",
                        "name", "OAuth User",
                        "picture", "https://example.com/p.png"));
    }

    @Test
    @DisplayName("기존 소셜 링크가 있으면 해당 사용자를 우선 반환한다")
    void resolveUser_returnsLinkedUserFirst() {
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("provider-user-id")
                .build();

        when(socialAccountRepository.findByProviderAndProviderId("google", "provider-user-id"))
                .thenReturn(Optional.of(socialAccount));

        Optional<User> resolvedUser = oauthUserResolutionService.resolveUser("google", oauthAttributes);

        assertThat(resolvedUser).contains(user);
        verify(sanctionService).validateNotBanned(user);
        verify(socialAccountLinkService, never()).linkSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("이메일 일치 사용자가 있으면 소셜 계정을 링크한 뒤 반환한다")
    void resolveUser_linksExistingEmailUser() {
        when(socialAccountRepository.findByProviderAndProviderId("google", "provider-user-id"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));

        Optional<User> resolvedUser = oauthUserResolutionService.resolveUser("google", oauthAttributes);

        assertThat(resolvedUser).contains(user);
        verify(sanctionService).validateNotBanned(user);
        verify(socialAccountLinkService).linkSocialAccount(user, "google", "provider-user-id");
    }

    @Test
    @DisplayName("연결된 계정도 이메일 일치 계정도 없으면 빈 결과를 반환한다")
    void resolveUser_returnsEmptyWhenNoUserMatches() {
        when(socialAccountRepository.findByProviderAndProviderId("google", "provider-user-id"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.empty());

        Optional<User> resolvedUser = oauthUserResolutionService.resolveUser("google", oauthAttributes);

        assertThat(resolvedUser).isEmpty();
        verify(socialAccountLinkService, never()).linkSocialAccount(any(), any(), any());
        verify(sanctionService, never()).validateNotBanned(any());
    }
}
