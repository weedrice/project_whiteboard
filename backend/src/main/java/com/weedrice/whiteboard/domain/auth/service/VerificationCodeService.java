package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;

    @Transactional
    public void sendVerificationCode(String email) {
        String code = generateRandomCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5); // 5분 유효

        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .code(code)
                .expiryDate(expiryDate)
                .build();

        verificationCodeRepository.save(verificationCode);

        String subject = "[Whiteboard] 이메일 인증 코드";
        String body = "<h1>이메일 인증 코드</h1><p>아래 코드를 입력하여 인증을 완료해주세요.</p><h3>" + code + "</h3>";

        emailService.sendEmail(email, subject, body);
    }

    @Transactional
    public void verifyCode(String email, String code) {
        VerificationCode verificationCode = verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "인증 코드를 찾을 수 없습니다."));

        if (verificationCode.isExpired()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "만료된 인증 코드입니다.");
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "잘못된 인증 코드입니다.");
        }

        verificationCode.verify();
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
