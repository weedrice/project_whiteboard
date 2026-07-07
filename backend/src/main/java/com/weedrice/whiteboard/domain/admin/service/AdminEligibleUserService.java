package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEligibleUserService {

    private static final int LOGIN_ID_MAX_LENGTH = 30;

    private final UserRepository userRepository;

    public User getActiveUserByLoginId(String loginId) {
        String normalizedLoginId = TextInputNormalizer.normalizeRequired(loginId, LOGIN_ID_MAX_LENGTH);
        User user = userRepository.findByLoginId(normalizedLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveUser(user);
        return user;
    }

    public void validateActiveUser(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }
}
