package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);
    void deleteByExpiryDateBefore(LocalDateTime now);
}
