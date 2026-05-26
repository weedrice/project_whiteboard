package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminPolicy {

    private final UserRepository userRepository;

    public void requireUsableSuperAdmin(Long actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!actor.isUsableSuperAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
