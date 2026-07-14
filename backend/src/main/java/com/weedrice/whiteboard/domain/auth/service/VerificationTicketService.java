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
import java.util.Optional;
import java.util.UUID;

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
    private final TokenHashService tokenHashService;
    private final VerifyCodeResponseAssembler verifyCodeResponseAssembler;
    private final Clock clock;

    @Transactional
    public VerifyCodeResponse verifyCode(String email, String code, VerificationPurpose purpose) {
        VerificationCode verificationCode = getLatestSentVerificationCodeForUpdate(email, purpose);

        boolean codeMatches = matchesVerificationCode(verificationCode.getCode(), code);

        LocalDateTime now = now();

        if (verificationCode.isExpiredAt(now)) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, EXPIRED_CODE_MESSAGE_KEY);
        }

        if (Boolean.TRUE.equals(verificationCode.getIsVerified())) {
            if (verificationCode.hasActiveVerificationTicketAt(now)) {
                if (!codeMatches) {
                    throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, INVALID_CODE_MESSAGE_KEY);
                }
                return verifyCodeResponseAssembler.assemble(email, purpose, verificationCode.getVerificationTicket());
            }
            if (!codeMatches) {
                throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, INVALID_CODE_MESSAGE_KEY);
            }
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, USED_CODE_MESSAGE_KEY);
        }

        if (!codeMatches) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, INVALID_CODE_MESSAGE_KEY);
        }

        String verificationTicket = UUID.randomUUID().toString();
        invalidateOutstandingTickets(email, purpose, verificationCode.getVerificationId());
        verificationCode.issueVerificationTicket(verificationTicket, now.plusMinutes(10));

        return verifyCodeResponseAssembler.assemble(email, purpose, verificationTicket);
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

    private void invalidateOutstandingTickets(String email, VerificationPurpose purpose, Long excludeVerificationId) {
        verificationCodeRepository.invalidateActiveTickets(
                email,
                purpose,
                excludeVerificationId,
                now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private VerificationCode getLatestSentVerificationCodeForUpdate(String email, VerificationPurpose purpose) {
        return findLatestSentVerificationCodeForUpdate(email, purpose)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        VERIFICATION_CODE_NOT_FOUND_MESSAGE));
    }

    private Optional<VerificationCode> findLatestSentVerificationCodeForUpdate(
            String email,
            VerificationPurpose purpose) {
        return verificationCodeRepository.findLatestSentByEmailAndPurposeForUpdate(email, purpose.name());
    }

    private boolean matchesVerificationCode(String storedCode, String rawCode) {
        if (storedCode == null || rawCode == null) {
            return false;
        }
        if (isLegacyPlainCode(storedCode)) {
            return storedCode.equals(rawCode);
        }
        return storedCode.equals(hashVerificationCode(rawCode));
    }

    private boolean isLegacyPlainCode(String code) {
        return code.length() == VerificationCode.LEGACY_PLAIN_CODE_LENGTH
                && code.chars().allMatch(Character::isDigit);
    }

    private String hashVerificationCode(String code) {
        return tokenHashService.hashSha256(code);
    }
}
