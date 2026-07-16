package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationTicketService {
    private static final String EXPIRED_CODE_MESSAGE_KEY = "validation.verification.code.expired";
    private static final String INVALID_CODE_MESSAGE_KEY = "validation.verification.code.invalid";
    private static final String USED_CODE_MESSAGE_KEY = "validation.verification.code.used";
    private static final String VERIFICATION_CODE_NOT_FOUND_MESSAGE =
            "인증 코드를 찾을 수 없습니다. 이메일을 변경했다면 다시 인증 코드를 발송해 주세요.";

    private final VerificationCodeRepository verificationCodeRepository;
    private final VerificationCodeAttemptService verificationCodeAttemptService;
    private final VerifyCodeResponseAssembler verifyCodeResponseAssembler;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VerifyCodeResponse verifyCode(String email, String code, VerificationPurpose purpose) {
        VerificationCodeAttemptService.AttemptResult result =
                verificationCodeAttemptService.attempt(email, code, purpose);
        return switch (result.status()) {
            case VERIFIED -> verifyCodeResponseAssembler.assemble(email, purpose, result.verificationTicket());
            case INVALID -> throw BusinessException.withMessageKey(
                    ErrorCode.VALIDATION_ERROR, INVALID_CODE_MESSAGE_KEY);
            case EXPIRED -> throw BusinessException.withMessageKey(
                    ErrorCode.VALIDATION_ERROR, EXPIRED_CODE_MESSAGE_KEY);
            case USED -> throw BusinessException.withMessageKey(
                    ErrorCode.VALIDATION_ERROR, USED_CODE_MESSAGE_KEY);
            case NOT_FOUND -> throw new BusinessException(
                    ErrorCode.NOT_FOUND, VERIFICATION_CODE_NOT_FOUND_MESSAGE);
        };
    }

    @Transactional
    public void consumeVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        VerificationCode verificationCode = getValidVerificationTicket(email, purpose, verificationTicket);
        verificationCode.consumeVerificationTicket();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        getValidVerificationTicketWithoutLock(email, purpose, verificationTicket);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeValidatedVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        VerificationCode verificationCode = getConsumableVerificationTicket(email, purpose, verificationTicket);
        verificationCode.consumeVerificationTicket();
    }

    private VerificationCode getValidVerificationTicket(
            String email,
            VerificationPurpose purpose,
            String verificationTicket) {
        VerificationCode verificationCode = getConsumableVerificationTicket(email, purpose, verificationTicket);

        if (verificationCode.isVerificationTicketExpiredAt(now())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return verificationCode;
    }

    private VerificationCode getValidVerificationTicketWithoutLock(
            String email,
            VerificationPurpose purpose,
            String verificationTicket) {
        VerificationCode verificationCode = getConsumableVerificationTicketWithoutLock(email, purpose, verificationTicket);

        if (verificationCode.isVerificationTicketExpiredAt(now())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return verificationCode;
    }

    private VerificationCode getConsumableVerificationTicket(
            String email,
            VerificationPurpose purpose,
            String verificationTicket) {
        VerificationCode verificationCode = verificationCodeRepository
                .findByEmailAndPurposeAndVerificationTicket(email, purpose, verificationTicket)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!Boolean.TRUE.equals(verificationCode.getIsVerified())
                || Boolean.TRUE.equals(verificationCode.getIsTicketConsumed())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return verificationCode;
    }

    private VerificationCode getConsumableVerificationTicketWithoutLock(
            String email,
            VerificationPurpose purpose,
            String verificationTicket) {
        VerificationCode verificationCode = verificationCodeRepository
                .findByEmailAndPurposeAndVerificationTicketWithoutLock(email, purpose, verificationTicket)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!Boolean.TRUE.equals(verificationCode.getIsVerified())
                || Boolean.TRUE.equals(verificationCode.getIsTicketConsumed())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return verificationCode;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

}
