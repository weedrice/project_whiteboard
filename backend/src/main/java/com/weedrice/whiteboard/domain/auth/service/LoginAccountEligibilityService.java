package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginAccountEligibilityService {

    public static final String FAILURE_REASON_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String FAILURE_REASON_USER_NOT_ACTIVE = "USER_NOT_ACTIVE";
    public static final String FAILURE_REASON_USER_BANNED = "USER_BANNED";

    private final SanctionPolicyService sanctionPolicyService;

    public LoginAccountEligibility evaluate(User user) {
        if (user == null) {
            return new LoginAccountEligibility(false, false, FAILURE_REASON_USER_NOT_FOUND);
        }

        boolean activeAccount = user.isActiveAccount();
        if (!activeAccount) {
            return new LoginAccountEligibility(false, true, FAILURE_REASON_USER_NOT_ACTIVE);
        }

        boolean banned = sanctionPolicyService.isUserBanned(user);
        if (banned) {
            return new LoginAccountEligibility(true, false, FAILURE_REASON_USER_BANNED);
        }

        return new LoginAccountEligibility(true, true, null);
    }

    public record LoginAccountEligibility(
            boolean enabled,
            boolean accountNonLocked,
            String failureReason) {

        public boolean isLoginAllowed() {
            return enabled && accountNonLocked;
        }
    }
}
