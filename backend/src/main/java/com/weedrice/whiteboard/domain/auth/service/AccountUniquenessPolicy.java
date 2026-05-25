package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountUniquenessPolicy {

    private final UserRepository userRepository;

    public Optional<User> findReregisterableSignupUser(String email) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        return userRepository.findByEmail(normalizedEmail)
                .map(user -> {
                    if (User.STATUS_DELETED.equals(user.getStatus())) {
                        return user;
                    }
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                });
    }

    public void validateSignupEmailAvailable(String email) {
        findReregisterableSignupUser(email);
    }

    public void validateLoginIdAvailable(String loginId) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
    }

    public RuntimeException resolveSignupConflict(String email, String loginId, RuntimeException ex) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByLoginId(loginId)) {
            return new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        return ex;
    }

    public void validateChangeEmailAvailable(String email, Long currentUserId) {
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

    public void validateChangeEmailAvailable(String email, User currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateChangeEmailAvailable(email, currentUser.getUserId());
    }

    public RuntimeException resolveChangeEmailConflict(String email, Long currentUserId, RuntimeException ex) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        if (userRepository.findByEmail(normalizedEmail)
                .filter(other -> !isSameUser(other, currentUserId))
                .isPresent()) {
            return new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        return ex;
    }

    private boolean isSameUser(User user, Long currentUserId) {
        return user.getUserId() != null && user.getUserId().equals(currentUserId);
    }
}
