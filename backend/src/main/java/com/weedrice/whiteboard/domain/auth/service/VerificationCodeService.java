package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /**
     * 이메일 인증 코드 발송.
     * 중복 검사는 is_email_verified=Y인 사용자만 대상으로 함 (미인증 이메일은 변경·재인증 가능).
     * @param email 수신 이메일 (형식 검증은 DTO @Email에서 수행)
     * @param forSignup true이면 회원가입용 - 이미 인증 완료된 이메일이면 DUPLICATE_EMAIL throw
     * @param currentUserId 로그인한 사용자 ID (마이페이지 등). null이면 비로그인. 마이페이지인 경우 해당 이메일을 인증 완료한 다른 회원이 있으면 DUPLICATE_EMAIL throw
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email, boolean forSignup, Long currentUserId) {
        if (forSignup) {
            // ACTIVE 사용자 이메일만 중복. DELETED는 재가입 허용으로 통과
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                User other = existing.get();
                if ("ACTIVE".equals(other.getStatus()) && Boolean.TRUE.equals(other.getIsEmailVerified())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                }
            }
        } else if (currentUserId != null) {
            // 마이페이지: ACTIVE+인증완료인 다른 회원 이메일이면 중복. 본인이거나 DELETED/미인증은 허용
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                User other = existing.get();
                if (!other.getUserId().equals(currentUserId)
                        && "ACTIVE".equals(other.getStatus())
                        && Boolean.TRUE.equals(other.getIsEmailVerified())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                }
            }
        }

        String code = generateRandomCode();

        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5); // 5분 유효

            VerificationCode verificationCode = VerificationCode.builder()
                    .email(email)
                    .code(code)
                    .expiryDate(expiryDate)
                    .build();

            verificationCodeRepository.save(verificationCode);
        });

        String subject = "[noviIs] 이메일 인증 코드";
        String body = "<h1>이메일 인증 코드</h1><p>아래 코드를 입력하여 인증을 완료해주세요.</p><h3>" + code + "</h3>";

        emailService.sendEmail(email, subject, body);
    }

    @Transactional
    public VerifyCodeResponse verifyCode(String email, String code) {
        VerificationCode verificationCode = verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "인증 코드를 찾을 수 없습니다. 이메일을 변경하셨다면 다시 인증 코드를 발송해주세요."));

        if (verificationCode.isExpired()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "만료된 인증 코드입니다.");
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "잘못된 인증 코드입니다.");
        }

        verificationCode.verify();

        // 재가입 시: DELETED 사용자 이메일이면 loginId 반환 (마스킹 해제용)
        return userRepository.findByEmail(email)
                .filter(user -> "DELETED".equals(user.getStatus()))
                .map(user -> VerifyCodeResponse.builder()
                        .verified(true)
                        .loginId(user.getLoginId())
                        .isReregister(true)
                        .build())
                .orElse(VerifyCodeResponse.builder().verified(true).isReregister(false).build());
    }

    public boolean isVerified(String email) {
        return verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .map(code -> code.getIsVerified() && !code.isExpired())
                .orElse(false);
    }

    @Transactional
    public void clearVerificationStatus(String email) {
        verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .ifPresent(code -> {
                    if (code.getIsVerified()) {
                        code.clearVerification();
                        verificationCodeRepository.save(code);
                    }
                });
    }

    private String generateRandomCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
