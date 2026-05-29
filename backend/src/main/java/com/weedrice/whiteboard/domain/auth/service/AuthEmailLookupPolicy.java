package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthEmailLookupPolicy {

    private final UserRepository userRepository;

    public User resolveFindIdEmail(String email) {
        return resolveByEmail(email, ErrorCode.USER_NOT_FOUND);
    }

    public User resolvePasswordResetEmail(String email) {
        return resolveByEmail(email, ErrorCode.USER_NOT_FOUND_BY_EMAIL);
    }

    private User resolveByEmail(String email, ErrorCode notFoundErrorCode) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(notFoundErrorCode));
    }
}
