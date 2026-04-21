package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.email.EmailService;
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

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email, VerificationPurpose purpose, Long currentUserId) {
        validateEmailForPurpose(email, purpose, currentUserId);

        String code = generateRandomCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5);
        Long verificationId = createPendingVerificationCode(email, purpose, code, expiryDate);

        String subject = "[noviIs] ?대찓???몄쬆 肄붾뱶";
        String body = "<h1>?대찓???몄쬆 肄붾뱶</h1><p>?꾨옒 肄붾뱶瑜??낅젰?섏뿬 ?몄쬆???꾨즺??二쇱꽭??</p><h3>" + code + "</h3>";

        try {
            emailService.sendEmail(email, subject, body);
            promotePendingVerificationCode(verificationId, email, purpose, code, expiryDate);
            invalidateOutstandingTickets(email, purpose, null);
        } catch (RuntimeException e) {
            updateDeliveryStatus(verificationId, false);
            throw e;
        }
    }

    @Transactional
    public VerifyCodeResponse verifyCode(String email, String code, VerificationPurpose purpose) {
        VerificationCode verificationCode = getLatestSentVerificationCodeForUpdate(email, purpose);

        if (verificationCode.isExpired()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "留뚮즺???몄쬆 肄붾뱶?낅땲??");
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "?섎せ???몄쬆 肄붾뱶?낅땲??");
        }

        if (Boolean.TRUE.equals(verificationCode.getIsVerified())) {
            if (verificationCode.hasActiveVerificationTicket()) {
                return buildVerifyCodeResponse(email, purpose, verificationCode.getVerificationTicket());
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "?대? ?ъ슜???몄쬆 肄붾뱶?낅땲??");
        }

        String verificationTicket = UUID.randomUUID().toString();
        invalidateOutstandingTickets(email, purpose, verificationCode.getVerificationId());
        verificationCode.issueVerificationTicket(verificationTicket, LocalDateTime.now().plusMinutes(10));

        return buildVerifyCodeResponse(email, purpose, verificationTicket);
    }

    @Transactional
    public void consumeVerificationTicket(String email, VerificationPurpose purpose, String verificationTicket) {
        VerificationCode verificationCode = verificationCodeRepository
                .findByEmailAndPurposeAndVerificationTicket(email, purpose, verificationTicket)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!Boolean.TRUE.equals(verificationCode.getIsVerified())
                || Boolean.TRUE.equals(verificationCode.getIsTicketConsumed())
                || verificationCode.isVerificationTicketExpired()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        verificationCode.consumeVerificationTicket();
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
            validateSignupEmail(email);
            return;
        }
        if (purpose == VerificationPurpose.CHANGE_EMAIL) {
            validateChangeEmail(email, currentUserId);
        }
    }

    private void validateSignupEmail(String email) {
        userRepository.findByEmail(email).ifPresent(other -> {
            if ("ACTIVE".equals(other.getStatus()) && Boolean.TRUE.equals(other.getIsEmailVerified())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
        });
    }

    private void validateChangeEmail(String email, Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        userRepository.findByEmail(email).ifPresent(other -> {
            if (!other.getUserId().equals(currentUserId)
                    && "ACTIVE".equals(other.getStatus())
                    && Boolean.TRUE.equals(other.getIsEmailVerified())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
        });
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

    private void promotePendingVerificationCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        try {
            updateDeliveryStatus(verificationId, true);
        } catch (RuntimeException e) {
            saveReplacementSentVerificationCode(email, purpose, code, expiryDate);
        }
    }

    private void saveReplacementSentVerificationCode(
            String email,
            VerificationPurpose purpose,
            String code,
            LocalDateTime expiryDate) {
        transactionTemplate.executeWithoutResult(status -> {
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

    private void invalidateOutstandingTickets(String email, VerificationPurpose purpose, Long excludeVerificationId) {
        verificationCodeRepository.findAllByEmailAndPurpose(email, purpose).stream()
                .filter(code -> excludeVerificationId == null || !code.getVerificationId().equals(excludeVerificationId))
                .filter(VerificationCode::hasActiveVerificationTicket)
                .forEach(VerificationCode::invalidateVerificationTicket);
    }

    private VerificationCode getLatestSentVerificationCodeForUpdate(String email, VerificationPurpose purpose) {
        return findLatestSentVerificationCodeForUpdate(email, purpose)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "?몄쬆 肄붾뱶瑜?李얠쓣 ???놁뒿?덈떎. ?대찓?쇱쓣 蹂寃쏀뻽?ㅻ㈃ ?ㅼ떆 ?몄쬆 肄붾뱶瑜?諛쒖넚??二쇱꽭??"));
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
