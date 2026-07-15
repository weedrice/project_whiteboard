package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountLinkServiceTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private SocialAccountLinkService socialAccountLinkService;

    private User user;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("user1")
                .password("encoded")
                .email("user1@example.com")
                .displayName("User 1")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        anotherUser = User.builder()
                .loginId("user2")
                .password("encoded")
                .email("user2@example.com")
                .displayName("User 2")
                .build();
        ReflectionTestUtils.setField(anotherUser, "userId", 2L);
    }

    @Test
    @DisplayName("Provider is lower-cased and both identifiers are trimmed before linking")
    void linkSocialAccount_normalizesProviderAndProviderIdBeforeRepositoryAccess() {
        SocialAccount savedLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("Google-User-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "Google-User-1"))
                .thenReturn(List.of());
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of());
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "Google-User-1"))
                .thenReturn(1);
        when(socialAccountRepository.findByProviderAndProviderId("google", "Google-User-1"))
                .thenReturn(Optional.of(savedLink));

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(
                user,
                " GoOgLe ",
                " Google-User-1 ");

        assertThat(linkedAccount).isSameAs(savedLink);
        verify(socialAccountRepository).findAllByNormalizedProviderAndProviderId("google", "Google-User-1");
        verify(socialAccountRepository).findAllByUserAndNormalizedProvider(user, "google");
        verify(socialAccountRepository).insertSocialAccountIfAbsent(1L, "google", "Google-User-1");
        verify(socialAccountRepository).findByProviderAndProviderId("google", "Google-User-1");
    }

    @Test
    @DisplayName("Same user's existing provider link is reused")
    void linkSocialAccount_reusesExistingProviderLink() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of(existingLink));

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
        verify(socialAccountRepository, never()).insertSocialAccountIfAbsent(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Provider link owned by another user is rejected")
    void linkSocialAccount_rejectsProviderLinkOwnedByAnotherUser() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(anotherUser)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of(existingLink));

        assertThatThrownBy(() -> socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("Different provider id for same user/provider is rejected")
    void linkSocialAccount_rejectsDifferentProviderIdForSameUserAndProvider() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-2"))
                .thenReturn(List.of());
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of(existingLink));

        assertThatThrownBy(() -> socialAccountLinkService.linkSocialAccount(user, "google", "google-user-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("New provider link is inserted and reloaded")
    void linkSocialAccount_insertsNewLink() {
        SocialAccount savedLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of());
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of());
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(1);
        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-1"))
                .thenReturn(Optional.of(savedLink));

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(savedLink);
        verify(socialAccountRepository).insertSocialAccountIfAbsent(1L, "google", "google-user-1");
    }

    @Test
    @DisplayName("Concurrent duplicate insert reuses same user's existing link")
    void linkSocialAccount_reusesExistingLinkAfterConcurrentDuplicate() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of())
                .thenReturn(List.of(existingLink));
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of());
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(0);

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
        verify(socialAccountRepository).insertSocialAccountIfAbsent(1L, "google", "google-user-1");
    }

    @Test
    @DisplayName("Concurrent user/provider duplicate insert reuses same provider id link")
    void linkSocialAccount_reusesExistingUserProviderLinkAfterConcurrentDuplicate() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of())
                .thenReturn(List.of(existingLink));
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(0);

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
        verify(socialAccountRepository).insertSocialAccountIfAbsent(1L, "google", "google-user-1");
    }

    @Test
    @DisplayName("Concurrent user/provider duplicate insert rejects different provider id")
    void linkSocialAccount_rejectsDifferentProviderIdAfterConcurrentUserProviderDuplicate() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-2")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(socialAccountRepository.findAllByUserAndNormalizedProvider(user, "google"))
                .thenReturn(List.of())
                .thenReturn(List.of(existingLink));
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(0);

        assertThatThrownBy(() -> socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("Legacy provider casing and spaces are resolved before inserting a new link")
    void linkSocialAccount_reusesLegacyNormalizedProviderLink() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider(" Google ")
                .providerId(" google-user-1 ")
                .build();

        when(socialAccountRepository.findAllByNormalizedProviderAndProviderId("google", "google-user-1"))
                .thenReturn(List.of(existingLink));

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
        verify(socialAccountRepository, never()).insertSocialAccountIfAbsent(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Invalid provider is rejected before repository access")
    void linkSocialAccount_rejectsInvalidProviderBeforeRepositoryAccess() {
        assertInvalidInput(null, "google-user-1");
        assertInvalidInput(" ", "google-user-1");
        assertInvalidInput("g".repeat(256), "google-user-1");

        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("Invalid provider id is rejected before repository access")
    void linkSocialAccount_rejectsInvalidProviderIdBeforeRepositoryAccess() {
        assertInvalidInput("google", null);
        assertInvalidInput("google", " ");
        assertInvalidInput("google", "u".repeat(256));

        verifyNoInteractions(socialAccountRepository);
    }

    private void assertInvalidInput(String provider, String providerId) {
        assertThatThrownBy(() -> socialAccountLinkService.linkSocialAccount(user, provider, providerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }
}
