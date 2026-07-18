package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountCredentialInvalidationService {

    private final SocialAccountRepository socialAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateForLockedUser(User user) {
        socialAccountRepository.deleteAllByUser(user);
        passwordResetTokenRepository.invalidateAllUnusedTokens(user);
    }
}
