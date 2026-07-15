package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeDeliveryService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String VERIFICATION_EMAIL_SUBJECT = "[NoviIs] 이메일 인증 코드";
    private static final String VERIFICATION_EMAIL_BODY =
            "<h1>이메일 인증 코드</h1><p>아래 인증 코드를 입력하여 이메일 인증을 완료해 주세요.</p><h3>{0}</h3>";
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int SEND_ATTEMPT_WINDOW_HOURS = 1;
    private static final int MAX_SEND_ATTEMPTS_PER_WINDOW = 5;
    private static final int VERIFICATION_SEND_LOCK_STRIPES = 64;

    private final VerificationCodeRepository verificationCodeRepository;
    private final AuthMailDeliveryOrchestrationService mailDeliveryOrchestrationService;
    private final TokenHashService tokenHashService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ReentrantLock[] verificationSendLocks = createVerificationSendLocks();
    private final MessageSource messageSource;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email, VerificationPurpose purpose) {
        String code = generateRandomCode();
        LocalDateTime expiryDate = now().plusMinutes(5);
        var locale = LocaleContextHolder.getLocale();
        String subject = messageSource.getMessage(
                "mail.verification.subject", null, VERIFICATION_EMAIL_SUBJECT, locale);
        String body = messageSource.getMessage(
                "mail.verification.body", new Object[]{code}, VERIFICATION_EMAIL_BODY, locale);

        mailDeliveryOrchestrationService.send(new AuthMailDeliveryOrchestrationService.MailDeliveryCommand(
                email,
                subject,
                body,
                () -> createPendingVerificationCode(email, purpose, code, expiryDate),
                this::markDeliveryFailed,
                verificationId -> promotePendingVerificationCode(
                        verificationId,
                        email,
                        purpose,
                        code,
                        expiryDate),
                (verificationId, e) -> markCurrentVerificationCodeSentAfterPromotionFailure(
                        verificationId,
                        email,
                        purpose,
                        e)));
    }

    private Long createPendingVerificationCode(
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        ReentrantLock sendLock = getVerificationSendLock(email, purpose);
        sendLock.lock();
        try {
            return createPendingVerificationCodeWithLock(email, purpose, code, expiryDate);
        } finally {
            sendLock.unlock();
        }
    }

    private Long createPendingVerificationCodeWithLock(
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        final Long[] verificationIdHolder = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            validateVerificationSendRateLimit(email, purpose, now());
            VerificationCode verificationCode = VerificationCode.builder()
                    .email(email)
                    .purpose(purpose)
                    .code(hashVerificationCode(code))
                    .expiryDate(expiryDate)
                    .build();
            verificationIdHolder[0] = verificationCodeRepository.save(verificationCode).getVerificationId();
        });
        return verificationIdHolder[0];
    }

    private static ReentrantLock[] createVerificationSendLocks() {
        ReentrantLock[] locks = new ReentrantLock[VERIFICATION_SEND_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private ReentrantLock getVerificationSendLock(String email, VerificationPurpose purpose) {
        int lockIndex = Math.floorMod(Objects.hash(email, purpose), verificationSendLocks.length);
        return verificationSendLocks[lockIndex];
    }

    private void validateVerificationSendRateLimit(
            String email,
            VerificationPurpose purpose,
            LocalDateTime now) {
        String purposeName = purpose.name();
        LocalDateTime cooldownThreshold = now.minusSeconds(RESEND_COOLDOWN_SECONDS);
        verificationCodeRepository.findLatestDeliveryAttemptCreatedAt(email, purposeName)
                .filter(latestAttemptAt -> latestAttemptAt.isAfter(cooldownThreshold))
                .ifPresent(latestAttemptAt -> {
                    throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
                });

        LocalDateTime windowStart = now.minusHours(SEND_ATTEMPT_WINDOW_HOURS);
        long recentAttemptCount = verificationCodeRepository.countDeliveryAttemptsSince(
                email,
                purposeName,
                windowStart);
        if (recentAttemptCount >= MAX_SEND_ATTEMPTS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private void markDeliveryFailed(Long verificationId) {
        transactionTemplate.executeWithoutResult(status -> verificationCodeRepository.findById(verificationId)
                .ifPresent(verificationCode -> {
                    verificationCode.markFailed();
                    verificationCodeRepository.save(verificationCode);
                }));
    }

    private void markSentAfterInvalidatingOutstandingTickets(
            Long verificationId,
            String email,
            VerificationPurpose purpose) {
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
                    .code(hashVerificationCode(code))
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
                now());
    }

    private String hashVerificationCode(String code) {
        return tokenHashService.hashSha256(code);
    }

    private String generateRandomCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
