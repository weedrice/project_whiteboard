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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("Same user's existing provider link is reused")
    void linkSocialAccount_reusesExistingProviderLink() {
        SocialAccount existingLink = SocialAccount.builder()
                .user(user)
                .provider("google")
                .providerId("google-user-1")
                .build();

        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-1"))
                .thenReturn(Optional.of(existingLink));

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

        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-1"))
                .thenReturn(Optional.of(existingLink));

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

        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-2"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.findByUserAndProvider(user, "google"))
                .thenReturn(Optional.of(existingLink));

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

        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-1"))
                .thenReturn(Optional.empty(), Optional.of(savedLink));
        when(socialAccountRepository.findByUserAndProvider(user, "google"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(1);

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

        when(socialAccountRepository.findByProviderAndProviderId("google", "google-user-1"))
                .thenReturn(Optional.empty(), Optional.of(existingLink));
        when(socialAccountRepository.findByUserAndProvider(user, "google"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.insertSocialAccountIfAbsent(1L, "google", "google-user-1"))
                .thenReturn(0);

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
        verify(socialAccountRepository).insertSocialAccountIfAbsent(1L, "google", "google-user-1");
    }
}
