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

    private final UserRepository userRepository;
    private final AuthAccountEligibilityPolicy authAccountEligibilityPolicy;

    public void validateSignupEmail(String email) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            if (!"DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
        });
    }

    public void validateChangeEmail(String email, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String normalizedEmail = AuthEmailNormalizer.normalize(email);

        userRepository.findByEmail(normalizedEmail)
                .filter(other -> !isSameUser(other, currentUserId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                });
    }

    public void validateChangeEmail(String email, User currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateChangeEmail(email, currentUser.getUserId());
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

    private boolean isSameUser(User user, Long currentUserId) {
        return user.getUserId() != null && user.getUserId().equals(currentUserId);
    }
}
