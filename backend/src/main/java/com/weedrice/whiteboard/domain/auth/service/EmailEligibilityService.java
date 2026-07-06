package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
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
    private final AuthEmailLookupPolicy authEmailLookupPolicy;
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
        User user = authEmailLookupPolicy.resolveFindIdEmail(email);
        authAccountEligibilityPolicy.validateUsableAccount(user);
    }

    public boolean canSendFindIdVerification(String email) {
        return canSendAccountLookupVerification(() -> validateFindIdEmail(email));
    }

    public void validatePasswordResetEmail(String email) {
        User user = authEmailLookupPolicy.resolvePasswordResetEmail(email);
        authAccountEligibilityPolicy.validateUsableAccount(user);
    }

    public boolean canSendPasswordResetVerification(String email) {
        return canSendAccountLookupVerification(() -> validatePasswordResetEmail(email));
    }

    private boolean canSendAccountLookupVerification(Runnable validator) {
        try {
            validator.run();
            return true;
        } catch (BusinessException ex) {
            if (isAccountLookupSuppressed(ex.getErrorCode())) {
                return false;
            }
            throw ex;
        }
    }

    private boolean isAccountLookupSuppressed(ErrorCode errorCode) {
        return errorCode == ErrorCode.USER_NOT_FOUND
                || errorCode == ErrorCode.USER_NOT_FOUND_BY_EMAIL
                || errorCode == ErrorCode.USER_DELETED
                || errorCode == ErrorCode.USER_NOT_ACTIVE;
    }
}
