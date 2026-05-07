package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class VerificationCodeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Test
    void findLatestSentByEmailAndPurpose_ordersByVerificationIdWhenCreatedAtTies() {
        VerificationCode first = persistCode("tie@example.com", VerificationPurpose.SIGNUP, "111111");
        VerificationCode second = persistCode("tie@example.com", VerificationPurpose.SIGNUP, "222222");
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        VerificationCode found = verificationCodeRepository
                .findLatestSentByEmailAndPurpose("tie@example.com", VerificationPurpose.SIGNUP.name())
                .orElseThrow();

        assertThat(found.getVerificationId()).isEqualTo(second.getVerificationId());
    }

    @Test
    void findLatestSentByEmailAndPurposeForUpdate_ordersByVerificationIdWhenCreatedAtTies() {
        VerificationCode first = persistCode("lock@example.com", VerificationPurpose.PASSWORD_RESET, "111111");
        VerificationCode second = persistCode("lock@example.com", VerificationPurpose.PASSWORD_RESET, "222222");
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        VerificationCode found = verificationCodeRepository
                .findLatestSentByEmailAndPurposeForUpdate("lock@example.com", VerificationPurpose.PASSWORD_RESET.name())
                .orElseThrow();

        assertThat(found.getVerificationId()).isEqualTo(second.getVerificationId());
    }

    @Test
    void invalidateActiveTickets_invalidatesOnlyMatchingActiveTickets() {
        LocalDateTime now = LocalDateTime.now();
        VerificationCode active = persistCode("user@example.com", VerificationPurpose.SIGNUP, "111111");
        active.issueVerificationTicket("ticket-active", now.plusMinutes(10));
        VerificationCode excluded = persistCode("user@example.com", VerificationPurpose.SIGNUP, "222222");
        excluded.issueVerificationTicket("ticket-excluded", now.plusMinutes(10));
        VerificationCode expired = persistCode("user@example.com", VerificationPurpose.SIGNUP, "333333");
        expired.issueVerificationTicket("ticket-expired", now.minusMinutes(1));
        VerificationCode consumed = persistCode("user@example.com", VerificationPurpose.SIGNUP, "444444");
        consumed.issueVerificationTicket("ticket-consumed", now.plusMinutes(10));
        consumed.consumeVerificationTicket();
        VerificationCode otherPurpose = persistCode("user@example.com", VerificationPurpose.PASSWORD_RESET, "555555");
        otherPurpose.issueVerificationTicket("ticket-purpose", now.plusMinutes(10));
        VerificationCode otherEmail = persistCode("other@example.com", VerificationPurpose.SIGNUP, "666666");
        otherEmail.issueVerificationTicket("ticket-email", now.plusMinutes(10));
        entityManager.flush();
        entityManager.clear();

        int updatedCount = verificationCodeRepository.invalidateActiveTickets(
                "user@example.com",
                VerificationPurpose.SIGNUP,
                excluded.getVerificationId(),
                now);
        entityManager.flush();
        entityManager.clear();

        assertThat(updatedCount).isEqualTo(1);
        assertThat(find(active).getVerificationTicket()).isNull();
        assertThat(find(active).getIsTicketConsumed()).isTrue();
        assertThat(find(excluded).getVerificationTicket()).isEqualTo("ticket-excluded");
        assertThat(find(expired).getVerificationTicket()).isEqualTo("ticket-expired");
        assertThat(find(consumed).getIsTicketConsumed()).isTrue();
        assertThat(find(otherPurpose).getVerificationTicket()).isEqualTo("ticket-purpose");
        assertThat(find(otherEmail).getVerificationTicket()).isEqualTo("ticket-email");
    }

    @Test
    void invalidateActiveTickets_invalidatesAllMatchingActiveTicketsWhenExcludeIsNull() {
        LocalDateTime now = LocalDateTime.now();
        VerificationCode first = persistCode("user@example.com", VerificationPurpose.FIND_ID, "111111");
        first.issueVerificationTicket("ticket-first", now.plusMinutes(10));
        VerificationCode second = persistCode("user@example.com", VerificationPurpose.FIND_ID, "222222");
        second.issueVerificationTicket("ticket-second", now.plusMinutes(10));
        entityManager.flush();
        entityManager.clear();

        int updatedCount = verificationCodeRepository.invalidateActiveTickets(
                "user@example.com",
                VerificationPurpose.FIND_ID,
                null,
                now);
        entityManager.flush();
        entityManager.clear();

        assertThat(updatedCount).isEqualTo(2);
        assertThat(find(first).getVerificationTicket()).isNull();
        assertThat(find(second).getVerificationTicket()).isNull();
    }

    private VerificationCode persistCode(String email, VerificationPurpose purpose, String code) {
        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .purpose(purpose)
                .code(code)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        verificationCode.markSent();
        entityManager.persist(verificationCode);
        return verificationCode;
    }

    private VerificationCode find(VerificationCode verificationCode) {
        return verificationCodeRepository.findById(verificationCode.getVerificationId()).orElseThrow();
    }

    private void updateCreatedAt(VerificationCode verificationCode, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE verification_codes SET created_at = :createdAt WHERE verification_id = :verificationId")
                .setParameter("createdAt", createdAt)
                .setParameter("verificationId", verificationCode.getVerificationId())
                .executeUpdate();
    }
}
