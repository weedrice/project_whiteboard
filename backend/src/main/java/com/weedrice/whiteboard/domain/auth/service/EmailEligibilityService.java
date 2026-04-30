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

    public void validateSignupEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!"DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
        });
    }

    public void validateChangeEmail(String email, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        userRepository.findByEmail(email)
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

    private boolean isSameUser(User user, Long currentUserId) {
        return user.getUserId() != null && user.getUserId().equals(currentUserId);
    }
}
