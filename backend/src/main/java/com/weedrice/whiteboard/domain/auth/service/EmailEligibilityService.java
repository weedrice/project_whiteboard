package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailEligibilityService {

    private final AccountUniquenessPolicy accountUniquenessPolicy;
    private final UserRepository userRepository;
    private final AuthAccountEligibilityPolicy authAccountEligibilityPolicy;

    public void validateSignupEmail(String email) {
        accountUniquenessPolicy.validateSignupEmailAvailable(email);
    }

    public void validateChangeEmail(String email, Long currentUserId) {
        accountUniquenessPolicy.validateChangeEmailAvailable(email, currentUserId);
    }

    public void validateChangeEmail(String email, User currentUser) {
        accountUniquenessPolicy.validateChangeEmailAvailable(email, currentUser);
    }

    public void validateFindIdEmail(String email) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        authAccountEligibilityPolicy.validateUsableAccount(user);
    }

    public void validatePasswordResetEmail(String email) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_EMAIL));
        authAccountEligibilityPolicy.validateUsableAccount(user);
    }

}
