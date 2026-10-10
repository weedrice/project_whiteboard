package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetServicePersistenceTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private VerificationCodeRepository verificationCodeRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final TokenHashService tokenHashService = new TokenHashService();
    private PasswordHistoryPolicy passwordHistoryPolicy;
    private RefreshTokenLifecycleService refreshTokenLifecycleService;
    private Fixture persistedFixture;

    @BeforeEach
    void setUp() {
        passwordHistoryPolicy = mock(PasswordHistoryPolicy.class);
        refreshTokenLifecycleService = mock(RefreshTokenLifecycleService.class);
    }

    @AfterEach
    void cleanUp() {
        if (persistedFixture == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(ignored -> {
            passwordResetTokenRepository.deleteById(persistedFixture.tokenId());
            passwordResetTokenRepository.deleteById(persistedFixture.pendingTokenId());
            passwordResetTokenRepository.flush();
            if (persistedFixture.verificationId() != null) {
                verificationCodeRepository.deleteById(persistedFixture.verificationId());
            }
            userRepository.deleteById(persistedFixture.userId());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetPasswordWithToken_rejectsConsumptionCommittedBetweenCandidateReadAndUserLock(
            boolean linkedVerification) {
        Fixture fixture = persistFixture(linkedVerification);
        UserRepository interceptedUserRepository = mock(UserRepository.class, delegatesTo(userRepository));
        PasswordResetService service = service(interceptedUserRepository);
        TransactionTemplate competingTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        competingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicBoolean competingResetStarted = new AtomicBoolean();
        when(passwordHistoryPolicy.encode("first-password")).thenReturn("first-password-hash");
        when(passwordHistoryPolicy.encode("second-password")).thenReturn("second-password-hash");

        doAnswer(invocation -> {
            if (competingResetStarted.compareAndSet(false, true)) {
                // Deterministically commit a real reset after the outer candidate read, before its lock.
                // Separate JPA persistence contexts reproduce the stale-entity failure without thread timing.
                competingTransaction.executeWithoutResult(ignored ->
                        service.resetPasswordWithToken(fixture.rawToken(), "first-password"));
            }
            return userRepository.findByIdForUpdate(fixture.userId());
        }).when(interceptedUserRepository).findByIdForUpdate(fixture.userId());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored ->
                service.resetPasswordWithToken(fixture.rawToken(), "second-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PASSWORD_RESET_TOKEN);

        assertThat(competingResetStarted).isTrue();
        transactionTemplate.executeWithoutResult(ignored -> {
            User user = userRepository.findById(fixture.userId()).orElseThrow();
            assertThat(user.getPassword()).isEqualTo("first-password-hash");
            assertThat(user.getSecurityVersion()).isEqualTo(1L);
            assertThat(passwordResetTokenRepository.findById(fixture.tokenId()).orElseThrow().getIsUsed()).isTrue();
            assertThat(passwordResetTokenRepository.findById(fixture.pendingTokenId()).orElseThrow().getIsUsed()).isTrue();
        });
        verify(passwordHistoryPolicy, never()).validateNotRecentlyUsed(any(User.class), eq("second-password"));
        verify(passwordHistoryPolicy).record(any(User.class), eq("first-password-hash"));
        verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(any(User.class));
    }

    @Test
    void resetPasswordWithToken_passwordPolicyFailureRollsBackTokenConsumption() {
        Fixture fixture = persistFixture(true);
        PasswordResetService service = service(userRepository);
        doThrow(new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED))
                .when(passwordHistoryPolicy).validateNotRecentlyUsed(any(User.class), eq("recent-password"));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(ignored ->
                service.resetPasswordWithToken(fixture.rawToken(), "recent-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);

        transactionTemplate.executeWithoutResult(ignored -> {
            User user = userRepository.findById(fixture.userId()).orElseThrow();
            assertThat(user.getPassword()).isEqualTo("original-password-hash");
            assertThat(user.getSecurityVersion()).isZero();
            assertThat(passwordResetTokenRepository.findById(fixture.tokenId()).orElseThrow().getIsUsed()).isFalse();
            assertThat(passwordResetTokenRepository.findById(fixture.pendingTokenId()).orElseThrow().getIsUsed()).isFalse();
        });
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(any(User.class));
    }

    private PasswordResetService service(UserRepository users) {
        VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
        Clock clock = Clock.systemUTC();
        PasswordResetTokenOrchestrationService orchestrationService = new PasswordResetTokenOrchestrationService(
                passwordResetTokenRepository, users, mock(AuthMailDeliveryOrchestrationService.class),
                transactionTemplate, tokenHashService, verificationCodeService, verificationCodeRepository, clock);
        // The test explicitly opens transactions around this service's command, just as its Spring proxy does.
        return new PasswordResetService(
                users, verificationCodeService, passwordResetTokenRepository, verificationCodeRepository,
                passwordHistoryPolicy, refreshTokenLifecycleService, tokenHashService, orchestrationService,
                transactionTemplate, new AuthAccountEligibilityPolicy(), clock,
                new StaticMessageSource(), userSettingsRepository);
    }

    private Fixture persistFixture(boolean linkedVerification) {
        persistedFixture = transactionTemplate.execute(ignored -> {
            String identifier = UUID.randomUUID().toString();
            User user = User.builder()
                    .loginId(identifier.substring(0, 30))
                    .email(identifier + "@example.com")
                    .password("original-password-hash")
                    .displayName("Reset persistence user")
                    .build();
            entityManager.persist(user);
            VerificationCode verificationCode = null;
            if (linkedVerification) {
                verificationCode = VerificationCode.builder()
                        .email(user.getEmail())
                        .purpose(VerificationPurpose.PASSWORD_RESET)
                        .code("a".repeat(64))
                        .expiryDate(LocalDateTime.now(Clock.systemUTC()).plusHours(1))
                        .build();
                entityManager.persist(verificationCode);
            }
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(user)
                    .verificationCode(verificationCode)
                    .token(tokenHashService.hashSha256(identifier))
                    .expiryDate(LocalDateTime.now(Clock.systemUTC()).plusHours(1))
                    .build();
            token.markSent();
            entityManager.persist(token);
            PasswordResetToken pendingToken = PasswordResetToken.builder()
                    .user(user)
                    .token(tokenHashService.hashSha256(identifier + "-pending"))
                    .expiryDate(LocalDateTime.now(Clock.systemUTC()).plusHours(1))
                    .build();
            entityManager.persist(pendingToken);
            entityManager.flush();
            return new Fixture(user.getUserId(), token.getTokenId(), pendingToken.getTokenId(),
                    verificationCode == null ? null : verificationCode.getVerificationId(), identifier);
        });
        return persistedFixture;
    }

    private record Fixture(Long userId, Long tokenId, Long pendingTokenId, Long verificationId, String rawToken) {
    }
}
