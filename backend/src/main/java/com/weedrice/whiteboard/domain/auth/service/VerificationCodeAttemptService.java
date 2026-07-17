package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class VerificationCodeAttemptService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final TokenHashService tokenHashService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AttemptResult attempt(String email, String rawCode, VerificationPurpose purpose) {
        VerificationCode verificationCode = verificationCodeRepository
                .findLatestSentByEmailAndPurposeForUpdate(email, purpose.name())
                .orElse(null);
        if (verificationCode == null) {
            return AttemptResult.notFound();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (verificationCode.isExpiredAt(now)) {
            return AttemptResult.expired();
        }

        boolean codeMatches = matchesVerificationCode(verificationCode.getCode(), rawCode);
        if (Boolean.TRUE.equals(verificationCode.getIsVerified())) {
            if (verificationCode.hasActiveVerificationTicketAt(now)) {
                return codeMatches
                        ? AttemptResult.verified(verificationCode.getVerificationTicket())
                        : AttemptResult.invalid();
            }
            return codeMatches ? AttemptResult.used() : AttemptResult.invalid();
        }

        if (verificationCode.hasExhaustedFailedAttempts()) {
            return AttemptResult.invalid();
        }
        if (!codeMatches) {
            verificationCode.recordFailedAttempt();
            return AttemptResult.invalid();
        }

        String verificationTicket = UUID.randomUUID().toString();
        verificationCodeRepository.invalidateActiveTickets(
                email, purpose, verificationCode.getVerificationId(), now);
        verificationCode.issueVerificationTicket(verificationTicket, now.plusMinutes(10));
        return AttemptResult.verified(verificationTicket);
    }

    private boolean matchesVerificationCode(String storedCode, String rawCode) {
        if (storedCode == null || rawCode == null) {
            return false;
        }
        if (storedCode.length() == VerificationCode.LEGACY_PLAIN_CODE_LENGTH
                && storedCode.chars().allMatch(Character::isDigit)) {
            return storedCode.equals(rawCode);
        }
        return storedCode.equals(tokenHashService.hashSha256(rawCode));
    }

    enum Status {
        VERIFIED,
        INVALID,
        EXPIRED,
        USED,
        NOT_FOUND
    }

    record AttemptResult(Status status, String verificationTicket) {
        static AttemptResult verified(String ticket) {
            return new AttemptResult(Status.VERIFIED, ticket);
        }

        static AttemptResult invalid() {
            return new AttemptResult(Status.INVALID, null);
        }

        static AttemptResult expired() {
            return new AttemptResult(Status.EXPIRED, null);
        }

        static AttemptResult used() {
            return new AttemptResult(Status.USED, null);
        }

        static AttemptResult notFound() {
            return new AttemptResult(Status.NOT_FOUND, null);
        }
    }
}
