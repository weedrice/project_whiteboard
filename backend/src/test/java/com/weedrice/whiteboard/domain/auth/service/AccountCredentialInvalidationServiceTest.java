package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AccountCredentialInvalidationServiceTest {

    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AccountCredentialInvalidationService accountCredentialInvalidationService;

    @Test
    void invalidateForLockedUser_removesSocialLinksAndInvalidatesUnusedResetTokens() {
        User user = User.builder().build();

        accountCredentialInvalidationService.invalidateForLockedUser(user);

        var inOrder = inOrder(socialAccountRepository, passwordResetTokenRepository);
        inOrder.verify(socialAccountRepository).deleteAllByUser(user);
        inOrder.verify(passwordResetTokenRepository).invalidateAllUnusedTokens(user);
    }
}
