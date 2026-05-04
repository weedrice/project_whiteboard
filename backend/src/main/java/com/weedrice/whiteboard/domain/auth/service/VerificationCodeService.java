package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String VERIFICATION_EMAIL_SUBJECT = "[NoviIs] 이메일 인증 코드";
    private static final String VERIFICATION_EMAIL_BODY_TEMPLATE =
            "<h1>이메일 인증 코드</h1><p>아래 인증 코드를 입력하여 이메일 인증을 완료해 주세요.</p><h3>%s</h3>";
    private static final String EXPIRED_CODE_MESSAGE = "만료된 인증 코드입니다.";
    private static final String INVALID_CODE_MESSAGE = "잘못된 인증 코드입니다.";
    private static final String USED_CODE_MESSAGE = "이미 사용된 인증 코드입니다.";
    private static final String VERIFICATION_CODE_NOT_FOUND_MESSAGE =
            "인증 코드를 찾을 수 없습니다. 이메일을 변경했다면 다시 인증 코드를 발송해 주세요.";

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final EmailEligibilityService emailEligibilityService;
    private final AuthMailDeliveryOrchestrationService mailDeliveryOrchestrationService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email, VerificationPurpose purpose, Long currentUserId) {
        validateEmailForPurpose(email, purpose, currentUserId);

        String code = generateRandomCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5);
        String body = VERIFICATION_EMAIL_BODY_TEMPLATE.formatted(code);

        mailDeliveryOrchestrationService.send(new AuthMailDeliveryOrchestrationService.MailDeliveryCommand(
                email,
                VERIFICATION_EMAIL_SUBJECT,
                body,
                () -> createPendingVerificationCode(email, purpose, code, expiryDate),
                verificationId -> updateDeliveryStatus(verificationId, false),
                verificationId -> promotePendingVerificationCode(verificationId, email, purpose, code, expiryDate),
                (verificationId, e) -> markCurrentVerificationCodeSentAfterPromotionFailure(
                        verificationId,
                        email,
                        purpose,
                        e)));
    }

    @Transactional
    public VerifyCodeResponse verifyCode(String email, String code, VerificationPurpose purpose) {
        VerificationCode verificationCode = getLatestSentVerificationCodeForUpdate(email, purpose);

        if (verificationCode.isExpired()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, EXPIRED_CODE_MESSAGE);
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, INVALID_CODE_MESSAGE);
        }

        if (Boolean.TRUE.equals(verificationCode.getIsVerified())) {
            if (verificationCode.hasActiveVerificationTicket()) {
                return buildVerifyCodeResponse(email, purpose, verificationCode.getVerificationTicket());
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, USED_CODE_MESSAGE);
        }

        String verificationTicket = UUID.randomUUID().toString();
        invalidateOutstandingTickets(email, purpose, verificationCode.getVerificationId());
        verificationCode.issueVerificationTicket(verificationTicket, LocalDateTime.now().plusMinutes(10));

        return buildVerifyCodeResponse(email, purpose, verificationTicket);
    }

    @Transactional
    public void consumeVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        VerificationCode verificationCode = getValidVerificationTicket(email, purpose, verificationTicket);
        verificationCode.consumeVerificationTicket();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        getValidVerificationTicket(email, purpose, verificationTicket);
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

        if (verificationCode.isVerificationTicketExpired()) {
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

    private VerifyCodeResponse buildVerifyCodeResponse(
            String email,
            VerificationPurpose purpose,
            String verificationTicket) {
        VerifyCodeResponse.VerifyCodeResponseBuilder builder = VerifyCodeResponse.builder()
                .verified(true)
                .verificationTicket(verificationTicket)
                .isReregister(false);

        if (purpose == VerificationPurpose.SIGNUP) {
            userRepository.findByEmail(email)
                    .filter(user -> "DELETED".equals(user.getStatus()))
                    .ifPresent(user -> builder
                            .loginId(user.getLoginId())
                            .isReregister(true));
        }

        return builder.build();
    }

    private void validateEmailForPurpose(String email, VerificationPurpose purpose, Long currentUserId) {
        if (purpose == VerificationPurpose.SIGNUP) {
            emailEligibilityService.validateSignupEmail(email);
            return;
        }
        if (purpose == VerificationPurpose.CHANGE_EMAIL) {
            emailEligibilityService.validateChangeEmail(email, currentUserId);
        }
    }

    private Long createPendingVerificationCode(
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        final Long[] verificationIdHolder = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            VerificationCode verificationCode = VerificationCode.builder()
                    .email(email)
                    .purpose(purpose)
                    .code(code)
                    .expiryDate(expiryDate)
                    .build();
            verificationIdHolder[0] = verificationCodeRepository.save(verificationCode).getVerificationId();
        });
        return verificationIdHolder[0];
    }

    private void updateDeliveryStatus(Long verificationId, boolean sent) {
        transactionTemplate.executeWithoutResult(status -> verificationCodeRepository.findById(verificationId)
                .ifPresent(verificationCode -> {
                    if (sent) {
                        verificationCode.markSent();
                    } else {
                        verificationCode.markFailed();
                    }
                    verificationCodeRepository.save(verificationCode);
                }));
    }

    private void markSentAfterInvalidatingOutstandingTickets(Long verificationId, String email, VerificationPurpose purpose) {
        transactionTemplate.executeWithoutResult(status -> {
            invalidateOutstandingTickets(email, purpose, verificationId);
            verificationCodeRepository.findById(verificationId)
                    .ifPresent(verificationCode -> {
                        verificationCode.markSent();
                        verificationCodeRepository.save(verificationCode);
                    });
        });
    }

    private void promotePendingVerificationCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        try {
            markSentAfterInvalidatingOutstandingTickets(verificationId, email, purpose);
        } catch (RuntimeException e) {
            saveReplacementSentVerificationCodeAfterInvalidatingOutstandingTickets(email, purpose, code, expiryDate);
        }
    }

    private void saveReplacementSentVerificationCodeAfterInvalidatingOutstandingTickets(
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        transactionTemplate.executeWithoutResult(status -> {
            invalidateOutstandingTickets(email, purpose, null);
            VerificationCode replacement = VerificationCode.builder()
                    .email(email)
                    .purpose(purpose)
                    .code(code)
                    .expiryDate(expiryDate)
                    .build();
            replacement.markSent();
            verificationCodeRepository.save(replacement);
        });
    }

    private void markCurrentVerificationCodeSentAfterPromotionFailure(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            RuntimeException promotionException) {
        try {
            markSentAfterInvalidatingOutstandingTickets(verificationId, email, purpose);
        } catch (RuntimeException statusUpdateException) {
            promotionException.addSuppressed(statusUpdateException);
            throw promotionException;
        }
    }

    private void invalidateOutstandingTickets(String email, VerificationPurpose purpose, Long excludeVerificationId) {
        verificationCodeRepository.invalidateActiveTickets(
                email,
                purpose,
                excludeVerificationId,
                LocalDateTime.now());
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

    private String generateRandomCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
