package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.shop.event.ShopItemSaleStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(ShopItemSaleStatusChangedEventIntegrationTest.TestConfig.class)
class ShopItemSaleStatusChangedEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NotificationSseEmitterRegistry notificationSseEmitterRegistry;

    @BeforeEach
    void setUp() {
        clearInvocations(notificationSseEmitterRegistry);
    }

    @Test
    void publishesOnDedicatedExecutorAfterCommit() {
        ShopItemSaleStatusChangedEvent event = event(false);
        AtomicReference<String> publisherThread = new AtomicReference<>();
        doAnswer(invocation -> {
            publisherThread.set(Thread.currentThread().getName());
            return null;
        }).when(notificationSseEmitterRegistry).publishShopItemSaleStatusChanged(event);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                applicationEventPublisher.publishEvent(event));

        verify(notificationSseEmitterRegistry, timeout(2_000))
                .publishShopItemSaleStatusChanged(event);
        assertThat(publisherThread.get()).startsWith("test-stream-");
    }

    @Test
    void doesNotPublishAfterRollback() {
        ShopItemSaleStatusChangedEvent event = event(true);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            applicationEventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(notificationSseEmitterRegistry, after(250).never())
                .publishShopItemSaleStatusChanged(event);
    }

    private ShopItemSaleStatusChangedEvent event(boolean saleEnabled) {
        return new ShopItemSaleStatusChangedEvent(3L, "EMOTICON", 9L, saleEnabled);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        NotificationSseEmitterRegistry notificationSseEmitterRegistry() {
            return mock(NotificationSseEmitterRegistry.class);
        }

        @Bean
        ShopItemSaleStatusChangedEventListener shopItemSaleStatusChangedEventListener(
                NotificationSseEmitterRegistry notificationSseEmitterRegistry) {
            return new ShopItemSaleStatusChangedEventListener(notificationSseEmitterRegistry);
        }

        @Bean(name = "streamTaskExecutor")
        ThreadPoolTaskExecutor streamTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("test-stream-");
            executor.initialize();
            return executor;
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource is required; transaction synchronization is the behavior under test.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No resource is required; AbstractPlatformTransactionManager triggers after-commit callbacks.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No resource is required; rollback must suppress AFTER_COMMIT listeners.
        }
    }
}
