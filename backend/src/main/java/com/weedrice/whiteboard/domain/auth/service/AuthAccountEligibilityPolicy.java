package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AuthAccountEligibilityPolicy {

    public void validateUsableAccount(User user) {
        if (User.STATUS_DELETED.equals(user.getStatus()) || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }
}
