package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(SessionRevocationStreamIntegrationTest.TestConfig.class)
class SessionRevocationStreamIntegrationTest {

    @Autowired private RefreshTokenLifecycleService lifecycleService;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private NotificationStreamControl streams;
    @Autowired private PushSubscriptionRepository subscriptions;
    @Autowired private PushDeliveryJobRepository jobs;
    @Autowired private PlatformTransactionManager transactionManager;

    private User user;

    @BeforeEach
    void setUp() {
        reset(users, tokens, streams, subscriptions, jobs);
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void credentialRevocationDisconnectsOnlyTheTargetUserAfterOuterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lifecycleService.revokeActiveRefreshTokens(user);

            verifyNoInteractions(streams);
        });

        verify(streams).disconnectUser(7L);
        verifyNoMoreInteractions(streams);
        verifyNoInteractions(subscriptions, jobs);
    }

    @Test
    void failedPasswordTransactionKeepsExistingStreamsConnected() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lifecycleService.revokeActiveRefreshTokens(user);
            status.setRollbackOnly();
        });

        verifyNoInteractions(streams, subscriptions, jobs);
    }

    @Test
    void prelockedRevocationAlsoDisconnectsAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lifecycleService.revokeActiveRefreshTokensForLockedUser(user);

            verifyNoInteractions(streams);
        });

        verify(streams).disconnectUser(7L);
        verifyNoInteractions(users, subscriptions, jobs);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({RefreshTokenLifecycleService.class, NotificationAccessInvalidationService.class,
            NotificationStreamInvalidationListener.class})
    static class TestConfig {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean RefreshTokenRepository tokens() { return mock(RefreshTokenRepository.class); }
        @Bean NotificationStreamControl streams() { return mock(NotificationStreamControl.class); }
        @Bean PushSubscriptionRepository subscriptions() { return mock(PushSubscriptionRepository.class); }
        @Bean PushDeliveryJobRepository jobs() { return mock(PushDeliveryJobRepository.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }

        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(EmbeddedDatabase dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
