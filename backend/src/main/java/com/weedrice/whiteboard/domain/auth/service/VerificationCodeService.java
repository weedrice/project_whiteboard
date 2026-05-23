package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeService {

    private final EmailEligibilityService emailEligibilityService;
    private final VerificationCodeDeliveryService verificationCodeDeliveryService;
    private final VerificationTicketService verificationTicketService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email, VerificationPurpose purpose, Long currentUserId) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        requirePurpose(purpose);
        validateEmailForPurpose(normalizedEmail, purpose, currentUserId);
        verificationCodeDeliveryService.sendVerificationCode(normalizedEmail, purpose);
    }

    @Transactional
    public VerifyCodeResponse verifyCode(String email, String code, VerificationPurpose purpose) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        requirePurpose(purpose);
        return verificationTicketService.verifyCode(normalizedEmail, code, purpose);
    }

    @Transactional
    public void consumeVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        requirePurpose(purpose);
        verificationTicketService.consumeVerificationTicket(normalizedEmail, purpose, verificationTicket);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        requirePurpose(purpose);
        verificationTicketService.validateVerificationTicket(normalizedEmail, purpose, verificationTicket);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeValidatedVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        requirePurpose(purpose);
        verificationTicketService.consumeValidatedVerificationTicket(normalizedEmail, purpose, verificationTicket);
    }

    private void requirePurpose(VerificationPurpose purpose) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "purpose is required");
        }
    }

    private void validateEmailForPurpose(String email, VerificationPurpose purpose, Long currentUserId) {
        switch (purpose) {
            case SIGNUP -> emailEligibilityService.validateSignupEmail(email);
            case FIND_ID -> emailEligibilityService.validateFindIdEmail(email);
            case PASSWORD_RESET -> emailEligibilityService.validatePasswordResetEmail(email);
            case CHANGE_EMAIL -> emailEligibilityService.validateChangeEmail(email, currentUserId);
        }
    }
}
