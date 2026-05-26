package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

public final class AuthenticatedUserResolver {

    private AuthenticatedUserResolver() {
    }

    public static Long optionalUserId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUserId();
    }

    public static Long requiredUserId(CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static CustomUserDetails requiredPrincipal(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails;
    }
}
