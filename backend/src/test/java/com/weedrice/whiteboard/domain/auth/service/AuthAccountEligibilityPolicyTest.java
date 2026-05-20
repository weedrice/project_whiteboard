package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAccountEligibilityPolicyTest {

    private final AuthAccountEligibilityPolicy policy = new AuthAccountEligibilityPolicy();

    @Test
    void validateUsableAccount_allowsActiveAccount() {
        assertThatCode(() -> policy.validateUsableAccount(user(User.STATUS_ACTIVE, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUsableAccount_rejectsDeletedStatus() {
        assertThatThrownBy(() -> policy.validateUsableAccount(user(User.STATUS_DELETED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);
    }

    @Test
    void validateUsableAccount_rejectsDeletedAt() {
        assertThatThrownBy(() -> policy.validateUsableAccount(user(User.STATUS_ACTIVE, LocalDateTime.now())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);
    }

    @Test
    void validateUsableAccount_rejectsInactiveStatus() {
        assertThatThrownBy(() -> policy.validateUsableAccount(user(User.STATUS_SUSPENDED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    private User user(String status, LocalDateTime deletedAt) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "status", status);
        ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
        return user;
    }
}
