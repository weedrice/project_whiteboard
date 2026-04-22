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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("같은 사용자의 동일 외부 계정 링크는 기존 row를 재사용한다")
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
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 사용자에게 이미 연결된 외부 계정은 충돌로 거부한다")
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
    @DisplayName("같은 사용자의 동일 provider 중 다른 providerId 연결은 충돌로 거부한다")
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
    @DisplayName("저장 중 유니크 충돌이 발생해도 같은 링크면 재조회해 재사용한다")
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
        when(socialAccountRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate social account"));

        SocialAccount linkedAccount = socialAccountLinkService.linkSocialAccount(user, "google", "google-user-1");

        assertThat(linkedAccount).isSameAs(existingLink);
    }
}
