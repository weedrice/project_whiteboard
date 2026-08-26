package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.inquiry.dto.InquiryCreateRequest;
import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryMessageRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryCommandService;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryReadService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        InquiryCommandService.class,
        InquiryUserAdapter.class,
        QuerydslConfig.class,
        InquiryActiveLimitConcurrencyTest.TestBeans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InquiryActiveLimitConcurrencyTest {

    @Autowired
    private InquiryCommandService commandService;
    @Autowired
    private InquiryRepository inquiryRepository;
    @Autowired
    private InquiryMessageRepository messageRepository;
    @Autowired
    private InquiryHistoryRepository historyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private InquiryFilePort filePort;
    @MockitoBean
    private InquiryNotificationPort notificationPort;
    @MockitoBean
    private InquiryReadService readService;
    @MockitoBean
    private SuperAdminPolicy superAdminPolicy;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            historyRepository.deleteAllInBatch();
            messageRepository.deleteAllInBatch();
            inquiryRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });
    }

    @Test
    void concurrentCreatesCannotExceedFiveActiveInquiries() throws Exception {
        Long userId = transactionTemplate.execute(status -> {
            User user = userRepository.saveAndFlush(User.builder()
                    .loginId("inquiry-limit-user")
                    .password("encoded-password")
                    .email("inquiry-limit@example.com")
                    .displayName("limit-user")
                    .build());
            LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
            inquiryRepository.saveAllAndFlush(List.of(
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-1", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-2", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-3", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-4", now)));
            return user.getUserId();
        });
        InquiryCreateRequest request = new InquiryCreateRequest(
                InquiryCategory.TECHNICAL, "concurrent", "content", List.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> first = executor.submit(() -> createAndCapture(userId, request, ready, start));
            Future<Throwable> second = executor.submit(() -> createAndCapture(userId, request, ready, start));
            ready.await();
            start.countDown();

            List<Throwable> outcomes = Arrays.asList(first.get(), second.get());
            assertThat(outcomes).filteredOn(it -> it == null).hasSize(1);
            assertThat(outcomes).filteredOn(BusinessException.class::isInstance)
                    .singleElement()
                    .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED));
        }

        long activeCount = transactionTemplate.execute(status ->
                inquiryRepository.countByAuthorUserIdAndStatusIn(
                        userId,
                        List.of(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS, InquiryStatus.RESOLVED)));
        assertThat(activeCount).isEqualTo(5);
    }

    @Test
    void concurrentCreateAndAdminReopenCannotExceedFiveActiveInquiries() throws Exception {
        Long[] ids = transactionTemplate.execute(status -> {
            User user = userRepository.saveAndFlush(User.builder()
                    .loginId("inquiry-reopen-limit-user")
                    .password("encoded-password")
                    .email("inquiry-reopen-limit@example.com")
                    .displayName("reopen-limit-user")
                    .build());
            LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
            inquiryRepository.saveAllAndFlush(List.of(
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-1", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-2", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-3", now),
                    new Inquiry(user.getUserId(), InquiryCategory.OTHER, "existing-4", now)));
            Inquiry closed = new Inquiry(user.getUserId(), InquiryCategory.OTHER, "closed", now);
            closed.closeByAdmin(user.getUserId(), now);
            inquiryRepository.saveAndFlush(closed);
            return new Long[]{user.getUserId(), closed.getInquiryId()};
        });
        InquiryCreateRequest request = new InquiryCreateRequest(
                InquiryCategory.TECHNICAL, "concurrent reopen", "content", List.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> create = executor.submit(() -> createAndCapture(ids[0], request, ready, start));
            Future<Throwable> reopen = executor.submit(() -> reopenAndCapture(ids[0], ids[1], ready, start));
            ready.await();
            start.countDown();

            List<Throwable> outcomes = Arrays.asList(create.get(), reopen.get());
            assertThat(outcomes).filteredOn(it -> it == null).hasSize(1);
            assertThat(outcomes).filteredOn(BusinessException.class::isInstance)
                    .singleElement()
                    .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED));
        }

        long activeCount = transactionTemplate.execute(status ->
                inquiryRepository.countByAuthorUserIdAndStatusIn(
                        ids[0],
                        List.of(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS, InquiryStatus.RESOLVED)));
        assertThat(activeCount).isEqualTo(5);
    }

    private Throwable createAndCapture(
            Long userId,
            InquiryCreateRequest request,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            commandService.create(userId, request);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private Throwable reopenAndCapture(
            Long adminUserId,
            Long inquiryId,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            commandService.reopenByAdmin(adminUserId, inquiryId);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
