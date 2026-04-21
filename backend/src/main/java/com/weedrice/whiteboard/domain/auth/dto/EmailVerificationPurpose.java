package com.weedrice.whiteboard.domain.auth.dto;

import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;

public enum EmailVerificationPurpose {
    SIGNUP(VerificationPurpose.SIGNUP),
    FIND_ID(VerificationPurpose.FIND_ID),
    PASSWORD_RESET(VerificationPurpose.PASSWORD_RESET),
    CHANGE_EMAIL(VerificationPurpose.CHANGE_EMAIL);

    private final VerificationPurpose purpose;

    EmailVerificationPurpose(VerificationPurpose purpose) {
        this.purpose = purpose;
    }

    public VerificationPurpose toPurpose() {
        return purpose;
    }
}
